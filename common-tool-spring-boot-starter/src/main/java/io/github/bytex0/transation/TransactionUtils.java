package io.github.bytex0.transation;

import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 提交后动作注册工具，无实际事务时立即同步执行，两个重载行为一致。
 *
 * @author bytex0
 * @since 2026-10-06 14:43:55
 */
public final class TransactionUtils {

    /**
     * 保留原公开无参构造入口。
     */
    public TransactionUtils() {
    }

    /**
     * 注册提交后回调；存在事务但同步机制未开启时明确报错。
     *
     * @param completion 非空事务完成回调
     */
    public static void doAfterTransaction(DoTransactionCompletion completion) {
        Objects.requireNonNull(completion, "completion");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException("Transaction synchronization is not active");
            }
            TransactionSynchronizationManager.registerSynchronization(completion);
        } else {
            completion.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
    }

    /**
     * 注册提交后动作，无实际事务时立即执行。
     *
     * @param runnable 非空动作
     */
    public static void doAfterTransaction(Runnable runnable) {
        doAfterTransaction(new DoTransactionCompletion(runnable));
    }
}
