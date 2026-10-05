package io.github.bytex0.cache.example;

import io.github.bytex0.cache.factory.LocalCaffeineCacheFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 缓存示例(CacheControllerTest)真实 Starter 集成及接口验证
 *
 * @author bytex0
 * @since 2026-10-05 15:16:06
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CacheControllerTest {

    /**
     * MVC 测试入口
     */
    private final MockMvc mvc;

    /**
     * 真实自动配置的缓存工厂
     */
    private final LocalCaffeineCacheFactory factory;

    CacheControllerTest(MockMvc mvc, LocalCaffeineCacheFactory factory) {
        this.mvc = mvc;
        this.factory = factory;
    }

    @BeforeEach
    void clearCache() {
        factory.clearAllCaches();
    }

    @Test
    void shouldUseActualRegisteredCacheForCrud() throws Exception {
        assertThat(factory.getCache(DemoCache.class)).isNotNull();
        mvc.perform(put("/api/cache/entry").param("key", "key").param("value", "value"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/cache/entry").param("key", "key"))
                .andExpect(jsonPath("$.data.value").value("value"));
        mvc.perform(delete("/api/cache/entry").param("key", "key")).andExpect(status().isOk());
        mvc.perform(get("/api/cache/entry").param("key", "key"))
                .andExpect(jsonPath("$.data.present").value(false));
    }

    @Test
    void shouldReuseLoadedValueAndReportStatistics() throws Exception {
        mvc.perform(get("/api/cache/load").param("key", "key").param("value", "first"))
                .andExpect(jsonPath("$.data.value").value("first"));
        mvc.perform(get("/api/cache/load").param("key", "key").param("value", "second"))
                .andExpect(jsonPath("$.data.value").value("first"));
        mvc.perform(get("/api/cache/stats")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.demoCache.hitCount").isNumber());
    }

    @Test
    void shouldClearAllCaches() throws Exception {
        factory.getCache(DemoCache.class).put("key", "value");
        mvc.perform(delete("/api/cache/all")).andExpect(status().isOk());
        mvc.perform(get("/api/cache/stats")).andExpect(jsonPath("$.data.demoCache.size").value(0));
    }

    @Test
    void shouldRejectBlankKey() throws Exception {
        mvc.perform(get("/api/cache/entry").param("key", " "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }
}
