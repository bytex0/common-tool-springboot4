package io.github.bytex0.ip2region.core;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.lionsoul.ip2region.xdb.Searcher;

/**
 * 无 DNS 查询的 IPv4 归属地模板；未知 IP 返回 null，错误明确抛出。
 *
 * @author bytex0
 * @since 2026-10-05 19:17:15
 */
public class Ip2RegionTemplate {

    /**
     * 由 Spring 容器管理生命周期的搜索器。
     */
    private final Searcher searcher;

    /**
     * 保护搜索器的可变读取状态；不涉及网络请求或外部回调。
     */
    private final ReentrantLock searchLock = new ReentrantLock();

    /**
     * 包装由调用方或容器管理的搜索器，本模板不取得其关闭责任。
     *
     * @param searcher 非空搜索器
     */
    public Ip2RegionTemplate(Searcher searcher) {
        this.searcher = Objects.requireNonNull(searcher);
    }

    /**
     * 查询 IPv4 字面量，不进行 DNS 查询；保留原引擎对十进制前导零的解释。
     *
     * @param ip IPv4 字面量
     * @return 归属地，数据库无记录时为 null
     * @throws IllegalArgumentException 地址不合法
     * @throws IllegalStateException 数据库查询或记录解析失败
     */
    public RegionResult search(String ip) {
        if (ip == null) {
            throw new IllegalArgumentException("A literal IPv4 address is required");
        }
        try {
            Searcher.checkIP(ip);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid IPv4 address", exception);
        }
        searchLock.lock();
        try {
            return RegionResult.fromRawString(searcher.search(ip));
        } catch (Exception exception) {
            throw new IllegalStateException("IP database lookup failed", exception);
        } finally {
            searchLock.unlock();
        }
    }
}
