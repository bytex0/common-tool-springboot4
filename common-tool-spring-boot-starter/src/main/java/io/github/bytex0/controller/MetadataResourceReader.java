package io.github.bytex0.controller;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.Resource;

/**
 * 元数据资源有界读取，关闭调用中打开的流，不持有全局资源状态。
 *
 * @author bytex0
 * @since 2026-10-06 18:02:16
 */
final class MetadataResourceReader {

    /**
     * 默认读取上限，一 MiB。
     */
    static final int DEFAULT_MAX_BYTES = 1024 * 1024;

    /**
     * 可配置读取上限，十六 MiB。
     */
    private static final int MAX_ALLOWED_BYTES = 16 * 1024 * 1024;

    /**
     * 禁止实例化内部工具。
     */
    private MetadataResourceReader() {
    }

    /**
     * 校验资源大小配置。
     *
     * @param maxBytes 最大字节数，范围 1 到十六 MiB
     */
    static void validateLimit(int maxBytes) {
        if (maxBytes <= 0 || maxBytes > MAX_ALLOWED_BYTES) {
            throw new IllegalArgumentException("Metadata resource limit must be 1 to 16777216 bytes");
        }
    }

    /**
     * 读取最多指定字节数，超限抛错而不返回截断数据。
     *
     * @param resource 待读取资源
     * @param maxBytes 最大字节数
     * @return 完整资源字节
     * @throws IOException 读取失败或资源超限
     */
    static byte[] read(Resource resource, int maxBytes) throws IOException {
        validateLimit(maxBytes);
        try (InputStream input = resource.getInputStream()) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                throw new IOException("Metadata resource exceeds the configured size limit");
            }
            return bytes;
        }
    }
}
