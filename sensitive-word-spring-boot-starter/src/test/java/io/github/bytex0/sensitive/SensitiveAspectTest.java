package io.github.bytex0.sensitive;

import io.github.bytex0.sensitive.annotation.SensitiveWordCheck;
import io.github.bytex0.sensitive.annotation.SensitiveWordField;
import io.github.bytex0.sensitive.config.SensitiveWordAutoConfiguration;
import io.github.bytex0.sensitive.core.DfaSensitiveWordFilter;
import io.github.bytex0.sensitive.core.HandleType;
import io.github.bytex0.sensitive.core.SensitiveWordException;
import io.github.bytex0.sensitive.core.SensitiveWordOperations;
import io.github.bytex0.sensitive.core.SensitiveWordOptions;
import io.github.bytex0.sensitive.handler.SensitiveWordAspect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注解行为(SensitiveAspectTest)通过真实 Spring 代理验证策略优先级和失败不修改参数。
 *
 * @author linshiqiang
 * @since 2026-10-06 09:10:08
 */
class SensitiveAspectTest {

    /**
     * 共享业务组件。
     */
    private SensitiveWordOperations operations;

    /**
     * 真实代理。
     */
    private Target target;

    /**
     * 在公开代理边界测试，不直接测试私有反射实现。
     */
    @BeforeEach
    void setUp() {
        SensitiveWordOptions options = new SensitiveWordOptions();
        options.setWords(Set.of("bad"));
        options.setWhiteList(Set.of("badge"));
        operations = new SensitiveWordOperations(new DfaSensitiveWordFilter(options), options);
        operations.afterPropertiesSet();
        ProxyFactory proxy = new ProxyFactory(new Target());
        proxy.addAdvisor(new SensitiveWordAutoConfiguration().sensitiveWordAdvisor(new SensitiveWordAspect(operations)));
        target = (Target) proxy.getProxy();
    }

    /**
     * 独立参数注解有效，只处理标记的参数。
     */
    @Test
    void processesParameterAnnotationWithoutMethodMarker() {
        assertThat(target.parameter("bad", "bad")).isEqualTo("***:bad");
        assertThat(target.parameter("badge", "bad")).isEqualTo("badge:bad");
        operations.removeWhiteList("badge");
        assertThat(target.parameter("badge", "bad")).isEqualTo("***ge:bad");
    }

    /**
     * 全部参数验证通过前不对 DTO 应用字段修改。
     */
    @Test
    void preservesObjectWhenLaterArgumentRejects() {
        Request request = new Request();
        request.content = "bad";
        assertThatThrownBy(() -> target.object(request, "bad")).isInstanceOf(SensitiveWordException.class);
        assertThat(request.content).isEqualTo("bad");
        assertThat(target.object(request, "safe")).isEqualTo("***");
    }

    /**
     * 字段选择不影响未选字段，未知字段和 final 字段明确报错。
     */
    @Test
    void enforcesSelectionsAndImmutableFields() {
        Request request = new Request();
        request.content = "bad";
        request.other = "bad";
        assertThat(target.selected(request)).isEqualTo("bad");
        assertThat(request.content).isEqualTo("***");
        assertThatThrownBy(() -> target.unknown(request)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> target.immutable(new Immutable("bad"))).hasMessageContaining("不可变字段");
    }

    /**
     * 代理目标(Target)覆盖旧方法注解和新增独立参数支持。
     *
     * @author linshiqiang
     * @since 2026-10-06 09:10:08
     */
    public static class Target {

        /**
         * 只检查第一个参数。
         *
         * @param checked 受保护文本
         * @param ignored 不受保护文本
         * @return 两段文本
         */
        public String parameter(@SensitiveWordCheck(handleType = HandleType.REPLACE) String checked, String ignored) {
            return checked + ":" + ignored;
        }

        /**
         * 字段替换覆盖方法拒绝，但后一个参数拒绝时不能出现部分修改。
         *
         * @param request 对象
         * @param other 第二个文本
         * @return 处理后字段
         */
        @SensitiveWordCheck
        public String object(Request request, String other) {
            return request.content;
        }

        /**
         * 仅选择 content。
         *
         * @param request 对象
         * @return 未选择字段
         */
        @SensitiveWordCheck(fields = "content")
        public String selected(Request request) {
            return request.other;
        }

        /**
         * 错误字段配置不得静默放行。
         *
         * @param request 对象
         * @return 对象
         */
        @SensitiveWordCheck(fields = "missing")
        public Request unknown(Request request) {
            return request;
        }

        /**
         * 不尝试写入不可变对象的 final 字段。
         *
         * @param request 不可变对象
         * @return 原对象
         */
        @SensitiveWordCheck(handleType = HandleType.REPLACE)
        public Immutable immutable(Immutable request) {
            return request;
        }
    }

    /**
     * 可变请求(Request)提供独立字段策略。
     *
     * @author linshiqiang
     * @since 2026-10-06 09:10:08
     */
    public static class Request {

        /**
         * 原字段注解默认替换。
         */
        @SensitiveWordField
        public String content;

        /**
         * 未声明独立策略的字段。
         */
        public String other;
    }

    /**
     * 不可变请求(Immutable)验证明确失败而非静默忽略。
     *
     * @author linshiqiang
     * @since 2026-10-06 09:10:08
     * @param content 内容
     */
    public record Immutable(
            /**
             * 不允许原地改写的内容。
             */
            String content) {
    }
}
