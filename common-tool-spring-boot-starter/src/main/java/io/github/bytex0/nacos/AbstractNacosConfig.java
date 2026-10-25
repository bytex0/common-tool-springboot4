package io.github.bytex0.nacos;

import com.alibaba.nacos.api.config.ConfigService;

/**
 * 原 Nacos 配置监听契约，每个监听对象明确提供自己的客户端、dataId 和 group。
 *
 * @author bytex0
 * @since 2026-10-06 18:24:37
 */
public abstract class AbstractNacosConfig {

    /**
     * 接收初始配置或后续更新，同一个监听对象的回调串行执行。
     *
     * @param content 配置文本，空文本保留，删除配置时可能为 null
     */
    public abstract void onReceived(String content);

    /**
     * 获取实际配置标识，不能以 Spring Bean 名替代。
     *
     * @return 非空 dataId
     */
    public abstract String getDataId();

    /**
     * 获取配置分组。
     *
     * @return group，null 或空白使用 Nacos 默认分组
     */
    public abstract String getGroup();

    /**
     * 获取配置所属客户端，监听器只注册和移除回调，不负责关闭该客户端。
     *
     * @return 非空配置客户端
     */
    public abstract ConfigService configService();
}
