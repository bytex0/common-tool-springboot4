package io.github.bytex0.excel.core.exporter;

import io.github.bytex0.excel.ExcelTemplate;
import io.github.bytex0.excel.core.TrackedExcelTask;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * 有序分页来源(OrderedPageSource)在写当前批次时预取下一页，始终只有一页待取任务。
 *
 * @param <T> 行类型
 * @author linshiqiang
 * @since 2026-10-06 11:06:59
 */
final class OrderedPageSource<T> implements Supplier<List<T>>, AutoCloseable {

    /**
     * 外部管理的执行器。
     */
    private final ExecutorService executor;

    /**
     * 一基页码查询。
     */
    private final IntFunction<List<T>> query;

    /**
     * 预期稳定总行数。
     */
    private final long total;

    /**
     * 每页上限。
     */
    private final int pageSize;

    /**
     * 数据查询截止的单调时钟值。
     */
    private final long deadline;

    /**
     * 取消时的实际退出等待上限。
     */
    private final Duration cancellationTimeout;

    /**
     * 已交付的行数，仅由写出线程更新。
     */
    private long emitted;

    /**
     * 下一待提交的一基页码。
     */
    private int nextPage = 1;

    /**
     * 唯一在途预取任务，结束后为 null。
     */
    private TrackedExcelTask<List<T>> pending;

    /**
     * 是否结束或已关闭。
     */
    private boolean closed;

    /**
     * 验证参数并开始第一页预取，不创建全量页码表。
     *
     * @param total 总行数，必须非负且页码不超 Integer
     * @param pageSize 每页大小
     * @param executor 共享执行器
     * @param query 实际查询
     * @param timeout 数据预取时间预算
     * @param cancellationTimeout 取消退出等待上限
     */
    OrderedPageSource(long total, int pageSize, ExecutorService executor, IntFunction<List<T>> query,
                      Duration timeout, Duration cancellationTimeout) {
        Assert.isTrue(total >= 0, "总行数不能为负数");
        Assert.isTrue(pageSize > 0 && pageSize <= ExcelTemplate.MAX_BATCH_ROWS, "分页大小必须在1至10000之间");
        Assert.isTrue(total / pageSize + (total % pageSize == 0 ? 0 : 1) <= Integer.MAX_VALUE,
                "导出页码超过Integer范围");
        Assert.notNull(executor, "Excel执行器不能为空");
        Assert.notNull(query, "分页查询不能为空");
        positive(timeout);
        positive(cancellationTimeout);
        this.total = total;
        this.pageSize = pageSize;
        this.executor = executor;
        this.query = query;
        this.deadline = System.nanoTime() + timeout.toNanos();
        this.cancellationTimeout = cancellationTimeout;
        if (total > 0) {
            pending = submit(nextPage++);
        }
    }

    /**
     * 按查询顺序交付批次，并启动唯一的下一页预取。
     *
     * @return 下一批，结束后为空列表
     */
    @Override
    public List<T> get() {
        Assert.state(!closed, "分页来源已关闭");
        if (pending == null) {
            return List.of();
        }
        try {
            List<T> rows = pending.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            long expected = Math.min(pageSize, total - emitted);
            Assert.state(rows.size() == expected, "分页结果与总行数不一致，需要稳定查询快照");
            emitted += rows.size();
            pending = emitted < total ? submit(nextPage++) : null;
            return rows;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待Excel分页被中断", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Excel分页查询失败", exception.getCause());
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Excel分页查询超时", exception);
        }
    }

    /**
     * 取消仍在途的查询并有限等待实际退出，不关闭共享执行器。
     */
    @Override
    public void close() {
        closed = true;
        if (pending != null) {
            Assert.state(pending.cancelAndAwait(cancellationTimeout), "Excel分页任务取消后未及时退出");
        }
    }

    /**
     * 在指定执行器上查询并快照当前批次。
     *
     * @param page 一基页码
     * @return 实际可中断的任务
     */
    private TrackedExcelTask<List<T>> submit(int page) {
        TrackedExcelTask<List<T>> task = new TrackedExcelTask<>(() -> {
            List<T> rows = query.apply(page);
            Assert.notNull(rows, "分页查询不能返回null");
            Assert.isTrue(rows.size() <= pageSize, "分页结果超过约定大小");
            return List.copyOf(rows);
        }, ignored -> { });
        try {
            executor.execute(task);
            return task;
        } catch (RuntimeException exception) {
            task.cancel(true);
            throw exception;
        }
    }

    /**
     * 校验有限的正时间预算。
     *
     * @param duration 时间预算
     */
    private static void positive(Duration duration) {
        Assert.notNull(duration, "等待时间不能为空");
        Assert.isTrue(!duration.isNegative() && !duration.isZero(), "等待时间必须为正");
        duration.toNanos();
    }
}
