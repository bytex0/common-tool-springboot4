package io.github.bytex0.lock.core;

/**
 * 锁所有权(LockHandle)表示一次成功获取，必须由获取线程关闭。
 *
 * @author linshiqiang
 * @since 2026-10-06 01:53:24
 */
@FunctionalInterface
public interface LockHandle extends AutoCloseable {

    /**
     * 释放此次获取的资源；重复关闭无副作用，跨线程关闭或丢失所有权时报错。
     */
    @Override
    void close();
}
