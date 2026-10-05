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
import org.springframework.util.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.jar.JarFile;

/**
 * 文档访问(DocsAccessFilter)仅保护文档路径，不改变业务接口认证
 *
 * @author bytex0
 * @since 2026-10-05 15:29:19
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class DocsAccessFilter extends OncePerRequestFilter {

    /**
     * 创建时冻结的文档开关，避免可变配置与凭据快照不同步。
     */
    private final boolean enabled;

    /**
     * 原 Knife4j 生产保护开关，对新旧文档入口一并生效。
     */
    private final boolean production;

    /**
     * 创建时验证过的认证开关；修改访问策略需重建过滤器。
     */
    private final boolean basicAuth;

    /**
     * 仅属于 Knife4j UI 制品的路径，不拦截其他业务 WebJar。
     */
    private final Set<String> knifePaths;

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

    /**
     * 校验凭据并固定访问策略，不使用上游隐含弱默认密码。
     *
     * @param properties 本项目文档配置
     * @param environment 上游及路径配置
     */
    public DocsAccessFilter(SwaggerProperties properties, Environment environment) {
        enabled = Boolean.TRUE.equals(properties.getEnabled());
        boolean knifeEnabled = environment.getProperty("knife4j.enable", Boolean.class, false);
        production = knifeEnabled && environment.getProperty("knife4j.production", Boolean.class, false);
        boolean nativeBasic = knifeEnabled && environment.getProperty("knife4j.basic.enable", Boolean.class, false);
        basicAuth = Boolean.TRUE.equals(properties.getBasicAuth()) || nativeBasic;
        String servletPath = environment.getProperty("spring.mvc.servlet.path", "").replaceAll("/+$", "");
        this.apiPath = servletPath + environment.getProperty("springdoc.api-docs.path", "/v3/api-docs");
        this.uiPath = servletPath + environment.getProperty("springdoc.swagger-ui.path", "/swagger-ui.html");
        this.uiResources = servletPath + "/swagger-ui";
        this.knifePaths = knifeResources(servletPath);
        String username = properties.getUsername();
        String password = properties.getPassword();
        if (nativeBasic) {
            String nativeUsername = environment.getProperty("knife4j.basic.username");
            String nativePassword = environment.getProperty("knife4j.basic.password");
            Assert.hasText(nativeUsername, "Knife4j Basic用户名必须显式配置");
            Assert.hasText(nativePassword, "Knife4j Basic密码必须显式配置");
            Assert.isTrue(!nativeUsername.contains(":"), "Knife4j用户名不能包含冒号");
            if (enabled && !production && Boolean.TRUE.equals(properties.getBasicAuth())) {
                Assert.isTrue(Objects.equals(username, nativeUsername) && Objects.equals(password, nativePassword),
                        "同时开启两套文档Basic认证时凭据必须一致");
            } else {
                username = nativeUsername;
                password = nativePassword;
            }
        }
        if (enabled && !production && basicAuth) {
            Assert.hasText(username, "文档用户名不能为空");
            Assert.hasText(password, "文档密码不能为空");
            Assert.isTrue(!username.contains(":"), "文档用户名不能包含冒号");
            credentials = (username + ":" + password).getBytes(StandardCharsets.UTF_8);
        } else {
            credentials = new byte[0];
        }
    }

    /**
     * 仅对文档及其资源应用认证；关闭时返回 404，认证失败返回 401。
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param chain 后续过滤器
     * @throws ServletException 后续 Servlet 处理失败
     * @throws IOException 响应写入失败
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = StringUtils.cleanPath(UrlPathHelper.defaultInstance.getPathWithinApplication(request))
                .replaceAll("/{2,}", "/");
        boolean docs = path.equals(apiPath) || path.startsWith(apiPath + "/") || path.equals(apiPath + ".yaml")
                || path.equals(uiPath) || path.equals(uiResources) || path.startsWith(uiResources + "/")
                || knifePaths.contains(path);
        if (!docs) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (!enabled) {
            response.setStatus(404);
            return;
        }
        if (production) {
            response.setStatus(403);
            return;
        }
        if (CorsUtils.isPreFlightRequest(request)) {
            chain.doFilter(request, response);
            return;
        }
        if (basicAuth && !authorized(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"API docs\", charset=\"UTF-8\"");
            response.setStatus(401);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * 对有效 Base64 凭据进行定时安全比较，不记录输入值。
     *
     * @param authorization Authorization 请求头
     * @return 凭据是否匹配
     */
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

    /**
     * 从实际 UI JAR 中读取资源清单，避免按整个 webjars 前缀误拦截业务资源。
     * 非 JAR 的资源部署仍保护文档入口和 API，静态公开库资源不包含业务文档数据。
     *
     * @param servletPath Servlet 路径前缀
     * @return 不可变的文档资源路径
     */
    private Set<String> knifeResources(String servletPath) {
        Set<String> paths = new HashSet<>();
        paths.add(servletPath + "/doc.html");
        ClassPathResource marker = new ClassPathResource(
                "META-INF/maven/com.github.xiaoymin/knife4j-openapi3-ui/pom.properties");
        if (!marker.exists()) {
            return Set.copyOf(paths);
        }
        try {
            var connection = marker.getURL().openConnection();
            connection.setUseCaches(false);
            if (connection instanceof JarURLConnection jarConnection) {
                try (JarFile jar = jarConnection.getJarFile()) {
                    String prefix = "META-INF/resources/";
                    jar.stream().filter(entry -> !entry.isDirectory() && entry.getName().startsWith(prefix))
                            .forEach(entry -> paths.add(servletPath + "/" + entry.getName().substring(prefix.length())));
                }
            }
            return Set.copyOf(paths);
        } catch (IOException exception) {
            throw new UncheckedIOException("无法读取Knife4j资源清单", exception);
        }
    }
}
