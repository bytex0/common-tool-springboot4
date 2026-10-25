package io.github.bytex0.example.audit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 操作日志测试输入，凭据字段只用于验证过滤，不向响应回传原值。
 *
 * @param name 普通展示字段
 * @param password 测试敏感字段
 * @param token 测试令牌字段
 * @author bytex0
 * @since 2026-10-06 19:10:52
 */
public record AuditInput(
        /**
         * 普通字段，不能为空且最长 128 字符。
         */
        @NotBlank
        @Size(max = 128)
        String name,

        /**
         * 用于验证遮蔽的非 null 密码字段，最长 256 字符。
         */
        @NotNull
        @Size(max = 256)
        String password,

        /**
         * 用于验证遮蔽的非 null 令牌字段，最长 256 字符。
         */
        @NotNull
        @Size(max = 256)
        String token) {
}
