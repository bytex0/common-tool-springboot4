package io.github.bytex0.sensitive.config;

import io.github.bytex0.sensitive.core.DfaSensitiveWordFilter;
import io.github.bytex0.sensitive.core.SensitiveWordConfigurationSource;
import io.github.bytex0.sensitive.core.SensitiveWordFilter;
import io.github.bytex0.sensitive.core.SensitiveWordOperations;
import io.github.bytex0.sensitive.handler.SensitiveWordAspect;
import io.github.bytex0.sensitive.handler.SensitiveWordService;
import io.github.bytex0.sensitive.properties.SensitiveWordProperties;
import io.github.bytex0.sensitive.util.SensitiveWordUtil;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.context.annotation.Role;
import org.aopalliance.intercept.MethodInterceptor;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;

/**
 * 原装配入口(SensitiveWordAutoConfiguration)恢复原服务和过滤器，所有默认扩展均可覆盖。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
@AutoConfiguration
@EnableConfigurationProperties
@ConditionalOnProperty(prefix = "sensitive-word", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SensitiveWordAutoConfiguration {

    /**
     * 原包与根包配置共享实际字段；单独使用原配置时保持独立默认值。
     *
     * @param sources 可选根包来源
     * @return 原配置视图
     */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveWordProperties legacySensitiveWordProperties(ObjectProvider<SensitiveWordConfigurationSource> sources) {
        SensitiveWordConfigurationSource source = sources.getIfAvailable();
        return source == null ? new SensitiveWordProperties() : new SensitiveWordProperties(source.options());
    }

    /**
     * 创建原过滤器，不写入任何静态全局容器。
     *
     * @param properties 匹配配置
     * @return 过滤器
     */
    @Bean
    @ConditionalOnMissingBean(SensitiveWordFilter.class)
    public DfaSensitiveWordFilter sensitiveWordFilter(SensitiveWordProperties properties) {
        return new DfaSensitiveWordFilter(properties);
    }

    /**
     * 保留原服务工厂方法，供直接 Java 调用。
     *
     * @param filter 过滤器
     * @param properties 配置
     * @return 原服务
     */
    public SensitiveWordService sensitiveWordService(SensitiveWordFilter filter, SensitiveWordProperties properties) {
        return new SensitiveWordService(filter, properties);
    }

    /**
     * 注册原包服务，与根包门面共享同一组件。
     *
     * @param filter 过滤器
     * @param properties 配置
     * @param resources 资源加载器
     * @return 原服务
     */
    @Bean
    @ConditionalOnMissingBean(SensitiveWordOperations.class)
    public SensitiveWordService legacySensitiveWordService(SensitiveWordFilter filter, SensitiveWordProperties properties,
                                                           ResourceLoader resources) {
        return new SensitiveWordService(filter, properties, resources);
    }

    /**
     * 保留原切面工厂调用。
     *
     * @param filter 过滤器
     * @param properties 配置
     * @return 切面逻辑
     */
    public SensitiveWordAspect sensitiveWordAspect(SensitiveWordFilter filter, SensitiveWordProperties properties) {
        return new SensitiveWordAspect(filter, properties);
    }

    /**
     * 注解路径直接复用业务服务，动态白名单立即生效。
     *
     * @param operations 原业务组件
     * @return 拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveWordAspect sensitiveWordAspect(SensitiveWordOperations operations) {
        return new SensitiveWordAspect(operations);
    }

    /**
     * 方法匹配器支持独立参数标记，字段策略在显式检查边界内生效，避免旧切点漏检。
     *
     * @param aspects 延迟获取的拦截器
     * @return Spring Advisor
     */
    @Bean
    @ConditionalOnMissingBean(name = "sensitiveWordAdvisor")
    @Role(ROLE_INFRASTRUCTURE)
    public static DefaultPointcutAdvisor sensitiveWordAdvisor(ObjectProvider<SensitiveWordAspect> aspects) {
        return advisor(aspects::getObject);
    }

    /**
     * 保留直接构造 Advisor 的入口，不触发额外容器查找。
     *
     * @param aspect 拦截器
     * @return Advisor
     */
    public DefaultPointcutAdvisor sensitiveWordAdvisor(SensitiveWordAspect aspect) {
        return advisor(() -> aspect);
    }

    /**
     * 匹配阶段不解析业务依赖，只有真正调用受保护方法才获取拦截器。
     *
     * @param aspects 拦截器来源
     * @return Advisor
     */
    private static DefaultPointcutAdvisor advisor(Supplier<SensitiveWordAspect> aspects) {
        return new DefaultPointcutAdvisor(new StaticMethodMatcherPointcut() {
            /**
             * {@inheritDoc}
             */
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                return SensitiveWordAspect.matches(method, targetClass);
            }
        }, (MethodInterceptor) invocation -> aspects.get().invoke(invocation));
    }

    /**
     * 提供实例快捷工具；词库引用共享，工具切换引用只影响工具本身。
     *
     * @param operations 已初始化的业务服务
     * @return 工具实例
     */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveWordUtil sensitiveWordUtil(SensitiveWordOperations operations) {
        return new SensitiveWordUtil(operations.getFilter());
    }
}
