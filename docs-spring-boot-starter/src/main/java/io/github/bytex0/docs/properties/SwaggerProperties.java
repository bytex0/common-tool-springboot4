package io.github.bytex0.docs.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 接口文档(SwaggerProperties)展示与访问配置
 *
 * @author linshiqiang
 * @since 2026-10-05 15:29:19
 */
@Getter
@Setter
@ConfigurationProperties("swagger")
public class SwaggerProperties {

    /**
     * 是否允许访问文档和 Swagger UI
     */
    private boolean enabled = true;

    /**
     * 文档标题
     */
    private String title = "API文档";

    /**
     * 文档描述
     */
    private String description = "API接口文档";

    /**
     * API 版本
     */
    private String version = "1.0.0";

    /**
     * 联系人姓名
     */
    private String contactName;

    /**
     * 联系人邮箱
     */
    private String contactEmail;

    /**
     * 联系人地址
     */
    private String contactUrl;

    /**
     * 是否对文档页面、静态资源及 JSON/YAML 文档要求 Basic 认证
     */
    private boolean basicAuth;

    /**
     * 文档访问用户名，开启认证时必须提供
     */
    private String username;

    /**
     * 文档访问密码，无默认密码，不输出到日志
     */
    private String password;
}
