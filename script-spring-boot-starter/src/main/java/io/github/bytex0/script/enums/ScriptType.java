package io.github.bytex0.script.enums;

/**
 * 脚本引擎类型，名称用于选择执行器，不使用枚举序号作为业务编码。
 *
 * @author bytex0
 * @since 2026-10-06 13:18:57
 */
public enum ScriptType {

    /**
     * Groovy 脚本，兼容入口默认调用 execute(Map)。
     */
    GROOVY,

    /**
     * GraalJS JavaScript 脚本。
     */
    JAVASCRIPT,

    /**
     * LuaJ Lua 脚本，兼容入口返回字符串。
     */
    LUA,

    /**
     * Jython Python 2.7 脚本，不代表 Python 3 支持。
     */
    PYTHON,

    /**
     * JDK 编译的 Java 类，默认调用 execute(Map)。
     */
    JAVA
}
