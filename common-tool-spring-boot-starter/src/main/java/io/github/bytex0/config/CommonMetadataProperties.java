package io.github.bytex0.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 元数据接口配置，默认关闭，避免仅引入基础库就公开应用构建信息。
 *
 * @author bytex0
 * @since 2026-10-06 18:02:16
 */
@Data
@ConfigurationProperties(prefix = "common-tool.metadata")
public class CommonMetadataProperties {

    /**
     * 是否创建 Git、版本及 ping 接口，默认 false，仍受基础总开关控制。
     */
    private boolean enabled;

    /**
     * 是否展示全部 git.properties 字段，默认 false；开启时应自行配置接口访问控制。
     */
    private boolean includeGitDetails;

    /**
     * 单个元数据资源读取上限，默认一 MiB，范围 1 到十六 MiB。
     */
    private int maxResourceBytes = 1024 * 1024;
}
