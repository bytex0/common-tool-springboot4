package io.github.bytex0.oss.listener;

/**
 * 对象存储(CustomProgressListener)传输进度回调
 *
 * @author linshiqiang
 * @since 2026-10-05 14:42:56
 */
@FunctionalInterface
public interface CustomProgressListener {

    /**
     * 同步传输回调；重试时重新计数，服务端确认成功后才报告 100%。
     *
     * @param bytesRead 当前尝试已传输的字节数
     * @param totalBytes 总字节数
     * @param percentage 进度百分比
     * @param speed 当前速度
     * @param speedUnit KB/s 或 MB/s
     */
    void onProgress(long bytesRead, long totalBytes, double percentage, double speed, String speedUnit);
}
