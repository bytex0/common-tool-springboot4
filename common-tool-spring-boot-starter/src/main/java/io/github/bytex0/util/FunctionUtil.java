package io.github.bytex0.util;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.function.ThrowingSupplier;

/**
 * 缓存读取与数据回源模板，缓存故障不影响已获取的数据，数据库异常向上传播。
 *
 * @author bytex0
 * @since 2026-10-06 14:41:36
 */
public class FunctionUtil {

    /**
     * 只记录缓存故障，不输出缓存键或数据正文。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(FunctionUtil.class);

    /**
     * 先查询缓存，未命中或缓存失败时回源，命中数据库后尝试写缓存。
     *
     * @param cacheKey 保留原调用参数，不作为锁键或输出到日志
     * @param loaderCache 缓存读取，返回非空 Optional
     * @param loaderDb 数据库读取，返回非空 Optional
     * @param consumer 缓存写入，失败不改变返回结果
     * @param <T> 数据类型
     * @return 缓存或数据库结果
     */
    public <T> Optional<T> getCachedOrLoadDb(String cacheKey, ThrowingSupplier<Optional<T>> loaderCache,
                                           Supplier<Optional<T>> loaderDb, Consumer<T> consumer) {
        Objects.requireNonNull(loaderCache, "loaderCache");
        Objects.requireNonNull(loaderDb, "loaderDb");
        Objects.requireNonNull(consumer, "consumer");
        try {
            Optional<T> cached = Objects.requireNonNull(loaderCache.getWithException(), "cache result");
            if (cached.isPresent()) {
                return cached;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cache read interrupted", exception);
        } catch (Exception exception) {
            LOGGER.warn("Cache read failed; loading from the data source", exception);
        }
        Optional<T> loaded = Objects.requireNonNull(loaderDb.get(), "database result");
        if (loaded.isPresent()) {
            try {
                consumer.accept(loaded.orElseThrow());
            } catch (RuntimeException exception) {
                LOGGER.warn("Cache write failed after data was loaded", exception);
            }
        }
        return loaded;
    }
}
