package io.github.bytex0.oss.client;

import io.github.bytex0.oss.listener.CustomProgressListener;
import io.github.bytex0.oss.listener.ProgressListenerAdapter;
import io.github.bytex0.oss.model.ChunkDTO;
import io.github.bytex0.oss.model.ChunkMergeDTO;
import org.springframework.util.Assert;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Objects;

/**
 * 对象存储(S3OssClient)AWS SDK v2 同步实现
 *
 * @author bytex0
 * @since 2026-10-05 14:42:56
 */
public class S3OssClient implements OssClient {

    /**
     * S3 非末尾分片最小长度，5MiB。
     */
    private static final long MIN_PART_SIZE = 5L * 1024 * 1024;

    /**
     * S3 单分片最大长度，5GiB。
     */
    private static final long MAX_PART_SIZE = 5L * 1024 * 1024 * 1024;

    /**
     * 单上传任务最多包含的分片数。
     */
    private static final int MAX_PARTS = 10_000;

    /**
     * S3 单次批量删除最大对象数。
     */
    private static final int DELETE_BATCH_SIZE = 1000;

    /**
     * 文件下载缓冲大小。
     */
    private static final int COPY_BUFFER_SIZE = 8192;

    /**
     * Spring 管理的原生 S3 客户端
     */
    private final S3Client s3Client;

    /**
     * 使用相同区域、端点和凭据的签名器
     */
    private final S3Presigner presigner;

    /**
     * 达到此字节数时自动分片。
     */
    private final long multipartThreshold;

    /**
     * 自动分片的基础字节数。
     */
    private final long multipartPartSize;

    /**
     * 保留默认构造方式，16MiB 阈值、8MiB 基础分片。
     *
     * @param s3Client 外部管理的同步客户端
     * @param presigner 外部管理的签名器
     */
    public S3OssClient(S3Client s3Client, S3Presigner presigner) {
        this(s3Client, presigner, 16L * 1024 * 1024, 8L * 1024 * 1024);
    }

