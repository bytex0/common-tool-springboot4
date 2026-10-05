package io.github.bytex0.oss.listener;

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

    public ProgressListenerAdapter(CustomProgressListener listener, long totalBytes) {
        this.listener = listener;
        this.totalBytes = totalBytes;
    }

    public void reset() {
        bytesRead = 0;
        lastBytesRead = 0;
        lastUpdateNanos = System.nanoTime();
        if (listener != null) {
            listener.onProgress(0, totalBytes, 0, 0, "KB/s");
        }
    }

    public void transferred(long count) {
        bytesRead = Math.min(totalBytes, bytesRead + count);
        if (System.nanoTime() - lastUpdateNanos >= 100_000_000L) {
            publish(false);
        }
    }

    public void complete() {
        bytesRead = totalBytes;
        publish(true);
    }

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
