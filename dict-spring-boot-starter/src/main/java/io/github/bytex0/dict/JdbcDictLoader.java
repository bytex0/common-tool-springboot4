package io.github.bytex0.dict;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据库字典(JdbcDictLoader)可信标识符映射及参数绑定
 *
 * @author bytex0
 * @since 2026-10-05 16:08:16
 */
public class JdbcDictLoader implements DictLoader {

    /**
     * 应用管理的数据源模板
     */
    private final JdbcTemplate jdbc;

    /**
     * 允许访问的字典和表映射
     */
    private final Map<String, TableMapping> mappings;

    public JdbcDictLoader(JdbcTemplate jdbc, Map<String, TableMapping> mappings) {
        this.jdbc = jdbc;
        this.mappings = Map.copyOf(mappings);
    }

    @Override
    public Map<String, String> loadDict(String type) {
        TableMapping mapping = mappings.get(type);
        if (mapping == null) { return Map.of(); }
        String sql = "SELECT " + mapping.codeColumn() + ", " + mapping.textColumn() + " FROM " + mapping.table();
        boolean filtered = mapping.typeColumn() != null && !mapping.typeColumn().isBlank();
        if (filtered) { sql += " WHERE " + mapping.typeColumn() + " = ?"; }
        String query = sql;
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement(query);
            statement.setMaxRows(100001);
            if (filtered) { statement.setString(1, type); }
            return statement;
        }, result -> {
            Map<String, String> values = new LinkedHashMap<>();
            int rows = 0;
            while (result.next()) {
                Assert.isTrue(++rows <= 100000, "单个数据库字典超过100000行");
                String key = result.getString(1);
                String text = result.getString(2);
                Assert.notNull(key, "字典code不能为空");
                Assert.notNull(text, "字典text不能为空");
                Assert.isTrue(values.putIfAbsent(key, text) == null, "字典code重复");
            }
            return Map.copyOf(values);
        });
    }

    @Override
    public Map<String, Map<String, String>> loadAllDict() {
        Map<String, Map<String, String>> values = new LinkedHashMap<>();
        mappings.keySet().forEach(type -> values.put(type, loadDict(type)));
        return Map.copyOf(values);
    }

    /**
     * 字典表映射(TableMapping)仅允许可信SQL标识符
     *
     * @author bytex0
     * @since 2026-10-05 16:08:16
     * @param table 表名
     * @param codeColumn code列
     * @param textColumn text列
     * @param typeColumn 可选类型列，类型值使用参数绑定
     */
    public record TableMapping(String table, String codeColumn, String textColumn, String typeColumn) {
        public TableMapping {
            validate(table);
            validate(codeColumn);
            validate(textColumn);
            if (typeColumn != null && !typeColumn.isBlank()) { validate(typeColumn); }
        }

        private static void validate(String name) {
            Assert.isTrue(name != null && name.matches("[A-Za-z_][A-Za-z0-9_]*"), "仅支持简单SQL标识符");
        }
    }
}
