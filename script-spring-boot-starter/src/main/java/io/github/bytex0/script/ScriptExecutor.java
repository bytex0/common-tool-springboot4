package io.github.bytex0.script;

import java.util.Map;

/**
 * 受信脚本执行器扩展点，实现必须隔离每次调用的可变状态并响应中断。
 *
 * @author bytex0
 * @since 2026-10-05 20:00:38
 */
public interface ScriptExecutor {

    String language();

    Object execute(String source, Map<String, Object> parameters) throws Exception;
}
