package io.github.bytex0.util;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.MDC;

/**
 * 请求追踪标识工具，保留 requestId MDC 键与原有重载。
 *
 * @author bytex0
 * @since 2026-10-06 14:41:36
 */
public final class TraceIdUtil {

    /**
     * 与原版一致的 MDC 请求标识键。
     */
    public static final String TRACE_ID = "requestId";

    /**
     * 构建追踪标识时的默认分隔符。
     */
    private static final String DEFAULT_SEPARATOR = "@";

    /**
     * 构建并写入 MDC 时的默认分隔符。
     */
    private static final String MDC_SEPARATOR = " ";

    /**
     * 保留原公开无参构造入口。
     */
    public TraceIdUtil() {
    }

    /**
     * 使用 @ 拼接五个业务字段，null 字段表示为文本 null。
     *
     * @param requestId 请求标识
     * @param messageType 消息类型
     * @param companyCode 企业编码
     * @param agentId 坐席标识
     * @param extensionId 扩展标识
     * @return 拼接结果
     */
    public static String buildTraceId(String requestId, String messageType, String companyCode,
                                     String agentId, String extensionId) {
        return buildTraceId(DEFAULT_SEPARATOR,
                new Object[]{requestId, messageType, companyCode, agentId, extensionId});
    }

    /**
     * 使用 @ 拼接参数。
     *
     * @param parameters 非空参数数组
     * @return 拼接结果
     */
    public static String buildTraceId(Object... parameters) {
        return buildTraceId(DEFAULT_SEPARATOR, parameters);
    }

    /**
     * 使用指定分隔符拼接参数，参数值 null 转为文本 null。
     *
     * @param separator 分隔符，为空时表示文本 null
     * @param parameters 非空参数数组
     * @return 拼接结果，空数组返回空字符串
     */
    public static String buildTraceId(String separator, Object... parameters) {
        Objects.requireNonNull(parameters, "parameters");
        return Arrays.stream(parameters).map(String::valueOf)
                .collect(Collectors.joining(String.valueOf(separator)));
    }

    /**
     * 使用空格拼接参数并写入 MDC。
     *
     * @param parameters 非空参数数组
     */
    public static void buildAndSetTraceId(Object... parameters) {
        buildAndSetTraceId(MDC_SEPARATOR, parameters);
    }

    /**
     * 使用指定分隔符拼接并写入 MDC，修复原分隔符参数不生效的问题。
     *
     * @param separator 分隔符
     * @param parameters 非空参数数组
     */
    public static void buildAndSetTraceId(String separator, Object... parameters) {
        MDC.put(TRACE_ID, buildTraceId(separator, parameters));
    }

    /**
     * 获取当前请求标识。
     *
     * @return 标识，不存在时为空字符串
     */
    public static String getTraceId() {
        String value = MDC.get(TRACE_ID);
        return value == null ? "" : value;
    }

    /**
     * 设置非空请求标识；null 和空字符串沿用原版忽略语义。
     *
     * @param traceId 请求标识
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(TRACE_ID, traceId);
        }
    }

    /**
     * 使用 @ 拼接参数后设置标识。
     *
     * @param parameters 非空参数数组
     */
    public static void setTraceId(Object... parameters) {
        setTraceId(buildTraceId(parameters));
    }

    /**
     * 仅移除请求标识，保留其他 MDC 字段。
     */
    public static void remove() {
        MDC.remove(TRACE_ID);
    }

    /**
     * 清空当前线程全部 MDC 字段，调用方需明确拥有该上下文。
     */
    public static void clear() {
        MDC.clear();
    }

    /**
     * 获取与 MDC 独立的快照。
     *
     * @return 快照，当前没有上下文时可为 null
     */
    public static Map<String, String> getMdcContextMap() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * 生成无连字符的随机 UUID。
     *
     * @return 32 位十六进制标识
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
