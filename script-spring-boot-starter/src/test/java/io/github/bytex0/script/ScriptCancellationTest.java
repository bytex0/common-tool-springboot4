package io.github.bytex0.script;

import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.exception.ScriptExecuteException;
import io.github.bytex0.script.executor.ScriptExecutor;
import io.github.bytex0.script.service.ScriptService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 脚本超时和关闭竞争回归，模拟会清除中断的受信编译器，验证持久取消状态。
 *
 * @author bytex0
 * @since 2026-10-25 11:02:17
 */
class ScriptCancellationTest {

    /**
     * 测试调用超时，单位毫秒；替身编译不使用真实引擎。
     */
    private static final long CALL_TIMEOUT_MILLIS = 300;

    /**
     * 闩锁和 Future 的测试保护等待上限，单位秒。
     */
    private static final int WAIT_SECONDS = 10;

    /**
     * 验证超时后晚到的编译结果既不能执行，也不能被后续请求复用。
     */
    @Test
    void timedOutCompilationCannotExecuteOrPopulateCache() {
        CountDownLatch proceed = new CountDownLatch(1);
        AtomicInteger compilations = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        ScriptExecutor executor = executor();
        when(executor.compile("late")).thenAnswer(invocation -> {
            if (compilations.incrementAndGet() == 1) {
                awaitIgnoringInterrupt(proceed);
            }
            return "late";
        });
        when(executor.compile("barrier")).thenReturn("barrier");
        when(executor.executeCompiled(anyString(), anyMap())).thenAnswer(invocation -> {
            executions.incrementAndGet();
            return invocation.getArgument(0);
        });
        try (ScriptService service = new ScriptService(List.of(executor), 1, 2, CALL_TIMEOUT_MILLIS, 4)) {
            try {
                assertThatThrownBy(() -> service.execute("late", ScriptType.GROOVY, "late", Map.of()))
                        .isInstanceOf(ScriptExecuteException.class);
            } finally {
                proceed.countDown();
            }
            assertThat(service.execute("barrier", ScriptType.GROOVY, "barrier", Map.of())).isEqualTo("barrier");
            assertThat(executions.get()).isEqualTo(1);
            assertThat(service.execute("late", ScriptType.GROOVY, "late", Map.of())).isEqualTo("late");
            assertThat(compilations.get()).isEqualTo(2);
        } finally {
            proceed.countDown();
        }
    }

    /**
     * 验证刷新超时即使编译器清除中断也保留旧缓存，不被晚到结果替换。
     */
    @Test
    void timedOutRefreshPreservesPreviousCache() {
        CountDownLatch proceed = new CountDownLatch(1);
        AtomicInteger oldCompilations = new AtomicInteger();
        ScriptExecutor executor = executor();
        when(executor.compile("old")).thenAnswer(invocation -> {
            oldCompilations.incrementAndGet();
            return "old";
        });
        when(executor.compile("new")).thenAnswer(invocation -> {
            awaitIgnoringInterrupt(proceed);
            return "new";
        });
        when(executor.executeCompiled(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        try (ScriptService service = new ScriptService(List.of(executor), 1, 2, CALL_TIMEOUT_MILLIS, 4)) {
            service.execute("id", ScriptType.GROOVY, "old", Map.of());
            try {
                assertThatThrownBy(() -> service.refresh("id", ScriptType.GROOVY, "new"))
                        .isInstanceOf(ScriptExecuteException.class);
            } finally {
                proceed.countDown();
            }
            assertThat(service.execute("id", ScriptType.GROOVY, "old", Map.of())).isEqualTo("old");
            assertThat(oldCompilations.get()).isEqualTo(1);
        } finally {
            proceed.countDown();
        }
    }

    /**
     * 验证关闭主动解除在途调用等待，无需等待不响应中断的编译器结束。
     *
     * @throws Exception 测试线程提交、等待或退出失败
     */
    @Test
    void closingCancelsInflightCallBeforeCompilerReturns() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        ScriptExecutor executor = executor();
        when(executor.compile("late")).thenAnswer(invocation -> {
            started.countDown();
            awaitIgnoringInterrupt(proceed);
            return "late";
        });
        ScriptService service = new ScriptService(List.of(executor), 1, 2, 10000, 4);
        try (ThreadPoolExecutor clients = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2), new ThreadPoolExecutor.AbortPolicy())) {
            Future<Object> call = clients.submit(() -> service.execute("id", ScriptType.GROOVY, "late", Map.of()));
            try {
                assertThat(started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                Future<?> closing = clients.submit(service::close);
                try {
                    assertThatThrownBy(() -> call.get(1, TimeUnit.SECONDS))
                            .hasCauseInstanceOf(ScriptExecuteException.class);
                } finally {
                    proceed.countDown();
                }
                closing.get(WAIT_SECONDS, TimeUnit.SECONDS);
            } finally {
                proceed.countDown();
            }
        } finally {
            proceed.countDown();
            service.close();
        }
    }

    /**
     * 创建仅声明语言的执行器，行为由每个测试显式配置。
     *
     * @return 当前测试独享的执行器
     */
    private ScriptExecutor executor() {
        ScriptExecutor executor = mock(ScriptExecutor.class);
        when(executor.getType()).thenReturn(ScriptType.GROOVY);
        return executor;
    }

    /**
     * 模拟清除中断的第三方编译器，但保留测试总等待上限，避免测试挂起。
     *
     * @param signal 允许编译结束的信号
     */
    private void awaitIgnoringInterrupt(CountDownLatch signal) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (signal.getCount() > 0 && System.nanoTime() < deadline) {
            try {
                signal.await(10, TimeUnit.MILLISECONDS);
            } catch (InterruptedException expected) {
                assertThat(Thread.currentThread().isInterrupted()).isFalse();
            }
        }
        assertThat(signal.getCount()).isZero();
    }
}
