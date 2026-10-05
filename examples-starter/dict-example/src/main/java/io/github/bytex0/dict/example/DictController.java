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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;

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

    /**
     * 注入真实缓存与业务来源。
     *
     * @param cache 当前缓存
     * @param loader 内存字典来源
     */
    public DictController(DictCache cache, InMemoryDictLoader loader) {
        this.cache = cache;
        this.loader = loader;
    }

    /**
     * 输出字符串及数值字典编码，保留原值并补充文本。
     *
     * @param status 状态编码
     * @return 注解模型
     */
    @GetMapping("/sample")
    public ApiResponse<Sample> sample(@RequestParam(defaultValue = "1") String status) {
        Sample sample = new Sample();
        sample.status = status;
        return ApiResponse.ok(sample);
    }

    /**
     * 修改来源并刷新单个类型。
     *
     * @param values 新状态字典
     * @return 操作结果
     */
    @PutMapping("/status")
    public ApiResponse<Void> update(@RequestBody Map<String, String> values) {
        loader.replace("status", values);
        cache.refresh("status", values);
        return ApiResponse.ok();
    }

    /**
     * 只修改数据来源，用于验证完整刷新前后的缓存差异。
     *
     * @param values 新状态字典
     * @return 操作结果
     */
    @PutMapping("/source")
    public ApiResponse<Void> source(@RequestBody Map<String, String> values) {
        loader.replace("status", values);
        return ApiResponse.ok();
    }

    /**
     * 从来源刷新完整缓存。
     *
     * @return 操作结果
     */
    @PostMapping("/refresh")
    public ApiResponse<Void> refresh() {
        cache.refreshAll();
        return ApiResponse.ok();
    }

    /**
     * 查询编码与文本分列的数据库字典。
     *
     * @param code 部门编码，作为绑定参数
     * @return 原编码与可选文本
     */
    @GetMapping("/department")
    public ApiResponse<Department> department(@RequestParam(defaultValue = "D1") String code) {
        Department result = new Department();
        result.code = code;
        return ApiResponse.ok(result);
    }

    /**
     * 通过原四参数入口查询同列匹配的字典。
     *
     * @param value 查询值，始终参数绑定
     * @return 是否存在与文本
     */
    @GetMapping("/legacy")
    public ApiResponse<Map<String, Object>> legacy(@RequestParam String value) {
        String text = cache.getDictText("department", value, "dict_departments", "label");
        return ApiResponse.ok(text == null ? Map.of("found", false) : Map.of("found", true, "text", text));
    }

    /**
     * 不合法字典输入返回明确的请求错误。
     *
     * @return HTTP 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400));
    }

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

    /**
     * 部门输出(Department)通过表字段注解验证真实数据库翻译。
     *
     * @author linshiqiang
     * @since 2026-10-06 02:11:58
     */
    public static class Department {

        /**
         * 部门编码与独立文本列，数据库中没有对应记录时保留编码且不补文本。
         */
        @Dict(value = "department", table = "dict_departments", field = "label", codeField = "code")
        public String code;
    }
}
