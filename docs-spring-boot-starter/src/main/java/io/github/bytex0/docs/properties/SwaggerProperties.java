package io.github.bytex0.docs.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 接口文档(SwaggerProperties)展示与访问配置
 *
 * @author bytex0
 * @since 2026-10-05 15:29:19
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString(exclude = {"username", "password"})
@ConfigurationProperties("swagger")
public class SwaggerProperties {

    /**
     * 是否允许访问文档和 UI，默认 true；null 按关闭处理。
     */
    private Boolean enabled = true;

    /**
     * 文档标题，默认“API文档”。
     */
    private String title = "API文档";

    /**
     * 文档描述，默认“API接口文档”。
     */
    private String description = "API接口文档";

    /**
     * API 展示版本，默认 1.0.0，不决定依赖或 OpenAPI 规范版本。
     */
    private String version = "1.0.0";

    /**
     * 联系人姓名，可为空。
     */
    private String contactName;

    /**
     * 联系人邮箱，可为空，按原样写入元信息。
     */
    private String contactEmail;

    /**
     * 联系人网址，可为空；组件不会访问该地址。
     */
    private String contactUrl;

    /**
     * 是否对文档页面、静态资源及 JSON/YAML 要求 Basic 认证，默认 false。
     */
    private Boolean basicAuth = false;

    /**
     * 文档访问用户名，无默认值，开启认证时必须提供且不能包含冒号。
     */
    private String username;

    /**
     * 文档访问密码，无默认密码，不输出到日志
     */
    private String password;

    /**
     * knife4j.cors=true 时允许的 Origin，默认通配；携带凭据时不能使用通配。
     */
    private List<String> corsAllowedOrigins = List.of("*");

    /**
     * 是否允许文档 CORS 携带凭据，默认 false，true 时必须配置明确的 Origin 白名单。
     */
    private Boolean corsAllowCredentials = false;

    /**
     * 保留此前版本的判断入口，不干扰原 Boolean getEnabled/setEnabled 属性绑定。
     *
     * @return 非空开关值
     */
    public Boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    /**
     * 保留 Basic 认证判断入口，null 配置按关闭处理。
     *
     * @return 非空认证开关值
     */
    public Boolean isBasicAuth() {
        return Boolean.TRUE.equals(basicAuth);
    }
}
