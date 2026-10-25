package io.github.bytex0.common.log.config;

import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 操作日志安全与容量配置；属性激活和注解激活均使用同一限制。
 *
 * @author bytex0
 * @since 2026-10-06 18:55:22
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "common-tool.operation-log")
public class OperationLogProperties {

    /**
     * 是否通过属性激活，默认 false；注解可在属性未设置时激活，显式 false 可关闭注解激活。
     */
    private boolean enabled;

    /**
     * 单个捕获 JSON 的字符上限，默认 4096，范围 128 到 1048576。
     */
    private int maxPayloadLength = 4096;

    /**
     * JSON 最大嵌套深度，默认 16，范围 1 到 100。
     */
    private int maxCaptureDepth = 16;

    /**
     * 元数据字段最长字符数，默认 1024，范围 1 到 16384。
     */
    private int maxMetadataLength = 1024;

    /**
     * 内置日志处理器是否输出已过滤的参数和响应，默认 false，仅输出操作元数据。
     */
    private boolean logPayloads;

    /**
     * 是否信任代理头，默认 false；仅在入口代理已删除并重建外部转发头时启用。
     */
    private boolean trustProxyHeaders;

    /**
     * 额外敏感字段片段，忽略大小写和分隔符；内置密码、密钥和令牌规则不能被移除。
     */
    private Set<String> sensitiveFields = Set.of();
}
