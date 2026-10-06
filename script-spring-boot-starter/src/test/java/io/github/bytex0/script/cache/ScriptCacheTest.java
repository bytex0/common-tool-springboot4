package io.github.bytex0.script.cache;

import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.exception.ScriptCompileException;
import io.github.bytex0.script.executor.ScriptExecutor;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 有界缓存的租约释放、失效竞争和实例隔离测试。
 *
 * @author bytex0
 * @since 2026-10-06 13:58:31
 */
class ScriptCacheTest {

    /**
     * 并发测试等待上限，单位为秒。
     */
    private static final int WAIT_SECONDS = 10;

    /**
     * 验证接管外部编译产物后不重复编译，并拒绝重复转移同一产物。
     */
    @Test
    void putTakesOwnershipWithoutRecompiling() {
        CountingExecutor executor = new CountingExecutor();
        Object compiled = executor.compile("one");
        try (ScriptCache cache = new ScriptCache(2)) {
            cache.put("id", "one", compiled, executor);
            try (ScriptCache.Lease lease = cache.acquire("id", "one", executor)) {
                assertThat(lease.compiledScript()).isSameAs(compiled);
                assertThat(executor.compilations.get()).isEqualTo(1);
            }
            assertThatThrownBy(() -> cache.put("other", "one", compiled, executor))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(executor.releases.get()).isEqualTo(1);
    }

    /**
     * 验证语言配额独立于全局容量生效。
     */
    @Test
    void languageQuotaEvictsWithinGlobalCapacity() {
        CountingExecutor executor = new CountingExecutor();
        try (ScriptCache cache = new ScriptCache(3, Map.of(ScriptType.GROOVY, 1))) {
            cache.refresh("first", "one", executor);
            cache.refresh("second", "two", executor);
            assertThat(cache.get("first")).isEmpty();
            assertThat(cache.get("second")).isPresent();
            assertThat(executor.releases.get()).isEqualTo(1);
        }
        assertThat(executor.releases.get()).isEqualTo(2);
    }

    /**
     * 验证只有相同源码和执行器才能复用，替换和移除时等待租约归还。
     */
    @Test
    void reuseAndReplacementRespectActiveLeases() {
        CountingExecutor executor = new CountingExecutor();
        try (ScriptCache cache = new ScriptCache(2)) {
            ScriptCache.Lease first = cache.acquire("id", "one", executor);
            try (ScriptCache.Lease same = cache.acquire("id", "one", executor)) {
                assertThat(same.compiledScript()).isSameAs(first.compiledScript());
                assertThat(executor.compilations.get()).isEqualTo(1);
            }
            try (ScriptCache.Lease updated = cache.acquire("id", "two", executor)) {
                assertThat(updated.compiledScript()).isNotSameAs(first.compiledScript());
                assertThat(executor.releases.get()).isZero();
                first.close();
                first.close();
                assertThat(executor.releases.get()).isEqualTo(1);
                cache.remove("id");
                assertThat(executor.releases.get()).isEqualTo(1);
            }
            assertThat(executor.releases.get()).isEqualTo(2);
            assertThat(cache.get("id")).isEmpty();
        }
    }

    /**
     * 验证淘汰和缓存关闭不会提前释放活跃资源。
     */
    @Test
    void evictionAndShutdownWaitForLeaseReturn() {
        CountingExecutor executor = new CountingExecutor();
        ScriptCache cache = new ScriptCache(1);
        try (ScriptCache.Lease first = cache.acquire("first", "one", executor);
             ScriptCache.Lease second = cache.acquire("second", "two", executor)) {
            assertThat(cache.get("first")).isEmpty();
            assertThat(cache.get("second")).isPresent();
            cache.close();
            assertThat(executor.releases.get()).isZero();
            assertThat(first.compiledScript()).isNotNull();
            assertThat(second.compiledScript()).isNotNull();
            assertThatThrownBy(() -> cache.acquire("third", "three", executor))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            cache.close();
        }
        assertThat(executor.releases.get()).isEqualTo(2);
    }

    /**
     * 验证删除与编译竞争时，旧编译结果不能重新放回缓存。
     *
     * @throws Exception 并发任务失败或等待超时
     */
    @Test
    void removalPreventsInflightCompilationFromRepopulating() throws Exception {
        CountingExecutor executor = new CountingExecutor();
        executor.started = new CountDownLatch(1);
        executor.proceed = new CountDownLatch(1);
        try (ScriptCache cache = new ScriptCache(1);
             ThreadPoolExecutor client = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                     new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy())) {
            var future = client.submit(() -> {
                try (ScriptCache.Lease lease = cache.acquire("id", "one", executor)) {
                    assertThat(lease.compiledScript()).isNotNull();
                }
            });
            try {
                assertThat(executor.started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                cache.remove("id");
            } finally {
                executor.proceed.countDown();
            }
            future.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(cache.get("id")).isEmpty();
            assertThat(executor.releases.get()).isEqualTo(1);
        }
    }

    /**
     * 验证刷新编译失败保留旧内容，替换执行器和清空均能正确失效。
     */
    @Test
    void refreshFailurePreservesPreviousEntryAndExecutorChangeInvalidates() {
        CountingExecutor first = new CountingExecutor();
        CountingExecutor second = new CountingExecutor();
        try (ScriptCache cache = new ScriptCache(2)) {
            cache.refresh("id", "one", first);
            assertThatThrownBy(() -> cache.refresh("id", "invalid", first))
                    .isInstanceOf(ScriptCompileException.class);
            assertThat(cache.get("id").orElseThrow().getScriptContent()).isEqualTo("one");
            assertThat(cache.get("id").orElseThrow().getMd5()).hasSize(32);
            try (ScriptCache.Lease changed = cache.acquire("id", "one", second)) {
                assertThat(changed.compiledScript()).isNotNull();
                assertThat(first.releases.get()).isEqualTo(1);
                cache.clear();
                assertThat(second.releases.get()).isZero();
            }
            assertThat(second.releases.get()).isEqualTo(1);
        }
    }

    /**
     * 可控制编译时序并统计释放次数的执行器，不依赖任何脚本引擎。
     *
     * @author bytex0
     * @since 2026-10-06 13:58:31
     */
    private static final class CountingExecutor implements ScriptExecutor {

        /**
         * 成功编译次数。
         */
        private final AtomicInteger compilations = new AtomicInteger();

        /**
         * 资源释放次数。
         */
        private final AtomicInteger releases = new AtomicInteger();

        /**
         * 可选编译开始信号，在任务提交前配置。
         */
        private CountDownLatch started;

        /**
         * 可选编译继续信号，在任务提交前配置。
         */
        private CountDownLatch proceed;

        /**
         * {@inheritDoc}
         */
        @Override
        public ScriptType getType() {
            return ScriptType.GROOVY;
        }

        /**
         * 创建唯一资源，必要时等待测试线程允许继续。
         *
         * @param script invalid 表示模拟编译失败
         * @return 唯一资源
         */
        @Override
        public Object compile(String script) {
            if ("invalid".equals(script)) {
                throw new ScriptCompileException("invalid");
            }
            if (started != null) {
                started.countDown();
                try {
                    if (!proceed.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                        throw new ScriptCompileException("Test compilation wait timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new ScriptCompileException("Interrupted", exception);
                }
            }
            compilations.incrementAndGet();
            return new Object();
        }

        /**
         * 返回资源标识以便进行身份断言。
         *
         * @param compiledScript 测试资源
         * @param params 测试参数
         * @return 原资源
         */
        @Override
        public Object executeCompiled(Object compiledScript, Map<String, Object> params) {
            return compiledScript;
        }

        /**
         * 返回资源标识以便进行身份断言。
         *
         * @param compiledScript 测试资源
         * @param methodName 测试方法名
         * @param params 测试参数
         * @return 原资源
         */
        @Override
        public Object executeCompiledMethod(Object compiledScript, String methodName, Map<String, Object> params) {
            return compiledScript;
        }

        /**
         * 记录资源释放。
         *
         * @param compiledScript 测试资源
         */
        @Override
        public void release(Object compiledScript) {
            releases.incrementAndGet();
        }
    }
}
