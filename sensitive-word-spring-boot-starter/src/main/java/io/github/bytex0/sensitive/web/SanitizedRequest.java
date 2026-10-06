package io.github.bytex0.sensitive.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MVC请求视图(SanitizedRequest)保存已处理参数和有界请求体，不修改容器原参数数组。
 *
 * @author linshiqiang
 * @since 2026-10-06 09:10:08
 */
final class SanitizedRequest extends HttpServletRequestWrapper {

    /**
     * 当前请求拥有的参数数组。
     */
    private final Map<String, String[]> parameters;

    /**
     * 可为空的处理后请求体，null 表示委托容器原流。
     */
    private final byte[] body;

    /**
     * 请求体编码。
     */
    private final Charset charset;

    /**
     * 接管过滤阶段新建的参数和字节数组，不接管原请求流关闭。
     *
     * @param request 原请求
     * @param parameters 已复制的参数
     * @param body 请求体
     * @param charset 字符集
     */
    SanitizedRequest(HttpServletRequest request, Map<String, String[]> parameters, byte[] body, Charset charset) {
        super(request);
        this.parameters = parameters;
        this.body = body;
        this.charset = charset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getParameter(String name) {
        String[] values = parameters.get(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    /**
     * 返回独立数组，避免下游修改本视图。
     *
     * @param name 参数名
     * @return 副本或 null
     */
    @Override
    public String[] getParameterValues(String name) {
        String[] values = parameters.get(name);
        return values == null ? null : values.clone();
    }

    /**
     * 返回包含独立数组的不可变映射。
     *
     * @return 参数快照
     */
    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> copy = new LinkedHashMap<>();
        parameters.forEach((name, values) -> copy.put(name, values.clone()));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameters.keySet());
    }

    /**
     * 返回可重复读取的处理后字节；未读取的二进制/表单请求仍委托原流。
     *
     * @return 输入流
     * @throws IOException 获取原请求流失败
     */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (body == null) {
            return super.getInputStream();
        }
        ByteArrayInputStream input = new ByteArrayInputStream(body);
        return new ServletInputStream() {

            /**
             * {@inheritDoc}
             */
            @Override
            public int read() {
                return input.read();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int read(byte[] bytes, int offset, int length) {
                return input.read(bytes, offset, length);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isReady() {
                return true;
            }

            /**
             * 缓存视图用于 MVC 阻塞读取，不伪造 Servlet 非阻塞回调。
             *
             * @param listener 非阻塞监听器
             */
            @Override
            public void setReadListener(ReadListener listener) {
                throw new IllegalStateException("敏感词请求体视图仅支持MVC同步读取");
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BufferedReader getReader() throws IOException {
        return body == null ? super.getReader() : new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getContentLength() {
        return body == null ? super.getContentLength() : body.length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getContentLengthLong() {
        return body == null ? super.getContentLengthLong() : body.length;
    }

    /**
     * 使下游读取到的长度头与替换后字节数一致。
     *
     * @param name 头名称
     * @return 头值
     */
    @Override
    public String getHeader(String name) {
        if (body != null && "Transfer-Encoding".equalsIgnoreCase(name)) {
            return null;
        }
        return body != null && "Content-Length".equalsIgnoreCase(name) ? Integer.toString(body.length) : super.getHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration<String> getHeaders(String name) {
        if (body != null && "Transfer-Encoding".equalsIgnoreCase(name)) {
            return Collections.emptyEnumeration();
        }
        return body != null && "Content-Length".equalsIgnoreCase(name)
                ? Collections.enumeration(List.of(Integer.toString(body.length))) : super.getHeaders(name);
    }

    /**
     * 缓存请求体不再按分块传输，头名称与实际视图保持一致。
     *
     * @return 头名称
     */
    @Override
    public Enumeration<String> getHeaderNames() {
        if (body == null) {
            return super.getHeaderNames();
        }
        List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
        names.removeIf(name -> "Content-Length".equalsIgnoreCase(name) || "Transfer-Encoding".equalsIgnoreCase(name));
        names.add("Content-Length");
        return Collections.enumeration(names);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getIntHeader(String name) {
        return body != null && "Content-Length".equalsIgnoreCase(name) ? body.length : super.getIntHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCharacterEncoding() {
        return body == null ? super.getCharacterEncoding() : charset.name();
    }
}
