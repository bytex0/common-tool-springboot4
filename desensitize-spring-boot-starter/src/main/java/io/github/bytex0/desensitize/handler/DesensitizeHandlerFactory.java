package io.github.bytex0.desensitize.handler;

import io.github.bytex0.desensitize.annotation.Desensitize;
import io.github.bytex0.desensitize.annotation.DesensitizeFor;
import io.github.bytex0.desensitize.enums.DesensitizeType;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.Assert;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 脱敏策略(DesensitizeHandlerFactory)完整默认策略和Spring扩展
 *
 * @author bytex0
 * @since 2026-10-05 15:57:28
 */
public class DesensitizeHandlerFactory implements InitializingBean, SmartInitializingSingleton {

    /**
     * 当前上下文 Bean 工厂，不保存全局应用上下文。
     */
    private final ListableBeanFactory beanFactory;

    /**
     * 默认处理器和动态覆盖，所有调用入口共享。
     */
    private final Map<DesensitizeType, DesensitizeHandler> handlers = new ConcurrentHashMap<>();

    /**
     * 创建工厂即可使用完整默认策略，不依赖 Spring 初始化回调才能调用。
     *
     * @param beanFactory 自定义处理器来源
     */
    public DesensitizeHandlerFactory(ListableBeanFactory beanFactory) {
        Assert.notNull(beanFactory, "Bean工厂不能为空");
        this.beanFactory = beanFactory;
        handlers.put(DesensitizeType.PHONE, new PhoneDesensitizeHandler());
        handlers.put(DesensitizeType.EMAIL, new EmailDesensitizeHandler());
        handlers.put(DesensitizeType.NAME, new NameDesensitizeHandler());
        handlers.put(DesensitizeType.ID_CARD, new IdCardDesensitizeHandler());
        handlers.put(DesensitizeType.BANK_CARD, new BankCardDesensitizeHandler());
        handlers.put(DesensitizeType.ADDRESS, new AddressDesensitizeHandler());
        handlers.put(DesensitizeType.PASSWORD, new PasswordDesensitizeHandler());
        handlers.put(DesensitizeType.CAR_NUMBER, new CarNumberDesensitizeHandler());
        handlers.put(DesensitizeType.FIXED_PHONE, new FixedPhoneDesensitizeHandler());
        handlers.put(DesensitizeType.IPV4, new IpDesensitizeHandler());
        handlers.put(DesensitizeType.IPV6, new Ipv6DesensitizeHandler());
        handlers.put(DesensitizeType.PASSPORT, new PassportDesensitizeHandler());
        handlers.put(DesensitizeType.MILITARY_ID, new MilitaryIdDesensitizeHandler());
        handlers.put(DesensitizeType.CNAPS_CODE, new CnapsCodeDesensitizeHandler());
        handlers.put(DesensitizeType.MASK_ALL, new MastAllDesensitizeHandler());
        handlers.put(DesensitizeType.DOMAIN, new DomainDesensitizeHandler());
    }

    /**
     * 保留旧初始化入口；默认策略已构造完成，不在此提前创建依赖工厂的扩展 Bean。
     */
    @Override
    public void afterPropertiesSet() {
        Assert.notEmpty(handlers, "默认脱敏处理器尚未注册");
    }

    /**
     * 单例就绪后收集扩展，支持代理类注解，重复策略明确失败。
     */
    @Override
    public void afterSingletonsInstantiated() {
        Map<DesensitizeType, DesensitizeHandler> replacements = new EnumMap<>(DesensitizeType.class);
        beanFactory.getBeansOfType(DesensitizeHandler.class).values().forEach(handler -> {
            DesensitizeFor annotation = AnnotatedElementUtils.findMergedAnnotation(AopUtils.getTargetClass(handler),
                    DesensitizeFor.class);
            if (annotation != null) {
                Assert.state(replacements.putIfAbsent(annotation.value(), handler) == null,
                        "同一策略不能声明多个DesensitizeFor处理器");
            }
        });
        handlers.putAll(replacements);
    }

    /**
     * 获取实际生效的处理器。
     *
     * @param type 策略类型
     * @return 非空处理器，未注册 CUSTOM 时明确失败
     */
    public DesensitizeHandler getHandler(DesensitizeType type) {
        Assert.notNull(type, "脱敏类型不能为空");
        DesensitizeHandler handler = handlers.get(type);
        Assert.notNull(handler, "脱敏策略未注册");
        return handler;
    }

    /**
     * 原子替换一个策略，不影响其他策略。
     *
     * @param type 被替换的策略
     * @param handler 线程安全的处理器
     */
    public void registerHandler(DesensitizeType type, DesensitizeHandler handler) {
        Assert.notNull(type, "脱敏类型不能为空");
        Assert.notNull(handler, "脱敏处理器不能为空");
        handlers.put(type, handler);
    }

    /**
     * 按属性注解执行处理；失败直接抛出，不能降级返回敏感原文。
     *
     * @param value 原始文本
     * @param annotation 属性参数
     * @return 脱敏结果
     */
    public String mask(String value, Desensitize annotation) {
        Assert.notNull(annotation, "脱敏注解不能为空");
        DesensitizeRules.validateToken(annotation.maskChar());
        if (value == null || value.isEmpty()) {
            return value;
        }
        DesensitizeHandler handler = annotation.type() == DesensitizeType.CUSTOM
                ? custom(annotation.handler()) : getHandler(annotation.type());
        return handler.desensitize(value, annotation);
    }

    /**
     * 按类型调用实际生效的策略，包含动态注册和声明式覆盖。
     *
     * @param value 原始文本
     * @param type 策略
     * @return 脱敏结果
     */
    public String mask(String value, DesensitizeType type) {
        Assert.notNull(type, "脱敏类型不能为空");
        if (value == null || value.isEmpty()) {
            return value;
        }
        return getHandler(type).desensitize(value);
    }

    /**
     * 直接使用 Unicode 范围，不需要构造注解。
     *
     * @param value 文本
     * @param startIndex 包含的起点
     * @param endIndex 不包含的终点，-1 指整个末尾
     * @param token 替换文本
     * @return 脱敏文本
     */
    public String maskRange(String value, int startIndex, int endIndex, String token) {
        return DesensitizeRules.range(value, startIndex, endIndex, token);
    }

    /**
     * 优先使用容器 Bean；保留原公开无参构造器接入，用于无资源的无状态处理器。
     *
     * @param type 指定类
     * @return 容器实例或本次新建的无状态实例
     */
    private DesensitizeHandler custom(Class<? extends DesensitizeHandler> type) {
        Assert.isTrue(type != DesensitizeHandler.class, "CUSTOM必须指定处理器");
        String[] names = beanFactory.getBeanNamesForType(type);
        if (names.length > 0) {
            return beanFactory.getBean(type);
        }
        try {
            return type.getConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("自定义处理器需要Spring Bean或公开无参构造器", exception);
        }
    }
}
