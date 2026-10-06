package io.github.bytex0.desensitize.example;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.desensitize.annotation.Desensitize;
import io.github.bytex0.desensitize.enums.DesensitizeType;
import io.github.bytex0.desensitize.fastjson.DesensitizeFastjson2ValueFilter;
import io.github.bytex0.desensitize.fastjson.DesensitizeValueFilter;
import io.github.bytex0.desensitize.handler.DesensitizeHandlerFactory;
import io.github.bytex0.desensitize.util.DesensitizeUtil;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 脱敏联调接口(MaskIntegrationController)验证三类序列化器、原规则及异常时不返回原文。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:38:00
 */
@RestController
@RequestMapping("/api/desensitize")
public class MaskIntegrationController {

    /**
     * 真实自动装配的处理器工厂。
     */
    private final DesensitizeHandlerFactory factory;

    /**
     * 实例隔离的 Jackson 转换工具。
     */
    private final DesensitizeUtil util;

    /**
     * 原 Fastjson API 兼容过滤器。
     */
    private final DesensitizeValueFilter legacy;

    /**
     * Fastjson 2 原生过滤器。
     */
    private final DesensitizeFastjson2ValueFilter nativeFilter;

    /**
     * 注入 Starter 提供的成品组件，不复制其规则。
     *
     * @param factory 工厂
     * @param util Jackson 工具
     * @param legacy 兼容过滤器
     * @param nativeFilter 原生过滤器
     */
    public MaskIntegrationController(DesensitizeHandlerFactory factory, DesensitizeUtil util,
                                     DesensitizeValueFilter legacy, DesensitizeFastjson2ValueFilter nativeFilter) {
        this.factory = factory;
        this.util = util;
        this.legacy = legacy;
        this.nativeFilter = nativeFilter;
    }

    /**
     * 比较三条序列化路径的输出，返回真正 JSON 而非被二次编码的字符串。
     *
     * @param engine jackson、fastjson 或 fastjson2
     * @return JSON 业务响应
     */
    @GetMapping(value = "/engines/{engine}", produces = MediaType.APPLICATION_JSON_VALUE)
    public String serialize(@PathVariable String engine) {
        return serialize(engine, ApiResponse.ok(new DetailedProfile()));
    }

    /**
     * 验证 record 组件注解在三种实际序列化路径中均生效。
     *
     * @param engine 引擎
     * @return record 的 JSON 响应
     */
    @GetMapping(value = "/records/{engine}", produces = MediaType.APPLICATION_JSON_VALUE)
    public String record(@PathVariable String engine) {
        return serialize(engine, ApiResponse.ok(new MaskedRecord("13800138000", "public")));
    }

    /**
     * 显式模型转换后仍输出相同脱敏内容。
     *
     * @return 脱敏 Map
     */
    @GetMapping("/converted")
    public ApiResponse<Map<String, Object>> converted() {
        Map<String, Object> result = util.convertObject(new DetailedProfile(), new TypeReference<>() { });
        return ApiResponse.ok(result);
    }

    /**
     * 通过完整工厂提供固定合成样本的全部内置策略结果。
     *
     * @return 策略到脱敏文本的映射
     */
    @GetMapping("/rules")
    public ApiResponse<Map<String, String>> rules() {
        Map<DesensitizeType, String> inputs = Map.ofEntries(
                Map.entry(DesensitizeType.PHONE, "13800138000"),
                Map.entry(DesensitizeType.EMAIL, "alice@example.com"),
                Map.entry(DesensitizeType.NAME, "张三丰"),
                Map.entry(DesensitizeType.ID_CARD, "110101199001011234"),
                Map.entry(DesensitizeType.BANK_CARD, "1234567890123456"),
                Map.entry(DesensitizeType.ADDRESS, "北京市朝阳区测试路18号"),
                Map.entry(DesensitizeType.PASSWORD, "synthetic-password"),
                Map.entry(DesensitizeType.CAR_NUMBER, "京A12345"),
                Map.entry(DesensitizeType.FIXED_PHONE, "010-12345678"),
                Map.entry(DesensitizeType.IPV4, "192.168.1.100:6379"),
                Map.entry(DesensitizeType.IPV6, "2001:db8::1"),
                Map.entry(DesensitizeType.PASSPORT, "E12345678"),
                Map.entry(DesensitizeType.MILITARY_ID, "军字12345678"),
                Map.entry(DesensitizeType.CNAPS_CODE, "123456789012"),
                Map.entry(DesensitizeType.MASK_ALL, "synthetic"),
                Map.entry(DesensitizeType.DOMAIN, "api.example.com:443"));
        Map<String, String> result = new LinkedHashMap<>();
        inputs.forEach((type, value) -> result.put(type.name(), factory.getHandler(type).desensitize(value)));
        return ApiResponse.ok(result);
    }

