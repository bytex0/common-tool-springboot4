package io.github.bytex0.id;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 保留原 10 位节点、41 位时间和 12 位序列布局的并发 ID 生成器。
 * 不同进程必须配置不同节点号；自动推导不提供分布式节点唯一性保证。
 *
 * @author bytex0
 * @since 2026-10-06 14:43:55
 */
public class IdWorker {

    /**
     * 原版实际纪元毫秒值，保持已有 ID 解码协议。
     */
    private static final long EPOCH_MILLIS = 1719911958471L;

    /**
     * 序列占用位数。
     */
    private static final int SEQUENCE_BITS = 12;

    /**
     * 时间戳占用位数。
     */
    private static final int TIMESTAMP_BITS = 41;

    /**
     * 节点号最大值。
     */
    private static final long MAX_WORKER_ID = 1023;

    /**
     * 单毫秒序列最大值。
     */
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    /**
     * 相对纪元时间最大毫秒值。
     */
    private static final long MAX_TIMESTAMP = (1L << TIMESTAMP_BITS) - 1;

    /**
     * 序列耗尽后的最大等待纳秒数。
     */
    private static final long MAX_WAIT_NANOS = TimeUnit.SECONDS.toNanos(1);

    /**
     * 等待时钟推进的单次休眠纳秒数，避免忙等。
     */
    private static final long WAIT_STEP_NANOS = TimeUnit.MICROSECONDS.toNanos(100);

    /**
     * 自动节点号退化时的日志，不记录网卡地址。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(IdWorker.class);

    /**
     * 已左移到原版高十位的节点号。
     */
    private final long workerBits;

    /**
     * 时间源，默认系统时钟；测试注入确定性时钟。
     */
    private final LongSupplier clock;

    /**
     * 最后分配的时间戳与序列，-1 表示尚未分配。
     */
    private final AtomicLong timestampAndSequence = new AtomicLong(-1);

    /**
     * 创建 ID 生成器。
     *
     * @param workerId 节点号 0 到 1023，null 时从网卡推导或随机回退
     */
    public IdWorker(Long workerId) {
        this(workerId, System::currentTimeMillis);
    }

    /**
     * 创建使用指定时钟的生成器，供同包测试验证回拨及序列耗尽。
     *
     * @param workerId 节点号，可为空
     * @param clock 非空毫秒时钟
     */
    IdWorker(Long workerId, LongSupplier clock) {
        long actualWorkerId = workerId == null ? generateWorkerId() : workerId;
        if (actualWorkerId < 0 || actualWorkerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and 1023");
        }
        workerBits = actualWorkerId << (TIMESTAMP_BITS + SEQUENCE_BITS);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 分配并发唯一的 ID；时钟回拨失败快返，序列耗尽最多等待一秒。
     *
     * @return 保持原位布局的非负 ID
     * @throws IllegalStateException 时钟越界、回拨、线程中断或时钟超过等待上限仍未推进
     */
    public long nextId() {
        long waitStarted = System.nanoTime();
        for (;;) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("ID generation interrupted", new InterruptedException());
            }
            long previous = timestampAndSequence.get();
            long now = timestamp();
            long previousTime = previous < 0 ? -1 : previous >>> SEQUENCE_BITS;
            if (now < previousTime) {
                throw new IllegalStateException("Clock moved backwards");
            }
            long sequence = now == previousTime ? (previous & MAX_SEQUENCE) + 1 : 0;
            if (sequence > MAX_SEQUENCE) {
                if (System.nanoTime() - waitStarted >= MAX_WAIT_NANOS) {
                    throw new IllegalStateException("Clock did not advance after ID sequence exhaustion");
                }
                LockSupport.parkNanos(WAIT_STEP_NANOS);
                continue;
            }
            long next = (now << SEQUENCE_BITS) | sequence;
            if (timestampAndSequence.compareAndSet(previous, next)) {
                return workerBits | next;
            }
        }
    }

    /**
     * 验证绝对时间并转换为相对纪元毫秒值。
     *
     * @return 可编码时间戳
     */
    private long timestamp() {
        long now = clock.getAsLong();
        if (now < EPOCH_MILLIS || now - EPOCH_MILLIS > MAX_TIMESTAMP) {
            throw new IllegalStateException("Clock is outside the ID timestamp range");
        }
        return now - EPOCH_MILLIS;
    }

    /**
     * 从可用网卡地址低十位推导节点，失败时随机回退。
     *
     * @return 节点号，不能保证不同进程唯一
     */
    private static long generateWorkerId() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                byte[] address = network.getHardwareAddress();
                if (!network.isLoopback() && !network.isVirtual() && address != null && address.length >= 2) {
                    int last = address.length - 1;
                    return (((address[last - 1] & 0xff) << Byte.SIZE) | (address[last] & 0xff)) & MAX_WORKER_ID;
                }
            }
        } catch (SocketException exception) {
            LOGGER.warn("Cannot inspect network interfaces for an ID worker; using a random node", exception);
        }
        return ThreadLocalRandom.current().nextLong(MAX_WORKER_ID + 1);
    }
}
