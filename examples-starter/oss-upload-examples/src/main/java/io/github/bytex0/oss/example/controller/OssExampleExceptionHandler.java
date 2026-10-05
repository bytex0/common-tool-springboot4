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
 * @author linshiqiang
 * @since 2026-10-05 14:55:00
 */
@RestControllerAdvice(assignableTypes = OssExampleController.class)
public class OssExampleExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ApiResponse<Void>> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failOfMessage("请求参数不合法", 400));
    }

    @ExceptionHandler(S3Exception.class)
    ResponseEntity<ApiResponse<Void>> s3Failure(S3Exception exception) {
        int status = exception.statusCode();
        if (status < 400 || status > 599) {
            status = 502;
        }
        return ResponseEntity.status(status).body(ApiResponse.failOfMessage("对象存储请求失败", status));
    }

    @ExceptionHandler({SdkClientException.class, IOException.class})
    ResponseEntity<ApiResponse<Void>> transferFailure(Exception exception) {
        return ResponseEntity.status(502).body(ApiResponse.failOfMessage("对象存储传输失败", 502));
    }
}
