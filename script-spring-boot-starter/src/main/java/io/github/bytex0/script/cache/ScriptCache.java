package io.github.bytex0.script.cache;

import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.executor.ScriptExecutor;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.util.Assert;
import org.springframework.util.DigestUtils;

/**
 * 应用实例拥有的有界脚本缓存，通过租约延迟释放仍在执行的编译产物。
 *
 * @author bytex0
 * @since 2026-10-06 13:55:16
 */
public final class ScriptCache implements AutoCloseable {

    /**
     * 缓存容量上限，防止错误配置导致无限制持有编译类型。
     */
    private static final int MAX_CAPACITY = 10000;

    /**
     * 缓存容量，单位为编译产物数量。
     */
    private final int capacity;

    /**
     * 指定语言的额外容量限制，和全局限制同时生效。
     */
    private final Map<ScriptType, Integer> typeCapacities;

    /**
     * 访问顺序缓存，只能在 stateLock 内操作。
     */
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    /**
     * 仅保护缓存、引用计数和修订号；编译和资源释放均在锁外执行。
     */
    private final ReentrantLock stateLock = new ReentrantLock();

    /**
     * 失效修订号，删除、刷新或清空后阻止旧编译结果重新进入缓存。
     */
    private long revision;

    /**
     * 关闭标记，由 stateLock 保护。
     */
    private boolean closed;

    /**
     * 创建应用独立的缓存，不使用静态可变状态。
     *
     * @param capacity 容量，范围为 1 到 10000
     */
    public ScriptCache(int capacity) {
        this(capacity, Map.of());
    }

    /**
     * 创建具有额外语言配额的有界缓存。
     *
     * @param capacity 总容量，范围 1 到 10000
     * @param typeCapacities 各语言配额，范围 1 到 10000，未设置的语言仅受总容量限制
     */
    public ScriptCache(int capacity, Map<ScriptType, Integer> typeCapacities) {
        Assert.isTrue(capacity > 0 && capacity <= MAX_CAPACITY, "Script cache capacity must be 1 to 10000");
        this.capacity = capacity;
        typeCapacities.forEach((type, limit) -> {
            Assert.notNull(type, "Script cache type is required");
            Assert.isTrue(limit != null && limit > 0 && limit <= MAX_CAPACITY, "Invalid language cache capacity");
        });
        this.typeCapacities = Map.copyOf(typeCapacities);
    }

