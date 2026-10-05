package io.github.bytex0.ip2region;

import io.github.bytex0.ip2region.core.Ip2RegionTemplate;
import io.github.bytex0.ip2region.core.RegionResult;
import io.github.bytex0.ip2region.core.Ip2RegionProperties;
import java.beans.Introspector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 自动配置隔离和查询边界回归测试。
 *
 * @author bytex0
 * @since 2026-10-05 19:17:15
 */
class Ip2RegionTest {

    /**
     * 不加载业务应用的自动配置测试入口。
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Ip2RegionConfiguration.class));

    /**
     * 当前测试独占的文件目录。
     */
    @TempDir
    Path temporary;

    /**
     * 未显式开启或显式关闭时不访问数据库。
     */
    @Test
    void disabledDoesNotReadDatabase() {
        runner.run(context -> assertThat(context).doesNotHaveBean(Searcher.class));
        runner.withPropertyValues("ip2region.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(Ip2RegionTemplate.class));
    }

    /**
     * 校验必要配置并允许整体替换模板。
     */
    @Test
    void enabledRequiresDatabaseButAllowsUserTemplate() {
        runner.withPropertyValues("ip2region.enabled=true").run(context -> assertThat(context).hasFailed());
        Ip2RegionTemplate custom = new Ip2RegionTemplate(mock(Searcher.class));
        runner.withPropertyValues("ip2region.enabled=true")
                .withBean(Ip2RegionTemplate.class, () -> custom)
                .run(context -> assertThat(context.getBean(Ip2RegionTemplate.class)).isSameAs(custom));
    }

    /**
     * 复用自定义搜索器并明确传播数据库错误。
     *
     * @throws Exception 搜索器测试替身配置失败
     */
    @Test
    void usesCustomSearcherAndPropagatesFailure() throws Exception {
        Searcher searcher = mock(Searcher.class);
        when(searcher.search("1.2.3.4")).thenReturn("CN||ZJ|HZ|ISP");
        runner.withPropertyValues("ip2region.enabled=true").withBean(Searcher.class, () -> searcher)
                .run(context -> assertThat(context.getBean(Ip2RegionTemplate.class).search("1.2.3.4").city()).isEqualTo("HZ"));
        when(searcher.search("1.2.3.4")).thenThrow(new IllegalStateException("bad database"));
        assertThatIllegalStateException().isThrownBy(() -> new Ip2RegionTemplate(searcher).search("1.2.3.4"));
    }

    /**
     * 拒绝非 IPv4 地址，解析时保留空字段。
     */
    @Test
    void validatesIpAndKeepsEmptyRegionFields() {
        Ip2RegionTemplate template = new Ip2RegionTemplate(mock(Searcher.class));
        for (String ip : new String[]{"localhost", "::1", "1.2.3.256", ""}) {
            assertThatIllegalArgumentException().isThrownBy(() -> template.search(ip));
        }
        assertThat(RegionResult.fromRawString("CN||||").isp()).isEmpty();
        assertThat(RegionResult.fromRawString("")).isNull();
        assertThatIllegalStateException().isThrownBy(() -> RegionResult.fromRawString("bad"));
    }

    /**
     * 恢复原无参模型、setter、相等性及 Boolean 配置属性的 JavaBeans 契约。
     *
     * @throws Exception 属性内省失败
     */
    @Test
    void restoresBeanContracts() throws Exception {
        RegionResult result = new RegionResult();
        result.setCountry("CN");
        result.setArea("");
        result.setProvince("ZJ");
        result.setCity("HZ");
        result.setIsp("ISP");
        assertThat(result).isEqualTo(new RegionResult("CN", "", "ZJ", "HZ", "ISP"));
        assertThat(result.getCountry()).isEqualTo(result.country());
        assertThat(result.getCity()).isEqualTo(result.city());
        Ip2RegionProperties properties = new Ip2RegionProperties();
        assertThat(properties.getEnabled()).isTrue();
        var enabled = Arrays.stream(Introspector.getBeanInfo(Ip2RegionProperties.class).getPropertyDescriptors())
                .filter(property -> property.getName().equals("enabled")).findFirst().orElseThrow();
        assertThat(enabled.getPropertyType()).isEqualTo(Boolean.class);
        assertThat(enabled.getWriteMethod()).isNotNull();
    }

    /**
     * 足够大的垃圾文件也必须在启动时被头部校验拒绝。
     *
     * @throws Exception 测试文件操作失败
     */
    @Test
    void rejectsCorruptDatabaseHeader() throws Exception {
        Path database = temporary.resolve("bad.xdb");
        Files.write(database, new byte[Searcher.HeaderInfoLength + Searcher.VectorIndexSize + Searcher.SegmentIndexSize]);
        runner.withPropertyValues("ip2region.enabled=true", "ip2region.db-path=" + database)
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 数据库异常后其他线程仍能取得同一模板的锁，原前导零格式仍可查询。
     *
     * @throws Exception 搜索或线程协作失败
     */
    @Test
    void releasesLockAfterFailure() throws Exception {
        Searcher searcher = mock(Searcher.class);
        when(searcher.search("1.2.3.4")).thenThrow(new IllegalStateException("corrupt"));
        when(searcher.search("001.2.3.4")).thenReturn("CN||ZJ|HZ|ISP");
        Ip2RegionTemplate template = new Ip2RegionTemplate(searcher);
        assertThatIllegalStateException().isThrownBy(() -> template.search("1.2.3.4"));
        FutureTask<RegionResult> task = new FutureTask<>(() -> template.search("001.2.3.4"));
        Thread thread = Thread.ofPlatform().daemon(true).start(task);
        try {
            assertThat(task.get(2, TimeUnit.SECONDS).getCountry()).isEqualTo("CN");
        } finally {
            thread.interrupt();
            thread.join(1000);
        }
    }
}
