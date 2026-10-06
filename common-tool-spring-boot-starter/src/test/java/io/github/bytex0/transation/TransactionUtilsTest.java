package io.github.bytex0.transation;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 提交回调的无事务、提交、回滚及失效同步状态测试。
 *
 * @author bytex0
 * @since 2026-10-06 14:47:34
 */
class TransactionUtilsTest {

    /**
     * 清理本测试线程创建的 Spring 事务上下文。
     */
    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    /**
     * 两个重载在没有实际事务时都立即执行。
     */
    @Test
    void bothOverloadsRunWithoutTransaction() {
        AtomicInteger calls = new AtomicInteger();
        TransactionUtils.doAfterTransaction(calls::incrementAndGet);
        TransactionUtils.doAfterTransaction(new DoTransactionCompletion(calls::incrementAndGet));
        assertThat(calls.get()).isEqualTo(2);
    }

    /**
     * 活跃事务中注册回调，仅提交状态触发动作。
     */
    @Test
    void registersCallbackAndIgnoresRollback() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger calls = new AtomicInteger();
        TransactionUtils.doAfterTransaction(calls::incrementAndGet);
        assertThat(calls.get()).isZero();
        TransactionSynchronization callback = TransactionSynchronizationManager.getSynchronizations().getFirst();
        callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(calls.get()).isZero();
        callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(calls.get()).isEqualTo(1);
    }

    /**
     * 实际事务没有同步支持时明确报错，不提前执行。
     */
    @Test
    void refusesTransactionWithoutSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> TransactionUtils.doAfterTransaction(calls::incrementAndGet))
                .isInstanceOf(IllegalStateException.class);
        assertThat(calls.get()).isZero();
    }
}
