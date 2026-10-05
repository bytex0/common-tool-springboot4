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
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
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
import software.amazon.awssdk.services.s3.model.MetadataDirective;
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
import software.amazon.awssdk.utils.http.SdkHttpUtils;

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

/**
 * 对象存储(S3OssClient)AWS SDK v2 同步实现
 *
 * @author linshiqiang
 * @since 2026-10-05 14:42:56
 */
public class S3OssClient implements OssClient {

    /**
     * Spring 管理的原生 S3 客户端
     */
    private final S3Client s3Client;

    /**
     * 使用相同区域、端点和凭据的签名器
     */
    private final S3Presigner presigner;

    public S3OssClient(S3Client s3Client, S3Presigner presigner) {
        Assert.notNull(s3Client, "s3Client 不能为空");
        Assert.notNull(presigner, "presigner 不能为空");
        this.s3Client = s3Client;
        this.presigner = presigner;
    }

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

    @Override
    public List<Bucket> getAllBuckets() {
        return s3Client.listBucketsPaginator().buckets().stream().toList();
    }

    @Override
    public void removeBucket(String bucketName) {
        Assert.hasText(bucketName, "bucketName 不能为空");
        s3Client.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());
    }

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

    @Override
    public PutObjectResponse putObject(String bucketName, String objectName, InputStream stream) throws IOException {
        return putObject(bucketName, objectName, stream, defaultContentType(objectName));
    }

    @Override
    public PutObjectResponse putObject(String bucketName, String objectName, File file) {
        return putObject(bucketName, objectName, file, null);
    }

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

    @Override
    public CreateMultipartUploadResponse initiateMultipartUpload(String bucketName, String objectName) {
        validateObject(bucketName, objectName);
        return s3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(bucketName).key(objectName).contentType(defaultContentType(objectName)).build());
    }

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

    @Override
    public List<Part> listParts(String bucketName, String objectName, String uploadId) {
        validateUpload(bucketName, objectName, uploadId);
        return s3Client.listPartsPaginator(ListPartsRequest.builder()
                .bucket(bucketName).key(objectName).uploadId(uploadId).build()).parts().stream().toList();
    }

    @Override
    public void abortMultipartUpload(String bucketName, String objectName, String uploadId) {
        validateUpload(bucketName, objectName, uploadId);
        s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(bucketName).key(objectName).uploadId(uploadId).build());
    }

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

    @Override
    public ResponseInputStream<GetObjectResponse> getObject(String bucketName, String objectName) {
        validateObject(bucketName, objectName);
        return s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(objectName).build());
    }

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

    @Override
    public void removeObject(String bucketName, String objectName) {
        validateObject(bucketName, objectName);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(objectName).build());
    }

    @Override
    public void removeObjects(String bucketName, List<String> objectNames) {
        Assert.hasText(bucketName, "bucketName 不能为空");
        Assert.notNull(objectNames, "objectNames 不能为空");
        objectNames.forEach(name -> Assert.hasText(name, "objectName 不能为空"));
        for (int offset = 0; offset < objectNames.size(); offset += 1000) {
            List<ObjectIdentifier> objects = objectNames.subList(offset, Math.min(offset + 1000, objectNames.size()))
                    .stream().map(name -> ObjectIdentifier.builder().key(name).build()).toList();
            DeleteObjectsResponse response = s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucketName).delete(Delete.builder().objects(objects).quiet(true).build()).build());
            if (response.hasErrors()) {
                throw SdkClientException.create("S3 批量删除部分失败，失败数量: " + response.errors().size()
                        + "，首个错误代码: " + response.errors().getFirst().code());
            }
        }
    }

    @Override
    public List<S3Object> getAllObjectsByPrefix(String bucketName, String prefix, boolean recursive) {
        Assert.hasText(bucketName, "bucketName 不能为空");
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName)
                .prefix(prefix == null ? "" : prefix).delimiter(recursive ? null : "/").build();
        return s3Client.listObjectsV2Paginator(request).contents().stream().toList();
    }

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

    @Override
    public S3Client getS3Client() {
        return s3Client;
    }

    @Override
    public InputStream downloadObject(String bucketName, String objectName) {
        return getObject(bucketName, objectName);
    }

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
                byte[] buffer = new byte[8192];
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

    @Override
    public HeadObjectResponse getObjectMetadata(String bucketName, String objectName) {
        validateObject(bucketName, objectName);
        return s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(objectName).build());
    }

    @Override
    public void updateObjectMetadata(String bucketName, String objectName, Map<String, String> metadata,
                                      String contentType) {
        Assert.notNull(metadata, "metadata 不能为空");
        HeadObjectResponse current = getObjectMetadata(bucketName, objectName);
        // 自复制更新元数据时保留标准响应头，并防止覆盖读取元数据之后被并发改写的对象。
        s3Client.copyObject(CopyObjectRequest.builder()
                .copySource(SdkHttpUtils.urlEncodeIgnoreSlashes(bucketName + "/" + objectName))
                .destinationBucket(bucketName).destinationKey(objectName)
                .copySourceIfMatch(current.eTag())
                .metadataDirective(MetadataDirective.REPLACE).metadata(metadata)
                .contentType(contentType == null ? current.contentType() : contentType)
                .cacheControl(current.cacheControl()).contentDisposition(current.contentDisposition())
                .contentEncoding(current.contentEncoding()).contentLanguage(current.contentLanguage())
                .expires(current.expires()).build());
    }

    private PutObjectResponse uploadFile(String bucketName, String objectName, Path path, String contentType,
                                          CustomProgressListener listener) throws IOException {
        long size = Files.size(path);
        ProgressListenerAdapter progress = new ProgressListenerAdapter(listener, size);
        String type = contentType == null ? defaultContentType(objectName) : contentType;
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

    private String defaultContentType(String objectName) {
        Assert.hasText(objectName, "objectName 不能为空");
        int dot = objectName.lastIndexOf('.');
        return getContentType(dot < 0 ? "" : objectName.substring(dot + 1));
    }

    private void validateObject(String bucketName, String objectName) {
        Assert.hasText(bucketName, "bucketName 不能为空");
        Assert.hasText(objectName, "objectName 不能为空");
    }

    private void validateUpload(String bucketName, String objectName, String uploadId) {
        validateObject(bucketName, objectName);
        Assert.hasText(uploadId, "uploadId 不能为空");
    }

    private void validatePartNumber(int partNumber) {
        Assert.isTrue(partNumber >= 1 && partNumber <= 10000, "分片编号必须在 1 至 10000 之间");
    }

    /**
     * 对象传输(ProgressInputStream)读取进度流
     *
     * @author linshiqiang
     * @since 2026-10-05 14:42:56
     */
    private static class ProgressInputStream extends FilterInputStream {

        /**
         * 当前传输的进度计数器
         */
        private final ProgressListenerAdapter progress;

        ProgressInputStream(InputStream input, ProgressListenerAdapter progress) {
            super(input);
            this.progress = progress;
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value != -1) {
                progress.transferred(1);
            }
            return value;
        }

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
