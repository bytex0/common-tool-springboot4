package io.github.bytex0.desensitize;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson2.JSONWriter;
import io.github.bytex0.desensitize.annotation.Desensitize;
import io.github.bytex0.desensitize.enums.DesensitizeType;
import io.github.bytex0.desensitize.fastjson.DesensitizeFastjson2ValueFilter;
import io.github.bytex0.desensitize.fastjson.DesensitizeValueFilter;
import io.github.bytex0.desensitize.handler.DesensitizeHandler;
import io.github.bytex0.desensitize.handler.DesensitizeHandlerFactory;
import io.github.bytex0.desensitize.jackson.DesensitizeModule;
import io.github.bytex0.desensitize.jackson.DesensitizeSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 序列化兼容(SerializationCompatibilityTest)通过真实 Jackson/Fastjson API 验证属性隔离与失败保护。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:31:46
 */
class SerializationCompatibilityTest {

    /**
     * 测试工厂不包含外部服务。
     */
    private final DesensitizeHandlerFactory factory = new DesensitizeHandlerFactory(new DefaultListableBeanFactory());

    /**
     * 两种 Fastjson 路径都能读取继承、别名和 getter 规则，且不修改原对象或全局配置。
     */
    @Test
    void shouldMaskInheritedAliasedAndGetterProperties() {
        Profile profile = new Profile();
        for (String json : List.of(legacy(profile), nativeJson(profile))) {
            JsonNode tree = JsonMapper.builder().build().readTree(json);
            assertThat(tree.path("phone_alias").asString()).isEqualTo("138****8000");
            assertThat(tree.path("name").asString()).isEqualTo("张*丰");
            assertThat(tree.path("range").asString()).isEqualTo("A####");
            assertThat(tree.path("custom").asString()).isEqualTo("no-arg");
            assertThat(tree.path("ordinary").asString()).isEqualTo("public");
        }
        assertThat(profile.getPhone()).isEqualTo("13800138000");
        assertThat(JSON.toJSONString(profile)).contains("13800138000");
        assertThat(legacy(List.of(profile))).doesNotContain("13800138000");
        assertThat(nativeJson(List.of(profile))).doesNotContain("13800138000");
        factory.registerHandler(DesensitizeType.NAME, value -> "updated");
        assertThat(legacy(profile)).contains("\"name\":\"updated\"");
        assertThat(nativeJson(profile)).contains("\"name\":\"updated\"");
    }

    /**
     * 处理器异常和错误字段类型不能使 Fastjson 返回原文。
     */
    @Test
    void shouldFailClosedForBadHandlerAndInvalidType() {
        assertThatThrownBy(() -> legacy(new Broken())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> nativeJson(new Broken())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> legacy(new Numeric())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> nativeJson(new Numeric())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new DesensitizeValueFilter(factory)
                .process(new Profile(), "unmapped_phone", "must-not-return"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DesensitizeFastjson2ValueFilter(factory)
                .apply(new Profile(), "unmapped_phone", "must-not-return"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 原 Serializer/Module 包装构造入口仍可接入 Jackson 3，普通字段和根字符串不继承规则。
     */
    @Test
    void shouldRetainContextualSerializerAndModuleEntryPoints() {
        SimpleModule source = new SimpleModule();
        source.addSerializer(String.class, new DesensitizeSerializer(factory));
        JsonMapper mapper = JsonMapper.builder().addModule(new DesensitizeModule(source)).build();
        JsonNode tree = mapper.readTree(mapper.writeValueAsString(new Profile()));
        assertThat(tree.path("phone").asString()).isEqualTo("138****8000");
        assertThat(tree.path("ordinary").asString()).isEqualTo("public");
        assertThat(mapper.writeValueAsString("raw")).isEqualTo("\"raw\"");
    }

    /**
     * 使用原兼容 API 序列化，不修改静态全局 SerializeConfig。
     *
     * @param value 需要处理的对象
     * @return JSON 文本
     */
    private String legacy(Object value) {
        return JSON.toJSONString(value, new DesensitizeValueFilter(factory));
    }

    /**
     * 使用 Fastjson 2 局部写入上下文注册过滤器。
     *
     * @param value 需要处理的对象
     * @return JSON 文本
     */
    private String nativeJson(Object value) {
        try (JSONWriter writer = JSONWriter.of()) {
            writer.getContext().configFilter(new DesensitizeFastjson2ValueFilter(factory));
            writer.writeAny(value);
            return writer.toString();
        }
    }

    /**
     * 父类字段(Parent)验证私有继承字段不再漏检。
     *
     * @author linshiqiang
     * @since 2026-10-06 10:31:46
     */
    public static class Parent {

        /**
         * 合成手机号，别名由 getter 提供。
         */
        @Desensitize(type = DesensitizeType.PHONE)
        private final String phone = "13800138000";

        /**
         * 获取合成手机号，序列化别名应仍匹配字段注解。
         *
         * @return 合成手机号
         */
        @JSONField(name = "phone_alias")
        public String getPhone() {
            return phone;
        }
    }

    /**
     * 测试模型(Profile)覆盖字段、getter、自定义无参处理器和普通值。
     *
     * @author linshiqiang
     * @since 2026-10-06 10:31:46
     */
    public static class Profile extends Parent {

        /**
         * getter 注解应覆盖此字段的全遮蔽策略。
         */
        @Desensitize(type = DesensitizeType.MASK_ALL)
        private final String name = "张三丰";

        /**
         * 显式范围。
         */
        @Desensitize(type = DesensitizeType.MASK_ALL, startIndex = 1, maskChar = "#")
        public String range = "ABCDE";

        /**
         * 无参处理器兼容入口。
         */
        @Desensitize(type = DesensitizeType.CUSTOM, handler = NoArgHandler.class)
        public String custom = "private";

        /**
         * 普通文本不脱敏。
         */
        public String ordinary = "public";

        /**
         * getter 规则优先。
         *
         * @return 原姓名
         */
        @Desensitize(type = DesensitizeType.NAME)
        public String getName() {
            return name;
        }
    }

    /**
     * 无参处理器(NoArgHandler)验证旧非 Bean 的无状态扩展入口。
     *
     * @author linshiqiang
     * @since 2026-10-06 10:31:46
     */
    public static class NoArgHandler implements DesensitizeHandler {

        /**
         * {@inheritDoc}
         */
        @Override
        public String desensitize(String value) {
            return "no-arg";
        }
    }

    /**
     * 失败处理器(FailingHandler)模拟业务策略错误。
     *
     * @author linshiqiang
     * @since 2026-10-06 10:31:46
     */
    public static class FailingHandler implements DesensitizeHandler {

        /**
         * {@inheritDoc}
         */
        @Override
        public String desensitize(String value) {
            throw new IllegalStateException("configured failure");
        }
    }

    /**
     * 失败模型(Broken)不能在策略失败时泄露原字段。
     *
     * @author linshiqiang
     * @since 2026-10-06 10:31:46
     */
    public static class Broken {

        /**
         * 必须经过失败处理器的字段。
         */
        @Desensitize(type = DesensitizeType.CUSTOM, handler = FailingHandler.class)
        public String secret = "must-not-return";
    }

    /**
     * 非法模型(Numeric)验证非 String 属性显式失败。
     *
     * @author linshiqiang
     * @since 2026-10-06 10:31:46
     */
    public static class Numeric {

        /**
         * 不支持数字字段直接脱敏。
         */
        @Desensitize(type = DesensitizeType.PHONE)
        public int phone = 123;
    }
}
