package io.github.bytex0.id;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 原 ID 位协议、并发唯一性及可控时钟边界测试。
 *
 * @author bytex0
 * @since 2026-10-06 14:47:34
 */
class IdWorkerTest {

    /**
     * 原版纪元毫秒值。
     */
    private static final long EPOCH = 1719911958471L;

    /**
     * 节点位移，保持原版高十位布局。
     */
    private static final int WORKER_SHIFT = 53;

    /**
     * 时间戳位移。
     */
    private static final int TIME_SHIFT = 12;

    /**
     * 单毫秒最大容量。
     */
    private static final int IDS_PER_MILLISECOND = 4096;

    /**
     * 并发线程数量。
     */
    private static final int THREADS = 8;

    /**
     * 每个线程的取号数量。
     */
    private static final int IDS_PER_THREAD = 2000;

    /**
     * 验证节点布局、时钟推进和回拨拒绝。
     */
    @Test
    void followsClockAndPreservesWorkerLayout() {
        AtomicLong clock = new AtomicLong(EPOCH + 1000);
        IdWorker worker = new IdWorker(17L, clock::get);
        long first = worker.nextId();
        assertThat(first >>> WORKER_SHIFT).isEqualTo(17);
        assertThat((first & ((1L << WORKER_SHIFT) - 1)) >>> TIME_SHIFT).isEqualTo(1000);
        clock.incrementAndGet();
        assertThat(worker.nextId()).isGreaterThan(first);
        clock.addAndGet(-2);
        assertThatThrownBy(worker::nextId).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("backwards");
    }

    /**
     * 验证序列耗尽后等待下一毫秒，而不是污染时间位或返回重复 ID。
     */
    @Test
    void waitsForClockAfterSequenceExhaustion() {
        AtomicInteger clockReads = new AtomicInteger();
        IdWorker worker = new IdWorker(0L,
                () -> EPOCH + 1000 + (clockReads.getAndIncrement() > IDS_PER_MILLISECOND ? 1 : 0));
        long previous = -1;
        for (int index = 0; index <= IDS_PER_MILLISECOND; index++) {
            long current = worker.nextId();
            assertThat(current).isGreaterThan(previous);
            previous = current;
        }
        assertThat(previous >>> TIME_SHIFT).isEqualTo(1001);
    }

    /**
     * 验证共享实例高并发取号不重复。
     *
     * @throws Exception 任务执行或等待失败
     */
    @Test
    void generatesUniqueIdsConcurrently() throws Exception {
        IdWorker worker = new IdWorker(3L);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        try (ThreadPoolExecutor pool = new ThreadPoolExecutor(THREADS, THREADS, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(THREADS), new ThreadPoolExecutor.AbortPolicy())) {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < THREADS; index++) {
                futures.add(pool.submit(() -> {
                    for (int count = 0; count < IDS_PER_THREAD; count++) {
                        assertThat(ids.add(worker.nextId())).isTrue();
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }
        assertThat(ids).hasSize(THREADS * IDS_PER_THREAD);
    }

    /**
     * 验证参数、纪元及线程中断边界，不吞中断标志。
     */
    @Test
    void rejectsInvalidWorkerClockAndInterruptedCalls() {
        assertThatThrownBy(() -> new IdWorker(-1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdWorker(1024L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdWorker(0L, () -> EPOCH - 1).nextId())
                .isInstanceOf(IllegalStateException.class);
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> new IdWorker(0L).nextId()).isInstanceOf(IllegalStateException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
