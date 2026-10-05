package io.github.bytex0.dict;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.bytex0.dict.annotation.Dict;
import io.github.bytex0.dict.config.DictAutoConfiguration;
import io.github.bytex0.dict.jackson.DictModule;
import io.github.bytex0.dict.jackson.DictSerializer;
import io.github.bytex0.dict.properties.DictProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * 字典(DictTest)缓存、数据库边界及JSON字段回归
 *
 * @author bytex0
 * @since 2026-10-05 16:08:16
 */
class DictTest {

    /**
     * 无数据库也能自动配置，支持关闭开关和业务加载器覆盖。
     */
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

    /**
     * 发布数据经过防御性复制，加载失败和容量超限不影响旧快照。
     */
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

    /**
     * 使用真实 JSON 字段名，并尊重属性省略规则。
     */
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

    /**
     * 文本属性名称冲突在输出之前明确失败。
     */
    @Test
    void shouldRejectTextFieldCollision() {
        JsonMapper mapper = JsonMapper.builder().addModule(
                new DictModule(new DictCache(new InMemoryDictLoader(), 10))).build();
        assertThatThrownBy(() -> mapper.writeValueAsString(new Conflict())).isInstanceOf(RuntimeException.class);
    }

    /**
     * 类型值通过参数绑定，SQL 表列名严格校验。
     */
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
     * 自动刷新关闭时不执行完整加载，但仍支持查询时加载单个字典。
     */
    @Test
    void shouldRespectOriginalRefreshProperties() {
        DictLoader loader = mock(DictLoader.class);
        when(loader.loadDict("status")).thenReturn(Map.of("1", "loaded"));
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DictAutoConfiguration.class))
                .withPropertyValues("dict.auto-refresh=false")
                .withBean(DictLoader.class, () -> loader)
                .run(context -> {
                    assertThat(context.getBean(DictProperties.class).getAutoRefresh()).isFalse();
                    verify(loader, never()).loadAllDict();
                    assertThat(context.getBean(DictCache.class).getDictText("status", "1")).isEqualTo("loaded");
                });
    }

    /**
     * 原 Runner 构造方式可绑定当前容器，多个启动回调只完整加载一次。
     */
    @Test
    void shouldRestoreRefresherWithoutDuplicateStartupLoad() {
        DictLoader loader = mock(DictLoader.class);
        when(loader.loadAllDict()).thenReturn(Map.of("status", Map.of("1", "ready")));
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DictAutoConfiguration.class))
                .withBean(DictLoader.class, () -> loader)
                .withBean(DictRefresher.class, () -> new DictRefresher(new DictProperties()))
                .run(context -> {
                    context.getBean(DictRefresher.class).run(null);
                    verify(loader, times(1)).loadAllDict();
                    assertThat(context.getBean(DictCache.class).getDictText("status", "1")).isEqualTo("ready");
                });
    }

    /**
     * 慢加载不能覆盖已经完成的更新，其他读取不会等待其 I/O。
     *
     * @throws Exception 线程协调失败
     */
    @Test
    void shouldDiscardStaleLoadAfterRefresh() throws Exception {
        DictLoader loader = mock(DictLoader.class);
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        when(loader.loadDict("status")).thenAnswer(call -> {
            loading.countDown();
            assertThat(finish.await(5, TimeUnit.SECONDS)).isTrue();
            return Map.of("1", "stale");
        });
        DictCache cache = new DictCache(loader, 10);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var pending = worker.submit(() -> cache.getDictText("status", "1"));
            try {
                assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
                cache.refresh("status", Map.of("1", "fresh"));
                assertThat(cache.getDictText("status", "1")).isEqualTo("fresh");
            } finally {
                finish.countDown();
            }
            assertThat(pending.get(5, TimeUnit.SECONDS)).isEqualTo("fresh");
        }
    }

    /**
     * 原 init 能力按实例隔离，失败不替换旧数据。
     */
    @Test
    void shouldInitializeInstanceWithoutGlobalState() {
        DictCache first = new DictCache(new InMemoryDictLoader(), 10);
        DictCache second = new DictCache(new InMemoryDictLoader(), 10);
        InMemoryDictLoader source = new InMemoryDictLoader();
        source.replace("status", Map.of("1", "first"));
        first.init(source, null);
        assertThat(first.getDictText("status", "1")).isEqualTo("first");
        assertThat(second.getDictText("status", "1")).isNull();
        DictLoader failing = mock(DictLoader.class);
        when(failing.loadAllDict()).thenThrow(new IllegalStateException("failed"));
        assertThatThrownBy(() -> first.init(failing, null)).hasMessage("failed");
        assertThat(first.getDictText("status", "1")).isEqualTo("first");
    }

    /**
     * 恢复原四参数表字段查询，同时支持独立编码列，非法值不能改变 SQL 语义。
     */
    @Test
    void shouldRestoreTableFallbackAndBoundLookup() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE departments (code VARCHAR(40), label VARCHAR(50))");
        jdbc.update("INSERT INTO departments VALUES (?, ?)", "D1", "Engineering");
        DictCache cache = new DictCache(new InMemoryDictLoader(), jdbc, 10);
        assertThat(cache.getDictText("department", "Engineering", "departments", "label")).isEqualTo("Engineering");
        assertThat(cache.getDictText("department", "D1", "departments", "label", "code")).isEqualTo("Engineering");
        assertThat(cache.getDictText("department", "x' OR '1'='1", "departments", "label", "code")).isNull();
        assertThatThrownBy(() -> cache.getDictText("department", "D1", "departments;DROP TABLE x", "label", "code"))
                .isInstanceOf(IllegalArgumentException.class);
        jdbc.update("INSERT INTO departments VALUES (?, ?)", "D1", "Duplicate");
        assertThatThrownBy(() -> cache.getDictText("department", "D1", "departments", "label", "code"))
                .hasMessageContaining("expected 1");
    }

    /**
     * 原字符串序列化器和包装模块恢复，现代模块共存时不生成重复文本字段。
     */
    @Test
    void shouldRestoreContextualSerializerAndModuleWrapper() {
        InMemoryDictLoader loader = new InMemoryDictLoader();
        loader.replace("status", Map.of("1", "enabled"));
        DictCache cache = new DictCache(loader, 10);
        SimpleModule legacy = new SimpleModule().addSerializer(String.class, new DictSerializer(cache));
        JsonMapper wrapped = JsonMapper.builder().addModule(new DictModule(legacy)).build();
        String original = wrapped.writeValueAsString(new Sample());
        assertThat(original).containsOnlyOnce("\"stateText\"").contains("\"stateText\":\"enabled\"");
        SimpleModule noArgs = new SimpleModule().addSerializer(String.class, new DictSerializer());
        JsonMapper modern = JsonMapper.builder().addModule(noArgs).addModule(new DictModule(cache)).build();
        String json = modern.writeValueAsString(new Sample());
        assertThat(json).containsOnlyOnce("\"stateText\"").doesNotContain("\"empty\"");
        assertThat(modern.writeValueAsString(Map.of("plain", "1"))).isEqualTo("{\"plain\":\"1\"}");
    }

    /**
     * 字典样例(Sample)重命名与省略属性
     *
     * @author bytex0
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
     * @author bytex0
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
