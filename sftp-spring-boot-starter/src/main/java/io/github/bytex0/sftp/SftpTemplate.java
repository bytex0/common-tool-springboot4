package io.github.bytex0.sftp;

import com.jcraft.jsch.ChannelSftp;
import io.github.bytex0.sftp.core.JschConnectionPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.util.Assert;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 有界 SFTP 连接池，操作结束自动归还或销毁连接，始终校验主机公钥。
 *
 * @author bytex0
 * @since 2026-10-05 19:47:49
 */
public class SftpTemplate implements AutoCloseable {

    /**
     * 实际命名池管理器。
     */
    private final JschConnectionPool pools;

    /**
     * 只有直接配置构造方式由模板关闭管理器，注入方式交给外部容器。
     */
    private final boolean owned;

    /**
     * 保留原配置构造方式，创建默认安全池但不立即连接。
     *
     * @param properties 配置
     */
    public SftpTemplate(SftpProperties properties) {
        this(new JschConnectionPool(properties), true);
    }

    /**
     * 注入可共享的命名池管理器。
     *
     * @param pools 外部管理器
     */
    public SftpTemplate(JschConnectionPool pools) {
        this(pools, false);
    }

    /**
     * 固定管理器与资源归属。
     *
     * @param pools 管理器
     * @param owned 是否管理其关闭
     */
    private SftpTemplate(JschConnectionPool pools, boolean owned) {
        this.pools = Objects.requireNonNull(pools);
        this.owned = owned;
    }

    /**
     * 上传到默认池，输入流由调用方关闭。
     *
     * @param path 远端路径
     * @param input 输入内容
     * @throws Exception SFTP 操作失败
     */
    public void upload(String path, InputStream input) throws Exception {
        upload(JschConnectionPool.DEFAULT_KEY, path, input);
    }

    /**
     * 上传到指定命名池，输入流不得逃离同步调用。
     *
     * @param poolName 池名称
     * @param path 远端路径
     * @param input 调用方拥有的输入流
     * @throws Exception 上传失败
     */
    public void upload(String poolName, String path, InputStream input) throws Exception {
        Assert.notNull(input, "SFTP上传流不能为空");
        execute(poolName, channel -> {
            channel.put(input, path);
            return null;
        });
    }

    /**
     * 从默认池下载，输出流由调用方关闭。
     *
     * @param path 远端路径
     * @param output 目标输出
     * @throws Exception 下载失败
     */
    public void download(String path, OutputStream output) throws Exception {
        download(JschConnectionPool.DEFAULT_KEY, path, output);
    }

    /**
     * 从指定池下载，不关闭调用方输出流。
     *
     * @param poolName 池名称
     * @param path 远端路径
     * @param output 目标流
     * @throws Exception 下载失败
     */
    public void download(String poolName, String path, OutputStream output) throws Exception {
        Assert.notNull(output, "SFTP下载流不能为空");
        execute(poolName, channel -> {
            channel.get(path, output);
            return null;
        });
    }

    /**
     * 删除默认池中的文件。
     *
     * @param path 远端路径
     * @throws Exception 删除失败
     */
    public void delete(String path) throws Exception {
        delete(JschConnectionPool.DEFAULT_KEY, path);
    }

    /**
     * 删除指定池中的文件。
     *
     * @param poolName 池名称
     * @param path 远端路径
     * @throws Exception 删除失败
     */
    public void delete(String poolName, String path) throws Exception {
        execute(poolName, channel -> {
            channel.rm(path);
            return null;
        });
    }

    /**
     * 列举默认池目录，跳过点目录。
     *
     * @param path 远端目录
     * @return 不可变文件名列表
     * @throws Exception 列举失败
     */
    public List<String> list(String path) throws Exception {
        return list(JschConnectionPool.DEFAULT_KEY, path);
    }

    /**
     * 列举指定池目录。
     *
     * @param poolName 池名称
     * @param path 目录路径
     * @return 不可变名称列表
     * @throws Exception 列举失败
     */
    public List<String> list(String poolName, String path) throws Exception {
        return execute(poolName, channel -> {
            List<String> result = new ArrayList<>();
            channel.ls(path, entry -> {
                if (!entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                    result.add(entry.getFilename());
                }
                return ChannelSftp.LsEntrySelector.CONTINUE;
            });
            return List.copyOf(result);
        });
    }

    /**
     * 获取默认池正在借出的连接数。
     *
     * @return 活动连接数
     */
    public int activeConnections() {
        return activeConnections(JschConnectionPool.DEFAULT_KEY);
    }

    /**
     * 获取命名池的活动连接数。
     *
     * @param poolName 名称
     * @return 活动数
     */
    public int activeConnections(String poolName) {
        GenericObjectPool<ChannelSftp> pool = pools.getPool(poolName);
        Assert.notNull(pool, "sftp pool is null");
        return pool.getNumActive();
    }

    /**
     * 在默认池执行完整同步操作，可使用重命名、目录、权限等全部通道功能。
     *
     * @param operation 同步回调，通道和远程流不能逃逸，打开的远程流须在回调内关闭
     * @param <T> 普通结果类型
     * @return 回调结果
     * @throws Exception 操作失败，通道销毁而不是放回池
     */
    public <T> T execute(Operation<T> operation) throws Exception {
        return execute(JschConnectionPool.DEFAULT_KEY, operation);
    }

    /**
     * 使用真实命名池执行回调，异常时销毁通道并将清理异常作为 suppressed 保留。
     *
     * @param poolName 池名称
     * @param operation 同步操作，不能返回仍持有远程资源的对象
     * @param <T> 返回值类型
     * @return 普通业务结果
     * @throws Exception 原操作失败或中断
     */
    public <T> T execute(String poolName, Operation<T> operation) throws Exception {
        Assert.notNull(operation, "SFTP回调不能为空");
        try (Lease lease = new Lease(poolName, pools.borrowObject(poolName))) {
            T result = operation.apply(lease.channel);
            lease.successful = true;
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    /**
     * 只关闭本模板自己创建的池管理器。
     */
    @Override
    public void close() {
        if (owned) {
            pools.close();
        }
    }

    /**
     * 通道操作(Operation)限定在借用作用域内使用非线程安全的 ChannelSftp。
     *
     * @author bytex0
     * @since 2026-10-05 19:47:49
     * @param <T> 普通返回结果
     */
    @FunctionalInterface
    public interface Operation<T> {

        /**
         * 同步执行操作，不关闭 Session，所有远程流必须在返回前关闭。
         *
         * @param channel 当前独占通道
         * @return 普通结果
         * @throws Exception 业务或 SFTP 错误
         */
        T apply(ChannelSftp channel) throws Exception;
    }

    /**
     * 通道作用域(Lease)利用自动资源关闭保留主异常和清理异常。
     *
     * @author linshiqiang
     * @since 2026-10-06 02:50:50
     */
    private final class Lease implements AutoCloseable {

        /**
         * 原池名称。
         */
        private final String name;

        /**
         * 本次独占通道。
         */
        private final ChannelSftp channel;

        /**
         * 默认失败，只有回调正常返回才允许复用连接。
         */
        private boolean successful;

        /**
         * 接管已成功借出的通道。
         *
         * @param name 池名称
         * @param channel 通道
         */
        private Lease(String name, ChannelSftp channel) {
            this.name = name;
            this.channel = channel;
        }

        /**
         * 成功归还，失败销毁；不关闭外部流或整个池。
         */
        @Override
        public void close() {
            if (successful) {
                pools.returnObject(name, channel);
            } else {
                pools.invalidateObject(name, channel);
            }
        }
    }
}
