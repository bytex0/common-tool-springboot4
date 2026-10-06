package io.github.bytex0.util;

import io.github.bytex0.balancer.RandomLoadBalancer;
import io.github.bytex0.balancer.RoundRobinLoadBalancer;
import io.github.bytex0.chain.AbstractChainHandler;
import io.github.bytex0.enums.BaseEnum;
import io.github.bytex0.exception.ParamsException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 轮询、责任链、编码枚举、分组校验与代理地址信任测试。
 *
 * @author bytex0
 * @since 2026-10-06 15:06:13
 */
class SelectionAndValidationTest {

    /**
     * 验证首次轮询、先递增顺序、实例隔离、容量淘汰和清空。
     */
    @Test
    void roundRobinPreservesOrderAndBoundsKeyState() {
        List<Integer> values = List.of(0, 1, 2);
        RoundRobinLoadBalancer<Integer> balancer = new RoundRobinLoadBalancer<>(1);
        assertThat(balancer.get("a", values)).isEqualTo(1);
        assertThat(balancer.get("a", values)).isEqualTo(2);
        assertThat(balancer.get("b", values)).isEqualTo(1);
        assertThat(balancer.get("a", values)).isEqualTo(1);
        balancer.clear();
        assertThat(balancer.get("a", values)).isEqualTo(1);
        assertThat(new RoundRobinLoadBalancer<Integer>().get("a", values)).isEqualTo(1);
        assertThat(balancer.get(List.of())).isNull();
        assertThat(balancer.get(values)).isEqualTo(1);
        assertThat(balancer.get(List.of(9))).isEqualTo(9);
        assertThat(new RandomLoadBalancer<Integer>().get(List.of(7))).isEqualTo(7);
        assertThatThrownBy(() -> new RandomLoadBalancer<>().get(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证责任链顺序、空链、重复节点和构建后修改的拒绝行为。
     */
    @Test
    void chainCannotBeMutatedOrLinkedIntoCyclesByBuilder() {
        AbstractChainHandler.Builder<List<String>> builder = new AbstractChainHandler.Builder<>();
        RecordingHandler first = new RecordingHandler("first");
        RecordingHandler second = new RecordingHandler("second");
        builder.addHandler(first);
        builder.addHandler(second);
        AbstractChainHandler<List<String>> chain = builder.build();
        List<String> result = new ArrayList<>();
        chain.doHandler(result);
        assertThat(result).containsExactly("first", "second");
        assertThatThrownBy(() -> builder.addHandler(new RecordingHandler("third")))
                .isInstanceOf(IllegalStateException.class);
        AbstractChainHandler.Builder<List<String>> another = new AbstractChainHandler.Builder<>();
        assertThatThrownBy(() -> another.addHandler(second)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new AbstractChainHandler.Builder<>().build()).isNull();
    }

    /**
     * 验证显式编码解析不使用序号，空值和未匹配值返回 null。
     */
    @Test
    void enumLookupIsTypeSafeAndDeterministic() {
        assertThat(BaseEnum.parseByCode(Status.class, 10)).isEqualTo(Status.FIRST);
        assertThat(BaseEnum.parseByCode(Status.class, null)).isNull();
        assertThat(BaseEnum.parseByCode(Status.class, 99)).isNull();
        assertThat(Status.FIRST.getName()).isEqualTo("FIRST");
    }

    /**
     * 验证默认分组、指定分组以及两个属性校验重载。
     */
    @Test
    void validatesDefaultGroupsAndIndividualProperties() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            ValidationUtil validation = new ValidationUtil(factory.getValidator());
            Input invalid = new Input("", -1);
            assertThatThrownBy(() -> validation.validate(invalid)).isInstanceOf(ParamsException.class)
                    .hasMessage("name is required");
            assertThatThrownBy(() -> validation.validate(invalid, CreateGroup.class))
                    .isInstanceOf(ParamsException.class).hasMessage("age is invalid");
            assertThatThrownBy(() -> validation.validate(invalid, "name")).isInstanceOf(ParamsException.class);
            assertThatThrownBy(() -> validation.validate(invalid, "age", CreateGroup.class))
                    .isInstanceOf(ParamsException.class);
            validation.validate(new Input("valid", 1));
        }
    }

    /**
     * 验证默认拒绝代理头伪造，显式信任代理时才读取头部。
     */
    @Test
    void trustsForwardedHeadersOnlyWhenExplicitlyRequested() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "198.51.100.3, 192.0.2.10");
        assertThat(NetworkUtil.getClientIp(request)).isEqualTo("192.0.2.10");
        assertThat(NetworkUtil.getClientIp(request, true)).isEqualTo("198.51.100.3");
        assertThat(NetworkUtil.getLocalIp()).isNotBlank();
    }

    /**
     * 将处理顺序写入上下文的测试节点。
     *
     * @author bytex0
     * @since 2026-10-06 15:06:13
     */
    private static final class RecordingHandler extends AbstractChainHandler<List<String>> {

        /**
         * 当前步骤名称。
         */
        private final String name;

        /**
         * 创建测试节点。
         *
         * @param name 步骤名称
         */
        private RecordingHandler(String name) {
            this.name = name;
        }

        /**
         * 记录本步骤后继续后继节点。
         *
         * @param context 顺序记录
         */
        @Override
        public void doHandler(List<String> context) {
            context.add(name);
            nextHandler(context);
        }
    }

    /**
     * 用于验证重复和空业务编码的测试枚举。
     *
     * @author bytex0
     * @since 2026-10-06 15:06:13
     */
    private enum Status implements BaseEnum<Status> {

        /**
         * 未设置业务编码的状态。
         */
        UNKNOWN(null),

        /**
         * 第一个编码为 10 的状态。
         */
        FIRST(10),

        /**
         * 重复编码测试，解析时保留第一个声明项。
         */
        DUPLICATE(10);

        /**
         * 显式业务编码。
         */
        private final Integer code;

        /**
         * 创建测试状态。
         *
         * @param code 业务编码
         */
        Status(Integer code) {
            this.code = code;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Integer getCode() {
            return code;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getName() {
            return name();
        }
    }

    /**
     * 测试用创建校验分组。
     *
     * @author bytex0
     * @since 2026-10-06 15:06:13
     */
    private interface CreateGroup {
    }

    /**
     * 分组校验测试模型。
     *
     * @param name 默认组校验姓名
     * @param age 创建组校验年龄
     * @author bytex0
     * @since 2026-10-06 15:06:13
     */
    private record Input(
            /**
             * 默认组必填姓名。
             */
            @NotBlank(message = "name is required")
            String name,

            /**
             * 创建组要求非负的年龄。
             */
            @Min(value = 0, groups = CreateGroup.class, message = "age is invalid")
            int age) {
    }
}
