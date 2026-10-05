package io.github.bytex0.dict;

import org.springframework.util.Assert;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 字典缓存(DictCache)上下文隔离、不可变快照及失败保留
 *
 * @author bytex0
 * @since 2026-10-05 16:08:16
 */
public class DictCache {

    /**
     * 业务字典来源
     */
    private final AtomicReference<State> state;

    /**
     * 最多缓存的字典类型数
     */
    private final int maxTypes;

    /**
     * 构造不使用数据库回退的实例级缓存。
     *
     * @param loader 字典来源
     * @param maxTypes 最大缓存类型数，必须大于零
     */
    public DictCache(DictLoader loader, int maxTypes) {
        this(loader, null, maxTypes);
    }

    /**
     * 构造带可选 JDBC 回退的缓存，构造时不访问外部服务。
     *
     * @param loader 字典来源
     * @param jdbc 可选 JDBC 模板
     * @param maxTypes 最大类型数
     */
    public DictCache(DictLoader loader, JdbcTemplate jdbc, int maxTypes) {
        Assert.isTrue(maxTypes > 0, "dict.max-types必须大于0");
        this.maxTypes = maxTypes;
        this.state = new AtomicReference<>(new State(Objects.requireNonNull(loader), jdbc, Map.of()));
    }

    /**
     * 保留原初始化能力，但改为注入实例调用，避免不同 Spring 容器共享静态数据。
     * 只有加载及校验全部成功才替换原配置与数据。
     *
     * @param loader 新字典来源
     * @param jdbc 可选数据库回退模板
     */
    public void init(DictLoader loader, JdbcTemplate jdbc) {
        Objects.requireNonNull(loader);
        state.set(new State(loader, jdbc, copy(loader.loadAllDict())));
    }

    /**
     * 从实例缓存查询文本，未知类型按需加载。
     *
     * @param type 非空字典类型
     * @param value 原编码，null 返回 null
     * @return 文本，未知值返回 null
     */
    public String getDictText(String type, String value) {
        return getDictText(type, value, "", "", "");
    }

    /**
     * 恢复原四参数回退入口，原 field 同时用于返回列和匹配列。
     *
     * @param type 字典类型
     * @param value 原编码
     * @param table 可信数据库表名，可为空
     * @param field 可信字段名，可为空
     * @return 文本，未命中返回 null
     */
    public String getDictText(String type, String value, String table, String field) {
        return getDictText(type, value, table, field, field);
    }

    /**
     * 查询字典后按需回退 JDBC，编码与显示字段可分别指定。
     *
     * @param type 字典类型
     * @param value 原编码，始终通过 SQL 参数绑定
     * @param table 可信简单表标识符
     * @param field 可信显示列标识符
     * @param codeField 匹配列，空时采用 field
     * @return 文本或 null；数据库不可用及重复结果明确抛错
     */
    public String getDictText(String type, String value, String table, String field, String codeField) {
        if (value == null) {
            return null;
        }
        Assert.hasText(type, "字典类型不能为空");
        State current = loaded(type);
        String text = current.values().get(type).get(value);
        if (text != null || table == null || table.isBlank() || field == null || field.isBlank()) {
            return text;
        }
        JdbcDictLoader.TableMapping mapping = new JdbcDictLoader.TableMapping(table,
                codeField == null || codeField.isBlank() ? field : codeField, field, null);
        Assert.state(current.jdbc() != null, "表字段字典回退需要JdbcTemplate");
        String sql = "SELECT " + mapping.textColumn() + " FROM " + mapping.table()
                + " WHERE " + mapping.codeColumn() + " = ?";
        try {
            return current.jdbc().queryForObject(sql, String.class, value);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    /**
     * 在锁外加载未知类型，用 CAS 发布；并发刷新先完成时丢弃旧加载结果并重读。
     *
     * @param type 类型
     * @return 包含该类型的同版本状态
     */
    private State loaded(String type) {
        while (true) {
            State current = state.get();
            if (current.values().containsKey(type)) {
                return current;
            }
            Map<String, String> values = current.loader().loadDict(type);
            State next = with(current, type, values == null ? Map.of() : values);
            if (state.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    /**
     * 原子替换一个类型，输入复制后发布，失败保留旧快照。
     *
     * @param type 非空类型
     * @param values 非空编码到文本映射，键值均不允许 null
     */
    public void refresh(String type, Map<String, String> values) {
        Assert.hasText(type, "字典类型不能为空");
        Map<String, String> immutable = Map.copyOf(values);
        state.updateAndGet(current -> with(current, type, immutable));
    }

    /**
     * 构造包含指定类型的新状态，不能改变已有配置。
     *
     * @param current 当前状态
     * @param type 字典类型
     * @param values 新字典
     * @return 不可变状态
     */
    private State with(State current, String type, Map<String, String> values) {
        Map<String, Map<String, String>> next = new LinkedHashMap<>(current.values());
        next.put(type, Map.copyOf(values));
        Assert.isTrue(next.size() <= maxTypes, "字典类型超过配置上限");
        return new State(current.loader(), current.jdbc(), Map.copyOf(next));
    }

    /**
     * 完整刷新全部类型，加载期间不阻塞读取；竞争更新时重新加载，避免覆盖较新的写入。
     */
    public void refreshAll() {
        while (true) {
            State current = state.get();
            State next = new State(current.loader(), current.jdbc(), copy(current.loader().loadAllDict()));
            if (state.compareAndSet(current, next)) {
                return;
            }
        }
    }

    /**
     * 校验并深复制字典层级，任何错误都不修改当前缓存。
     *
     * @param loaded 完整数据
     * @return 两级不可变快照
     */
    private Map<String, Map<String, String>> copy(Map<String, Map<String, String>> loaded) {
        Assert.notNull(loaded, "字典加载器不能返回null快照");
        Assert.isTrue(loaded.size() <= maxTypes, "字典类型超过配置上限");
        Map<String, Map<String, String>> next = new LinkedHashMap<>();
        loaded.forEach((type, values) -> {
            Assert.hasText(type, "字典类型不能为空");
            next.put(type, Map.copyOf(values));
        });
        return Map.copyOf(next);
    }

    /**
     * 获取一致的只读快照。
     *
     * @return 不可变两级字典
     */
    public Map<String, Map<String, String>> snapshot() {
        return state.get().values();
    }

    /**
     * 字典状态(State)将数据来源与缓存版本一起发布，防止初始化/刷新竞争混用配置。
     *
     * @author linshiqiang
     * @since 2026-10-06 02:07:34
     * @param loader 当前加载器
     * @param jdbc 当前可选数据库
     * @param values 当前完整快照
     */
    private record State(
            /**
             * 当前加载器，不为空。
             */
            DictLoader loader,

            /**
             * 可选数据库模板，生命周期由调用方管理。
             */
            JdbcTemplate jdbc,

            /**
             * 不可变两级快照。
             */
            Map<String, Map<String, String>> values) {
    }
}
