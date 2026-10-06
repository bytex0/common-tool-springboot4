package io.github.bytex0.id;

/**
 * 单实例 ID 工具，构造时完成初始化，不在每次取号时重复创建生成器。
 *
 * @author bytex0
 * @since 2026-10-06 14:43:55
 */
public class IdWorkerUtil {

    /**
     * 本实例拥有的线程安全生成器。
     */
    private final IdWorker idWorker;

    /**
     * 创建指定节点的工具。
     *
     * @param workerId 节点号，可为空以使用自动推导
     */
    public IdWorkerUtil(Long workerId) {
        idWorker = new IdWorker(workerId);
    }

    /**
     * 保留原显式初始化入口；构造时已完成初始化，重复调用不重置序列。
     */
    public void buildIdWorker() {
        // 初始化已由 final 字段保证，保留旧调用方的幂等入口。
    }

    /**
     * 创建独立生成器，不替换当前实例的生成器。
     *
     * @param workerId 新生成器节点号
     * @return 独立生成器
     */
    public IdWorker buildIdWorker(Long workerId) {
        return new IdWorker(workerId);
    }

    /**
     * 获取下一个 ID。
     *
     * @return ID
     */
    public Long nextId() {
        return idWorker.nextId();
    }

    /**
     * 获取十进制文本 ID，避免 JavaScript 数字精度损失。
     *
     * @return ID 文本
     */
    public String nextIdStr() {
        return Long.toString(idWorker.nextId());
    }
}
