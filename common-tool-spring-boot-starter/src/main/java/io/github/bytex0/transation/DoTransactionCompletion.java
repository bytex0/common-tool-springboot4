package io.github.bytex0.transation;

import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronization;

/**
 * 事务提交完成后同步执行的回调，保留历史 transation 包名。
 *
 * @author bytex0
 * @since 2026-10-06 14:43:55
 */
public class DoTransactionCompletion implements TransactionSynchronization {

    /**
     * 仅在事务提交完成后调用的动作。
     */
    private final Runnable runnable;

    /**
     * 创建提交回调，不创建线程或转移资源所有权。
     *
     * @param runnable 非空动作
     */
    public DoTransactionCompletion(Runnable runnable) {
        this.runnable = Objects.requireNonNull(runnable, "runnable");
    }

    /**
     * 提交后同步执行，回滚和未知状态不执行。
     * 由 Spring 调用时，异常遵循 TransactionSynchronization 的日志处理语义。
     *
     * @param status Spring 事务完成状态
     */
    @Override
    public void afterCompletion(int status) {
        if (status == STATUS_COMMITTED) {
            runnable.run();
        }
    }
}
