package io.github.bytex0.dict;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.bytex0.dict.annotation.Dict;
import io.github.bytex0.dict.config.DictAutoConfiguration;
import io.github.bytex0.dict.jackson.DictModule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 字典(DictTest)缓存、数据库边界及JSON字段回归
 *
 * @author linshiqiang
 * @since 2026-10-05 16:08:16
 */
class DictTest {

    @Test
    void shouldWorkWithoutDatabaseAndRespectDisableAndOverride() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DictAutoConfiguration.class, JacksonAutoConfiguration.class));
        runner.run(context -> assertThat(context).hasSingleBean(DictCache.class));
        runner.withPropertyValues("dict.enabled=false").run(context -> assertThat(context).doesNotHaveBean(DictCache.class));
        InMemoryDictLoader custom = new InMemoryDictLoader();
        runner.withBean(DictLoader.class, () -> custom)
                .run(context -> assertThat(context.getBean(DictLoader.class)).isSameAs(custom));
    }

    @Test
    void shouldKeepImmutableSnapshotsAndOldDataAfterRefreshFailure() {
        DictLoader loader = mock(DictLoader.class);
        Map<String, String> source = new HashMap<>(Map.of("1", "first"));
        when(loader.loadAllDict()).thenReturn(Map.of("status", source)).thenThrow(new IllegalStateException("failed"));
        DictCache cache = new DictCache(loader, 1);
        cache.refreshAll();
        source.put("1", "changed");
        assertThat(cache.getDictText("status", "1")).isEqualTo("first");
        assertThatThrownBy(cache::refreshAll).isInstanceOf(IllegalStateException.class);
        assertThat(cache.getDictText("status", "1")).isEqualTo("first");
        assertThatThrownBy(() -> cache.snapshot().get("status").clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> cache.refresh("other", Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(cache.snapshot()).containsOnlyKeys("status");
    }

    @Test
    void shouldPreserveCodeAndUseActualJsonPropertyName() {
        InMemoryDictLoader loader = new InMemoryDictLoader();
        loader.replace("status", Map.of("1", "enabled", "", "empty"));
        JsonMapper mapper = JsonMapper.builder().addModule(new DictModule(new DictCache(loader, 10))).build();
        var json = mapper.readTree(mapper.writeValueAsString(new Sample()));
        assertThat(json.path("state").asString()).isEqualTo("1");
        assertThat(json.path("stateText").asString()).isEqualTo("enabled");
        assertThat(json.has("empty")).isFalse();
        assertThat(json.has("emptyText")).isFalse();
        assertThat(mapper.writeValueAsString(Map.of("state", "1"))).isEqualTo("{\"state\":\"1\"}");
    }

    @Test
    void shouldRejectTextFieldCollision() {
        JsonMapper mapper = JsonMapper.builder().addModule(
                new DictModule(new DictCache(new InMemoryDictLoader(), 10))).build();
        assertThatThrownBy(() -> mapper.writeValueAsString(new Conflict())).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldUseBoundParametersAndRejectUnsafeIdentifiers() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE dict_items (kind VARCHAR(100), code VARCHAR(20), label VARCHAR(50))");
        jdbc.update("INSERT INTO dict_items VALUES (?, ?, ?)", "status", "1", "启用");
        jdbc.update("INSERT INTO dict_items VALUES (?, ?, ?)", "x' OR '1'='1", "2", "单独数据");
        JdbcDictLoader.TableMapping mapping = new JdbcDictLoader.TableMapping("dict_items", "code", "label", "kind");
        JdbcDictLoader loader = new JdbcDictLoader(jdbc, Map.of("status", mapping, "x' OR '1'='1", mapping));
        assertThat(loader.loadDict("status")).containsExactlyEntriesOf(Map.of("1", "启用"));
        assertThat(loader.loadDict("x' OR '1'='1")).containsExactlyEntriesOf(Map.of("2", "单独数据"));
        assertThat(loader.loadDict("unconfigured")).isEmpty();
        assertThatThrownBy(() -> new JdbcDictLoader.TableMapping("dict_items;DROP TABLE x", "code", "label", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 字典样例(Sample)重命名与省略属性
     *
     * @author linshiqiang
     * @since 2026-10-05 16:08:16
     */
    public static class Sample {

        /**
         * 显式JSON属性名
         */
        @Dict("status")
        @JsonProperty("state")
        public String code = "1";

        /**
         * 被省略的空字段不应单独产生Text
         */
        @Dict("status")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String empty = "";
    }

    /**
     * 字段冲突(Conflict)验证配置错误
     *
     * @author linshiqiang
     * @since 2026-10-05 16:08:16
     */
    public static class Conflict {

        /**
         * 待翻译code
         */
        @Dict("status")
        public String code = "1";

        /**
         * 已存在的文本字段
         */
        public String codeText = "existing";
    }
}
