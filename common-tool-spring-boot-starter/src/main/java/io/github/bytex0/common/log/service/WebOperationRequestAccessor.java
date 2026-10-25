package io.github.bytex0.common.log.service;

import io.github.bytex0.common.log.model.OperationLogInfo;
import io.github.bytex0.util.NetworkUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

/**
 * 可选 Servlet 请求适配器，仅读取元数据和绑定参数注解，不读取请求流。
 *
 * @author bytex0
 * @since 2026-10-06 18:55:22
 */
public class WebOperationRequestAccessor implements OperationRequestAccessor {

    /**
     * 最大允许的元数据长度。
     */
    private static final int MAX_LIMIT = 16384;

    /**
     * 显式信任时兼容原有代理头，并额外支持常见 X-Real-IP。
     */
    private static final List<String> PROXY_HEADERS = List.of("X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
            "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR");

    /**
     * 当前元数据截取上限。
     */
    private final int maxLength;

    /**
     * 是否显式启用受控代理头解析。
     */
    private final boolean trustProxyHeaders;

    /**
     * 创建元数据访问器。
     *
     * @param maxLength 字符限制，范围 1 到 16384
     */
    public WebOperationRequestAccessor(int maxLength) {
        this(maxLength, false);
    }

    /**
     * 创建具有显式代理信任策略的访问器。
     *
     * @param maxLength 元数据字符限制
     * @param trustProxyHeaders 是否信任经入口代理重建的转发头
     */
    public WebOperationRequestAccessor(int maxLength, boolean trustProxyHeaders) {
        if (maxLength <= 0 || maxLength > MAX_LIMIT) {
            throw new IllegalArgumentException("Invalid operation metadata length limit");
        }
        this.maxLength = maxLength;
        this.trustProxyHeaders = trustProxyHeaders;
    }

    /**
     * 填充当前请求元数据，非 Servlet 上下文保持为空。
     *
     * @param info 日志模型
     */
    @Override
    public void populate(OperationLogInfo info) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            info.setRequestUri(limit(request.getRequestURI()))
                    .setRequestMethod(limit(request.getMethod()))
                    .setRequestIp(limit(clientAddress(request)))
                    .setUserAgent(limit(request.getHeader("User-Agent")));
        }
    }

    /**
     * 排除请求、响应、会话、上传对象和绑定结果。
     *
     * @param argument 方法参数
     * @return 是否属于不可展开对象
     */
    @Override
    public boolean excludeArgument(Object argument) {
        return argument instanceof ServletRequest || argument instanceof ServletResponse || argument instanceof HttpSession
                || argument instanceof MultipartFile || argument instanceof BindingResult;
    }

    /**
     * 获取已经完成数据绑定的 RequestBody 参数位置。
     *
     * @param method 业务方法
     * @return 请求体参数索引
     */
    @Override
    public Set<Integer> requestBodyIndexes(Method method) {
        Set<Integer> result = new HashSet<>();
        Parameter[] parameters = method.getParameters();
        for (int index = 0; index < parameters.length; index++) {
            if (parameters[index].isAnnotationPresent(RequestBody.class)) {
                result.add(index);
            }
        }
        return result;
    }

    /**
     * 默认读取实际对端，仅在明确授权时按原顺序读取有效代理头。
     *
     * @param request 请求
     * @return 客户端地址
     */
    private String clientAddress(HttpServletRequest request) {
        if (trustProxyHeaders) {
            for (String header : PROXY_HEADERS) {
                String value = request.getHeader(header);
                if (value != null) {
                    String first = value.split(",", 2)[0].trim();
                    if (!first.isEmpty() && !"unknown".equalsIgnoreCase(first)) {
                        return first;
                    }
                }
            }
        }
        return NetworkUtil.getClientIp(request);
    }

    /**
     * 限制元数据长度，保持 null 语义。
     *
     * @param value 元数据文本
     * @return 限长文本
     */
    private String limit(String value) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return value.substring(0, end);
    }
}