    /**
     * 构造自动分片客户端，不转移 SDK 客户端和签名器的生命周期。
     *
     * @param s3Client 同步客户端
     * @param presigner 签名器
     * @param multipartThreshold 阈值字节数，5MiB 至 5GiB
     * @param multipartPartSize 基础分片字节数，5MiB 至 5GiB
     */
    public S3OssClient(S3Client s3Client, S3Presigner presigner, long multipartThreshold, long multipartPartSize) {
        Assert.notNull(s3Client, "s3Client 不能为空");
        Assert.notNull(presigner, "presigner 不能为空");
        Assert.isTrue(multipartThreshold >= MIN_PART_SIZE && multipartThreshold <= MAX_PART_SIZE, "自动分片阈值不合法");
        Assert.isTrue(multipartPartSize >= MIN_PART_SIZE && multipartPartSize <= MAX_PART_SIZE, "自动分片大小不合法");
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.multipartThreshold = multipartThreshold;
        this.multipartPartSize = multipartPartSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createBucket(String bucketName) {
        Assert.hasText(bucketName, "bucketName 不能为空");
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            return;
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
        }
        CreateBucketRequest.Builder request = CreateBucketRequest.builder().bucket(bucketName);
        Region region = s3Client.serviceClientConfiguration().region();
        if (!Region.US_EAST_1.equals(region)) {
            request.createBucketConfiguration(CreateBucketConfiguration.builder()
                    .locationConstraint(region.id()).build());
        }
        s3Client.createBucket(request.build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Bucket> getAllBuckets() {
        return s3Client.listBucketsPaginator().buckets().stream().toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeBucket(String bucketName) {
        Assert.hasText(bucketName, "bucketName 不能为空");
        s3Client.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PutObjectResponse putObject(String bucketName, String objectName, InputStream stream, String contentType)
            throws IOException {
        validateObject(bucketName, objectName);
        Path temporaryFile = spool(stream, -1);
        try {
            return uploadFile(bucketName, objectName, temporaryFile, contentType, null);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PutObjectResponse putObject(String bucketName, String objectName, InputStream stream) throws IOException {
        return putObject(bucketName, objectName, stream, defaultContentType(objectName));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PutObjectResponse putObject(String bucketName, String objectName, File file) {
        return putObject(bucketName, objectName, file, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PutObjectResponse putObject(String bucketName, String objectName, File file, CustomProgressListener listener) {
        validateObject(bucketName, objectName);
        Assert.notNull(file, "file 不能为空");
        try {
            return uploadFile(bucketName, objectName, file.toPath(), defaultContentType(objectName), listener);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PutObjectResponse putObject(String bucketName, String objectName, InputStream stream, long size,
                                       String contentType, CustomProgressListener listener) throws IOException {
        validateObject(bucketName, objectName);
        Assert.isTrue(size >= 0, "size 不能为负数");
        Path temporaryFile = spool(stream, size);
        try {
            return uploadFile(bucketName, objectName, temporaryFile, contentType, listener);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CreateMultipartUploadResponse initiateMultipartUpload(String bucketName, String objectName) {
        validateObject(bucketName, objectName);
        return s3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(bucketName).key(objectName).contentType(defaultContentType(objectName)).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UploadPartResponse uploadPart(ChunkDTO chunk) throws IOException {
        Assert.notNull(chunk, "chunk 不能为空");
        Assert.notNull(chunk.getFile(), "chunk.file 不能为空");
        Assert.notNull(chunk.getChunkNumber(), "chunk.chunkNumber 不能为空");
        try (InputStream stream = chunk.getFile().getInputStream()) {
            return uploadPart(chunk.getBucketName(), chunk.getObjectName(), chunk.getUploadId(),
                    chunk.getChunkNumber(), stream, chunk.getFile().getSize());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UploadPartResponse uploadPart(String bucketName, String objectName, String uploadId, int partNumber,
                                         InputStream stream, long size) throws IOException {
        validateUpload(bucketName, objectName, uploadId);
        validatePartNumber(partNumber);
        Assert.isTrue(size >= 0, "size 不能为负数");
        Path temporaryFile = spool(stream, size);
        try {
            return s3Client.uploadPart(UploadPartRequest.builder()
                            .bucket(bucketName).key(objectName).uploadId(uploadId).partNumber(partNumber)
                            .contentLength(size).build(),
                    RequestBody.fromFile(temporaryFile));
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Part> listParts(String bucketName, String objectName, String uploadId) {
        validateUpload(bucketName, objectName, uploadId);
        return s3Client.listPartsPaginator(ListPartsRequest.builder()
                .bucket(bucketName).key(objectName).uploadId(uploadId).build()).parts().stream().toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void abortMultipartUpload(String bucketName, String objectName, String uploadId) {
        validateUpload(bucketName, objectName, uploadId);
        s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(bucketName).key(objectName).uploadId(uploadId).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompleteMultipartUploadResponse completeMultipartUpload(ChunkMergeDTO chunk) {
        Assert.notNull(chunk, "chunk 不能为空");
        validateUpload(chunk.getBucketName(), chunk.getObjectName(), chunk.getUploadId());
        Assert.notEmpty(chunk.getChunkList(), "chunkList 不能为空");
        Set<Integer> numbers = new HashSet<>();
        for (CompletedPart part : chunk.getChunkList()) {
            Assert.notNull(part, "分片不能为空");
            Assert.notNull(part.partNumber(), "分片编号不能为空");
            validatePartNumber(part.partNumber());
            Assert.hasText(part.eTag(), "分片 ETag 不能为空");
            Assert.isTrue(numbers.add(part.partNumber()), "分片编号不能重复");
        }
        List<CompletedPart> parts = chunk.getChunkList().stream()
                .sorted(Comparator.comparingInt(CompletedPart::partNumber)).toList();
        return s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(chunk.getBucketName()).key(chunk.getObjectName()).uploadId(chunk.getUploadId())
                .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build()).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseInputStream<GetObjectResponse> getObject(String bucketName, String objectName) {
        validateObject(bucketName, objectName);
        return s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(objectName).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getObjectUrl(String bucketName, String objectName, Duration expires) {
        validateObject(bucketName, objectName);
        Assert.isTrue(expires != null && expires.compareTo(Duration.ofSeconds(1)) >= 0
                && expires.compareTo(Duration.ofDays(7)) <= 0, "expires 必须在 1 秒至 7 天之间");
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(expires)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucketName).key(objectName).build())
                .build()).url().toExternalForm();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeObject(String bucketName, String objectName) {
        validateObject(bucketName, objectName);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(objectName).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeObjects(String bucketName, List<String> objectNames) {
        Assert.hasText(bucketName, "bucketName 不能为空");
        Assert.notNull(objectNames, "objectNames 不能为空");
        objectNames.forEach(name -> Assert.hasText(name, "objectName 不能为空"));
        for (int offset = 0; offset < objectNames.size(); offset += DELETE_BATCH_SIZE) {
            List<ObjectIdentifier> objects = objectNames.subList(offset, Math.min(offset + DELETE_BATCH_SIZE, objectNames.size()))
                    .stream().map(name -> ObjectIdentifier.builder().key(name).build()).toList();
            DeleteObjectsResponse response = s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucketName).delete(Delete.builder().objects(objects).quiet(true).build()).build());
            if (response.hasErrors()) {
                throw SdkClientException.create("S3 批量删除部分失败，失败数量: " + response.errors().size()
                        + "，首个错误代码: " + response.errors().getFirst().code());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<S3Object> getAllObjectsByPrefix(String bucketName, String prefix, boolean recursive) {
        Assert.hasText(bucketName, "bucketName 不能为空");
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName)
                .prefix(prefix == null ? "" : prefix).delimiter(recursive ? null : "/").build();
        return s3Client.listObjectsV2Paginator(request).contents().stream().toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentType(String format) {
        String extension = format == null ? "" : format.toLowerCase(Locale.ROOT);
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "mp4" -> "video/mp4";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "pdf" -> "application/pdf";
            case "json" -> "application/json";
            case "txt" -> "text/plain";
            case "csv" -> "text/csv";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public S3Client getS3Client() {
        return s3Client;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream downloadObject(String bucketName, String objectName) {
        return getObject(bucketName, objectName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void downloadObject(String bucketName, String objectName, CustomProgressListener listener, File destination)
            throws IOException {
        Assert.notNull(destination, "destination 不能为空");
        Path target = destination.toPath().toAbsolutePath();
        Path temporaryFile = Files.createTempFile(target.getParent(), ".oss-download-", ".part");
        try {
            ProgressListenerAdapter progress;
            try (ResponseInputStream<GetObjectResponse> stream = getObject(bucketName, objectName);
                 OutputStream output = Files.newOutputStream(temporaryFile)) {
                progress = new ProgressListenerAdapter(listener, stream.response().contentLength());
                progress.reset();
                byte[] buffer = new byte[COPY_BUFFER_SIZE];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    progress.transferred(count);
                }
                if (Files.size(temporaryFile) != stream.response().contentLength()) {
                    throw new IOException("下载内容长度与对象元数据不一致");
                }
            }
            Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
            progress.complete();
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HeadObjectResponse getObjectMetadata(String bucketName, String objectName) {
        validateObject(bucketName, objectName);
        return s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(objectName).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateObjectMetadata(String bucketName, String objectName, Map<String, String> metadata,
                                      String contentType) {
        Assert.notNull(metadata, "metadata 不能为空");
        HeadObjectResponse current = getObjectMetadata(bucketName, objectName);
        updateObjectMetadata(bucketName, objectName, current.toBuilder().metadata(metadata)
                .contentType(contentType == null ? current.contentType() : contentType).build());
    }

    /**
     * 根据文件大小选择单次或自动分片上传，所有请求内容均可重新打开。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @param path 源文件
     * @param contentType 可选类型
     * @param listener 可选进度监听
     * @return 上传结果
     * @throws IOException 文件读取失败
     */
    private PutObjectResponse uploadFile(String bucketName, String objectName, Path path, String contentType,
                                          CustomProgressListener listener) throws IOException {
        long size = Files.size(path);
        ProgressListenerAdapter progress = new ProgressListenerAdapter(listener, size);
        String type = contentType == null ? defaultContentType(objectName) : contentType;
        if (size >= multipartThreshold) {
            return uploadMultipart(bucketName, objectName, path, type, size, progress);
        }
        RequestBody body = listener == null ? RequestBody.fromFile(path)
                : RequestBody.fromContentProvider(() -> {
                    progress.reset();
                    try {
                        return new ProgressInputStream(Files.newInputStream(path), progress);
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                }, size, type);
        PutObjectResponse response = s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName).key(objectName).contentType(type).contentLength(size).build(), body);
        if (listener != null) {
            progress.complete();
        }
        return response;
    }

    /**
     * 自动上传分片，逐片范围流可重试，失败尝试取消本次任务并保留原异常。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @param path 源文件
     * @param contentType 类型
     * @param size 完整文件长度
     * @param progress 当前同步传输进度
     * @return 标准上传结果
     * @throws IOException 本地读取失败
     */
    private PutObjectResponse uploadMultipart(String bucketName, String objectName, Path path, String contentType,
                                              long size, ProgressListenerAdapter progress) throws IOException {
        Assert.isTrue(size <= MAX_PART_SIZE * MAX_PARTS, "文件超过分片协议上限");
        long partSize = Math.max(multipartPartSize, (size - 1) / MAX_PARTS + 1);
        String uploadId = s3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(bucketName).key(objectName).contentType(contentType).build()).uploadId();
        List<CompletedPart> parts = new ArrayList<>();
        try {
            long offset = 0;
            while (offset < size) {
                long start = offset;
                long length = Math.min(partSize, size - offset);
                int number = parts.size() + 1;
                RequestBody body = RequestBody.fromContentProvider(() -> {
                    progress.reset(start);
                    try {
                        return new ProgressInputStream(openSegment(path, start, length), progress);
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                }, length, contentType);
                UploadPartResponse uploaded = s3Client.uploadPart(UploadPartRequest.builder()
                        .bucket(bucketName).key(objectName).uploadId(uploadId).partNumber(number).contentLength(length).build(), body);
                parts.add(CompletedPart.builder().partNumber(number).eTag(uploaded.eTag()).build());
                offset += length;
            }
            if (Files.size(path) != size) {
                throw new IOException("上传期间源文件长度发生变化");
            }
            CompleteMultipartUploadResponse completed = s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName).key(objectName).uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build()).build());
            progress.complete();
            PutObjectResponse.Builder result = PutObjectResponse.builder().eTag(completed.eTag()).versionId(completed.versionId())
                    .serverSideEncryption(completed.serverSideEncryption()).ssekmsKeyId(completed.ssekmsKeyId())
                    .bucketKeyEnabled(completed.bucketKeyEnabled());
            result.sdkHttpResponse(completed.sdkHttpResponse());
            return result.build();
        } catch (IOException | RuntimeException exception) {
            try {
                abortMultipartUpload(bucketName, objectName, uploadId);
            } catch (RuntimeException cleanup) {
                exception.addSuppressed(cleanup);
            }
            throw exception;
        }
    }

    /**
     * 从文件偏移处打开有界流，重试时重新定位，不把分片全部放入堆。
     *
     * @param path 源文件
     * @param offset 起始偏移
     * @param size 分片字节数
     * @return SDK 负责关闭的分片流
     * @throws IOException 定位或读取失败
     */
    private InputStream openSegment(Path path, long offset, long size) throws IOException {
        InputStream input = Files.newInputStream(path);
        try {
            input.skipNBytes(offset);
            return new SegmentInputStream(input, size);
        } catch (IOException | RuntimeException exception) {
            try {
                input.close();
            } catch (IOException cleanup) {
                exception.addSuppressed(cleanup);
            }
            throw exception;
        }
    }

    /**
     * 将不可回放流落到临时文件，输入流仍由调用方管理。
     *
     * @param stream 原内容
     * @param expectedSize 预期长度，负数表示未知
     * @return 调用方必须清理的临时路径
     * @throws IOException 读写或长度检查失败
     */
    private Path spool(InputStream stream, long expectedSize) throws IOException {
        Assert.notNull(stream, "stream 不能为空");
        Path temporaryFile = Files.createTempFile("common-tool-oss-", ".upload");
        boolean complete = false;
        try {
            long actualSize = Files.copy(stream, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            if (expectedSize >= 0 && expectedSize != actualSize) {
                throw new IOException("声明的流长度与实际字节数不一致");
            }
            complete = true;
            return temporaryFile;
        } finally {
            if (!complete) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    /**
     * 根据对象扩展名推断类型。
     *
     * @param objectName 非空对象键
     * @return MIME 类型
     */
    private String defaultContentType(String objectName) {
        Assert.hasText(objectName, "objectName 不能为空");
        int dot = objectName.lastIndexOf('.');
        return getContentType(dot < 0 ? "" : objectName.substring(dot + 1));
    }

    /**
     * 在发起请求前校验对象定位信息。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     */
    private void validateObject(String bucketName, String objectName) {
        Assert.hasText(bucketName, "bucketName 不能为空");
        Assert.hasText(objectName, "objectName 不能为空");
    }

    /**
     * 校验分片任务定位信息。
     *
     * @param bucketName 桶名
     * @param objectName 对象键
     * @param uploadId 上传 ID
     */
    private void validateUpload(String bucketName, String objectName, String uploadId) {
        validateObject(bucketName, objectName);
        Assert.hasText(uploadId, "uploadId 不能为空");
    }

    /**
     * 校验服务端允许的分片号。
     *
     * @param partNumber 从 1 开始的分片编号
     */
    private void validatePartNumber(int partNumber) {
        Assert.isTrue(partNumber >= 1 && partNumber <= MAX_PARTS, "分片编号必须在 1 至 10000 之间");
    }

    /**
     * 文件分片流(SegmentInputStream)限制本次请求可读取的文件范围。
     *
     * @author linshiqiang
     * @since 2026-10-06 02:23:14
     */
    private static final class SegmentInputStream extends FilterInputStream {

        /**
         * 当前范围剩余字节数，实例只由一个同步请求使用。
         */
        private long remaining;

        /**
         * 包装已定位到分片起点的文件流。
         *
         * @param input 文件流，关闭本流时一起关闭
         * @param size 可读取长度
         */
        private SegmentInputStream(InputStream input, long size) {
            super(input);
            remaining = size;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int read() throws IOException {
            if (remaining == 0) {
                return -1;
            }
            int value = in.read();
            if (value < 0) {
                throw new IOException("上传期间源文件被截断");
            }
            remaining--;
            return value;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return 0;
            }
            if (remaining == 0) {
                return -1;
            }
            int count = in.read(bytes, offset, (int) Math.min(length, remaining));
            if (count < 0) {
                throw new IOException("上传期间源文件被截断");
            }
            remaining -= count;
            return count;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long skip(long count) throws IOException {
            long skipped = in.skip(Math.min(Math.max(0, count), remaining));
            remaining -= skipped;
            return skipped;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int available() throws IOException {
            return (int) Math.min(in.available(), remaining);
        }
    }

    /**
     * 对象传输(ProgressInputStream)读取进度流
     *
     * @author bytex0
     * @since 2026-10-05 14:42:56
     */
    private static class ProgressInputStream extends FilterInputStream {

        /**
         * 当前传输的进度计数器
         */
        private final ProgressListenerAdapter progress;

        /**
         * 包装单次 SDK 请求流，关闭本流会关闭底层文件流。
         *
         * @param input 内容流
         * @param progress 当前传输进度
         */
        ProgressInputStream(InputStream input, ProgressListenerAdapter progress) {
            super(input);
            this.progress = progress;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value != -1) {
                progress.transferred(1);
            }
            return value;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = in.read(bytes, offset, length);
            if (count > 0) {
                progress.transferred(count);
            }
            return count;
        }
    }
}