    /**
     * 命中相同源码、类型及执行器时复用产物，否则在锁外编译。
     * 与删除或刷新竞争的旧编译结果只供本次调用使用，不重新发布到缓存。
     *
     * @param scriptId 非空脚本 ID
     * @param source 非空源码
     * @param executor 非空执行器
     * @return 调用者必须关闭的执行租约
     */
    public Lease acquire(String scriptId, String source, ScriptExecutor executor) {
        validate(scriptId, source, executor);
        long expectedRevision;
        stateLock.lock();
        try {
            Assert.state(!closed, "Script cache is closed");
            Entry existing = entries.get(scriptId);
            if (matches(existing, source, executor)) {
                existing.references++;
                return new Lease(existing);
            }
            expectedRevision = revision;
        } finally {
            stateLock.unlock();
        }

        Entry candidate = new Entry(source, executor.compile(source), executor);
        List<Entry> cleanup = new ArrayList<>();
        Lease lease;
        stateLock.lock();
        try {
            Entry existing = entries.get(scriptId);
            if (!closed && matches(existing, source, executor)) {
                retire(candidate, cleanup);
                existing.references++;
                lease = new Lease(existing);
            } else {
                candidate.references++;
                lease = new Lease(candidate);
                if (!closed && !Thread.currentThread().isInterrupted() && revision == expectedRevision) {
                    retire(entries.put(scriptId, candidate), cleanup);
                    evict(cleanup);
                } else {
                    candidate.retired = true;
                }
            }
        } finally {
            stateLock.unlock();
        }
        try {
            releaseAll(cleanup);
            return lease;
        } catch (RuntimeException | Error exception) {
            try {
                lease.close();
            } catch (RuntimeException | Error closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    /**
     * 显式刷新；编译失败保留原缓存，与更晚的失效操作竞争时不发布旧结果。
     *
     * @param scriptId 非空脚本 ID
     * @param source 非空源码
     * @param executor 非空执行器
     */
    public void refresh(String scriptId, String source, ScriptExecutor executor) {
        validate(scriptId, source, executor);
        long expectedRevision;
        stateLock.lock();
        try {
            Assert.state(!closed, "Script cache is closed");
            expectedRevision = ++revision;
        } finally {
            stateLock.unlock();
        }
        Entry candidate = new Entry(source, executor.compile(source), executor);
        List<Entry> cleanup = new ArrayList<>();
        stateLock.lock();
        try {
            if (!closed && !Thread.currentThread().isInterrupted() && revision == expectedRevision) {
                retire(entries.put(scriptId, candidate), cleanup);
                evict(cleanup);
            } else {
                retire(candidate, cleanup);
            }
        } finally {
            stateLock.unlock();
        }
        releaseAll(cleanup);
    }

    /**
     * 接管外部已经编译的产物，避免重新编译。
     * 新 API 显式接收创建执行器以确定类型及释放策略；入参验证失败时所有权仍属于调用方。
     * 同一产物不得重复放入缓存，放入后不能由调用方自行关闭。
     *
     * @param scriptId 非空脚本 ID
     * @param source 非空源码
     * @param compiledScript 非空编译产物
     * @param executor 产物的创建执行器
     */
    public void put(String scriptId, String source, Object compiledScript, ScriptExecutor executor) {
        validate(scriptId, source, executor);
        Assert.notNull(compiledScript, "Compiled script is required");
        Entry candidate = new Entry(source, compiledScript, executor);
        List<Entry> cleanup = new ArrayList<>();
        stateLock.lock();
        try {
            Assert.state(!closed, "Script cache is closed");
            Assert.isTrue(entries.values().stream().noneMatch(entry -> entry.compiled == compiledScript),
                    "Compiled script is already owned by this cache");
            revision++;
            retire(entries.put(scriptId, candidate), cleanup);
            evict(cleanup);
        } finally {
            stateLock.unlock();
        }
        releaseAll(cleanup);
    }

    /**
     * 获取只读元数据快照，不授予编译产物的执行或释放所有权。
     *
     * @param scriptId 脚本 ID
     * @return 不含可变引擎资源的元数据，未命中为空
     */
    public Optional<CachedScript> get(String scriptId) {
        stateLock.lock();
        try {
            Entry entry = entries.get(scriptId);
            return entry == null ? Optional.empty() : Optional.of(entry.metadata);
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * 移除脚本，正在执行的租约在归还时释放资源。
     *
     * @param scriptId 非空脚本 ID
     */
    public void remove(String scriptId) {
        Assert.hasText(scriptId, "Script ID is required");
        List<Entry> cleanup = new ArrayList<>();
        stateLock.lock();
        try {
            revision++;
            retire(entries.remove(scriptId), cleanup);
        } finally {
            stateLock.unlock();
        }
        releaseAll(cleanup);
    }

    /**
     * 清空缓存但保持可用，同时使所有在途编译结果失效。
     */
    public void clear() {
        invalidate(false);
    }

    /**
     * 关闭缓存，拒绝新调用；在途执行仍由租约保护直到结束。
     */
    @Override
    public void close() {
        invalidate(true);
    }

    /**
     * 统一执行清空与关闭，不持锁释放引擎资源。
     *
     * @param shutdown 是否永久关闭
     */
    private void invalidate(boolean shutdown) {
        List<Entry> cleanup = new ArrayList<>();
        stateLock.lock();
        try {
            closed |= shutdown;
            revision++;
            entries.values().forEach(entry -> retire(entry, cleanup));
            entries.clear();
        } finally {
            stateLock.unlock();
        }
        releaseAll(cleanup);
    }

    /**
     * 在锁内按访问顺序淘汰超出容量的条目。
     *
     * @param cleanup 待锁外释放的资源
     */
    private void evict(List<Entry> cleanup) {
        for (Map.Entry<ScriptType, Integer> limit : typeCapacities.entrySet()) {
            List<String> matching = entries.entrySet().stream()
                    .filter(entry -> entry.getValue().metadata.type == limit.getKey())
                    .map(Map.Entry::getKey).toList();
            int excess = matching.size() - limit.getValue();
            for (int index = 0; index < excess; index++) {
                retire(entries.remove(matching.get(index)), cleanup);
            }
        }
        while (entries.size() > capacity) {
            String eldest = entries.keySet().iterator().next();
            retire(entries.remove(eldest), cleanup);
        }
    }

    /**
     * 在锁内标记资源退休，只有没有活跃租约时才能释放。
     *
     * @param entry 可为空的条目
     * @param cleanup 待释放条目
     */
    private static void retire(Entry entry, List<Entry> cleanup) {
        if (entry != null && !entry.retired) {
            entry.retired = true;
            if (entry.references == 0) {
                cleanup.add(entry);
            }
        }
    }

    /**
     * 锁外释放全部资源，首个运行时异常保留，其他失败附加为 suppressed。
     *
     * @param cleanup 互不重复且没有活跃租约的条目
     */
    private static void releaseAll(List<Entry> cleanup) {
        RuntimeException failure = null;
        for (Entry entry : cleanup) {
            try {
                entry.executor.release(entry.compiled);
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * 检查源码和执行器身份，不仅比较 ID 或内容摘要。
     *
     * @param entry 候选条目，可为空
     * @param source 请求源码
     * @param executor 请求执行器
     * @return 是否可以直接复用
     */
    private static boolean matches(Entry entry, String source, ScriptExecutor executor) {
        return entry != null && entry.executor == executor
                && entry.metadata.type == executor.getType() && entry.metadata.scriptContent.equals(source);
    }

    /**
     * 验证编译请求基本参数。
     *
     * @param scriptId 脚本 ID
     * @param source 源码
     * @param executor 执行器
     */
    private static void validate(String scriptId, String source, ScriptExecutor executor) {
        Assert.hasText(scriptId, "Script ID is required");
        Assert.hasText(source, "Script source is required");
        Assert.notNull(executor, "Script executor is required");
        Assert.notNull(executor.getType(), "Script type is required");
    }

    /**
     * 编译产物的单次执行租约，不允许关闭后使用或与关闭并发使用。
     *
     * @author bytex0
     * @since 2026-10-06 13:55:16
     */
    public final class Lease implements AutoCloseable {

        /**
         * 被租用的缓存条目。
         */
        private final Entry entry;

        /**
         * 保证租约只归还一次。
         */
        private final AtomicBoolean returned = new AtomicBoolean();

        /**
         * 接收已增加引用计数的条目。
         *
         * @param entry 引用计数已增加的条目
         */
        private Lease(Entry entry) {
            this.entry = entry;
        }

        /**
         * 获得租约期间有效的编译产物，不得自行释放。
         *
         * @return 编译产物
         */
        public Object compiledScript() {
            Assert.state(!returned.get(), "Script lease is closed");
            return entry.compiled;
        }

        /**
         * 归还引用并在需要时释放退休条目。
         */
        @Override
        public void close() {
            if (returned.compareAndSet(false, true)) {
                boolean release;
                stateLock.lock();
                try {
                    entry.references--;
                    release = entry.retired && entry.references == 0;
                } finally {
                    stateLock.unlock();
                }
                if (release) {
                    entry.executor.release(entry.compiled);
                }
            }
        }
    }

    /**
     * 不可变的脚本元数据，不向监控调用方泄露可关闭的引擎资源。
     *
     * @author bytex0
     * @since 2026-10-06 13:55:16
     */
    public static final class CachedScript {

        /**
         * 内容 MD5，仅用于兼容性展示，不作为安全校验或唯一缓存判断。
         */
        private final String md5;

        /**
         * 原始受信源码，不应写入日志或直接暴露为 HTTP 响应。
         */
        private final String scriptContent;

        /**
         * 引擎类型。
         */
        private final ScriptType type;

        /**
         * 编译成功时的本地时间。
         */
        private final LocalDateTime lastUpdateTime;

        /**
         * 构建编译元数据。
         *
         * @param source 原始源码
         * @param type 引擎类型
         */
        private CachedScript(String source, ScriptType type) {
            md5 = HexFormat.of().formatHex(DigestUtils.md5Digest(source.getBytes(StandardCharsets.UTF_8)));
            scriptContent = source;
            this.type = type;
            lastUpdateTime = LocalDateTime.now();
        }

        /**
         * 获取兼容内容摘要。
         *
         * @return MD5 十六进制文本
         */
        public String getMd5() {
            return md5;
        }

        /**
         * 获取源码，不转移任何编译资源所有权。
         *
         * @return 源码
         */
        public String getScriptContent() {
            return scriptContent;
        }

        /**
         * 获取脚本类型。
         *
         * @return 引擎类型
         */
        public ScriptType getType() {
            return type;
        }

        /**
         * 获取本次编译时间。
         *
         * @return 本地编译时间
         */
        public LocalDateTime getLastUpdateTime() {
            return lastUpdateTime;
        }
    }

    /**
     * 缓存内部条目，引用计数和退休状态由所属缓存锁保护。
     *
     * @author bytex0
     * @since 2026-10-06 13:55:16
     */
    private static final class Entry {

        /**
         * 不可变元数据。
         */
        private final CachedScript metadata;

        /**
         * 由执行器创建的编译资源。
         */
        private final Object compiled;

        /**
         * 编译及释放资源的执行器。
         */
        private final ScriptExecutor executor;

        /**
         * 活跃租约数量。
         */
        private int references;

        /**
         * 是否已退出缓存，不再接受新的租约。
         */
        private boolean retired;

        /**
         * 接管编译产物。
         *
         * @param source 源码
         * @param compiled 编译产物
         * @param executor 编译执行器
         */
        private Entry(String source, Object compiled, ScriptExecutor executor) {
            metadata = new CachedScript(source, executor.getType());
            this.compiled = compiled;
            this.executor = executor;
        }
    }
}
