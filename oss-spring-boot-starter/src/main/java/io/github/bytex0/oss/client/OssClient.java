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
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.utils.http.SdkHttpUtils;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 对象存储(OssClient)S3 兼容操作接口，返回类型采用 AWS SDK v2
 *
 * @author bytex0
 * @since 2026-10-05 14:42:56
 */
public interface OssClient {

    /**
     * 创建桶，已存在且属于当前账号时直接返回。
     *
     * @param bucketName 非空桶名
     */
    void createBucket(String bucketName);

    /**
     * 遍历全部分页，返回当前账号可见桶。
     *
     * @return 桶列表
     */
    List<Bucket> getAllBuckets();

    /**
     * 删除空桶，不递归删除业务内容。
     *
     * @param bucketName 桶名
     */
    void removeBucket(String bucketName);

    /**
     * 上传未知长度流，临时落盘支持签名和重试；调用方关闭输入流，大文件自动分片。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @param stream 调用方持有的内容流
     * @param contentType MIME 类型，null 时按扩展名推断
     * @return 上传结果
     * @throws IOException 本地 I/O 失败
     */
    PutObjectResponse putObject(String bucketName, String objectName, InputStream stream, String contentType)
            throws IOException;

    /**
     * 按扩展名推断类型并上传流。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @param stream 调用方负责关闭的内容流
     * @return 上传结果
     * @throws IOException 本地 I/O 失败
     */
    PutObjectResponse putObject(String bucketName, String objectName, InputStream stream) throws IOException;

    /**
     * 上传文件，大文件自动分片，内存使用不随文件大小增长。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @param file 源文件，传输期间不得修改
     * @return 上传结果
     */
    PutObjectResponse putObject(String bucketName, String objectName, File file);

    /**
     * 上传文件并同步报告进度，服务端确认完成后才报告 100%。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @param file 源文件，传输期间不得修改
     * @param listener 可选回调，不应阻塞
     * @return 上传结果
     */
    PutObjectResponse putObject(String bucketName, String objectName, File file, CustomProgressListener listener);

    /**
     * 校验显式长度并上传流，长度不符时不发起远程上传。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @param stream 调用方负责关闭的流
     * @param size 声明长度，字节，非负
     * @param contentType MIME 类型
     * @param listener 可选进度回调
     * @return 上传结果
     * @throws IOException 本地 I/O 或长度校验失败
     */
    PutObjectResponse putObject(String bucketName, String objectName, InputStream stream, long size,
                                String contentType, CustomProgressListener listener) throws IOException;

    /**
     * 创建手工分片任务，调用方须合并或取消，不能永久遗留任务。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @return 包含上传 ID 的结果
     */
    CreateMultipartUploadResponse initiateMultipartUpload(String bucketName, String objectName);

    /**
     * 上传 HTTP 分片，实际长度来自 MultipartFile，内部打开的流由组件关闭。
     *
     * @param chunk 分片信息
     * @return 当前分片 ETag
     * @throws IOException 读取内容失败
     */
    UploadPartResponse uploadPart(ChunkDTO chunk) throws IOException;

    /**
     * 上传普通流分片，适用于非 Web 使用方；调用方关闭原流。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @param uploadId 上传 ID
     * @param partNumber 分片号，1 至 10000
     * @param stream 分片内容
     * @param size 精确长度，字节
     * @return 分片结果
     * @throws IOException 读取或长度校验失败
     */
    UploadPartResponse uploadPart(String bucketName, String objectName, String uploadId, int partNumber,
                                  InputStream stream, long size) throws IOException;

    /**
     * 读取全部已上传分片，内部遍历分页，供断点续传使用。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @param uploadId 上传 ID
     * @return 全部分片
     */
    List<Part> listParts(String bucketName, String objectName, String uploadId);

    /**
     * 终止任务并清理未合并分片，不删除已存在对象。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @param uploadId 上传 ID
     */
    void abortMultipartUpload(String bucketName, String objectName, String uploadId);

    /**
     * 校验并按编号排序后合并分片，不修改输入列表。
     *
     * @param chunk 任务与已完成分片列表
     * @return 合并结果
     */
    CompleteMultipartUploadResponse completeMultipartUpload(ChunkMergeDTO chunk);

