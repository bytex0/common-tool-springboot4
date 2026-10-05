package io.github.bytex0.oss.example.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 元数据更新(MetadataUpdateRequest)演示完整标准头替换，null 标准头表示删除。
 *
 * @author linshiqiang
 * @since 2026-10-06 02:28:10
 */
@Data
public class MetadataUpdateRequest {

    /**
     * 完整用户元数据，默认空映射表示清空，不允许 null。
     */
    @NotNull
    private Map<String, String> metadata = Map.of();

    /**
     * MIME 类型，null 使用服务端复制默认行为。
     */
    private String contentType;

    /**
     * 缓存控制标准头，null 删除原值。
     */
    private String cacheControl;

    /**
     * 下载内容处置标准头，null 删除原值。
     */
    private String contentDisposition;

    /**
     * 内容编码，必须与真实内容相符，null 删除原值。
     */
    private String contentEncoding;

    /**
     * 内容语言，null 删除原值。
     */
    private String contentLanguage;

    /**
     * HTTP 缓存到期时刻，不是对象生命周期删除时间，null 删除原值。
     */
    private Instant expires;
}
