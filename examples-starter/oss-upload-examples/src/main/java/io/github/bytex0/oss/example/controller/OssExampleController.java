package io.github.bytex0.oss.example.controller;

import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.oss.OssProperties;
import io.github.bytex0.oss.client.OssClient;
import io.github.bytex0.oss.example.model.CompleteUploadRequest;
import io.github.bytex0.oss.example.model.MetadataUpdateRequest;
import io.github.bytex0.oss.model.ChunkDTO;
import io.github.bytex0.oss.model.ChunkMergeDTO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 对象存储联调(OssExampleController)测试接口，仅操作配置的测试桶
 *
 * @author bytex0
 * @since 2026-10-05 14:55:00
 */
@RestController
@RequestMapping("/api/oss")
public class OssExampleController {

    /**
     * 示例单次批量删除接口上限。
     */
    private static final int MAX_DELETE_REQUEST_SIZE = 10_000;

    /**
     * 自动配置提供的 OSS 操作接口
     */
    private final OssClient ossClient;

    /**
     * 示例固定使用的测试桶
     */
    private final String bucketName;

    /**
     * 注入实际 Starter 并固定本次测试桶，不允许请求任意选择业务桶。
     *
     * @param ossClient 对象存储组件
     * @param properties 测试桶配置
     */
    public OssExampleController(OssClient ossClient, OssProperties properties) {
        this.ossClient = ossClient;
        Assert.hasText(properties.getBucketName(), "示例必须通过 OSS_BUCKET 配置测试桶");
        this.bucketName = properties.getBucketName();
    }

    /**
     * 验证测试桶存在且有权限访问。
     *
     * @return 测试桶名称
     */
    @GetMapping("/bucket")
    public ApiResponse<Map<String, String>> bucket() {
        ossClient.getS3Client().headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        return ApiResponse.ok(Map.of("bucketName", bucketName));
    }

    /**
     * 幂等创建本次测试桶。
     *
     * @return 操作结果
     */
    @PostMapping("/bucket")
    public ApiResponse<Void> createBucket() {
        ossClient.createBucket(bucketName);
        return ApiResponse.ok();
    }

    /**
     * 验证 Starter 桶查询能力，输出普通名称而非 SDK 内部对象。
     *
     * @return 桶名列表
     */
    @GetMapping("/buckets")
    public ApiResponse<List<String>> buckets() {
        return ApiResponse.ok(ossClient.getAllBuckets().stream().map(Bucket::name).toList());
    }

    /**
     * 删除已清空的测试桶。
     *
     * @return 操作结果
     */
    @DeleteMapping("/bucket")
    public ApiResponse<Void> deleteBucket() {
        ossClient.removeBucket(bucketName);
        return ApiResponse.ok();
    }

