package io.github.bytex0.oss.example.controller;

import io.github.bytex0.common.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;

/**
 * 对象存储示例(OssExampleExceptionHandler)接口错误转换，不回传 SDK 请求或凭据
 *
 * @author bytex0
 * @since 2026-10-05 14:55:00
 */
@RestControllerAdvice(assignableTypes = OssExampleController.class)
public class OssExampleExceptionHandler {

    /**
     * 参数错误采用统一安全响应，不返回异常中的请求内容。
     *
     * @param exception 参数错误
     * @return HTTP 400
     */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ApiResponse<Void>> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failOfMessage("请求参数不合法", 400));
    }

    /**
     * 保留合法的 S3 HTTP 状态，但不透出 SDK 请求或签名。
     *
     * @param exception SDK 错误
     * @return 安全错误响应
     */
    @ExceptionHandler(S3Exception.class)
    ResponseEntity<ApiResponse<Void>> s3Failure(S3Exception exception) {
        int status = exception.statusCode();
        if (status < 400 || status > 599) {
            status = 502;
        }
        return ResponseEntity.status(status).body(ApiResponse.failOfMessage("对象存储请求失败", status));
    }

    /**
     * 网络与 I/O 错误返回网关错误。
     *
     * @param exception 传输异常
     * @return HTTP 502
     */
    @ExceptionHandler({SdkClientException.class, IOException.class})
    ResponseEntity<ApiResponse<Void>> transferFailure(Exception exception) {
        return ResponseEntity.status(502).body(ApiResponse.failOfMessage("对象存储传输失败", 502));
    }
}
