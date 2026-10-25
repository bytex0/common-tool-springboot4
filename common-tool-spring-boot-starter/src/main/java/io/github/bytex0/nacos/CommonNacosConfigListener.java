package io.github.bytex0.nacos;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.CommandLineRunner;

/**
 * Nacos 监听生命周期管理，使用每个配置对象的客户端和 dataId，不输出配置正文。
 * 同一监听对象串行处理最新状态；关闭后不再开始新回调，已进入的回调允许正常结束。
 *
 * @author bytex0
 * @since 2026-10-06 18:24:37
 */
public class CommonNacosConfigListener implements CommandLineRunner, AutoCloseable {

    /**
     * 默认初始化请求超时，单位毫秒。
     */
    private static final long DEFAULT_TIMEOUT = 10000;

    /**
     * 请求超时上限，单位毫秒。
     */
    private static final long MAX_TIMEOUT = 120000;

    /**
     * 默认回调队列容量。
     */
    private static final int DEFAULT_CAPACITY = 128;

    /**
     * 回调队列容量上限。
     */
    private static final int MAX_CAPACITY = 10000;

    /**
     * 关闭等待上限，单位秒。
     */
    private static final int SHUTDOWN_SECONDS = 5;

    /**
     * 当前应用的监听对象快照，去除同一实例的 Bean 别名重复。
     */
    private final List<AbstractNacosConfig> configs;

    /**
     * 初始配置请求超时，单位毫秒。
     */
    private final long timeout;

    /**
     * 本监听器拥有的有界回调执行器，队列满时向 SDK 提交线程施加背压。
     */
    private final ThreadPoolExecutor callbacks;

    /**
     * 当前回调工作线程，避免回调内部关闭时等待自身退出。
     */
    private final AtomicReference<Thread> callbackThread = new AtomicReference<>();

    /**
     * 已完成 SDK 注册的回调。
     */
    private final ConcurrentLinkedQueue<Registration> registrations = new ConcurrentLinkedQueue<>();

    /**
     * 防止重复启动注册。
     */
    private final AtomicBoolean started = new AtomicBoolean();

    /**
     * 关闭标记，阻止初始化期间的晚到注册和关闭后的回调。
     */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 使用默认请求超时和队列容量创建管理器。
     *
     * @param configs Bean 名到监听对象的映射
     */
    public CommonNacosConfigListener(Map<String, AbstractNacosConfig> configs) {
        this(configs, DEFAULT_TIMEOUT, DEFAULT_CAPACITY);
    }

