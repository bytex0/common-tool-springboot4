package io.github.bytex0.script;

import java.util.Map;

/**
 * 受信脚本执行器扩展点，实现必须隔离每次调用的可变状态并响应中断。
 *
 * @author bytex0
 * @since 2026-10-05 20:00:38
 */
public interface ScriptExecutor {

    /**
     * 获取语言标识，用于根包服务路由。
     *
     * @return 非空语言标识
     */
    String language();

    /**
     * 执行受信源码的脚本体，每次调用必须隔离可变状态。
     *
     * @param source 非空源码
     * @param parameters 非空参数映射
     * @return 脚本体结果，可为空
     * @throws Exception 编译或执行失败
     */
    Object execute(String source, Map<String, Object> parameters) throws Exception;
}
