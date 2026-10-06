package io.github.bytex0.desensitize;

import io.github.bytex0.desensitize.enums.DesensitizeType;
import io.github.bytex0.desensitize.handler.DesensitizeHandler;
import io.github.bytex0.desensitize.handler.DesensitizeHandlerFactory;
import io.github.bytex0.desensitize.annotation.DesensitizeFor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 脱敏契约(HandlerCompatibilityTest)验证原工厂和正常输入的规则，不以通用全遮蔽替代。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:25:51
 */
class HandlerCompatibilityTest {

    /**
     * 原姓名、银行卡、地址和固话规则在正常输入上保持原输出。
     */
    @Test
    void shouldRestoreOriginalHandlersAndRules() {
        DesensitizeHandlerFactory factory = new DesensitizeHandlerFactory(new DefaultListableBeanFactory());
        factory.afterPropertiesSet();
        assertThat(factory.getHandler(DesensitizeType.NAME).desensitize("张三丰")).isEqualTo("张*丰");
        assertThat(factory.getHandler(DesensitizeType.BANK_CARD).desensitize("1234567890123456"))
                .isEqualTo("1234****3456");
        assertThat(factory.getHandler(DesensitizeType.ADDRESS).desensitize("北京市朝阳区测试路18号"))
                .isEqualTo("北京市朝阳区****8号");
        assertThat(factory.getHandler(DesensitizeType.FIXED_PHONE).desensitize("010-12345678"))
                .isEqualTo("010-****5678");
        assertThat(factory.getHandler(DesensitizeType.DOMAIN).desensitize("api.example.com:443"))
                .isEqualTo("****.example.com:443");
        assertThat(factory.getHandler(DesensitizeType.PASSWORD).desensitize("long-secret")).isEqualTo("******");
    }

    /**
     * 动态注册与 reverse 原接口同时有效。
     */
    @Test
    void shouldHonorRegisteredHandlersEverywhere() {
        DesensitizeHandlerFactory factory = new DesensitizeHandlerFactory(new DefaultListableBeanFactory());
        DesensitizeHandler handler = value -> "changed";
        factory.registerHandler(DesensitizeType.NAME, handler);
        assertThat(factory.mask("张三", DesensitizeType.NAME)).isEqualTo("changed");
        assertThat(factory.getHandler(DesensitizeType.NAME)).isSameAs(handler);
        assertThat(handler.reverse("masked")).containsExactly("masked");
    }

    /**
     * 代理扩展可以覆盖默认规则，重复声明不能依赖容器遍历顺序悄悄覆盖。
     */
    @Test
    void shouldDiscoverProxyHandlersAndRejectDuplicateDeclarations() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        ProxyFactory proxy = new ProxyFactory(new Replacement());
        beans.registerSingleton("first", proxy.getProxy());
        DesensitizeHandlerFactory factory = new DesensitizeHandlerFactory(beans);
        factory.afterSingletonsInstantiated();
        assertThat(factory.mask("张三", DesensitizeType.NAME)).isEqualTo("replacement");
        beans.registerSingleton("duplicate", new Replacement());
        assertThatThrownBy(factory::afterSingletonsInstantiated).isInstanceOf(IllegalStateException.class);
        assertThat(factory.mask("张三", DesensitizeType.NAME)).isEqualTo("replacement");
    }

    /**
     * 声明式替换(Replacement)用于验证代理后的目标类注解解析。
     *
     * @author linshiqiang
     * @since 2026-10-06 10:38:00
     */
    @DesensitizeFor(DesensitizeType.NAME)
    public static class Replacement implements DesensitizeHandler {

        /**
         * {@inheritDoc}
         */
        @Override
        public String desensitize(String value) {
            return "replacement";
        }
    }
}
