package io.github.bytex0.oss.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 分片合并(CompleteUploadRequest)接口参数
 *
 * @author linshiqiang
 * @since 2026-10-05 14:55:00
 */
@Getter
@Setter
public class CompleteUploadRequest {

    /**
     * 对象名称
     */
    @NotBlank
    private String objectName;

    /**
     * 分片上传任务 ID
     */
    @NotBlank
    private String uploadId;

    /**
     * 上传完成的分片清单
     */
    @NotEmpty
    @Size(max = 10000)
    @Valid
    private List<@NotNull PartDTO> parts;

    /**
     * 已上传分片(PartDTO)接口模型，与 SDK 内部类型隔离
     *
     * @author linshiqiang
     * @since 2026-10-05 14:55:00
     */
    @Getter
    @Setter
    public static class PartDTO {

        /**
         * 分片编号
         */
        @NotNull
        @Min(1)
        @Max(10000)
        private Integer partNumber;

        /**
         * S3 返回的分片 ETag
         */
        @NotBlank
        @JsonProperty("eTag")
        private String eTag;
    }
}
