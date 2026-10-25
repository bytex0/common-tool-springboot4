package io.github.bytex0.nacos;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nacos SDK 配置，不生成包含密码的 toString，默认不连接外部服务。
 *
 * @author bytex0
 * @since 2026-10-06 18:24:37
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "common-tool.nacos")
public class CommonNacosProperties {

    /**
     * 显式启用标志，默认 false；新键未配置时兼容旧 enabled=true，但不沿用默认自动连接。
     */
    private boolean enabled;

    /**
     * 服务地址，优先于旧 spring.cloud.nacos.config.server-addr 和 SDK 透传属性。
     */
    private String serverAddr;

    /**
     * 命名空间，null 时回退旧配置或 SDK 默认命名空间。
     */
    private String namespace;

    /**
     * 登录用户名，null 时回退旧配置；不输出到日志。
     */
    private String username;

    /**
     * 登录密码，null 时回退旧配置；只通过运行环境或本地未跟踪配置提供。
     */
    private String password;

    /**
     * 初始配置请求超时，单位毫秒，默认 10000，范围 1 到 120000。
     */
    private long timeout = 10000;

    /**
     * 回调队列容量，默认 128，范围 1 到 10000；满时在提交线程执行以形成背压。
     */
    private int callbackQueueCapacity = 128;

    /**
     * 其他 SDK 属性透传；显式字段覆盖同名透传值，日志全属性输出始终强制关闭。
     */
    private Map<String, String> clientProperties = new LinkedHashMap<>();
}
