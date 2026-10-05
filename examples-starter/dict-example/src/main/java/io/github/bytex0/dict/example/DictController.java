package io.github.bytex0.dict.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.dict.DictCache;
import io.github.bytex0.dict.InMemoryDictLoader;
import io.github.bytex0.dict.annotation.Dict;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 字典接口(DictController)验证翻译及刷新
 *
 * @author bytex0
 * @since 2026-10-05 16:08:16
 */
@RestController
@RequestMapping("/api/dict")
public class DictController {

    /**
     * 实际字典缓存
     */
    private final DictCache cache;

    /**
     * 示例数据源
     */
    private final InMemoryDictLoader loader;

    public DictController(DictCache cache, InMemoryDictLoader loader) { this.cache = cache; this.loader = loader; }

    @GetMapping("/sample")
    public ApiResponse<Sample> sample(@RequestParam(defaultValue = "1") String status) {
        Sample sample = new Sample();
        sample.status = status;
        return ApiResponse.ok(sample);
    }

    @PutMapping("/status")
    public ApiResponse<Void> update(@RequestBody Map<String, String> values) {
        loader.replace("status", values);
        cache.refresh("status", values);
        return ApiResponse.ok();
    }

    @PostMapping("/refresh")
    public ApiResponse<Void> refresh() { cache.refreshAll(); return ApiResponse.ok(); }

    /**
     * 字典模型(Sample)重命名及数值类型
     *
     * @author bytex0
     * @since 2026-10-05 16:08:16
     */
    public static class Sample {

        /**
         * 原始状态code
         */
        @Dict("status")
        @JsonProperty("state")
        public String status;

        /**
         * 数值型code
         */
        @Dict("status")
        public Integer numeric = 1;
    }
}
