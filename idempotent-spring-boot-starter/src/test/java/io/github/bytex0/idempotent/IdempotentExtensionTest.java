package io.github.bytex0.idempotent;

import io.github.bytex0.idempotent.core.IdempotentKeyGenerator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.expression.BeanFactoryResolver;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 幂等扩展(IdempotentExtensionTest)验证原自定义生成器与当前容器的兼容边界。
 *
 * @author linshiqiang
 * @since 2026-10-06 01:48:45
 */
class IdempotentExtensionTest {

    /**
     * 仅覆盖原四参数接口的子类，在新的默认切面入口仍然生效。
     *
     * @throws Exception 样例反射失败
     */
    @Test
    void shouldHonorLegacyOverride() throws Exception {
        IdempotentKeyGenerator keys = new LegacyGenerator();
        Method method = getClass().getDeclaredMethod("operation", String.class);
        assertThat(keys.generateInvocationKey("", "scope:", mock(ProceedingJoinPoint.class), method))
                .isEqualTo("legacy-custom");
    }

    /**
     * 同时覆盖新旧接口时，优先选择现代方法描述符接口。
     *
     * @throws Exception 样例反射失败
     */
    @Test
    void shouldPreferModernOverride() throws Exception {
        IdempotentKeyGenerator keys = new ModernGenerator();
        Method method = getClass().getDeclaredMethod("operation", String.class);
        assertThat(keys.generateInvocationKey("", "scope:", mock(ProceedingJoinPoint.class), method))
                .isEqualTo("modern-custom");
    }

    /**
     * 无参原扩展 Bean 自动绑定所属容器，不依赖静态上下文。
     *
     * @throws Exception 样例反射失败
     */
    @Test
    void shouldBindNoArgGeneratorToItsOwnContext() throws Exception {
        Method method = getClass().getDeclaredMethod("operation", String.class);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.getTarget()).thenReturn(this);
        when(point.getArgs()).thenReturn(new Object[]{"request"});
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(IdempotentConfiguration.class))
                .withBean("greeting", String.class, () -> "hello")
                .withBean(IdempotentKeyGenerator.class, IdempotentKeyGenerator::new)
                .run(context -> {
                    IdempotentKeyGenerator keys = context.getBean(IdempotentKeyGenerator.class);
                    String fromBean = keys.generateInvocationKey("@greeting", "scope", point, method);
                    String literal = keys.generateInvocationKey("'hello'", "scope", point, method);
                    assertThat(fromBean).isEqualTo(literal);
                });
    }

    /**
     * 原子生命周期回调不能覆盖构造器显式指定的工厂。
     *
     * @throws Exception 样例反射失败
     */
    @Test
    void shouldKeepExplicitFactoryBinding() throws Exception {
        DefaultListableBeanFactory explicit = new DefaultListableBeanFactory();
        explicit.registerSingleton("greeting", "hello");
        IdempotentKeyGenerator keys = new IdempotentKeyGenerator(explicit);
        keys.setBeanFactory(new DefaultListableBeanFactory());
        Method method = getClass().getDeclaredMethod("operation", String.class);
        assertThat(keys.generateKey("@greeting", "scope", this, method, new Object[]{"request"}))
                .isEqualTo(keys.generateKey("'hello'", "scope", this, method, new Object[]{"request"}));
    }

    /**
     * 为方法描述符提供真实参数名。
     *
     * @param request 请求标识
     */
    private void operation(String request) {
    }

    /**
     * 原接口扩展(LegacyGenerator)模拟已存在的自定义生成策略。
     *
     * @author linshiqiang
     * @since 2026-10-06 01:48:45
     */
    private static class LegacyGenerator extends IdempotentKeyGenerator {

        /**
         * {@inheritDoc}
         */
        @Override
        public String generateKey(String expression, String prefix, ProceedingJoinPoint point, BeanFactoryResolver resolver) {
            return "legacy-custom";
        }
    }

    /**
     * 双接口扩展(ModernGenerator)模拟逐步升级后的自定义生成器。
     *
     * @author linshiqiang
     * @since 2026-10-06 01:48:45
     */
    private static class ModernGenerator extends LegacyGenerator {

        /**
         * {@inheritDoc}
         */
        @Override
        public String generateKey(String expression, String prefix, Object target, Method method, Object[] args) {
            return "modern-custom";
        }
    }
}