    /**
     * 返回响应元数据与内容流，调用方必须关闭返回流。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @return 调用方拥有的响应流
     */
    ResponseInputStream<GetObjectResponse> getObject(String bucketName, String objectName);

    /**
     * 生成 SigV4 下载 URL，不可在日志中输出签名链接。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @param expires 有效期，1 秒至 7 天
     * @return 临时下载链接
     */
    String getObjectUrl(String bucketName, String objectName, Duration expires);

    /**
     * 删除单个对象。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     */
    void removeObject(String bucketName, String objectName);

    /**
     * 分批删除，每批最多 1000 个，部分失败时明确抛错。
     *
     * @param bucketName 桶名
     * @param objectNames 对象列表，空列表不调用服务端
     */
    void removeObjects(String bucketName, List<String> objectNames);

    /**
     * 遍历分页查询匹配对象；非递归模式仅返回当前层文件，不含虚拟目录。
     *
     * @param bucketName 桶名
     * @param prefix 前缀，可为空
     * @param recursive 是否递归
     * @return 对象列表
     */
    List<S3Object> getAllObjectsByPrefix(String bucketName, String prefix, boolean recursive);

    /**
     * 按扩展名推断 MIME 类型，未知类型返回 application/octet-stream。
     *
     * @param format 扩展名，允许前导点、大小写或 null
     * @return MIME 类型
     */
    String getContentType(String format);

    /**
     * 获取底层客户端，不转移生命周期所有权。
     *
     * @return 由原提供者管理的 SDK 客户端
     */
    S3Client getS3Client();

    /**
     * 获取内容流，调用方必须关闭。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @return 内容流
     */
    InputStream downloadObject(String bucketName, String objectName);

    /**
     * 下载完成后替换目标文件，失败保留原文件并清理临时文件。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @param listener 可选进度回调
     * @param destination 目标文件，父目录必须存在
     * @throws IOException 本地 I/O 或内容长度检查失败
     */
    void downloadObject(String bucketName, String objectName, CustomProgressListener listener, File destination)
            throws IOException;

    /**
     * 获取对象大小、类型、标准头和用户元数据。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @return SDK v2 元数据
     */
    HeadObjectResponse getObjectMetadata(String bucketName, String objectName);

    /**
     * 替换用户元数据，contentType 为 null 时保留原类型与其他可复制标准头。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @param metadata 完整用户元数据，空映射表示清空
     * @param contentType 新 MIME 类型，可为 null
     */
    void updateObjectMetadata(String bucketName, String objectName, Map<String, String> metadata, String contentType);

    /**
     * 恢复原完整元数据更新能力，SDK v1 ObjectMetadata 对应 SDK v2 HeadObjectResponse。
     * 建议从 getObjectMetadata().toBuilder() 修改，null 标准头表示删除；
     * eTag 存在时作为预期内容版本，否则先读取当前 ETag。大小、修改时间等只读字段不写回。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @param metadata 用户元数据及可修改标准头
     */
    default void updateObjectMetadata(String bucketName, String objectName, HeadObjectResponse metadata) {
        Assert.hasText(bucketName, "bucketName不能为空");
        Assert.hasText(objectName, "objectName不能为空");
        Assert.notNull(metadata, "metadata不能为空");
        String expected = metadata.eTag() == null ? getObjectMetadata(bucketName, objectName).eTag() : metadata.eTag();
        getS3Client().copyObject(CopyObjectRequest.builder()
                .copySource(SdkHttpUtils.urlEncodeIgnoreSlashes(bucketName + "/" + objectName))
                .destinationBucket(bucketName).destinationKey(objectName).copySourceIfMatch(expected)
                .metadataDirective(MetadataDirective.REPLACE).metadata(metadata.metadata())
                .contentType(metadata.contentType()).cacheControl(metadata.cacheControl())
                .contentDisposition(metadata.contentDisposition()).contentEncoding(metadata.contentEncoding())
                .contentLanguage(metadata.contentLanguage()).expires(metadata.expires())
                .websiteRedirectLocation(metadata.websiteRedirectLocation()).storageClass(metadata.storageClass())
                .serverSideEncryption(metadata.serverSideEncryption()).ssekmsKeyId(metadata.ssekmsKeyId())
                .bucketKeyEnabled(metadata.bucketKeyEnabled()).build());
    }
}
