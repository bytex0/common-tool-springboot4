package io.github.bytex0.redis.example;

/**
 * 线格式模型(RedisWireValue)验证显式类型序列化，不携带任意运行时类型元数据。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:13:01
 */
public class RedisWireValue {

    /**
     * 测试名称，默认 null，允许空值。
     */
    private String name;

    /**
     * 获取测试名称。
     *
     * @return 名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置测试名称。
     *
     * @param name 名称
     */
    public void setName(String name) {
        this.name = name;
    }
}
