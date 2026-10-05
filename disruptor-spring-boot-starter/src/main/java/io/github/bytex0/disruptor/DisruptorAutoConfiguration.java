package io.github.bytex0.disruptor;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 注册受容器生命周期管理的消息队列模板。
 *
 * @author bytex0
 * @since 2026-10-05 19:39:53
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "disruptor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DisruptorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DisruptorTemplate disruptorTemplate(List<MessageHandler<?>> handlers,
                                               @Value("${disruptor.buffer-size:1024}") int bufferSize) {
        return new DisruptorTemplate(handlers, bufferSize);
    }
}
