package io.github.bytex0.oss.model;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对象存储分片(ChunkDTO)上传参数
 *
 * @author linshiqiang
 * @since 2026-10-05 14:42:56
 */
@Data
public class ChunkDTO {

    /**
     * S3 分片任务 ID
     */
    private String uploadId;

    /**
     * 当前分片编号，范围为 1 至 10000
     */
    private Integer chunkNumber;

    /**
     * 计划分片大小，仅用于业务记录，实际长度以 file 为准
     */
    private Long chunkSize;

    /**
     * 当前分片大小，仅用于业务记录
     */
    private Long currentChunkSize;

    /**
     * 完整文件大小，仅用于业务记录
     */
    private Long totalSize;

    /**
     * 目标桶名称
     */
    private String bucketName;

    /**
     * 目标对象名称
     */
    private String objectName;

    /**
     * 分片总数，仅用于业务记录
     */
    private Integer totalChunks;

    /**
     * 是否为最后一片，仅用于业务记录，S3 在合并时确定最终分片
     */
    private Boolean isLastPart;

    /**
     * 分片内容，上传时使用临时文件避免整片驻留堆内存
     */
    private MultipartFile file;
}
