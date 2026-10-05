package io.github.bytex0.sensitive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

/**
 * 显式资源加载和失败即中止的词库自动配置。
 *
 * @author bytex0
 * @since 2026-10-05 19:21:42
 */
@AutoConfiguration
@EnableConfigurationProperties(SensitiveWordProperties.class)
@ConditionalOnProperty(prefix = "sensitive-word", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SensitiveWordAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SensitiveWordService sensitiveWordService(SensitiveWordProperties properties, ResourceLoader loader) throws IOException {
        Set<String> words = new HashSet<>(properties.getWords());
        for (String path : properties.getDictPaths()) {
            Assert.isTrue(path.startsWith("classpath:") || path.startsWith("file:"),
                    "Dictionary must be a classpath: or file: resource");
            try (InputStream input = loader.getResource(path).getInputStream()) {
                byte[] bytes = input.readNBytes(2 * 1024 * 1024 + 1);
                Assert.isTrue(bytes.length <= 2 * 1024 * 1024, "Dictionary file exceeds 2 MiB");
                new String(bytes, StandardCharsets.UTF_8).lines().map(String::strip)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#")).forEach(words::add);
            }
        }
        SensitiveWordService service = new SensitiveWordService(properties);
        service.replaceWords(words);
        return service;
    }
}