    /**
     * 创建有界生命周期管理器，构造时不访问 Nacos。
     *
     * @param configs Bean 名到监听对象的映射，Bean 名不作为 dataId
     * @param timeout 请求超时毫秒，范围 1 到 120000
     * @param queueCapacity 回调队列容量，范围 1 到 10000
     */
    public CommonNacosConfigListener(Map<String, AbstractNacosConfig> configs, long timeout, int queueCapacity) {
        Objects.requireNonNull(configs, "configs");
        if (timeout <= 0 || timeout > MAX_TIMEOUT || queueCapacity <= 0 || queueCapacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("Invalid Nacos timeout or callback queue capacity");
        }
        Set<AbstractNacosConfig> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        List<AbstractNacosConfig> ordered = new ArrayList<>();
        configs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            AbstractNacosConfig config = Objects.requireNonNull(entry.getValue(), "config");
            if (unique.add(config)) {
                ordered.add(config);
            }
        });
        this.configs = List.copyOf(ordered);
        this.timeout = timeout;
        callbacks = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                task -> {
                    Thread worker = Thread.ofPlatform().daemon(true).name("common-nacos-callback").unstarted(task);
                    callbackThread.set(worker);
                    return worker;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * 注册并加载初始内容，失败时回滚已注册的所有监听器。
     *
     * @param args 启动参数，不参与配置查询
     * @throws NacosException SDK 初始请求失败
     */
    @Override
    public void run(String... args) throws NacosException {
        if (closed.get()) {
            throw new IllegalStateException("Nacos listener is closed");
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            for (AbstractNacosConfig config : configs) {
                register(config);
            }
        } catch (NacosException | RuntimeException | Error exception) {
            try {
                close();
            } catch (RuntimeException cleanupFailure) {
                preserveFailure(exception, cleanupFailure);
            }
            throw exception;
        }
    }

    /**
     * 在不持有锁的情况下执行 SDK 请求，并处理与关闭竞争的晚到注册。
     *
     * @param config 监听对象
     * @throws NacosException SDK 请求失败
     */
    private void register(AbstractNacosConfig config) throws NacosException {
        if (closed.get()) {
            throw new IllegalStateException("Nacos listener is closed");
        }
        String dataId = config.getDataId();
        if (dataId == null || dataId.isBlank()) {
            throw new IllegalArgumentException("Nacos dataId is required");
        }
        String configuredGroup = config.getGroup();
        String group = configuredGroup == null || configuredGroup.isBlank() ? Constants.DEFAULT_GROUP : configuredGroup;
        ConfigService service = Objects.requireNonNull(config.configService(), "configService");
        Registration registration = new Registration(config, service, dataId, group);
        String initial;
        try {
            initial = service.getConfigAndSignListener(dataId, group, timeout, registration);
        } catch (NacosException | RuntimeException | Error exception) {
            try {
                registration.remove();
            } catch (RuntimeException cleanupFailure) {
                preserveFailure(exception, cleanupFailure);
            }
            throw exception;
        }
        registrations.add(registration);
        if (closed.get()) {
            registration.remove();
            registrations.remove(registration);
            return;
        }
        registration.initialize(initial);
    }

    /**
     * 移除本管理器的回调并关闭自己的执行器，不关闭外部客户端。
     * SDK 请求期间并发关闭的注册会在该请求返回后自行移除。
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        RuntimeException failure = null;
        Registration registration;
        while ((registration = registrations.poll()) != null) {
            try {
                registration.remove();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    preserveFailure(failure, exception);
                }
            }
        }
        callbacks.shutdownNow();
        if (Thread.currentThread() != callbackThread.get()) {
            try {
                callbacks.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * 保留清理故障，避免重复抛出同一异常实例时自抑制掩盖原异常。
     *
     * @param primary 原异常
     * @param secondary 后续异常
     */
    private static void preserveFailure(Throwable primary, Throwable secondary) {
        if (primary != secondary) {
            primary.addSuppressed(secondary);
        }
    }

    /**
     * 单个 SDK 注册的状态，异步更新先于初始请求返回时优先使用最新值。
     *
     * @author bytex0
     * @since 2026-10-06 18:24:37
     */
    private final class Registration implements Listener {

        /**
         * 业务配置接收者。
         */
        private final AbstractNacosConfig config;

        /**
         * 本配置所属客户端，不归监听器所有。
         */
        private final ConfigService service;

        /**
         * 实际 dataId。
         */
        private final String dataId;

        /**
         * 实际 group。
         */
        private final String group;

        /**
         * 待处理的最新状态，Payload 包装允许区分删除值 null 与没有更新。
         */
        private final AtomicReference<Payload> pending = new AtomicReference<>();

        /**
         * 初始请求是否已经结束，结束前只收集异步状态。
         */
        private final AtomicBoolean initialized = new AtomicBoolean();

        /**
         * 是否正在执行当前对象的回调，避免并发进入业务接收者。
         */
        private final AtomicBoolean draining = new AtomicBoolean();

        /**
         * 当前注册是否仍然有效。
         */
        private final AtomicBoolean active = new AtomicBoolean(true);

        /**
         * 创建尚未发布初始状态的注册对象。
         *
         * @param config 业务接收者
         * @param service SDK 客户端
         * @param dataId 配置标识
         * @param group 分组
         */
        private Registration(AbstractNacosConfig config, ConfigService service, String dataId, String group) {
            this.config = config;
            this.service = service;
            this.dataId = dataId;
            this.group = group;
        }

        /**
         * 返回管理器拥有的有界执行器。
         *
         * @return 回调执行器
         */
        @Override
        public Executor getExecutor() {
            return callbacks;
        }

        /**
         * 接收最新配置；初始化完成前暂存，不被旧初始值覆盖。
         *
         * @param content 配置文本，删除时可为空
         */
        @Override
        public void receiveConfigInfo(String content) {
            if (active.get() && !closed.get()) {
                pending.set(new Payload(content));
                drain();
            }
        }

        /**
         * 发布初始状态，保留空字符串；已有异步更新时不再发布旧值。
         *
         * @param content 初始值，null 表示配置不存在
         */
        private void initialize(String content) {
            if (content != null) {
                pending.compareAndSet(null, new Payload(content));
            }
            initialized.set(true);
            drain();
        }

        /**
         * 串行执行最新状态，不持锁调用业务代码。
         * 回调异常保留并向 SDK 或初始化调用方传播，期间到达的新状态仍尝试处理。
         */
        private void drain() {
            if (!initialized.get()) {
                return;
            }
            RuntimeException failure = null;
            while (active.get() && !closed.get()) {
                if (!draining.compareAndSet(false, true)) {
                    break;
                }
                try {
                    Payload payload;
                    while (active.get() && !closed.get() && (payload = pending.getAndSet(null)) != null) {
                        try {
                            config.onReceived(payload.content);
                        } catch (RuntimeException exception) {
                            if (failure == null) {
                                failure = exception;
                            } else {
                                preserveFailure(failure, exception);
                            }
                        }
                    }
                } finally {
                    draining.set(false);
                }
                if (pending.get() == null) {
                    break;
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        /**
         * 幂等停用并移除该监听，不关闭客户端。
         */
        private void remove() {
            if (active.compareAndSet(true, false)) {
                pending.set(null);
                service.removeListener(dataId, group, this);
            }
        }
    }

    /**
     * 可表达删除状态的配置值。
     *
     * @param content 配置文本，可为空
     * @author bytex0
     * @since 2026-10-06 18:24:37
     */
    private record Payload(
            /**
             * 配置文本，null 表示删除。
             */
            String content) {
    }
}
