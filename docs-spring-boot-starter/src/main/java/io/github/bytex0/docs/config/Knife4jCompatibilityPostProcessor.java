package io.github.bytex0.docs.config;

import com.github.xiaoymin.knife4j.spring.configuration.Knife4jAutoConfiguration;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties;
import com.github.xiaoymin.knife4j.spring.extension.Knife4jOpenApiCustomizer;
import io.github.bytex0.docs.properties.SwaggerProperties;
import java.util.List;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.util.Assert;

/**
 * 仅适配 Knife4j 默认扩展器和 CORS 配置，保留用户 Bean 和显式 EnableKnife4j 用法。
 *
 * @author bytex0
 * @since 2026-10-05 23:51:33
 */
public class Knife4jCompatibilityPostProcessor implements BeanPostProcessor, PriorityOrdered {

    /**
     * 保留原预检结果缓存时间，单位为秒。
     */
    private static final long CORS_MAX_AGE_SECONDS = 10000;

    /**
     * 当前容器，用于检查 Bean 来源以及延迟读取 MVC 注册表。
     */
    private final ConfigurableListableBeanFactory beanFactory;

    /**
     * 注册当前容器，不在后处理器构造时实例化业务 Bean。
     *
     * @param beanFactory 当前 Bean 工厂
     */
    public Knife4jCompatibilityPostProcessor(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * 只处理上游默认工厂的精确类型；自定义实现、子类和其他用户 Bean 原样返回。
     *
     * @param bean 初始化完成的对象
     * @param beanName 注册名称
     * @return 原对象或兼容实现
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if ((bean.getClass() != Knife4jOpenApiCustomizer.class && bean.getClass() != CorsFilter.class)
                || !beanFactory.containsBeanDefinition(beanName)) {
            return bean;
        }
        if (bean instanceof CorsFilter) {
            return corsFilter(beanFactory.getBean(SwaggerProperties.class));
        }
        if (!(beanFactory.getBeanDefinition(beanName) instanceof AnnotatedBeanDefinition definition)
                || definition.getFactoryMethodMetadata() == null
                || !Knife4jAutoConfiguration.class.getName()
                        .equals(definition.getFactoryMethodMetadata().getDeclaringClassName())) {
            return bean;
        }
        return new Knife4jBoot4Customizer(beanFactory.getBean(Knife4jProperties.class),
                beanFactory.getBean(SpringDocConfigProperties.class),
                () -> beanFactory.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                        .getHandlerMethods().values());
    }

    /**
     * 修复原通配 Origin 与凭据组合，保留可配置的跨域能力。
     *
     * @param properties 本项目显式 CORS 安全配置
     * @return 兼容 Spring 7 的 CORS 过滤器
     */
    private CorsFilter corsFilter(SwaggerProperties properties) {
        Assert.notEmpty(properties.getCorsAllowedOrigins(), "CORS Origin列表不能为空");
        List<String> origins = List.copyOf(properties.getCorsAllowedOrigins());
        boolean credentials = Boolean.TRUE.equals(properties.getCorsAllowCredentials());
        Assert.isTrue(!credentials || origins.stream().noneMatch(origin -> origin.contains("*")),
                "携带CORS凭据必须配置明确Origin，不能使用通配");
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowCredentials(credentials);
        cors.addAllowedHeader("*");
        cors.addAllowedMethod("*");
        cors.setMaxAge(CORS_MAX_AGE_SECONDS);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return new CorsFilter(source);
    }

    /**
     * 在通用代理后处理之前替换默认对象，后续代理仍作用于兼容实现。
     *
     * @return 最高优先级
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
