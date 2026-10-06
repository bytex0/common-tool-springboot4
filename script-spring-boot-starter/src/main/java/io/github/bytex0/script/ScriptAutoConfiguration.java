package io.github.bytex0.script;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 脚本执行需显式开启，默认不产生线程或执行能力。
 *
 * @author bytex0
 * @since 2026-10-05 20:00:38
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "script", name = "enabled", havingValue = "true")
public class ScriptAutoConfiguration {

    /**
     * 创建已有脚本体语义的 Groovy 执行器。
     *
     * @return 默认执行器
     */
    @Bean
    @ConditionalOnMissingBean(GroovyScriptExecutor.class)
    @ConditionalOnProperty(prefix = "script.groovy", name = "enabled", havingValue = "true", matchIfMissing = true)
    public GroovyScriptExecutor groovyScriptExecutor() {
        return new GroovyScriptExecutor();
    }

    /**
     * 创建根包兼容服务，由 Spring 管理关闭。
     *
     * @param executors 脚本体执行器列表
     * @param parallelism 并行线程数，默认 2，范围 1 到 32
     * @param capacity 队列容量，默认 16，范围 1 到 1000
     * @param timeout 包含排队和编译的超时毫秒，默认 5000，范围 1 到 120000
     * @return 兼容服务
     */
    @Bean
    @ConditionalOnMissingBean
    public ScriptService scriptService(List<ScriptExecutor> executors,
                                       @Value("${script.parallelism:2}") int parallelism,
                                       @Value("${script.queue-capacity:16}") int capacity,
                                       @Value("${script.timeout:5000}") long timeout) {
        return new ScriptService(executors, parallelism, capacity, timeout);
    }
}
