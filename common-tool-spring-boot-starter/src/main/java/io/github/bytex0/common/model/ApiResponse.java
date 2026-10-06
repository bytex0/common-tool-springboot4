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

    /**
     * 创建无数据成功响应。
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
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

    /**
     * 创建自定义消息的成功响应。
     *
     * @param message 消息
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> okOfMessage(String message) {
        return create(0, message, null, System.currentTimeMillis(), null);
    }

    /**
     * 创建带随机请求标识的成功响应。
     *
     * @param data 业务数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> ok(T data) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        return ok(requestId, data);
    }

    /**
     * 创建指定请求标识的成功响应。
     *
     * @param requestId 请求标识
     * @param data 业务数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> ok(String requestId, T data) {
        return create(0, "success", requestId, System.currentTimeMillis(), data);
    }

    /**
     * 创建指定消息的成功响应。
     *
     * @param requestId 请求标识
     * @param message 消息
     * @param data 业务数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> ok(String requestId, String message, T data) {
        return create(0, message, requestId, System.currentTimeMillis(), data);
    }

    /**
     * 创建指定时间的成功响应。
     *
     * @param requestId 请求标识
     * @param ts 毫秒时间，可为空
     * @param data 业务数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> ok(String requestId, Long ts, T data) {
        return create(0, "success", requestId, ts, data);
    }

    /**
     * 创建所有字段由调用方指定的成功响应。
     *
     * @param requestId 请求标识
     * @param ts 毫秒时间
     * @param data 业务数据
     * @param message 消息
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> ok(String requestId, Long ts, T data, String message) {
        return create(0, message, requestId, ts, data);
    }

    /**
     * 创建默认代码 500 的失败响应。
     *
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> fail() {
        return fail(500);
    }

    /**
     * 创建指定代码的失败响应。
     *
     * @param code 错误码
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> fail(int code) {
        return create(code, "fail", null, System.currentTimeMillis(), null);
    }

    /**
     * 创建自定义消息和错误码的失败响应。
     *
     * @param message 消息
     * @param code 错误码
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> failOfMessage(String message, int code) {
        return create(code, message, null, System.currentTimeMillis(), null);
    }

    /**
     * 创建关联请求的失败响应。
     *
     * @param requestId 请求标识
     * @param code 错误码
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> fail(String requestId, int code) {
        return create(code, "fail", requestId, System.currentTimeMillis(), null);
    }

    /**
     * 创建默认代码 400 的失败响应。
     *
     * @param requestId 请求标识
     * @param message 消息
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> fail(String requestId, String message) {
        return fail(requestId, message, 400);
    }

    /**
     * 创建指定请求、消息和代码的失败响应。
     *
     * @param requestId 请求标识
     * @param message 消息
     * @param code 错误码
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> fail(String requestId, String message, int code) {
        return create(code, message, requestId, System.currentTimeMillis(), null);
    }

    /**
     * 创建指定时间的失败响应。
     *
     * @param requestId 请求标识
     * @param ts 毫秒时间
     * @param code 错误码
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> fail(String requestId, Long ts, int code) {
        return create(code, "fail", requestId, ts, null);
    }

    /**
     * 创建全部错误字段由调用方指定的响应。
     *
     * @param requestId 请求标识
     * @param ts 毫秒时间
     * @param code 错误码
     * @param message 消息
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> fail(String requestId, Long ts, int code, String message) {
        return create(code, message, requestId, ts, null);
    }

    /**
     * 判断响应是否为代码 0，null 视为失败。
     *
     * @param response 响应，可为空
     * @param <T> 数据类型
     * @return 是否成功
     */
    public static <T> Boolean isSuccess(ApiResponse<T> response) {
        return response != null && response.getCode() == 0;
    }

    /**
     * 判断当前响应代码是否为 0。
     *
     * @return 是否成功
     */
    public Boolean isSuccess() {
        return code == 0;
    }

    /**
     * 集中构建响应，不修改传入的业务数据。
     *
     * @param code 响应代码
     * @param message 消息
     * @param requestId 请求标识
     * @param ts 毫秒时间
     * @param data 业务数据
     * @param <T> 数据类型
     * @return 响应对象
     */
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
