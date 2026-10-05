package io.github.bytex0.ip2region;

import io.github.bytex0.ip2region.core.Ip2RegionProperties;
import io.github.bytex0.ip2region.core.Ip2RegionTemplate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import org.lionsoul.ip2region.xdb.Header;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.DefaultResourceLoader;
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

    /**
     * XDB 头及向量索引占用的最小字节数。
     */
    private static final int MIN_DATABASE_BYTES = Searcher.HeaderInfoLength + Searcher.VectorIndexSize;

    /**
     * 数据文件加载的硬上限，单位为字节。
     */
    private static final int MAX_DATABASE_BYTES = 256 * 1024 * 1024;

    /**
     * 创建受容器管理的内存搜索器，读取流无论成功或失败都关闭。
     *
     * @param properties 数据库配置
     * @param loader 当前上下文资源加载器
     * @return 初始化完成的搜索器
     * @throws IOException 数据库资源不可读
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean({Searcher.class, Ip2RegionTemplate.class})
    public Searcher searcher(Ip2RegionProperties properties, ResourceLoader loader) throws IOException {
        Assert.hasText(properties.getDbPath(), "ip2region.db-path is required");
        int limit = properties.getMaxDatabaseBytes();
        Assert.isTrue(limit >= MIN_DATABASE_BYTES && limit <= MAX_DATABASE_BYTES,
                "ip2region.max-database-bytes must be between 524544 and 268435456");
        String path = properties.getDbPath();
        Resource resource = path.startsWith("classpath:") ? loader.getResource(path)
                : new FileSystemResource(Path.of(path));
        try (InputStream input = resource.getInputStream()) {
            byte[] data = input.readNBytes(limit + 1);
            Assert.isTrue(data.length >= MIN_DATABASE_BYTES && data.length <= limit, "Invalid IP database size");
            Header header = new Header(Arrays.copyOf(data, Searcher.HeaderInfoLength));
            Assert.isTrue(header.version == 2 && header.startIndexPtr >= MIN_DATABASE_BYTES
                    && header.endIndexPtr >= header.startIndexPtr
                    && header.endIndexPtr <= data.length - Searcher.SegmentIndexSize
                    && (header.endIndexPtr - header.startIndexPtr) % Searcher.SegmentIndexSize == 0,
                    "Invalid IPv4 XDB header or segment bounds");
            return Searcher.newWithBuffer(data);
        }
    }

    /**
     * 保留原单参数配置调用入口，程序化调用方负责关闭返回的搜索器。
     *
     * @param properties 数据库配置
     * @return 初始化完成的搜索器
     * @throws UncheckedIOException 资源读取失败
     */
    public Searcher searcher(Ip2RegionProperties properties) {
        try {
            return searcher(properties, new DefaultResourceLoader());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * 装配可被业务自定义 Bean 覆盖的查询模板。
     *
     * @param searcher 容器管理的搜索器
     * @return 查询模板
     */
    @Bean
    @ConditionalOnMissingBean
    public Ip2RegionTemplate ip2RegionTemplate(Searcher searcher) {
        return new Ip2RegionTemplate(searcher);
    }
}
