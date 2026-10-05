package io.github.bytex0.oss.example.controller;

import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.oss.OssProperties;
import io.github.bytex0.oss.client.OssClient;
import io.github.bytex0.oss.example.model.CompleteUploadRequest;
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
     * 自动配置提供的 OSS 操作接口
     */
    private final OssClient ossClient;

    /**
     * 示例固定使用的测试桶
     */
    private final String bucketName;

    public OssExampleController(OssClient ossClient, OssProperties properties) {
        this.ossClient = ossClient;
        Assert.hasText(properties.getBucketName(), "示例必须通过 OSS_BUCKET 配置测试桶");
        this.bucketName = properties.getBucketName();
    }

    @GetMapping("/bucket")
    public ApiResponse<Map<String, String>> bucket() {
        ossClient.getS3Client().headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        return ApiResponse.ok(Map.of("bucketName", bucketName));
    }

    @PostMapping("/bucket")
    public ApiResponse<Void> createBucket() {
        ossClient.createBucket(bucketName);
        return ApiResponse.ok();
    }

    @GetMapping("/buckets")
    public ApiResponse<List<String>> buckets() {
        return ApiResponse.ok(ossClient.getAllBuckets().stream().map(Bucket::name).toList());
    }

    @DeleteMapping("/bucket")
    public ApiResponse<Void> deleteBucket() {
        ossClient.removeBucket(bucketName);
        return ApiResponse.ok();
    }

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

    @GetMapping("/objects")
    public ApiResponse<List<Map<String, Object>>> objects(@RequestParam(defaultValue = "") String prefix,
                                                          @RequestParam(defaultValue = "true") boolean recursive) {
        return ApiResponse.ok(ossClient.getAllObjectsByPrefix(bucketName, prefix, recursive).stream()
                .map(object -> Map.<String, Object>of("key", object.key(), "size", object.size(),
                        "eTag", object.eTag())).toList());
    }

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

    @GetMapping("/url")
    public ApiResponse<Map<String, String>> url(@RequestParam String objectName,
                                                @RequestParam(defaultValue = "900") long expiresSeconds) {
        return ApiResponse.ok(Map.of("url",
                ossClient.getObjectUrl(bucketName, objectName, Duration.ofSeconds(expiresSeconds))));
    }

    @GetMapping("/metadata")
    public ApiResponse<Map<String, Object>> metadata(@RequestParam String objectName) {
        HeadObjectResponse metadata = ossClient.getObjectMetadata(bucketName, objectName);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("size", metadata.contentLength());
        result.put("contentType", metadata.contentType());
        result.put("eTag", metadata.eTag());
        result.put("metadata", metadata.metadata());
        return ApiResponse.ok(result);
    }

    @PatchMapping("/metadata")
    public ApiResponse<Void> updateMetadata(@RequestParam String objectName,
                                            @RequestParam(required = false) String contentType,
                                            @RequestBody Map<String, String> metadata) {
        ossClient.updateObjectMetadata(bucketName, objectName, metadata, contentType);
        return ApiResponse.ok();
    }

    @DeleteMapping("/objects")
    public ApiResponse<Void> deleteObject(@RequestParam String objectName) {
        ossClient.removeObject(bucketName, objectName);
        return ApiResponse.ok();
    }

    @PostMapping("/objects/delete-batch")
    public ApiResponse<Void> deleteObjects(@RequestBody List<String> objectNames) {
        Assert.isTrue(objectNames.size() <= 10000, "示例一次最多删除 10000 个对象");
        ossClient.removeObjects(bucketName, objectNames);
        return ApiResponse.ok();
    }

    @PostMapping("/multipart")
    public ApiResponse<Map<String, String>> initiate(@RequestParam String objectName) {
        return ApiResponse.ok(Map.of("uploadId", ossClient.initiateMultipartUpload(bucketName, objectName).uploadId()));
    }

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

    @GetMapping("/multipart/parts")
    public ApiResponse<List<Map<String, Object>>> parts(@RequestParam String objectName, @RequestParam String uploadId) {
        return ApiResponse.ok(ossClient.listParts(bucketName, objectName, uploadId).stream()
                .map(part -> Map.<String, Object>of("partNumber", part.partNumber(), "eTag", part.eTag(),
                        "size", part.size())).toList());
    }

    @PostMapping("/multipart/complete")
    public ApiResponse<Map<String, String>> complete(@Valid @RequestBody CompleteUploadRequest request) {
        ChunkMergeDTO merge = new ChunkMergeDTO().setBucketName(bucketName).setObjectName(request.getObjectName())
                .setUploadId(request.getUploadId()).setChunkList(request.getParts().stream()
                        .map(part -> CompletedPart.builder().partNumber(part.getPartNumber()).eTag(part.getETag()).build())
                        .toList());
        return ApiResponse.ok(Map.of("eTag", ossClient.completeMultipartUpload(merge).eTag()));
    }

    @DeleteMapping("/multipart")
    public ApiResponse<Void> abort(@RequestParam String objectName, @RequestParam String uploadId) {
        ossClient.abortMultipartUpload(bucketName, objectName, uploadId);
        return ApiResponse.ok();
    }
}