    /**
     * 限制长度后执行 Unicode 范围处理，不记录输入文本。
     *
     * @param value 合成测试文本，最多 128 个 UTF-16 单元
     * @param start 起点
     * @param end 终点，-1 为末尾
     * @param token 替换文本
     * @return 脱敏值
     */
    @GetMapping("/range")
    public ApiResponse<Map<String, String>> range(@RequestParam String value, @RequestParam int start,
                                                  @RequestParam(defaultValue = "-1") int end,
                                                  @RequestParam(required = false) String token) {
        Assert.isTrue(value.length() <= 128, "测试文本太长");
        String replacement = token == null ? "*" : token;
        return ApiResponse.ok(Map.of("value", factory.maskRange(value, start, end, replacement)));
    }

    /**
     * 在序列化错误发生于响应写入前时验证失败保护，不返回含敏感原文的半成品。
     *
     * @param engine 序列化引擎
     * @return 仅错误响应，正常情况下此方法应抛出配置错误
     */
    @GetMapping(value = "/failure/{engine}", produces = MediaType.APPLICATION_JSON_VALUE)
    public String failure(@PathVariable String engine) {
        return serialize(engine, new InvalidProfile());
    }

    /**
     * 选择真实序列化 API，过滤器仅注册到本次调用。
     *
     * @param engine 引擎
     * @param value 待序列化数据
     * @return JSON 文本
     */
    private String serialize(String engine, Object value) {
        return switch (engine) {
            case "jackson" -> util.toJson(value);
            case "fastjson" -> JSON.toJSONString(value, legacy);
            case "fastjson2" -> {
                try (JSONWriter writer = JSONWriter.of()) {
                    writer.getContext().configFilter(nativeFilter);
                    writer.writeAny(value);
                    yield writer.toString();
                }
            }
            default -> throw new IllegalArgumentException("未知序列化引擎");
        };
    }

    /**
     * 错误响应不包含原文或底层异常消息。
     *
     * @return 统一参数或策略错误响应
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.failOfMessage("脱敏参数或策略错误", 400));
    }

    /**
     * 继承模型(DetailedProfile)验证原字段、别名和 getter 合并规则。
     *
     * @author linshiqiang
     * @since 2026-10-06 10:38:00
     */
    public static class DetailedProfile extends MaskProfile {

        /**
         * 多字合成姓名，固定别名在两种 Fastjson API 中均生效。
         */
        @Desensitize(type = DesensitizeType.NAME)
        private final String fullName = "张三丰";

        /**
         * 返回原始姓名，脱敏由 Starter 完成。
         *
         * @return 原始姓名
         */
        @JSONField(name = "full_name")
        @JsonProperty("full_name")
        public String getFullName() {
            return fullName;
        }
    }

    /**
     * 错误范围模型(InvalidProfile)确保失败时不会返回明文。
     *
     * @author linshiqiang
     * @since 2026-10-06 10:38:00
     */
    public static class InvalidProfile {

        /**
         * 错误的空范围，必须使三条路径明确失败。
         */
        @Desensitize(type = DesensitizeType.NAME, startIndex = 4, endIndex = 2)
        public String secret = "must-not-return";
    }

    /**
     * 记录模型(MaskedRecord)验证不可变模型的组件注解。
     *
     * @param phone 合成手机号
     * @param ordinary 普通文本
     * @author linshiqiang
     * @since 2026-10-06 10:38:00
     */
    public record MaskedRecord(
            /**
             * 合成手机号，只改变输出，不修改记录内容。
             */
            @Desensitize(type = DesensitizeType.PHONE) String phone,

            /**
             * 未标注的普通文本。
             */
            String ordinary) {
    }
}
