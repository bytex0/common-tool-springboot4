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

    @Bean
    @ConditionalOnMissingBean(GroovyScriptExecutor.class)
    public GroovyScriptExecutor groovyScriptExecutor() {
        return new GroovyScriptExecutor();
    }

    @Bean
    @ConditionalOnMissingBean
    public ScriptService scriptService(List<ScriptExecutor> executors,
                                       @Value("${script.parallelism:2}") int parallelism,
                                       @Value("${script.queue-capacity:16}") int capacity,
                                       @Value("${script.timeout:5000}") long timeout) {
        return new ScriptService(executors, parallelism, capacity, timeout);
    }
}
