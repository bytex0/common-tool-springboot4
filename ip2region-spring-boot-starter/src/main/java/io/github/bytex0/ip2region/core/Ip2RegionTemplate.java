package io.github.bytex0.ip2region.core;

import java.io.IOException;
import java.util.Objects;
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

    public Ip2RegionTemplate(Searcher searcher) {
        this.searcher = Objects.requireNonNull(searcher);
    }

    public synchronized RegionResult search(String ip) {
        if (ip == null || !ip.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) {
            throw new IllegalArgumentException("A literal IPv4 address is required");
        }
        for (String part : ip.split("\\.")) {
            if (Integer.parseInt(part) > 255 || (part.length() > 1 && part.startsWith("0"))) {
                throw new IllegalArgumentException("Invalid IPv4 address");
            }
        }
        try {
            return RegionResult.fromRawString(searcher.search(ip));
        } catch (IOException exception) {
            throw new IllegalStateException("IP database lookup failed", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("IP database lookup failed", exception);
        }
    }
}
