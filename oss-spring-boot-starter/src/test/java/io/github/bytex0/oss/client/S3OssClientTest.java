package io.github.bytex0.oss.client;

import io.github.bytex0.oss.model.ChunkMergeDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.services.s3.paginators.ListPartsIterable;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * S3 客户端(S3OssClientTest)流处理、分页和边界行为测试
 *
 * @author linshiqiang
 * @since 2026-10-05 14:57:26
 */
class S3OssClientTest {

    /**
     * 每个测试独立的本地文件目录
     */
    @TempDir
    Path temporaryDirectory;

    /**
     * 原生 S3 测试替身
     */
    private S3Client sdk;

    /**
     * 被测客户端
     */
    private S3OssClient client;

    @BeforeEach
    void setUp() {
        sdk = mock(S3Client.class);
        client = new S3OssClient(sdk, mock(S3Presigner.class));
    }

    @Test
    void shouldUseActualStreamLengthAndPreserveCallerOwnership() throws Exception {
        byte[] bytes = "actual-length".getBytes();
        AtomicBoolean closed = new AtomicBoolean();
        InputStream stream = new ByteArrayInputStream(bytes) {
            @Override
            public int available() {
                return 0;
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
        AtomicReference<RequestBody> captured = new AtomicReference<>();
        when(sdk.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            PutObjectRequest request = invocation.getArgument(0);
            RequestBody body = invocation.getArgument(1);
            captured.set(body);
            assertThat(request.contentLength()).isEqualTo(bytes.length);
            for (int attempt = 0; attempt < 2; attempt++) {
                try (InputStream input = body.contentStreamProvider().newStream()) {
                    assertThat(input.readAllBytes()).isEqualTo(bytes);
                }
            }
            return PutObjectResponse.builder().eTag("etag").build();
        });
        assertThat(client.putObject("bucket", "key", stream).eTag()).isEqualTo("etag");
        assertThat(closed).isFalse();
        assertThatThrownBy(() -> captured.get().contentStreamProvider().newStream()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldRejectIncorrectDeclaredSize() {
        assertThatThrownBy(() -> client.putObject("bucket", "key", new ByteArrayInputStream(new byte[3]),
                10, "text/plain", null)).isInstanceOf(IOException.class);
        verifyNoInteractions(sdk);
    }

    @Test
    void shouldCleanTemporaryUploadOnSdkFailure() {
        AtomicReference<RequestBody> captured = new AtomicReference<>();
        when(sdk.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(1));
            throw SdkClientException.create("simulated failure");
        });
        assertThatThrownBy(() -> client.putObject("bucket", "key", new ByteArrayInputStream(new byte[3])))
                .isInstanceOf(SdkClientException.class);
        assertThatThrownBy(() -> captured.get().contentStreamProvider().newStream()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldReportCompletionOnlyAfterSuccessfulUpload() throws Exception {
        Path file = Files.write(temporaryDirectory.resolve("upload.bin"), new byte[10]);
        List<Double> progress = new ArrayList<>();
        when(sdk.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            RequestBody body = invocation.getArgument(1);
            try (InputStream stream = body.contentStreamProvider().newStream()) {
                assertThat(stream.readAllBytes()).hasSize(10);
            }
            assertThat(progress).doesNotContain(100.0);
            return PutObjectResponse.builder().eTag("etag").build();
        });
        client.putObject("bucket", "key", file.toFile(),
                (bytes, total, percent, speed, unit) -> progress.add(percent));
        assertThat(progress.getLast()).isEqualTo(100.0);
        assertThat(file).exists();
    }

    @Test
    void shouldFollowObjectPaginationAndSetDelimiter() {
        when(sdk.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
                .thenAnswer(call -> new ListObjectsV2Iterable(sdk, call.getArgument(0)));
        when(sdk.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(S3Object.builder().key("prefix/a").build())
                        .isTruncated(true).nextContinuationToken("next").build(),
                ListObjectsV2Response.builder().contents(S3Object.builder().key("prefix/b").build())
                        .isTruncated(false).build());
        assertThat(client.getAllObjectsByPrefix("bucket", "prefix/", false))
                .extracting(S3Object::key).containsExactly("prefix/a", "prefix/b");
        ArgumentCaptor<ListObjectsV2Request> requests = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(sdk, times(2)).listObjectsV2(requests.capture());
        assertThat(requests.getAllValues().getFirst().delimiter()).isEqualTo("/");
        assertThat(requests.getAllValues().getLast().continuationToken()).isEqualTo("next");
    }

    @Test
    void shouldFollowPartPagination() {
        when(sdk.listPartsPaginator(any(ListPartsRequest.class)))
                .thenAnswer(call -> new ListPartsIterable(sdk, call.getArgument(0)));
        when(sdk.listParts(any(ListPartsRequest.class))).thenReturn(
                ListPartsResponse.builder().parts(Part.builder().partNumber(1).build())
                        .isTruncated(true).nextPartNumberMarker(1).build(),
                ListPartsResponse.builder().parts(Part.builder().partNumber(2).build()).isTruncated(false).build());
        assertThat(client.listParts("bucket", "key", "upload")).extracting(Part::partNumber).containsExactly(1, 2);
        ArgumentCaptor<ListPartsRequest> requests = ArgumentCaptor.forClass(ListPartsRequest.class);
        verify(sdk, times(2)).listParts(requests.capture());
        assertThat(requests.getAllValues().getLast().partNumberMarker()).isEqualTo(1);
    }

    @Test
    void shouldBatchDeletesAndIgnoreEmptyList() {
        client.removeObjects("bucket", List.of());
        verifyNoInteractions(sdk);
        when(sdk.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder().build());
        client.removeObjects("bucket", IntStream.range(0, 2001).mapToObj(index -> "key-" + index).toList());
        ArgumentCaptor<DeleteObjectsRequest> requests = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(sdk, times(3)).deleteObjects(requests.capture());
        assertThat(requests.getAllValues()).extracting(request -> request.delete().objects().size())
                .containsExactly(1000, 1000, 1);
    }

    @Test
    void shouldNotHidePartialDeleteFailures() {
        when(sdk.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key("key").code("AccessDenied").build()).build());
        assertThatThrownBy(() -> client.removeObjects("bucket", List.of("key")))
                .isInstanceOf(SdkClientException.class).hasMessageContaining("AccessDenied");
    }

    @Test
    void shouldSortPartsAndRejectDuplicates() {
        CompletedPart first = CompletedPart.builder().partNumber(1).eTag("a").build();
        CompletedPart second = CompletedPart.builder().partNumber(2).eTag("b").build();
        ChunkMergeDTO merge = new ChunkMergeDTO().setBucketName("bucket").setObjectName("key")
                .setUploadId("upload").setChunkList(List.of(second, first));
        when(sdk.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().eTag("etag").build());
        client.completeMultipartUpload(merge);
        ArgumentCaptor<CompleteMultipartUploadRequest> request =
                ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
        verify(sdk).completeMultipartUpload(request.capture());
        assertThat(request.getValue().multipartUpload().parts()).extracting(CompletedPart::partNumber)
                .containsExactly(1, 2);
        merge.setChunkList(List.of(first, first));
        assertThatThrownBy(() -> client.completeMultipartUpload(merge)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldPreserveExistingDownloadWhenResponseIsTruncated() throws Exception {
        Path target = Files.writeString(temporaryDirectory.resolve("download.bin"), "existing");
        when(sdk.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength(10L).build(), new ByteArrayInputStream(new byte[3])));
        assertThatThrownBy(() -> client.downloadObject("bucket", "key", null, target.toFile()))
                .isInstanceOf(IOException.class);
        assertThat(Files.readString(target)).isEqualTo("existing");
        try (var files = Files.list(temporaryDirectory)) {
            assertThat(files.toList()).containsExactly(target);
        }
    }

    @Test
    void shouldFinishDownloadWithProgress() throws Exception {
        byte[] content = "download".getBytes();
        when(sdk.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength((long) content.length).build(),
                new ByteArrayInputStream(content)));
        Path target = temporaryDirectory.resolve("download.bin");
        List<Double> progress = new ArrayList<>();
        client.downloadObject("bucket", "key",
                (bytes, total, percent, speed, unit) -> progress.add(percent), target.toFile());
        assertThat(Files.readAllBytes(target)).isEqualTo(content);
        assertThat(progress.getLast()).isEqualTo(100.0);
    }

    @Test
    void shouldPreserveHeadersAndEncodeMetadataCopySource() {
        when(sdk.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentType("text/plain").cacheControl("max-age=10").contentDisposition("attachment")
                .eTag("original").build());
        client.updateObjectMetadata("bucket", "dir/a b+.txt", Map.of("source", "test"), null);
        ArgumentCaptor<CopyObjectRequest> request = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(sdk).copyObject(request.capture());
        assertThat(request.getValue().copySource()).isEqualTo("bucket/dir/a%20b%2B.txt");
        assertThat(request.getValue().copySourceIfMatch()).isEqualTo("original");
        assertThat(request.getValue().contentType()).isEqualTo("text/plain");
        assertThat(request.getValue().cacheControl()).isEqualTo("max-age=10");
    }

    @Test
    void shouldNotCreateBucketAfterForbiddenHead() {
        when(sdk.headBucket(any(HeadBucketRequest.class))).thenThrow(S3Exception.builder().statusCode(403).build());
        assertThatThrownBy(() -> client.createBucket("bucket")).isInstanceOf(S3Exception.class);
        verify(sdk, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void shouldPresignWithExactDurationAndRejectOutOfRange() {
        try (S3Presigner presigner = S3Presigner.builder().endpointOverride(URI.create("http://127.0.0.1:19000"))
                .region(Region.US_EAST_1).credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access", "test-secret")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build()) {
            S3OssClient realSigner = new S3OssClient(sdk, presigner);
            String url = realSigner.getObjectUrl("bucket", "key", Duration.ofSeconds(60));
            assertThat(URI.create(url).getHost()).isEqualTo("127.0.0.1");
            assertThat(URI.create(url).getRawQuery()).contains("X-Amz-Expires=60");
            assertThatThrownBy(() -> realSigner.getObjectUrl("bucket", "key", Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> realSigner.getObjectUrl("bucket", "key", Duration.ofDays(8)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
