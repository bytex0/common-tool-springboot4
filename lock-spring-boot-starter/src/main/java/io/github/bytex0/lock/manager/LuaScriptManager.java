package io.github.bytex0.lock.manager;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 信号量脚本(LuaScriptManager)提供不可变脚本内容，资源缺失时明确失败。
 *
 * @author linshiqiang
 * @since 2026-10-06 01:53:24
 */
public class LuaScriptManager {

    /**
     * 原入口名称保留，协议升级为唯一凭证；KEYS 为凭证集合与容量键，ARGV 为 token、容量、租约毫秒。
     */
    private static final String ACQUIRE = read("semaphore-try-acquire");

    /**
     * 同一凭证协议的释放脚本，不再无条件增加计数。
     */
    private static final String RELEASE = read("semaphore-release");

    /**
     * 同一凭证协议的续租脚本。
     */
    private static final String RENEW = read("semaphore-renew");

    /**
     * 保留原静态获取入口，直接调用脚本的使用方须更新为新凭证协议。
     *
     * @return 获取脚本
     */
    public static String getSemaphoreTryAcquire() {
        return ACQUIRE;
    }

    /**
     * 保留原静态释放入口，释放仅对当前 token 生效。
     *
     * @return 释放脚本
     */
    public static String getSemaphoreRelease() {
        return RELEASE;
    }

    /**
     * 读取不能复活过期凭证的续租脚本。
     *
     * @return 续租脚本
     */
    public static String getSemaphoreRenew() {
        return RENEW;
    }

    /**
     * 以 UTF-8 读取专属资源，自动关闭输入流。
     *
     * @param name 脚本资源名
     * @return 脚本内容
     */
    private static String read(String name) {
        try (var input = new ClassPathResource("io/github/bytex0/lock/lua/" + name + ".lua").getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取锁脚本: " + name, exception);
        }
    }
}
