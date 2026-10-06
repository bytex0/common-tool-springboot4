package io.github.bytex0.script.config;

import io.github.bytex0.script.executor.GroovyScriptExecutor;
import io.github.bytex0.script.executor.JavaExecutor;
import io.github.bytex0.script.executor.JavaScriptExecutor;
import io.github.bytex0.script.executor.LuaScriptExecutor;
import io.github.bytex0.script.executor.PythonScriptExecutor;
import io.github.bytex0.script.executor.ScriptExecutor;
import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.service.ScriptService;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 类型化脚本服务自动配置，显式启用后按语言类路径及开关创建执行器。
 *
 * @author bytex0
 * @since 2026-10-06 14:03:07
 */
@AutoConfiguration
@EnableConfigurationProperties(ScriptProperties.class)
@ConditionalOnProperty(prefix = "script", name = "enabled", havingValue = "true")
@Import({ScriptAutoConfiguration.GroovyConfiguration.class, ScriptAutoConfiguration.JavaConfiguration.class,
        ScriptAutoConfiguration.JavaScriptConfiguration.class, ScriptAutoConfiguration.LuaConfiguration.class,
        ScriptAutoConfiguration.PythonConfiguration.class})
public class ScriptAutoConfiguration {

    /**
     * 创建类型化服务，参数校验由服务构造器统一执行。
     *
     * @param executors 可用执行器列表
     * @param properties 已绑定的脚本配置
     * @return 由 Spring 负责关闭的服务
     */
    @Bean
    @ConditionalOnMissingBean
    public ScriptService typedScriptService(List<ScriptExecutor> executors, ScriptProperties properties) {
        return new ScriptService(executors, properties.getParallelism(), properties.getQueueCapacity(),
                properties.getTimeout(), properties.getCacheSize(),
                Map.of(ScriptType.GROOVY, properties.getGroovy().getCacheSize()));
    }

    /**
     * Groovy 语言条件配置。
     *
     * @author bytex0
     * @since 2026-10-06 14:03:07
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "groovy.lang.GroovyClassLoader")
    @ConditionalOnProperty(prefix = "script.groovy", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class GroovyConfiguration {

        /**
         * 创建默认 Groovy 方法执行器。
         *
         * @return Groovy 执行器
         */
        @Bean
        @ConditionalOnMissingBean
        GroovyScriptExecutor typedGroovyScriptExecutor() {
            return new GroovyScriptExecutor();
        }
    }

    /**
     * Java 语言条件配置。
     *
     * @author bytex0
     * @since 2026-10-06 14:03:07
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "javax.tools.JavaCompiler")
    @ConditionalOnProperty(prefix = "script.java", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class JavaConfiguration {

        /**
         * 创建 JDK 编译执行器，运行环境必须具备 JDK。
         *
         * @return Java 执行器
         */
        @Bean
        @ConditionalOnMissingBean
        JavaExecutor javaExecutor() {
            return new JavaExecutor();
        }
    }

    /**
     * GraalJS 条件配置，缺少引擎时不加载执行器类型。
     *
     * @author bytex0
     * @since 2026-10-06 14:03:07
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"org.graalvm.polyglot.Context", "com.oracle.truffle.js.lang.JavaScriptLanguage"})
    @ConditionalOnProperty(prefix = "script.java-script", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class JavaScriptConfiguration {

        /**
         * 创建按配置启用严格模式的执行器。
         *
         * @param properties JavaScript 配置，严格模式默认 true
         * @return JavaScript 执行器
         */
        @Bean
        @ConditionalOnMissingBean
        JavaScriptExecutor javaScriptExecutor(ScriptProperties properties) {
            return new JavaScriptExecutor(properties.getJavaScript().isStrictMode(),
                    properties.getJavaScript().isAllowHostAccess());
        }
    }

    /**
     * LuaJ 条件配置。
     *
     * @author bytex0
     * @since 2026-10-06 14:03:07
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.luaj.vm2.Globals")
    @ConditionalOnProperty(prefix = "script.lua", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class LuaConfiguration {

        /**
         * 创建按配置限制标准库的 Lua 执行器。
         *
         * @param properties Lua 配置，默认限制系统及文件库
         * @return Lua 执行器
         */
        @Bean
        @ConditionalOnMissingBean
        LuaScriptExecutor luaScriptExecutor(ScriptProperties properties) {
            return new LuaScriptExecutor(properties.getLua().isSandbox());
        }
    }

    /**
     * Jython 条件配置。
     *
     * @author bytex0
     * @since 2026-10-06 14:03:07
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.python.util.PythonInterpreter")
    @ConditionalOnProperty(prefix = "script.python", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class PythonConfiguration {

        /**
         * 创建 Python 2.7 执行器。
         *
         * @return Python 执行器
         */
        @Bean
        @ConditionalOnMissingBean
        PythonScriptExecutor pythonScriptExecutor() {
            return new PythonScriptExecutor();
        }
    }
}
