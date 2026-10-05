package io.github.bytex0.oss.client;

import io.github.bytex0.oss.listener.CustomProgressListener;
import io.github.bytex0.oss.model.ChunkDTO;
import io.github.bytex0.oss.model.ChunkMergeDTO;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 对象存储(OssClient)S3 兼容操作接口，返回类型采用 AWS SDK v2
 *
 * @author linshiqiang
 * @since 2026-10-05 14:42:56
 */
public interface OssClient {

    /** 创建桶，已存在且属于当前账号时直接返回。 */
    void createBucket(String bucketName);

    /** 返回当前账号可见的全部桶。 */
    List<Bucket> getAllBuckets();

    /** 删除空桶，不会递归删除内容。 */
    void removeBucket(String bucketName);

    /** 上传未知长度流，临时落盘以支持签名和重试；调用方负责关闭输入流。 */
    PutObjectResponse putObject(String bucketName, String objectName, InputStream stream, String contentType)
            throws IOException;

    /** 根据对象扩展名推断类型并上传流，调用方负责关闭输入流。 */
    PutObjectResponse putObject(String bucketName, String objectName, InputStream stream) throws IOException;

    /** 上传文件，不把整个文件读入内存。 */
    PutObjectResponse putObject(String bucketName, String objectName, File file);

    /** 上传文件并同步报告进度。 */
    PutObjectResponse putObject(String bucketName, String objectName, File file, CustomProgressListener listener);

    /** 校验显式长度并上传流，调用方负责关闭输入流。 */
    PutObjectResponse putObject(String bucketName, String objectName, InputStream stream, long size,
                                String contentType, CustomProgressListener listener) throws IOException;

    /** 创建分片上传任务。 */
    CreateMultipartUploadResponse initiateMultipartUpload(String bucketName, String objectName);

    /** 上传 HTTP 分片，实际长度来自 MultipartFile。 */
    UploadPartResponse uploadPart(ChunkDTO chunk) throws IOException;

    /** 上传普通流分片，适用于非 Web 调用方；调用方负责关闭输入流。 */
    UploadPartResponse uploadPart(String bucketName, String objectName, String uploadId, int partNumber,
                                  InputStream stream, long size) throws IOException;

    /** 分页读取所有已上传分片，供断点续传使用。 */
    List<Part> listParts(String bucketName, String objectName, String uploadId);

    /** 终止任务并清理未合并的分片。 */
    void abortMultipartUpload(String bucketName, String objectName, String uploadId);

    /** 校验并按编号排序后合并分片。 */
    CompleteMultipartUploadResponse completeMultipartUpload(ChunkMergeDTO chunk);

    /** 返回响应元数据与内容流，调用方必须关闭返回流。 */
    ResponseInputStream<GetObjectResponse> getObject(String bucketName, String objectName);

    /** 生成 SigV4 下载 URL，有效期为 1 秒至 7 天。 */
    String getObjectUrl(String bucketName, String objectName, Duration expires);

    /** 删除一个对象。 */
    void removeObject(String bucketName, String objectName);

    /** 每批最多 1000 个对象；出现部分删除失败时抛出异常。 */
    void removeObjects(String bucketName, List<String> objectNames);

    /** 分页查询全部匹配对象；非递归模式仅返回当前层的文件。 */
    List<S3Object> getAllObjectsByPrefix(String bucketName, String prefix, boolean recursive);

    /** 推断 MIME 类型，支持带点的扩展名。 */
    String getContentType(String format);

    /** 返回由 Spring 管理生命周期的底层 SDK v2 客户端。 */
    S3Client getS3Client();

    /** 下载对象流，调用方必须关闭返回流。 */
    InputStream downloadObject(String bucketName, String objectName);

    /** 下载成功后替换目标文件，失败时保留原文件并清理临时文件。 */
    void downloadObject(String bucketName, String objectName, CustomProgressListener listener, File destination)
            throws IOException;

    /** 获取对象大小、类型和用户元数据。 */
    HeadObjectResponse getObjectMetadata(String bucketName, String objectName);

    /** 替换用户元数据；contentType 为 null 时保留原类型及其他标准响应头。 */
    void updateObjectMetadata(String bucketName, String objectName, Map<String, String> metadata, String contentType);
}
