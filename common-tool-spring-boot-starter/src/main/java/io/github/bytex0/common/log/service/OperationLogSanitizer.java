package io.github.bytex0.common.log.service;

import io.github.bytex0.common.log.config.OperationLogProperties;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 操作日志参数有界捕获与敏感字段过滤，先限制序列化输出，再解析和过滤 JSON。
 *
 * @author bytex0
 * @since 2026-10-06 18:55:22
 */
public class OperationLogSanitizer {

    /**
     * 内置不可关闭的敏感字段片段。
     */
    private static final Set<String> REQUIRED_FIELDS = Set.of("password", "passwd", "pwd", "secret", "token", "accesskey",
            "apikey", "privatekey", "auth", "authorization", "cookie", "credential", "signature", "密码", "密钥", "令牌");

    /**
     * 字段名称归一化，保留各语言字母和数字。
     */
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]");

    /**
     * 敏感字段替换标记。
     */
    private static final String MASK = "***";

    /**
     * 基础设施对象的替换标记。
     */
    private static final String EXCLUDED = "[infrastructure]";

    /**
     * 明确表示无法安全捕获的 JSON 模型。
     */
    private static final Map<String, String> OMITTED = Map.of("_capture", "omitted");

    /**
     * 最大允许配置的捕获字符数。
     */
    private static final int MAX_PAYLOAD_LIMIT = 1024 * 1024;

    /**
     * 最小字符限制，保证省略标记可以正常返回。
     */
    private static final int MIN_PAYLOAD_LIMIT = 128;

    /**
     * 最深捕获层级上限。
     */
    private static final int MAX_DEPTH_LIMIT = 100;

    /**
     * 本实例捕获字符数限制。
     */
    private final int maxLength;

    /**
     * 不可变敏感字段匹配集合。
     */
    private final Set<String> sensitiveNames;

    /**
     * 独立不可变映射器，不引用业务自定义流读取序列化器。
     */
    private final JsonMapper mapper;

    /**
     * 使用配置构建安全捕获器。
     *
     * @param properties 容量与额外敏感字段配置
     */
    public OperationLogSanitizer(OperationLogProperties properties) {
        maxLength = properties.getMaxPayloadLength();
        int depth = properties.getMaxCaptureDepth();
        if (maxLength < MIN_PAYLOAD_LIMIT || maxLength > MAX_PAYLOAD_LIMIT || depth <= 0 || depth > MAX_DEPTH_LIMIT) {
            throw new IllegalArgumentException("Invalid operation log capture limits");
        }
        Set<String> fields = new HashSet<>(REQUIRED_FIELDS);
        Objects.requireNonNull(properties.getSensitiveFields(), "sensitiveFields")
                .forEach(name -> fields.add(normalize(Objects.requireNonNull(name, "sensitive field"))));
        fields.remove("");
        sensitiveNames = Set.copyOf(fields);
        JsonFactory factory = JsonFactory.builder().streamWriteConstraints(
                StreamWriteConstraints.builder().maxNestingDepth(depth).build()).build();
        mapper = JsonMapper.builder(factory).build();
    }

    /**
     * 预过滤参数名和基础设施对象，保留原参数数组结构。
     *
     * @param method 实际业务方法
     * @param arguments 参数数组
     * @param accessor 可选 Web 适配器
     * @return 预过滤参数数组，不修改原参数
     */
    public Object[] prepareArguments(Method method, Object[] arguments, OperationRequestAccessor accessor) {
        Parameter[] parameters = method.getParameters();
        Object[] result = new Object[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            Object value = arguments[index];
            boolean named = index < parameters.length && parameters[index].isNamePresent();
            if ((named && sensitive(parameters[index].getName())) || (!named && value instanceof CharSequence)) {
                result[index] = MASK;
            } else if (excluded(value) || accessor.excludeArgument(value)) {
                result[index] = EXCLUDED;
            } else {
                result[index] = value;
            }
        }
        return result;
    }

    /**
     * 捕获并过滤数据，超限、循环引用或序列化失败返回明确的省略 JSON，不抛出业务异常。
     *
     * @param value 数据，可为空
     * @return 有界、过滤后的 JSON
     */
    public String capture(Object value) {
        try {
            String raw = writeBounded(excluded(value) ? EXCLUDED : value);
            JsonNode tree = mapper.readTree(raw);
            redact(tree);
            return writeBounded(tree);
        } catch (RuntimeException exception) {
            return mapper.writeValueAsString(OMITTED);
        }
    }

    /**
     * 写入受限缓冲区，避免先构造无限长 JSON 字符串再截断。
     *
     * @param value 数据
     * @return 完整 JSON
     */
    private String writeBounded(Object value) {
        LimitedWriter writer = new LimitedWriter(maxLength);
        mapper.writeValue(writer, value);
        return writer.content();
    }

    /**
     * 递归替换敏感字段，深度已由序列化约束限制。
     *
     * @param node JSON 节点
     */
    private void redact(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (String name : List.copyOf(object.propertyNames())) {
                if (sensitive(name)) {
                    object.put(name, MASK);
                } else {
                    redact(object.get(name));
                }
            }
        } else if (node != null && node.isArray()) {
            for (JsonNode child : node) {
                redact(child);
            }
        }
    }

    /**
     * 判断字段是否命中内置或额外的敏感片段。
     *
     * @param name 字段名称
     * @return 是否需要隐藏
     */
    private boolean sensitive(String name) {
        String normalized = normalize(name);
        return sensitiveNames.stream().anyMatch(normalized::contains);
    }

    /**
     * 归一化字段名称。
     *
     * @param name 字段名
     * @return 去分隔符并转小写的名称
     */
    private String normalize(String name) {
        return SEPARATORS.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    /**
     * 排除不应读取或展开内部状态的基础设施对象。
     *
     * @param value 对象
     * @return 是否为基础设施
     */
    private boolean excluded(Object value) {
        return value instanceof InputStream || value instanceof OutputStream || value instanceof Reader
                || value instanceof Writer || value instanceof Resource || value instanceof File || value instanceof Path
                || value instanceof Thread || value instanceof ClassLoader || value instanceof Executor
                || value instanceof Future<?>;
    }

    /**
     * 固定最大容量的内存 Writer，只在本次捕获中使用。
     *
     * @author bytex0
     * @since 2026-10-06 18:55:22
     */
    private static final class LimitedWriter extends Writer {

        /**
         * 最大可写字符数。
         */
        private final int limit;

        /**
         * 当前捕获文本。
         */
        private final StringBuilder buffer = new StringBuilder();

        /**
         * 创建容量受限的 Writer。
         *
         * @param limit 最大字符数
         */
        private LimitedWriter(int limit) {
            this.limit = limit;
        }

        /**
         * 只在容量内追加字符，超限立即停止序列化。
         *
         * @param characters 字符数组
         * @param offset 起点
         * @param length 字符数量
         * @throws IOException 超过容量
         */
        @Override
        public void write(char[] characters, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, characters.length);
            if (length > limit - buffer.length()) {
                throw new IOException("Operation log capture limit exceeded");
            }
            buffer.append(characters, offset, length);
        }

        /**
         * 内存 Writer 无外部资源需要刷新。
         */
        @Override
        public void flush() {
        }

        /**
         * 内存 Writer 无外部资源需要关闭，保留内容供调用方读取。
         */
        @Override
        public void close() {
        }

        /**
         * 获取完整捕获结果。
         *
         * @return 捕获文本
         */
        private String content() {
            return buffer.toString();
        }
    }
}
