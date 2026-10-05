package io.github.bytex0.oss.model;

import lombok.Data;
import lombok.experimental.Accessors;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.ArrayList;
import java.util.List;

/**
 * 对象存储分片(ChunkMergeDTO)合并参数
 *
 * @author linshiqiang
 * @since 2026-10-05 14:42:56
 */
@Data
@Accessors(chain = true)
public class ChunkMergeDTO {

    /**
     * S3 分片任务 ID
     */
    private String uploadId;

    /**
     * 目标桶名称
     */
    private String bucketName;

    /**
     * 目标对象名称
     */
    private String objectName;

    /**
     * 已上传分片编号与 ETag；合并时校验重复编号并自动排序
     */
    private List<CompletedPart> chunkList = new ArrayList<>();
}
