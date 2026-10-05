package io.github.bytex0.lock.core.impl;

import io.github.bytex0.lock.core.LockHandle;
import io.github.bytex0.lock.core.LockStrategy;
import io.github.bytex0.lock.core.LockTemplate;
import io.github.bytex0.lock.exception.LockException;
import io.github.bytex0.lock.model.LockRule;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 策略适配(AbstractTemplateLockStrategy)将原独立获取释放接口绑定到真实所有权句柄。
 *
 * @author linshiqiang
 * @since 2026-10-06 01:53:24
 */
public abstract class AbstractTemplateLockStrategy implements LockStrategy, AutoCloseable {

    /**
     * 当前策略共享的模板。
     */
    private final LockTemplate template;

    /**
     * 仅独立构造方式接管模板生命周期，不接管模板使用的 Redis 客户端。
     */
    private final boolean owned;

    /**
     * 同线程已成功取得的句柄，最后一个释放后移除 ThreadLocal。
     */
    private final ThreadLocal<Map<String, Deque<LockHandle>>> held = ThreadLocal.withInitial(HashMap::new);

    /**
     * 为独立或共享策略绑定模板。
     *
     * @param template 实际模板
     * @param owned 是否管理模板关闭
     */
    protected AbstractTemplateLockStrategy(LockTemplate template, boolean owned) {
        this.template = Objects.requireNonNull(template);
        this.owned = owned;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void lock(LockRule rule) {
        acquire(rule, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryLock(LockRule rule) {
        try {
            acquire(rule, false);
            return true;
        } catch (LockException exception) {
            if (Thread.currentThread().isInterrupted() || exception.getCause() != null) {
                throw exception;
            }
            return false;
        }
    }

    /**
     * 进入原获取接口，失败时不留下所有权记录。
     *
     * @param rule 锁规则
     * @param indefinite 是否无限等待
     */
    private void acquire(LockRule rule, boolean indefinite) {
        if (rule == null || !rule.isEnable()) {
            return;
        }
        LockRule snapshot = rule.toBuilder().lockType(getType()).block(true).build();
        try {
            LockHandle handle = template.acquire(snapshot, indefinite);
            held.get().computeIfAbsent(snapshot.getKey(), ignored -> new ArrayDeque<>()).push(handle);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LockException("锁获取被中断", exception);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlock(LockRule rule) {
        if (rule == null) {
            return;
        }
        Map<String, Deque<LockHandle>> handles = held.get();
        Deque<LockHandle> stack = handles.get(rule.getKey());
        try {
            if (stack != null && !stack.isEmpty()) {
                stack.pop().close();
            }
        } finally {
            if (stack != null && stack.isEmpty()) {
                handles.remove(rule.getKey());
            }
            if (handles.isEmpty()) {
                held.remove();
            }
        }
    }

    /**
     * 关闭本策略独立创建的模板，不替调用方释放在途锁。
     */
    @Override
    public void close() {
        if (owned) {
            template.close();
        }
    }
}
