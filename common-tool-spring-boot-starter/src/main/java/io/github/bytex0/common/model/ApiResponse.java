package io.github.bytex0.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

/**
 * 通用响应(ApiResponse)接口返回模型
 *
 * @author bytex0
 * @since 2026-10-05 14:26:50
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * 响应代码，0 表示成功
     */
    @JsonProperty("code")
    private int code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 请求唯一标识
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * 响应时间戳，单位为毫秒
     */
    @JsonProperty("ts")
    private Long ts;

    /**
     * 响应业务数据
     */
    @JsonProperty("data")
    private T data;

    public static <T> ApiResponse<T> ok() {
        return create(0, "success", null, System.currentTimeMillis(), null);
    }

    /**
     * 保留原项目语义：单个 String 参数代表请求 ID，字符串数据使用 ok(requestId, data)。
     *
     * @param requestId 请求 ID
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> ok(String requestId) {
        return create(0, "success", requestId, System.currentTimeMillis(), null);
    }

    public static <T> ApiResponse<T> okOfMessage(String message) {
        return create(0, message, null, System.currentTimeMillis(), null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        return ok(requestId, data);
    }

    public static <T> ApiResponse<T> ok(String requestId, T data) {
        return create(0, "success", requestId, System.currentTimeMillis(), data);
    }

    public static <T> ApiResponse<T> ok(String requestId, String message, T data) {
        return create(0, message, requestId, System.currentTimeMillis(), data);
    }

    public static <T> ApiResponse<T> ok(String requestId, Long ts, T data) {
        return create(0, "success", requestId, ts, data);
    }

    public static <T> ApiResponse<T> ok(String requestId, Long ts, T data, String message) {
        return create(0, message, requestId, ts, data);
    }

    public static <T> ApiResponse<T> fail() {
        return fail(500);
    }

    public static <T> ApiResponse<T> fail(int code) {
        return create(code, "fail", null, System.currentTimeMillis(), null);
    }

    public static <T> ApiResponse<T> failOfMessage(String message, int code) {
        return create(code, message, null, System.currentTimeMillis(), null);
    }

    public static <T> ApiResponse<T> fail(String requestId, int code) {
        return create(code, "fail", requestId, System.currentTimeMillis(), null);
    }

    public static <T> ApiResponse<T> fail(String requestId, String message) {
        return fail(requestId, message, 400);
    }

    public static <T> ApiResponse<T> fail(String requestId, String message, int code) {
        return create(code, message, requestId, System.currentTimeMillis(), null);
    }

    public static <T> ApiResponse<T> fail(String requestId, Long ts, int code) {
        return create(code, "fail", requestId, ts, null);
    }

    public static <T> ApiResponse<T> fail(String requestId, Long ts, int code, String message) {
        return create(code, message, requestId, ts, null);
    }

    public static <T> Boolean isSuccess(ApiResponse<T> response) {
        return response != null && response.getCode() == 0;
    }

    public Boolean isSuccess() {
        return code == 0;
    }

    private static <T> ApiResponse<T> create(int code, String message, String requestId, Long ts, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        response.setRequestId(requestId);
        response.setTs(ts);
        response.setData(data);
        return response;
    }
}
