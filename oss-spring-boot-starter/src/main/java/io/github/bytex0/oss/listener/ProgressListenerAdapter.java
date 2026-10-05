package io.github.bytex0.oss.listener;

import org.springframework.util.Assert;

/**
 * 对象存储(ProgressListenerAdapter)传输进度计数器
 *
 * @author bytex0
 * @since 2026-10-05 14:42:56
 */
public class ProgressListenerAdapter {

    /**
     * 业务回调，可为空
     */
    private final CustomProgressListener listener;

    /**
     * 总传输字节数
     */
    private final long totalBytes;

    /**
     * 当前尝试已读取的字节数
     */
    private long bytesRead;

    /**
     * 上次通知时的字节数
     */
    private long lastBytesRead;

    /**
     * 上次通知的单调时钟值
     */
    private long lastUpdateNanos;

    /**
     * 创建单次同步传输的线程封闭计数器，不可跨传输并发复用。
     *
     * @param listener 可选业务回调
     * @param totalBytes 总字节数，必须非负
     */
    public ProgressListenerAdapter(CustomProgressListener listener, long totalBytes) {
        Assert.isTrue(totalBytes >= 0, "总字节数不能为负数");
        this.listener = listener;
        this.totalBytes = totalBytes;
    }

    /**
     * 重置整个上传尝试，保留原入口。
     */
    public void reset() {
        reset(0);
    }

    /**
     * 分片重试从已完成前缀重新计数，不能把重试字节重复累加。
     *
     * @param completedBytes 已确认的前缀长度，范围为 0 至总长度
     */
    public void reset(long completedBytes) {
        Assert.isTrue(completedBytes >= 0 && completedBytes <= totalBytes, "已完成字节数不合法");
        bytesRead = completedBytes;
        lastBytesRead = completedBytes;
        lastUpdateNanos = System.nanoTime();
        if (listener != null) {
            double percentage = totalBytes == 0 ? 0 : Math.min(99.99, completedBytes * 100.0 / totalBytes);
            listener.onProgress(completedBytes, totalBytes, percentage, 0, "KB/s");
        }
    }

    /**
     * 累积本次读取长度，最多每 100 毫秒报告一次，不提前发出 100%。
     *
     * @param count 本次读取字节数，必须非负
     */
    public void transferred(long count) {
        Assert.isTrue(count >= 0, "传输字节数不能为负数");
        bytesRead += Math.min(count, totalBytes - bytesRead);
        if (System.nanoTime() - lastUpdateNanos >= 100_000_000L) {
            publish(false);
        }
    }

    /**
     * 服务端确认成功后发布最终 100%。
     */
    public void complete() {
        bytesRead = totalBytes;
        publish(true);
    }

    /**
     * 基于单调时间计算当前速度，业务回调异常向上传播。
     *
     * @param complete 是否已得到服务端成功确认
     */
    private void publish(boolean complete) {
        long now = System.nanoTime();
        double elapsedSeconds = Math.max(1L, now - lastUpdateNanos) / 1_000_000_000.0;
        double bytesPerSecond = (bytesRead - lastBytesRead) / elapsedSeconds;
        boolean megabytes = bytesPerSecond >= 1024 * 1024;
        double speed = bytesPerSecond / (megabytes ? 1024 * 1024 : 1024);
        double percentage = complete ? 100 : (totalBytes == 0 ? 0 : Math.min(99.99, bytesRead * 100.0 / totalBytes));
        if (listener != null) {
            listener.onProgress(bytesRead, totalBytes, percentage, speed, megabytes ? "MB/s" : "KB/s");
        }
        lastBytesRead = bytesRead;
        lastUpdateNanos = now;
    }
}
