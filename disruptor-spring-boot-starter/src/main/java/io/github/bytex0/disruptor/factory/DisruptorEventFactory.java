package io.github.bytex0.disruptor.factory;

import com.lmax.disruptor.EventFactory;
import io.github.bytex0.disruptor.event.DisruptorEvent;

/**
 * 事件工厂(DisruptorEventFactory)为环预先创建可复用槽位。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:17:49
 * @param <T> 消息类型
 */
public class DisruptorEventFactory<T> implements EventFactory<DisruptorEvent<T>> {

    /**
     * {@inheritDoc}
     */
    @Override
    public DisruptorEvent<T> newInstance() {
        return new DisruptorEvent<>();
    }
}
