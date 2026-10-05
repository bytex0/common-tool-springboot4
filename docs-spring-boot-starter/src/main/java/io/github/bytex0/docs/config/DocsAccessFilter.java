package io.github.bytex0.docs.config;

import io.github.bytex0.docs.properties.SwaggerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 文档访问(DocsAccessFilter)仅保护文档路径，不改变业务接口认证
 *
 * @author linshiqiang
 * @since 2026-10-05 15:29:19
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class DocsAccessFilter extends OncePerRequestFilter {

    /**
     * 文档配置
     */
    private final SwaggerProperties properties;

    /**
     * OpenAPI 文档基础路径，包含可能配置的 Servlet 前缀
     */
    private final String apiPath;

    /**
     * UI 入口
     */
    private final String uiPath;

    /**
     * UI 静态资源前缀
     */
    private final String uiResources;

    /**
     * 预期 Basic 凭据，不写入日志
     */
    private final byte[] credentials;

    public DocsAccessFilter(SwaggerProperties properties, Environment environment) {
        this.properties = properties;
        String servletPath = environment.getProperty("spring.mvc.servlet.path", "").replaceAll("/+$", "");
        this.apiPath = servletPath + environment.getProperty("springdoc.api-docs.path", "/v3/api-docs");
        this.uiPath = servletPath + environment.getProperty("springdoc.swagger-ui.path", "/swagger-ui.html");
        this.uiResources = servletPath + "/swagger-ui";
        if (properties.isEnabled() && properties.isBasicAuth()) {
            Assert.hasText(properties.getUsername(), "swagger.username 不能为空");
            Assert.hasText(properties.getPassword(), "swagger.password 不能为空");
            Assert.isTrue(!properties.getUsername().contains(":"), "swagger.username 不能包含冒号");
            credentials = (properties.getUsername() + ":" + properties.getPassword()).getBytes(StandardCharsets.UTF_8);
        } else {
            credentials = new byte[0];
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        boolean docs = path.equals(apiPath) || path.startsWith(apiPath + "/") || path.equals(apiPath + ".yaml")
                || path.equals(uiPath) || path.equals(uiResources) || path.startsWith(uiResources + "/");
        if (!docs) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (!properties.isEnabled()) {
            response.setStatus(404);
            return;
        }
        if (properties.isBasicAuth() && !authorized(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"API docs\", charset=\"UTF-8\"");
            response.setStatus(401);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean authorized(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return false;
        }
        try {
            return MessageDigest.isEqual(credentials, Base64.getDecoder().decode(authorization.substring(6)));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
