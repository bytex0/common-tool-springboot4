package io.github.bytex0.common.log.aspect;

import io.github.bytex0.common.log.annotation.OperationLog;
import io.github.bytex0.common.log.config.OperationLogProperties;
import io.github.bytex0.common.log.model.OperationLogInfo;
import io.github.bytex0.common.log.service.OperationLogHandler;
import io.github.bytex0.common.log.service.OperationLogSanitizer;
import io.github.bytex0.common.log.service.OperationRequestAccessor;
import io.github.bytex0.common.log.service.OperatorInfoProvider;
import io.github.bytex0.util.TraceIdUtil;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.support.AopUtils;

/**
 * 操作日志切面，业务只执行一次，采集或处理器故障不改变业务返回值和原始异常。
 *
 * @author bytex0
 * @since 2026-10-06 19:01:01
 */
@Aspect
public class OperationLogAspect {

    /**
     * 成功协议值。
     */
    private static final int SUCCESS = 0;

    /**
     * 失败协议值。
     */
    private static final int FAILURE = 1;

    /**
     * 默认元数据长度。
     */
    private static final int DEFAULT_METADATA_LENGTH = 1024;

    /**
     * 最大元数据长度。
     */
    private static final int MAX_METADATA_LENGTH = 16384;

    /**
     * 只记录采集故障类型，不记录可能包含凭据的异常消息。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLogAspect.class);

    /**
     * 应用日志处理器。
     */
    private final OperationLogHandler handler;

    /**
     * 当前操作者信息提供者。
     */
    private final OperatorInfoProvider provider;

    /**
     * 有界安全捕获器。
     */
    private final OperationLogSanitizer sanitizer;

    /**
     * 可选请求上下文访问器。
     */
    private final OperationRequestAccessor requestAccessor;

    /**
     * 元数据字符串长度限制。
     */
    private final int maxMetadataLength;

    /**
     * 防止日志处理器或提供者自身触发无限日志递归，每次使用后移除。
     */
    private final ThreadLocal<Boolean> infrastructureCall = new ThreadLocal<>();

    /**
     * 保留原两个依赖的构造入口，默认不依赖 Servlet。
     *
     * @param handler 日志处理器
     * @param provider 操作者提供者
     */
    public OperationLogAspect(OperationLogHandler handler, OperatorInfoProvider provider) {
        this(handler, provider, new OperationLogSanitizer(new OperationLogProperties()),
                OperationRequestAccessor.NONE, DEFAULT_METADATA_LENGTH);
    }

