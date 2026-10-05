package io.github.bytex0.ip2region;

import io.github.bytex0.ip2region.core.Ip2RegionTemplate;
import io.github.bytex0.ip2region.core.RegionResult;
import org.junit.jupiter.api.Test;
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

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Ip2RegionConfiguration.class));

    @Test
    void disabledDoesNotReadDatabase() {
        runner.run(context -> assertThat(context).doesNotHaveBean(Searcher.class));
        runner.withPropertyValues("ip2region.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(Ip2RegionTemplate.class));
    }

    @Test
    void enabledRequiresDatabaseButAllowsUserTemplate() {
        runner.withPropertyValues("ip2region.enabled=true").run(context -> assertThat(context).hasFailed());
        Ip2RegionTemplate custom = new Ip2RegionTemplate(mock(Searcher.class));
        runner.withPropertyValues("ip2region.enabled=true")
                .withBean(Ip2RegionTemplate.class, () -> custom)
                .run(context -> assertThat(context.getBean(Ip2RegionTemplate.class)).isSameAs(custom));
    }

    @Test
    void usesCustomSearcherAndPropagatesFailure() throws Exception {
        Searcher searcher = mock(Searcher.class);
        when(searcher.search("1.2.3.4")).thenReturn("CN||ZJ|HZ|ISP");
        runner.withPropertyValues("ip2region.enabled=true").withBean(Searcher.class, () -> searcher)
                .run(context -> assertThat(context.getBean(Ip2RegionTemplate.class).search("1.2.3.4").city()).isEqualTo("HZ"));
        when(searcher.search("1.2.3.4")).thenThrow(new IllegalStateException("bad database"));
        assertThatIllegalStateException().isThrownBy(() -> new Ip2RegionTemplate(searcher).search("1.2.3.4"));
    }

    @Test
    void validatesIpAndKeepsEmptyRegionFields() {
        Ip2RegionTemplate template = new Ip2RegionTemplate(mock(Searcher.class));
        for (String ip : new String[]{"localhost", "::1", "1.2.3.256", "01.2.3.4", ""}) {
            assertThatIllegalArgumentException().isThrownBy(() -> template.search(ip));
        }
        assertThat(RegionResult.fromRawString("CN||||").isp()).isEmpty();
        assertThat(RegionResult.fromRawString("")).isNull();
        assertThatIllegalStateException().isThrownBy(() -> RegionResult.fromRawString("bad"));
    }
}
