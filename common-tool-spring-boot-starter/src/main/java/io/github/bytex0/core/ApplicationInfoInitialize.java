package io.github.bytex0.core;

import io.github.bytex0.util.NetworkUtil;
import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * 应用信息(ApplicationInfoInitialize)启动日志初始化
 *
 * @author bytex0
 * @since 2026-10-05 14:26:50
 */
public class ApplicationInfoInitialize implements ApplicationListener<ApplicationReadyEvent> {

    /**
     * 应用启动日志
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationInfoInitialize.class);

    /**
     * 当前应用运行环境
     */
    private final Environment environment;

    /**
     * 保留原无参构造入口，使用事件所属应用环境。
     */
    public ApplicationInfoInitialize() {
        this.environment = null;
    }

    /**
     * 绑定当前应用环境，避免跨上下文重复输出。
     *
     * @param environment 当前环境
     */
    public ApplicationInfoInitialize(Environment environment) {
        this.environment = environment;
    }

    /**
     * 输出所属环境的名称、激活配置和访问入口，不输出中间件地址或凭据。
     *
     * @param event 应用就绪事件
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment current = event.getApplicationContext().getEnvironment();
        if (environment != null && current != environment) {
            return;
        }
        String applicationName = current.getProperty("spring.application.name", "application");
        String[] profiles = current.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = current.getDefaultProfiles();
        }
        LOGGER.info("Application ready: name={}, profiles={}", applicationName, String.join(",", profiles));
        logAccessUrls(current);
    }

    /**
     * 输出原版访问入口，优先使用实际监听端口，不打印 Nacos 配置正文或连接凭据。
     * 非 Web 应用没有端口时不构造 URL，诊断配置错误也不阻止应用就绪。
     *
     * @param current 当前应用环境
     */
    private void logAccessUrls(Environment current) {
        String configuredPort = current.getProperty("local.server.port", current.getProperty("server.port"));
        if (configuredPort == null) {
            return;
        }
        try {
            int port = Integer.parseInt(configuredPort);
            if (port <= 0) {
                return;
            }
            boolean ssl = current.getProperty("server.ssl.enabled", Boolean.class, false);
            String scheme = ssl ? "https" : "http";
            String host = current.getProperty("server.address");
            if (host == null || "0.0.0.0".equals(host) || "::".equals(host)) {
                host = NetworkUtil.getLocalIp();
            }
            String basePath = current.getProperty("server.servlet.context-path", "");
            int managementPort = current.getProperty("local.management.port", Integer.class, port);
            boolean separateManagement = managementPort != port;
            String managementHost = separateManagement ? current.getProperty("management.server.address", host) : host;
            String managementScheme = (separateManagement
                    ? current.getProperty("management.server.ssl.enabled", Boolean.class, ssl) : ssl)
                    ? "https" : "http";
            String managementPath = managementPort == port ? basePath
                    : current.getProperty("management.server.base-path", "");
            String actuatorPath = current.getProperty("management.endpoints.web.base-path", "/actuator");
            LOGGER.info("Application access URLs: version={}, health={}, prometheus={}, docs={}",
                    accessUrl(scheme, host, port, basePath, "/get-version"),
                    accessUrl(managementScheme, managementHost, managementPort, managementPath, actuatorPath + "/health"),
                    accessUrl(managementScheme, managementHost, managementPort, managementPath,
                            actuatorPath + "/prometheus"),
                    accessUrl(scheme, host, port, basePath, "/doc.html"));
        } catch (IllegalArgumentException | IllegalStateException | URISyntaxException exception) {
            LOGGER.warn("Application access URL construction failed: {}", exception.getClass().getSimpleName());
        }
    }

    /**
     * 使用 URI API 正确处理 IPv6、上下文路径和字符转义。
     *
     * @param scheme HTTP 协议
     * @param host 主机或字面地址
     * @param port 实际监听端口
     * @param basePath 应用上下文路径
     * @param endpoint 以斜杠开头的端点路径
     * @return 编码后的访问 URL
     * @throws URISyntaxException 配置不能构成合法 URI
     */
    private String accessUrl(String scheme, String host, int port, String basePath, String endpoint)
            throws URISyntaxException {
        String normalized = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        return new URI(scheme, null, host, port, normalized + endpoint, null, null).toASCIIString();
    }
}
