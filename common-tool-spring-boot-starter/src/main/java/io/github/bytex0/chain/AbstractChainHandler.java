package io.github.bytex0.chain;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 责任链处理节点，节点最多属于一条由 Builder 构建的链。
 *
 * @param <T> 上下文类型
 * @author bytex0
 * @since 2026-10-06 14:46:02
 */
public abstract class AbstractChainHandler<T> {

    /**
     * 后继节点，保留原扩展字段；子类修改链接时自行负责无环及并发安全。
     */
    protected volatile AbstractChainHandler<T> nextChain;

    /**
     * 防止同一节点加入不同 Builder 后破坏已构建链。
     */
    private final AtomicBoolean linked = new AtomicBoolean();

    /**
     * 判断当前节点是否为链尾。
     *
     * @return 是否没有后继节点
     */
    protected Boolean isEnd() {
        return nextChain == null;
    }

    /**
     * 存在后继节点时继续处理，不自动复制业务上下文。
     *
     * @param context 业务上下文
     */
    protected void nextHandler(T context) {
        AbstractChainHandler<T> next = nextChain;
        if (next != null) {
            next.doHandler(context);
        }
    }

    /**
     * 执行当前处理步骤；是否继续调用后继由子类决定。
     *
     * @param context 业务上下文
     */
    public abstract void doHandler(T context);

    /**
     * 单线程使用的责任链构建器，首次 build 后禁止再增加节点。
     *
     * @param <T> 上下文类型
     * @author bytex0
     * @since 2026-10-06 14:46:02
     */
    public static class Builder<T> {

        /**
         * 链首节点，空链时为 null。
         */
        private AbstractChainHandler<T> head;

        /**
         * 链尾节点，空链时为 null。
         */
        private AbstractChainHandler<T> tail;

        /**
         * 是否已经发布构建结果。
         */
        private boolean built;

        /**
         * 添加尚未属于其他链且没有后继的节点。
         *
         * @param handler 非空节点
         */
        public void addHandler(AbstractChainHandler<T> handler) {
            Objects.requireNonNull(handler, "handler");
            if (built) {
                throw new IllegalStateException("Chain is already built");
            }
            if (handler.nextChain != null || !handler.linked.compareAndSet(false, true)) {
                throw new IllegalArgumentException("Handler is already linked");
            }
            if (head == null) {
                head = handler;
            } else {
                tail.nextChain = handler;
            }
            tail = handler;
        }

        /**
         * 发布当前链；重复调用返回相同首节点。
         *
         * @return 首节点，空链为 null
         */
        public AbstractChainHandler<T> build() {
            built = true;
            return head;
        }
    }
}
