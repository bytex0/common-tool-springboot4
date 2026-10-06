package io.github.bytex0.script.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 脚本配置，保留原配置对象及嵌套属性，并增加有界调度参数。
 *
 * @author bytex0
 * @since 2026-10-06 14:08:29
 */
@Data
@ConfigurationProperties(prefix = "script")
public class ScriptProperties {

    /**
     * 是否开启脚本执行，默认 false；必须显式设置 true 才创建服务。
     */
    private boolean enabled;

    /**
     * 所有语言合计的编译缓存上限，默认 1000，范围 1 到 10000。
     */
    private int cacheSize = 1000;

    /**
     * 排队、编译和执行的总等待超时，单位毫秒，默认 5000，范围 1 到 120000。
     */
    private long timeout = 5000;

    /**
     * 每个服务的并行线程数，默认 2，范围 1 到 32。
     */
    private int parallelism = 2;

    /**
     * 每个服务的待执行队列容量，默认 16，范围 1 到 1000，满时拒绝新任务。
     */
    private int queueCapacity = 16;

    /**
     * Groovy 开关及编译缓存配额；配额与全局容量同时生效。
     */
    private GroovyProperties groovy = new GroovyProperties();

    /**
     * JavaScript 开关及严格模式；依赖缺失时即使启用也不创建执行器。
     */
    private JavaScriptProperties javaScript = new JavaScriptProperties();

    /**
     * Lua 开关及标准库限制；依赖缺失时不创建执行器。
     */
    private LuaProperties lua = new LuaProperties();

    /**
     * Groovy 语言配置。
     *
     * @author bytex0
     * @since 2026-10-06 14:08:29
     */
    @Data
    public static class GroovyProperties {

        /**
         * 是否创建 Groovy 执行器，默认 true，受总开关优先控制。
         */
        private boolean enabled = true;

        /**
         * Groovy 编译产物上限，默认 100，范围 1 到 10000，同时受全局容量约束。
         */
        private int cacheSize = 100;
    }

    /**
     * JavaScript 语言配置。
     *
     * @author bytex0
     * @since 2026-10-06 14:08:29
     */
    @Data
    public static class JavaScriptProperties {

        /**
         * 是否创建 JavaScript 执行器，默认 true，受总开关和引擎类路径控制。
         */
        private boolean enabled = true;

        /**
         * 是否强制 ECMAScript 严格模式，默认 true。
         */
        private boolean strictMode = true;

        /**
         * 是否允许全部宿主访问，默认 false；true 恢复原版 Java 互操作，仅适用于完全受信脚本。
         */
        private boolean allowHostAccess;
    }

    /**
     * Lua 语言配置，标准库限制不代表不可信代码安全沙箱。
     *
     * @author bytex0
     * @since 2026-10-06 14:08:29
     */
    @Data
    public static class LuaProperties {

        /**
         * 是否创建 Lua 执行器，默认 true，受总开关和引擎类路径控制。
         */
        private boolean enabled = true;

        /**
         * 是否移除文件、系统、模块加载及 Java 互操作入口，默认 true。
         */
        private boolean sandbox = true;
    }
}
