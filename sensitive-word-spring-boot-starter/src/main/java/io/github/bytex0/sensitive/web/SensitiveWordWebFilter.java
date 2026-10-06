package io.github.bytex0.sensitive.web;

import io.github.bytex0.sensitive.core.HandleType;
import io.github.bytex0.sensitive.core.SensitiveWordException;
import io.github.bytex0.sensitive.core.SensitiveWordOperations;
import io.github.bytex0.sensitive.core.SensitiveWordOptions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 请求过滤(SensitiveWordWebFilter)有界处理 Servlet MVC 参数和文本，JSON 按结构改写而非替换序列化正文。
 *
 * @author linshiqiang
 * @since 2026-10-06 09:10:08
 */
public class SensitiveWordWebFilter extends OncePerRequestFilter implements Ordered {

    /**
     * 共享词库服务。
     */
    private final SensitiveWordOperations operations;

    /**
     * 默认文本匹配与替换配置。
     */
    private final SensitiveWordOptions options;

    /**
     * 纳入范围的 Ant 路径，空列表表示全部。
     */
    private final List<String> includes;

    /**
     * 优先排除的 Ant 路径。
     */
    private final List<String> excludes;

    /**
     * 指定参数名，空表示全部。
     */
    private final Set<String> parameters;

    /**
     * 是否检查文本及 JSON 请求体。
     */
    private final boolean checkBody;

    /**
     * 当前请求处理策略。
     */
    private final HandleType handling;

    /**
     * 请求体字节数上限。
     */
    private final int maxBodyBytes;

    /**
     * JSON 遍历深度上限。
     */
    private final int maxDepth;

    /**
     * 不加载业务出站序列化模块的结构解析器。
     */
    private final JsonMapper json = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    /**
     * 可信路径模式解析器。
     */
    private final AntPathMatcher paths = new AntPathMatcher();

    /**
     * 使用与 Servlet 应用相同的路径解码规则。
     */
    private final UrlPathHelper pathHelper = new UrlPathHelper();

    /**
     * 固定过滤范围和资源限制，词库仍支持动态更新。
     *
     * @param operations 业务组件
     * @param options 配置
     */
    public SensitiveWordWebFilter(SensitiveWordOperations operations, SensitiveWordOptions options) {
        this.operations = operations;
        this.options = options;
        SensitiveWordOptions.Web web = options.getWeb();
        includes = List.copyOf(web.getUrlPatterns());
        excludes = List.copyOf(web.getExcludePatterns());
        parameters = Set.copyOf(web.getCheckParams());
        checkBody = web.isCheckBody();
        handling = web.getHandleType();
        maxBodyBytes = web.getMaxBodyBytes();
        maxDepth = web.getMaxJsonDepth();
        Assert.notNull(handling, "Web处理方式不能为空");
        Assert.isTrue(maxBodyBytes > 0 && maxBodyBytes < Integer.MAX_VALUE && maxDepth > 0, "Web资源上限不合法");
    }

    /**
     * 排除路径优先；只在初始同步 MVC 请求阶段读取请求体。
     *
     * @param request 当前请求
     * @return 是否跳过
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathHelper.getPathWithinApplication(request);
        return excludes.stream().anyMatch(pattern -> paths.match(pattern, path))
                || !includes.isEmpty() && includes.stream().noneMatch(pattern -> paths.match(pattern, path));
    }

    /**
     * 在进入控制器前完成处理；失败仅返回状态和通用消息，不返回敏感正文。
     *
     * @param request 请求
     * @param response 响应
     * @param chain 后续过滤器
     * @throws ServletException Servlet 处理失败
     * @throws IOException 请求/响应 I/O 失败
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SanitizedRequest sanitized;
        try {
            Map<String, String[]> values = new LinkedHashMap<>();
            request.getParameterMap().forEach((name, original) -> {
                String[] copy = original.clone();
                if (parameters.isEmpty() || parameters.contains(name)) {
                    for (int index = 0; index < copy.length; index++) {
                        copy[index] = process(copy[index]);
                    }
                }
                values.put(name, copy);
            });
            Charset charset = request.getCharacterEncoding() == null ? StandardCharsets.UTF_8
                    : Charset.forName(request.getCharacterEncoding());
            byte[] body = null;
            MediaType type = request.getContentType() == null ? null : MediaType.parseMediaType(request.getContentType());
            boolean structured = type != null && (type.getSubtype().equals("json") || type.getSubtype().endsWith("+json"));
            boolean text = type != null && type.getType().equals("text");
            if (checkBody && (structured || text)) {
                body = request.getInputStream().readNBytes(maxBodyBytes + 1);
                if (body.length > maxBodyBytes) {
                    fail(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                    return;
                }
                String original = new String(body, charset);
                if (structured && !original.isBlank()) {
                    JsonNode tree = json.readTree(original);
                    AtomicBoolean changed = new AtomicBoolean();
                    JsonNode rewritten = rewrite(tree, changed, 0);
                    if (changed.get()) {
                        body = json.writeValueAsString(rewritten).getBytes(charset);
                    }
                } else if (text) {
                    body = process(original).getBytes(charset);
                }
                Assert.isTrue(body.length <= maxBodyBytes, "处理后的请求体超过上限");
            }
            sanitized = new SanitizedRequest(request, values, body, charset);
        } catch (SensitiveWordException | IllegalArgumentException | JacksonException failure) {
            fail(response, HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        chain.doFilter(sanitized, response);
    }

    /**
     * 统一调用带白名单的处理策略。
     *
     * @param value 输入文本
     * @return 处理后文本
     */
    private String process(String value) {
        return operations.process(value, handling, options.getMatchType(), options.getReplaceChar(), options.getExceptionMessage());
    }

    /**
     * 只改写 JSON 字符串值，保留键、数值、布尔、数组与对象层级。
     *
     * @param node 当前节点
     * @param changed 是否存在实际修改
     * @param depth 当前深度
     * @return 处理后的节点
     */
    private JsonNode rewrite(JsonNode node, AtomicBoolean changed, int depth) {
        Assert.isTrue(depth <= maxDepth, "JSON深度超过上限");
        if (node.isString()) {
            String original = node.stringValue();
            String processed = process(original);
            if (!original.equals(processed)) {
                changed.set(true);
                return JsonNodeFactory.instance.stringNode(processed);
            }
        } else if (node instanceof ObjectNode object) {
            for (String name : List.copyOf(object.propertyNames())) {
                object.set(name, rewrite(object.get(name), changed, depth + 1));
            }
        } else if (node instanceof ArrayNode array) {
            for (int index = 0; index < array.size(); index++) {
                array.set(index, rewrite(array.get(index), changed, depth + 1));
            }
        }
        return node;
    }

    /**
     * 输出结构化错误，不序列化异常和词条内容。
     *
     * @param response 响应
     * @param status 状态码
     * @throws IOException 写响应失败
     */
    private void fail(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), Map.of("code", status, "message", "请求内容不符合过滤规则"));
    }

    /**
     * 在常规 MVC 处理前执行，不替代认证授权。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