    /**
     * 通过流、带进度流或文件入口实际上传，临时资源由示例清理。
     *
     * @param objectName 对象键
     * @param mode stream、progress 或 file
     * @param file HTTP 上传内容
     * @return ETag、字节数和最终进度
     * @throws IOException 本地读取失败
     */
    @PostMapping(value = "/objects", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(@RequestParam String objectName,
                                                   @RequestParam(defaultValue = "stream") String mode,
                                                   @RequestPart MultipartFile file) throws IOException {
        PutObjectResponse result;
        AtomicReference<Double> progress = new AtomicReference<>(0.0);
        switch (mode) {
            case "stream" -> {
                try (InputStream stream = file.getInputStream()) {
                    result = ossClient.putObject(bucketName, objectName, stream, file.getContentType());
                }
            }
            case "progress" -> {
                try (InputStream stream = file.getInputStream()) {
                    result = ossClient.putObject(bucketName, objectName, stream, file.getSize(),
                            file.getContentType(), (bytes, total, percent, speed, unit) -> progress.set(percent));
                }
            }
            case "file" -> {
                Path temporary = Files.createTempFile("oss-example-upload-", ".tmp");
                try {
                    try (InputStream stream = file.getInputStream()) {
                        Files.copy(stream, temporary, StandardCopyOption.REPLACE_EXISTING);
                    }
                    result = ossClient.putObject(bucketName, objectName, temporary.toFile(),
                            (bytes, total, percent, speed, unit) -> progress.set(percent));
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            default -> throw new IllegalArgumentException("不支持的上传模式");
        }
        return ApiResponse.ok(Map.of("eTag", result.eTag(), "size", file.getSize(), "progress", progress.get()));
    }

    /**
     * 查询测试桶中的对象，不输出 SDK 类型。
     *
     * @param prefix 可选前缀
     * @param recursive 是否递归
     * @return 对象摘要
     */
    @GetMapping("/objects")
    public ApiResponse<List<Map<String, Object>>> objects(@RequestParam(defaultValue = "") String prefix,
                                                          @RequestParam(defaultValue = "true") boolean recursive) {
        return ApiResponse.ok(ossClient.getAllObjectsByPrefix(bucketName, prefix, recursive).stream()
                .map(object -> Map.<String, Object>of("key", object.key(), "size", object.size(),
                        "eTag", object.eTag())).toList());
    }

    /**
     * 流式下载，响应完成后关闭输入流。
     *
     * @param objectName 对象键
     * @return 流式响应
     */
    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> download(@RequestParam String objectName) {
        HeadObjectResponse metadata = ossClient.getObjectMetadata(bucketName, objectName);
        StreamingResponseBody body = output -> {
            try (InputStream input = ossClient.downloadObject(bucketName, objectName)) {
                input.transferTo(output);
            }
        };
        return ResponseEntity.ok().contentLength(metadata.contentLength())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(body);
    }

    /**
     * 下载到临时文件并计算 SHA-256，验证内容与进度后清理文件。
     *
     * @param objectName 对象键
     * @return 大小、摘要和进度
     * @throws IOException 文件 I/O 失败
     * @throws NoSuchAlgorithmException 当前运行时缺少 SHA-256
     */
    @GetMapping("/download-progress")
    public ApiResponse<Map<String, Object>> downloadProgress(@RequestParam String objectName)
            throws IOException, NoSuchAlgorithmException {
        Path temporary = Files.createTempFile("oss-example-download-", ".tmp");
        AtomicReference<Double> progress = new AtomicReference<>(0.0);
        try {
            ossClient.downloadObject(bucketName, objectName,
                    (bytes, total, percent, speed, unit) -> progress.set(percent), temporary.toFile());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream input = new DigestInputStream(Files.newInputStream(temporary), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return ApiResponse.ok(Map.of("size", Files.size(temporary),
                    "sha256", HexFormat.of().formatHex(digest.digest()), "progress", progress.get()));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * 为测试对象创建临时签名链接，调用方不得记录链接正文。
     *
     * @param objectName 对象键
     * @param expiresSeconds 有效秒数
     * @return 下载链接
     */
    @GetMapping("/url")
    public ApiResponse<Map<String, String>> url(@RequestParam String objectName,
                                                @RequestParam(defaultValue = "900") long expiresSeconds) {
        return ApiResponse.ok(Map.of("url",
                ossClient.getObjectUrl(bucketName, objectName, Duration.ofSeconds(expiresSeconds))));
    }

    /**
     * 输出可用于接口断言的标准头及用户元数据。
     *
     * @param objectName 对象键
     * @return 普通 JSON 元数据
     */
    @GetMapping("/metadata")
    public ApiResponse<Map<String, Object>> metadata(@RequestParam String objectName) {
        HeadObjectResponse metadata = ossClient.getObjectMetadata(bucketName, objectName);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("size", metadata.contentLength());
        result.put("contentType", metadata.contentType());
        result.put("eTag", metadata.eTag());
        result.put("metadata", metadata.metadata());
        result.put("cacheControl", metadata.cacheControl());
        result.put("contentDisposition", metadata.contentDisposition());
        result.put("contentEncoding", metadata.contentEncoding());
        result.put("contentLanguage", metadata.contentLanguage());
        result.put("expires", metadata.expires());
        return ApiResponse.ok(result);
    }

    /**
     * 只替换用户元数据和可选内容类型，其余标准头保留。
     *
     * @param objectName 对象键
     * @param contentType 新类型，可为空
     * @param metadata 用户元数据
     * @return 操作结果
     */
    @PatchMapping("/metadata")
    public ApiResponse<Void> updateMetadata(@RequestParam String objectName,
                                            @RequestParam(required = false) String contentType,
                                            @RequestBody Map<String, String> metadata) {
        ossClient.updateObjectMetadata(bucketName, objectName, metadata, contentType);
        return ApiResponse.ok();
    }

    /**
     * 调用完整元数据重载，标准头以请求为准，保留原存储与加密设置。
     *
     * @param objectName 对象键
     * @param request 标准头及完整用户元数据
     * @return 操作结果
     */
    @PutMapping("/metadata")
    public ApiResponse<Void> replaceMetadata(@RequestParam String objectName, @Valid @RequestBody MetadataUpdateRequest request) {
        HeadObjectResponse current = ossClient.getObjectMetadata(bucketName, objectName);
        HeadObjectResponse replacement = current.toBuilder().metadata(request.getMetadata())
                .contentType(request.getContentType()).cacheControl(request.getCacheControl())
                .contentDisposition(request.getContentDisposition()).contentEncoding(request.getContentEncoding())
                .contentLanguage(request.getContentLanguage()).expires(request.getExpires()).build();
        ossClient.updateObjectMetadata(bucketName, objectName, replacement);
        return ApiResponse.ok();
    }

    /**
     * 删除本次桶中的单个测试对象。
     *
     * @param objectName 对象键
     * @return 操作结果
     */
    @DeleteMapping("/objects")
    public ApiResponse<Void> deleteObject(@RequestParam String objectName) {
        ossClient.removeObject(bucketName, objectName);
        return ApiResponse.ok();
    }

    /**
     * 批量删除测试对象，内部按 S3 单批上限拆分。
     *
     * @param objectNames 对象键列表
     * @return 操作结果
     */
    @PostMapping("/objects/delete-batch")
    public ApiResponse<Void> deleteObjects(@RequestBody List<String> objectNames) {
        Assert.isTrue(objectNames.size() <= MAX_DELETE_REQUEST_SIZE, "示例一次最多删除 10000 个对象");
        ossClient.removeObjects(bucketName, objectNames);
        return ApiResponse.ok();
    }

    /**
     * 开始手工分片任务。
     *
     * @param objectName 对象键
     * @return 上传 ID
     */
    @PostMapping("/multipart")
    public ApiResponse<Map<String, String>> initiate(@RequestParam String objectName) {
        return ApiResponse.ok(Map.of("uploadId", ossClient.initiateMultipartUpload(bucketName, objectName).uploadId()));
    }

    /**
     * 上传一个分片。
     *
     * @param objectName 对象键
     * @param uploadId 上传 ID
     * @param partNumber 分片号
     * @param file 分片内容
     * @return 分片号和 ETag
     * @throws IOException 读取分片失败
     */
    @PutMapping(value = "/multipart/parts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadPart(@RequestParam String objectName, @RequestParam String uploadId,
                                                       @RequestParam int partNumber, @RequestPart MultipartFile file)
            throws IOException {
        ChunkDTO chunk = new ChunkDTO();
        chunk.setBucketName(bucketName);
        chunk.setObjectName(objectName);
        chunk.setUploadId(uploadId);
        chunk.setChunkNumber(partNumber);
        chunk.setFile(file);
        UploadPartResponse result = ossClient.uploadPart(chunk);
        return ApiResponse.ok(Map.of("partNumber", partNumber, "eTag", result.eTag()));
    }

    /**
     * 查询全部已上传分片以支持断点续传。
     *
     * @param objectName 对象键
     * @param uploadId 上传 ID
     * @return 分片摘要列表
     */
    @GetMapping("/multipart/parts")
    public ApiResponse<List<Map<String, Object>>> parts(@RequestParam String objectName, @RequestParam String uploadId) {
        return ApiResponse.ok(ossClient.listParts(bucketName, objectName, uploadId).stream()
                .map(part -> Map.<String, Object>of("partNumber", part.partNumber(), "eTag", part.eTag(),
                        "size", part.size())).toList());
    }

    /**
     * 校验并合并手工上传分片。
     *
     * @param request 上传 ID 与分片列表
     * @return 合并后的 ETag
     */
    @PostMapping("/multipart/complete")
    public ApiResponse<Map<String, String>> complete(@Valid @RequestBody CompleteUploadRequest request) {
        ChunkMergeDTO merge = new ChunkMergeDTO().setBucketName(bucketName).setObjectName(request.getObjectName())
                .setUploadId(request.getUploadId()).setChunkList(request.getParts().stream()
                        .map(part -> CompletedPart.builder().partNumber(part.getPartNumber()).eTag(part.getETag()).build())
                        .toList());
        return ApiResponse.ok(Map.of("eTag", ossClient.completeMultipartUpload(merge).eTag()));
    }

    /**
     * 取消未完成分片任务。
     *
     * @param objectName 对象键
     * @param uploadId 上传 ID
     * @return 操作结果
     */
    @DeleteMapping("/multipart")
    public ApiResponse<Void> abort(@RequestParam String objectName, @RequestParam String uploadId) {
        ossClient.abortMultipartUpload(bucketName, objectName, uploadId);
        return ApiResponse.ok();
    }
}
