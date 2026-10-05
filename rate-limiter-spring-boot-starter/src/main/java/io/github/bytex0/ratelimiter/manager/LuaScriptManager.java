package io.github.bytex0.ratelimiter.manager;

import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.Assert;

/**
 * 只读 Lua 资源管理器，保留原四种脚本访问入口。
 *
 * @author bytex0
 * @since 2026-10-05 20:46:21
 */
public final class LuaScriptManager {

    /**
     * 不可变脚本内容，参数依次为窗口毫秒数、窗口容量、桶容量、速率、许可数及请求标识。
     */
    private static final Map<RateLimiterType, String> SCRIPTS = Map.of(
            RateLimiterType.REDIS_LUA_FIXED_WINDOW, load("fixed_window"),
            RateLimiterType.REDIS_LUA_SLIDING_WINDOW, load("sliding_window"),
            RateLimiterType.REDIS_LUA_TOKEN_BUCKET, load("token_bucket"),
            RateLimiterType.REDIS_LUA_LEAKY_BUCKET, load("leaky_bucket"));

    /**
     * 工具类不允许实例化。
     */
    private LuaScriptManager() {
    }

    /**
     * 获取固定窗口脚本。
     *
     * @return 返回整数许可标志的脚本
     */
    public static String getFixedWindowScript() {
        return getScript(RateLimiterType.REDIS_LUA_FIXED_WINDOW);
    }

    /**
     * 获取滑动窗口脚本。
     *
     * @return 使用 Redis 服务端时间的脚本
     */
    public static String getSlidingWindowScript() {
        return getScript(RateLimiterType.REDIS_LUA_SLIDING_WINDOW);
    }

    /**
     * 获取令牌桶脚本。
     *
     * @return 支持加权许可的脚本
     */
    public static String getTokenBucketScript() {
        return getScript(RateLimiterType.REDIS_LUA_TOKEN_BUCKET);
    }

    /**
     * 获取漏桶脚本。
     *
     * @return 保持拒绝请求排水进度的脚本
     */
    public static String getLeakyBucketScript() {
        return getScript(RateLimiterType.REDIS_LUA_LEAKY_BUCKET);
    }

    /**
     * 按算法返回脚本，不允许将本地或原生 Redisson 类型用于此入口。
     *
     * @param type 四种 Redis Lua 算法之一
     * @return 非空脚本
     */
    public static String getScript(RateLimiterType type) {
        String script = SCRIPTS.get(type);
        Assert.notNull(script, "该限流类型没有Lua脚本");
        return script;
    }

    /**
     * 加载随库打包的 UTF-8 脚本资源。
     *
     * @param name 文件基础名称
     * @return 脚本文本
     */
    private static String load(String name) {
        try {
            return new ClassPathResource("io/github/bytex0/ratelimiter/lua/" + name + ".lua")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("无法加载限流脚本: " + name, exception);
        }
    }
}
