package io.github.bytex0.disruptor.processor;

import com.lmax.disruptor.dsl.ProducerType;
import io.github.bytex0.disruptor.annotation.DisruptorListener;
import io.github.bytex0.disruptor.annotation.WaitStrategyType;
import io.github.bytex0.disruptor.config.DisruptorProperties;
import io.github.bytex0.disruptor.core.DisruptorEngine;
import io.github.bytex0.disruptor.template.DisruptorTemplate;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 监听装配(DisruptorListenerProcessor)在单例完成后注册方法，避免早期消费半初始化 Bean。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:17:49
 */
public class DisruptorListenerProcessor implements BeanPostProcessor, SmartInitializingSingleton {

    /**
     * 延迟获取模板，防止 BeanPostProcessor 提前实例化业务处理器。
     */
    private final Supplier<DisruptorTemplate> templates;

    /**
     * 全局默认配置来源。
     */
    private final Supplier<DisruptorProperties> defaults;

    /**
     * 待注册方法，不执行根包扫描。
     */
    private final ConcurrentLinkedQueue<ListenerMethod> methods = new ConcurrentLinkedQueue<>();

    /**
     * 是否完成单例注册阶段。
     */
    private final AtomicBoolean initialized = new AtomicBoolean();

    /**
     * 保留原模板构造器，手工使用时须调用单例完成回调。
     *
     * @param template 原模板入口
     */
    public DisruptorListenerProcessor(DisruptorTemplate template) {
        this(() -> template, DisruptorProperties::new);
    }

    /**
     * 注入延迟提供器，不触发业务 Bean 提前实例化。
     *
     * @param templates 模板来源
     * @param defaults 全局默认
     */
    public DisruptorListenerProcessor(Supplier<DisruptorTemplate> templates, Supplier<DisruptorProperties> defaults) {
        this.templates = templates;
        this.defaults = defaults;
    }

    /**
     * 收集当前 Bean 的合并注解，保留 Spring 代理；不注册桥接方法的重复副本。
     *
     * @param bean 初始化后的 Bean
     * @param beanName 名称
     * @return 原 Bean
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> target = AopUtils.getTargetClass(bean);
        Map<Method, DisruptorListener> found = MethodIntrospector.selectMethods(target,
                (MethodIntrospector.MetadataLookup<DisruptorListener>)
                        method -> AnnotatedElementUtils.findMergedAnnotation(method, DisruptorListener.class));
        found.forEach((method, listener) -> {
            ListenerMethod candidate = new ListenerMethod(bean, method, listener);
            if (initialized.get()) {
                register(candidate);
            } else {
                methods.add(candidate);
                if (initialized.get() && methods.remove(candidate)) {
                    register(candidate);
                }
            }
        });
        return bean;
    }

    /**
     * 在依赖全部初始化后创建监听队列，重复队列和不合法签名导致启动失败。
     */
    @Override
    public void afterSingletonsInstantiated() {
        initialized.set(true);
        ListenerMethod candidate;
        while ((candidate = methods.poll()) != null) {
            register(candidate);
        }
    }

    /**
     * 解析实际配置，按序号把消息分配到指定工作线程；不把一条消息广播执行多次。
     *
     * @param candidate 监听方法
     */
    @SuppressWarnings("unchecked")
    private void register(ListenerMethod candidate) {
        Method declared = candidate.method();
        Assert.isTrue(declared.getParameterCount() == 1, "DisruptorListener必须且只能声明一个消息参数");
        Object target = candidate.bean();
        Method invocation;
        if (!Modifier.isPublic(declared.getModifiers()) && AopUtils.isAopProxy(target)) {
            Object singleton = AopProxyUtils.getSingletonTarget(target);
            Assert.notNull(singleton, "非公开监听方法必须能解析到单例目标");
            target = singleton;
            invocation = declared;
        } else {
            invocation = AopUtils.selectInvocableMethod(declared, target.getClass());
        }
        ReflectionUtils.makeAccessible(invocation);
        Object receiver = target;
        DisruptorListener listener = candidate.listener();
        DisruptorProperties global = defaults.get();
        boolean inherit = listener.inheritDefaults();
        int size = inherit && listener.bufferSize() == 1024 ? global.getBufferSize() : listener.bufferSize();
        int workers = inherit && listener.threads() == 1 ? global.getThreads() : listener.threads();
        boolean virtual = inherit && listener.virtualThread() ? global.isVirtualThread() : listener.virtualThread();
        ProducerType producer = inherit && listener.producerType() == ProducerType.MULTI
                ? global.getProducerType() : listener.producerType();
        WaitStrategyType wait = inherit && listener.waitStrategy() == WaitStrategyType.BLOCKING
                ? global.getWaitStrategy() : listener.waitStrategy();
        Class<Object> type = (Class<Object>) ClassUtils.resolvePrimitiveIfNecessary(declared.getParameterTypes()[0]);
        templates.get().createListenerQueue(listener.value(), size, producer, wait.create(),
                DisruptorEngine.threads(listener.value(), virtual), workers, type,
                (event, sequence, end) -> invoke(invocation, receiver, event.getData()));
    }

    /**
     * 保留用户原异常类型，不把 InvocationTargetException 当作业务原因。
     *
     * @param method 可调用方法
     * @param bean 实际接收者或代理
     * @param data 消息
     * @throws Exception 原业务异常
     */
    private void invoke(Method method, Object bean, Object data) throws Exception {
        try {
            method.invoke(bean, data);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception failure) {
                throw failure;
            }
            if (cause instanceof Error failure) {
                throw failure;
            }
            throw new IllegalStateException("监听方法调用失败", cause);
        }
    }

    /**
     * 监听方法(ListenerMethod)仅保存已初始化 Bean 与元数据。
     *
     * @author linshiqiang
     * @since 2026-10-06 03:17:49
     * @param bean 实例或代理
     * @param method 原方法
     * @param listener 合并后的注解
     */
    private record ListenerMethod(
            /**
             * 实例或代理。
             */
            Object bean,

            /**
             * 原方法。
             */
            Method method,

            /**
             * 注解配置。
             */
            DisruptorListener listener) {
    }
}
