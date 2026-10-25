package io.github.bytex0.example.audit;

import io.github.bytex0.common.log.model.OperationLogInfo;
import io.github.bytex0.common.log.service.OperationLogHandler;
import org.springframework.stereotype.Component;

/**
 * 仅供同步示例请求查看当前操作日志，不使用全局集合积压记录。
 *
 * @author bytex0
 * @since 2026-10-06 19:10:52
 */
@Component
public class ExampleOperationLogHandler implements OperationLogHandler {

    /**
     * 当前请求线程最近的日志，由控制器 finally 清理。
     */
    private final ThreadLocal<OperationLogInfo> current = new ThreadLocal<>();

    /**
     * 当前请求是否模拟处理器失败，由控制器 finally 清理。
     */
    private final ThreadLocal<Boolean> failing = new ThreadLocal<>();

    /**
     * 保存已过滤日志并按需模拟故障，不输出敏感请求正文。
     *
     * @param info 日志模型
     */
    @Override
    public void handleLog(OperationLogInfo info) {
        current.set(info);
        if (Boolean.TRUE.equals(failing.get())) {
            throw new IllegalStateException("fixture audit handler failure");
        }
    }

    /**
     * 设置当前请求故障模式。
     *
     * @param fail 是否模拟失败
     */
    public void fail(boolean fail) {
        failing.set(fail);
    }

    /**
     * 获取当前请求日志。
     *
     * @return 最近日志
     */
    public OperationLogInfo current() {
        return current.get();
    }

    /**
     * 清理请求线程上的全部示例状态。
     */
    public void clear() {
        current.remove();
        failing.remove();
    }
}
