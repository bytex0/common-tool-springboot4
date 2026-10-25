package io.github.bytex0.common;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 可重置闩锁及后台服务启停、唤醒竞争测试。
 *
 * @author bytex0
 * @since 2026-10-06 14:50:52
 */
class ConcurrencyLifecycleTest {

    /**
     * 测试等待上限。
     */
    private static final Duration WAIT_LIMIT = Duration.ofSeconds(3);

    /**
     * 验证子类仍可使用原 protected waitPoint 唤醒已进入等待的服务。
     *
     * @throws InterruptedException 测试等待被中断
     */
    @Test
    void subclassCanReleaseOriginalWaitPoint() throws InterruptedException {
        DirectSignalService service = new DirectSignalService();
        service.start();
        try {
            await().atMost(WAIT_LIMIT).until(() -> service.running != null
                    && service.running.getState() == Thread.State.TIMED_WAITING);
            service.releaseWaitPoint();
            assertThat(service.returned.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            service.shutdown(true);
        }
    }

    /**
     * 验证归零后立即重置不会把旧代等待者重新阻塞。
     *
     * @throws InterruptedException 等待测试线程退出时被中断
     */
    @Test
    void completedGenerationSurvivesImmediateReset() throws InterruptedException {
        CountDownLatch2 latch = new CountDownLatch2(1);
        AtomicBoolean completed = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            try {
                latch.await();
                completed.set(true);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, "latch-test");
        waiter.start();
        try {
            await().atMost(WAIT_LIMIT).until(() -> waiter.getState() == Thread.State.WAITING);
            latch.countDown();
            latch.reset();
            await().atMost(WAIT_LIMIT).untilTrue(completed);
            assertThat(latch.getCount()).isEqualTo(1);
            assertThat(latch.await(1, TimeUnit.MILLISECONDS)).isFalse();
            latch.countDown();
            assertThat(latch.await(0, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            waiter.interrupt();
            waiter.join(WAIT_LIMIT.toMillis());
        }
        assertThat(waiter.isAlive()).isFalse();
    }

    /**
     * 验证提前到达的唤醒、重复启动、停机和实际退出后的重启。
     *
     * @throws InterruptedException 测试等待被中断
     */
    @Test
    void servicePreservesWakeupsAndRestartsAfterExit() throws InterruptedException {
        WaitingService service = new WaitingService();
        try {
            service.wakeup();
            service.start();
            service.start();
            assertThat(service.entries.tryAcquire(3, TimeUnit.SECONDS)).isTrue();
            assertThat(service.wakeups.tryAcquire(3, TimeUnit.SECONDS)).isTrue();
            assertThat(service.starts.get()).isEqualTo(1);
            service.shutdown();
            assertThat(service.isStopped()).isTrue();
            service.start();
            assertThat(service.entries.tryAcquire(3, TimeUnit.SECONDS)).isTrue();
            assertThat(service.starts.get()).isEqualTo(2);
        } finally {
            service.shutdown(true);
        }
    }

    /**
     * 验证停机等待超时后不能启动第二个仍在执行的实例线程。
     *
     * @throws InterruptedException 测试等待被中断
     */
    @Test
    void timedOutShutdownCannotStartConcurrentReplacement() throws InterruptedException {
        BlockingService service = new BlockingService();
        try {
            service.start();
            assertThat(service.entered.await(3, TimeUnit.SECONDS)).isTrue();
            service.shutdown(false);
            service.start();
            assertThat(service.starts.get()).isEqualTo(1);
        } finally {
            service.release.countDown();
            service.shutdown(true);
        }
        assertThat(service.exited.await(3, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * 使用信号量向测试暴露启动和等待结束事件的服务。
     *
     * @author bytex0
     * @since 2026-10-06 14:50:52
     */
    private static final class WaitingService extends ServiceThread {

        /**
         * 实际进入 run 的次数。
         */
        private final AtomicInteger starts = new AtomicInteger();

        /**
         * 启动事件，不在重启时替换共享状态。
         */
        private final Semaphore entries = new Semaphore(0);

        /**
         * 等待结束事件。
         */
        private final Semaphore wakeups = new Semaphore(0);

        /**
         * 获取测试服务名称。
         *
         * @return 固定名称
         */
        @Override
        public String getServiceName() {
            return "waiting-service-test";
        }

        /**
         * 运行可被 wakeup 或停机唤醒的循环。
         */
        @Override
        public void run() {
            starts.incrementAndGet();
            entries.release();
            while (!isStopped()) {
                waitForRunning(TimeUnit.MINUTES.toMillis(1));
            }
        }

        /**
         * 记录等待结束事件。
         */
        @Override
        protected void onWaitEnd() {
            wakeups.release();
        }

        /**
         * 限定测试停机等待。
         *
         * @return 毫秒上限
         */
        @Override
        public long getJointime() {
            return WAIT_LIMIT.toMillis();
        }
    }

    /**
     * 用显式释放信号模拟停机时仍在执行的业务任务。
     *
     * @author bytex0
     * @since 2026-10-06 14:50:52
     */
    private static final class BlockingService extends ServiceThread {

        /**
         * 实际运行次数。
         */
        private final AtomicInteger starts = new AtomicInteger();

        /**
         * 任务开始信号。
         */
        private final CountDownLatch entered = new CountDownLatch(1);

        /**
         * 测试拥有的业务任务释放信号。
         */
        private final CountDownLatch release = new CountDownLatch(1);

        /**
         * 任务结束信号。
         */
        private final CountDownLatch exited = new CountDownLatch(1);

        /**
         * 获取测试服务名称。
         *
         * @return 固定名称
         */
        @Override
        public String getServiceName() {
            return "blocking-service-test";
        }

        /**
         * 等待测试释放业务任务，保留中断语义。
         */
        @Override
        public void run() {
            starts.incrementAndGet();
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exited.countDown();
            }
        }

        /**
         * 使用较短等待制造停机超时分支。
         *
         * @return 停机等待毫秒数
         */
        @Override
        public long getJointime() {
            return 10;
        }
    }

    /**
     * 通过原受保护等待点通知的兼容性测试服务。
     *
     * @author bytex0
     * @since 2026-10-06 19:20:43
     */
    private static final class DirectSignalService extends ServiceThread {

        /**
         * 正在等待的线程。
         */
        private volatile Thread running;

        /**
         * 等待返回信号。
         */
        private final CountDownLatch returned = new CountDownLatch(1);

        /**
         * 获取测试线程名称。
         *
         * @return 服务名称
         */
        @Override
        public String getServiceName() {
            return "direct-wait-point-test";
        }

        /**
         * 执行一次可被子类直接释放的等待。
         */
        @Override
        public void run() {
            running = Thread.currentThread();
            waitForRunning(TimeUnit.SECONDS.toMillis(30));
            returned.countDown();
        }

        /**
         * 使用原保护字段发送信号。
         */
        private void releaseWaitPoint() {
            waitPoint.countDown();
        }

        /**
         * 限制测试清理等待时长。
         *
         * @return 毫秒上限
         */
        @Override
        public long getJointime() {
            return WAIT_LIMIT.toMillis();
        }
    }
}
