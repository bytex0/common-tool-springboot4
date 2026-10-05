package io.github.bytex0.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import io.github.bytex0.disruptor.event.DisruptorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认事件处理器(DisruptorHandler)保留原扩展类型，只记录序号而不输出消息正文。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:17:49
 * @param <T> 消息类型
 */
public class DisruptorHandler<T> implements EventHandler<DisruptorEvent<T>> {

    /**
     * 不记录业务数据的诊断日志。
     */
    private static final Logger LOG = LoggerFactory.getLogger(DisruptorHandler.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void onEvent(DisruptorEvent<T> event, long sequence, boolean endOfBatch) {
        LOG.debug("Disruptor event received, sequence={}", sequence);
    }
}