    /**
     * 创建具有显式捕获策略的切面。
     *
     * @param handler 处理器
     * @param provider 操作者提供者
     * @param sanitizer 捕获过滤器
     * @param requestAccessor 请求适配器
     * @param maxMetadataLength 元数据长度，范围 1 到 16384
     */
    public OperationLogAspect(OperationLogHandler handler, OperatorInfoProvider provider,
                              OperationLogSanitizer sanitizer, OperationRequestAccessor requestAccessor,
                              int maxMetadataLength) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
        this.requestAccessor = Objects.requireNonNull(requestAccessor, "requestAccessor");
        if (maxMetadataLength <= 0 || maxMetadataLength > MAX_METADATA_LENGTH) {
            throw new IllegalArgumentException("Invalid operation metadata limit");
        }
        this.maxMetadataLength = maxMetadataLength;
    }

    /**
     * 记录已声明注解的方法调用，保留业务异常实例及返回值。
     *
     * @param point 业务连接点
     * @param operationLog 日志声明
     * @return 原业务返回值
     * @throws Throwable 原业务异常，不替换为日志异常
     */
    @Around(value = "@annotation(operationLog)", argNames = "point,operationLog")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        if (Boolean.TRUE.equals(infrastructureCall.get())) {
            return point.proceed();
        }
        long started = System.nanoTime();
        Method declared = ((MethodSignature) point.getSignature()).getMethod();
        Method specific = point.getTarget() == null ? declared
                : AopUtils.getMostSpecificMethod(declared, point.getTarget().getClass());
        String traceId = TraceIdUtil.getTraceId();
        OperationLogInfo info = new OperationLogInfo()
                .setStartTime(System.currentTimeMillis())
                .setOperationType(operationLog.type())
                .setDescription(limit(operationLog.description()))
                .setTraceId(limit(traceId.isEmpty() ? MDC.get("traceId") : traceId))
                .setStatus(FAILURE);
        captureSafely(() -> captureRequest(info, operationLog, specific, declared, point.getArgs()));
        Throwable businessFailure = null;
        try {
            Object result = point.proceed();
            info.setStatus(SUCCESS);
            if (operationLog.saveResponseData()) {
                captureSafely(() -> info.setResponseData(sanitizer.capture(result)));
            }
            return result;
        } catch (Exception exception) {
            businessFailure = exception;
            info.setErrorMessage(exception.getClass().getSimpleName());
            throw exception;
        } catch (Error error) {
            businessFailure = error;
            info.setErrorMessage(error.getClass().getSimpleName());
            throw error;
        } finally {
            info.setEndTime(System.currentTimeMillis())
                    .setDuration(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            recordSafely(info, businessFailure);
        }
    }

    /**
     * 填充请求及操作者信息，原始参数不被修改，也不重新读取 HTTP 请求流。
     *
     * @param info 日志模型
     * @param annotation 日志声明
     * @param specific 实际方法
     * @param declared 声明方法，保留接口上的 RequestBody 信息
     * @param arguments 方法参数
     */
    private void captureRequest(OperationLogInfo info, OperationLog annotation, Method specific,
                                Method declared, Object[] arguments) {
        requestAccessor.populate(info);
        info.setOperatorId(limit(provider.getOperatorId()))
                .setOperatorName(limit(provider.getOperatorName()))
                .setOperatorDeptId(limit(provider.getOperatorDeptId()))
                .setOperatorDeptName(limit(provider.getOperatorDeptName()));
        if (annotation.saveRequestData()) {
            Object[] prepared = sanitizer.prepareArguments(specific, arguments, requestAccessor);
            info.setRequestParams(sanitizer.capture(prepared));
            Set<Integer> bodyIndexes = new HashSet<>(requestAccessor.requestBodyIndexes(specific));
            bodyIndexes.addAll(requestAccessor.requestBodyIndexes(declared));
            if (!bodyIndexes.isEmpty()) {
                Object[] bodies = bodyIndexes.stream().sorted().map(index -> prepared[index]).toArray();
                info.setRequestBody(sanitizer.capture(bodies.length == 1 ? bodies[0] : bodies));
            }
        }
    }

    /**
     * 隔离采集器及身份提供者故障，并清理递归防护上下文。
     *
     * @param capture 采集动作
     */
    private void captureSafely(Runnable capture) {
        infrastructureCall.set(true);
        try {
            capture.run();
        } catch (RuntimeException | Error exception) {
            LOGGER.warn("Operation log capture failed: {}", exception.getClass().getSimpleName());
        } finally {
            infrastructureCall.remove();
        }
    }

    /**
     * 防止日志处理器替换业务结果；业务已失败时将日志故障保留为 suppressed。
     *
     * @param info 日志模型
     * @param businessFailure 原业务异常，可为空
     */
    private void recordSafely(OperationLogInfo info, Throwable businessFailure) {
        infrastructureCall.set(true);
        try {
            handler.handleLog(info);
        } catch (RuntimeException | Error exception) {
            if (businessFailure != null && exception != businessFailure) {
                businessFailure.addSuppressed(exception);
            }
            LOGGER.warn("Operation log handler failed: {}", exception.getClass().getSimpleName());
        } finally {
            infrastructureCall.remove();
        }
    }

    /**
     * 限制元数据长度，不输出完整异常对象。
     *
     * @param value 字符串
     * @return 限长值，可为空
     */
    private String limit(String value) {
        if (value == null || value.length() <= maxMetadataLength) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(maxMetadataLength - 1))
                ? maxMetadataLength - 1 : maxMetadataLength;
        return value.substring(0, end);
    }
}
