package io.github.bytex0.ip2region;

import io.github.bytex0.ip2region.core.Ip2RegionProperties;
import io.github.bytex0.ip2region.core.Ip2RegionTemplate;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

/**
 * 显式开启、可覆盖且具有资源上限的 IP 数据库自动装配。
 *
 * @author bytex0
 * @since 2026-10-05 19:17:15
 */
@AutoConfiguration
@EnableConfigurationProperties(Ip2RegionProperties.class)
@ConditionalOnProperty(prefix = "ip2region", name = "enabled", havingValue = "true")
public class Ip2RegionConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean({Searcher.class, Ip2RegionTemplate.class})
    public Searcher searcher(Ip2RegionProperties properties, ResourceLoader loader) throws IOException {
        Assert.hasText(properties.getDbPath(), "ip2region.db-path is required");
        int limit = properties.getMaxDatabaseBytes();
        Assert.isTrue(limit >= 524544 && limit <= 256 * 1024 * 1024,
                "ip2region.max-database-bytes must be between 524544 and 268435456");
        String path = properties.getDbPath();
        Resource resource = path.startsWith("classpath:") ? loader.getResource(path)
                : new FileSystemResource(Path.of(path));
        try (InputStream input = resource.getInputStream()) {
            byte[] data = input.readNBytes(limit + 1);
            Assert.isTrue(data.length >= 524544 && data.length <= limit, "Invalid IP database size");
            return Searcher.newWithBuffer(data);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public Ip2RegionTemplate ip2RegionTemplate(Searcher searcher) {
        return new Ip2RegionTemplate(searcher);
    }
}
