package io.github.bytex0.dict;

import io.github.bytex0.dict.properties.DictProperties;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.util.Assert;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 字典预热(DictRefresher)尊重启动刷新开关，避免单例完成与 Runner 回调重复加载。
 *
 * @author linshiqiang
 * @since 2026-10-06 02:07:34
 */
public class DictRefresher implements ApplicationRunner, SmartInitializingSingleton, BeanFactoryAware {

    /**
     * 原启动刷新属性。
     */
    private final DictProperties properties;

    /**
     * 当前容器的缓存，原单参数构造方式通过生命周期绑定。
     */
    private final AtomicReference<DictCache> cache;

    /**
     * 启动刷新是否已执行。
     */
    private final AtomicBoolean initialized = new AtomicBoolean();

    /**
     * 保留原属性构造器，非 Spring 调用应改用显式缓存构造器。
     *
     * @param properties 刷新配置
     */
    public DictRefresher(DictProperties properties) {
        this(properties, null);
    }

    /**
     * 显式绑定缓存，避免任何静态容器依赖。
     *
     * @param properties 刷新配置
     * @param cache 实例缓存
     */
    public DictRefresher(DictProperties properties, DictCache cache) {
        this.properties = Objects.requireNonNull(properties);
        this.cache = new AtomicReference<>(cache);
    }

    /**
     * 只为未显式配置的缓存绑定当前容器。
     *
     * @param beanFactory 当前容器
     */
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        if (cache.get() == null) {
            cache.compareAndSet(null, beanFactory.getBean(DictCache.class));
        }
    }

    /**
     * 在全部单例完成后准备字典，错误让启动失败而不是静默使用半成品。
     */
    @Override
    public void afterSingletonsInstantiated() {
        refreshOnce();
    }

    /**
     * 保留原 Runner 入口，不重复执行已完成的启动加载。
     *
     * @param args 启动参数，不影响字典
     */
    @Override
    public void run(ApplicationArguments args) {
        refreshOnce();
    }

    /**
     * 原子标记单次启动刷新；失败恢复标志，显式重试不会被永久跳过。
     */
    private void refreshOnce() {
        if (!Boolean.TRUE.equals(properties.getAutoRefresh()) || !initialized.compareAndSet(false, true)) {
            return;
        }
        try {
            DictCache current = cache.get();
            Assert.state(current != null, "DictRefresher需要实例DictCache或所属Spring容器");
            current.refreshAll();
        } catch (RuntimeException exception) {
            initialized.set(false);
            throw exception;
        }
    }
}
