package io.github.bytex0.lock.core;

import io.github.bytex0.lock.exception.LockException;
import io.github.bytex0.lock.manager.LuaScriptManager;
import io.github.bytex0.util.MethodExpressionEvaluator;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 模板信号量(TemplateSemaphore)用服务端时间和唯一凭证实现过期回收与安全释放。
 *
 * @author linshiqiang
 * @since 2026-10-06 01:53:24
 */
final class TemplateSemaphore {

    /**
     * 非阻塞轮询间隔上限，单位为毫秒。
     */
    private static final long POLL_MILLIS = 25;

    /**
     * 实际使用时解析模板，外部连接由 Spring 管理。
     */
    private final Supplier<RedisTemplate<?, ?>> templates;

    /**
     * 注册按需模板提供器。
     *
     * @param templates 模板提供器
     */
    TemplateSemaphore(Supplier<RedisTemplate<?, ?>> templates) {
        this.templates = templates;
    }

    /**
     * 获取一个带租约的凭证；无锁可用时按单调时钟限时轮询。
     *
     * @param key 原业务键
     * @param permits 总额度
     * @param wait 等待毫秒数
     * @param lease 租约毫秒数
     * @return 当前凭证
     * @throws InterruptedException 轮询被中断
     */
    Permit acquire(String key, int permits, long wait, long lease) throws InterruptedException {
        RedisTemplate<?, ?> template = templates.get();
        Assert.state(template != null, "RedisTemplate信号量需要RedisTemplate");
        String base = "common-tool:lock:template:{" + MethodExpressionEvaluator.digest(key) + "}";
        Permit permit = new Permit(template, base, UUID.randomUUID().toString(), permits, lease);
        long start = System.nanoTime();
        long budget = TimeUnit.MILLISECONDS.toNanos(wait);
        do {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("信号量获取被中断");
            }
            long result = permit.evaluate(LuaScriptManager.getSemaphoreTryAcquire());
            Assert.isTrue(result != -1, "同一个Redis信号量的总额度不一致");
            if (result == 1) {
                return permit;
            }
            long remaining = budget - (System.nanoTime() - start);
            if (remaining <= 0) {
                throw new LockException("未获取RedisTemplate信号量凭证");
            }
            TimeUnit.NANOSECONDS.sleep(Math.min(TimeUnit.MILLISECONDS.toNanos(POLL_MILLIS), remaining));
        } while (true);
    }

    /**
     * 模板凭证(Permit)所有操作绑定同一 token，过期后不能续租复活。
     *
     * @author linshiqiang
     * @since 2026-10-06 01:53:24
     */
    static final class Permit {

        /**
         * 外部管理的模板。
         */
        private final RedisTemplate<?, ?> template;

        /**
         * 带 hash tag 的后端命名空间。
         */
        private final String base;

        /**
         * 每次获取独有的凭证标识。
         */
        private final String token;

        /**
         * 总额度校验值。
         */
        private final int permits;

        /**
         * 租约毫秒数。
         */
        private final long lease;

        /**
         * 固定当前凭证的不可变参数。
         *
         * @param template 外部模板
         * @param base 命名空间
         * @param token 凭证标识
         * @param permits 总额度
         * @param lease 租约毫秒数
         */
        Permit(RedisTemplate<?, ?> template, String base, String token, int permits, long lease) {
            this.template = template;
            this.base = base;
            this.token = token;
            this.permits = permits;
            this.lease = lease;
        }

        /**
         * 只延长尚未过期且仍存在的凭证。
         *
         * @return 是否成功续租
         */
        boolean renew() {
            return evaluate(LuaScriptManager.getSemaphoreRenew()) == 1;
        }

        /**
         * 只删除当前凭证，不增加计数或影响其他所有者。
         *
         * @return 凭证是否仍有效且成功释放
         */
        boolean release() {
            return evaluate(LuaScriptManager.getSemaphoreRelease()) == 1;
        }

        /**
         * 原始连接使用固定 UTF-8 协议，避开模板的 JDK/JSON 序列化差异。
         *
         * @param script 可信的内置 Lua 脚本
         * @return 脚本整数结果
         */
        long evaluate(String script) {
            Long result = template.execute((RedisCallback<Long>) connection -> connection.scriptingCommands()
                    .eval(bytes(script), ReturnType.INTEGER, 2, bytes(base + ":permits"), bytes(base + ":capacity"),
                            bytes(token), bytes(Integer.toString(permits)), bytes(Long.toString(lease))));
            Assert.state(result != null, "Redis信号量脚本未返回结果");
            return result;
        }

        /**
         * 编码脚本协议字符串。
         *
         * @param value 协议内容
         * @return UTF-8 字节数组
         */
        private byte[] bytes(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }
}
