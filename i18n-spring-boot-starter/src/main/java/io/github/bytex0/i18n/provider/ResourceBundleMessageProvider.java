package io.github.bytex0.i18n.provider;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.util.Assert;

import java.time.Duration;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.PropertyResourceBundle;
import java.util.Properties;
import java.util.Objects;

/**
 * 资源消息(ResourceBundleMessageProvider)有界缓存及标准语言回退
 *
 * @author bytex0
 * @since 2026-10-05 15:49:14
 */
public class ResourceBundleMessageProvider implements I18nMessageProvider {

    /**
     * 默认缓存时长，单位为秒。
     */
    private static final int DEFAULT_CACHE_SECONDS = 3600;

    /**
     * 防止任意语言标签无限占用资源缓存。
     */
    private static final int MAX_CACHED_LOCALES = 256;

    /**
     * 资源基础名称
     */
    private final String basename;

    /**
     * 当前提供器使用的类加载器
     */
    private final ClassLoader classLoader;

    /**
     * 当前提供器私有缓存，避免无限语言标签和全局缓存串扰
     */
    private final Cache<Locale, Map<String, String>> cache;

    /**
     * 禁用JDK全局缓存及机器默认语言回退
     */
    private final ResourceBundle.Control control = new ResourceBundle.Control() {
        /**
         * 仅加载 properties，不执行资源类。
         *
         * @param baseName 基础名称
         * @return properties 格式列表
         */
        @Override
        public List<String> getFormats(String baseName) {
            return FORMAT_PROPERTIES;
        }

        /**
         * 禁用 JDK 全局缓存，由当前提供器控制缓存时长。
         *
         * @param baseName 基础名称
         * @param locale 语言
         * @return 不缓存标记
         */
        @Override
        public long getTimeToLive(String baseName, Locale locale) {
            return TTL_DONT_CACHE;
        }

        /**
         * 禁止回退到运行机器的默认语言。
         *
         * @param baseName 基础名称
         * @param locale 请求语言
         * @return null
         */
        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            return null;
        }

        /**
         * 显式按 UTF-8 读取，避免 JVM 全局编码配置改变原组件语义。
         *
         * @param baseName 基础名称
         * @param locale 语言
         * @param format properties 格式
         * @param loader 调用方的类加载器
         * @param reload JDK 重载标志，连接缓存始终关闭
         * @return 资源包，不存在时为 null
         * @throws IOException 资源无法读取
         */
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format,
                                        ClassLoader loader, boolean reload) throws IOException {
            URL url = loader.getResource(toResourceName(toBundleName(baseName, locale), "properties"));
            if (url == null) {
                return null;
            }
            try (Reader reader = utf8(url)) {
                return new PropertyResourceBundle(reader);
            }
        }
    };

    /**
     * 保留原单参数构造方式。
     *
     * @param basename 资源基础名称
     */
    public ResourceBundleMessageProvider(String basename) {
        this(basename, DEFAULT_CACHE_SECONDS, Thread.currentThread().getContextClassLoader());
    }

    /**
     * 创建有界资源提供器，不接管类加载器的关闭责任。
     *
     * @param basename 非空资源基础名称
     * @param cacheSeconds -1 永久，0 不缓存，正数为缓存秒数
     * @param classLoader 资源类加载器，null 使用本组件类加载器
     */
    public ResourceBundleMessageProvider(String basename, int cacheSeconds, ClassLoader classLoader) {
        Assert.hasText(basename, "i18n.basename不能为空");
        Assert.isTrue(cacheSeconds >= -1, "i18n.cache-seconds必须大于等于-1");
        this.basename = basename.replace('/', '.');
        this.classLoader = classLoader == null ? getClass().getClassLoader() : classLoader;
        Caffeine<Object, Object> builder = Caffeine.newBuilder().maximumSize(MAX_CACHED_LOCALES);
        if (cacheSeconds >= 0) {
            builder.expireAfterWrite(Duration.ofSeconds(cacheSeconds));
        }
        this.cache = builder.build();
    }

    /**
     * 返回标准回退与原精确文件名合并后的快照，原文件的同名编码优先。
     *
     * @param locale 非空语言，不允许路径分隔符
     * @return 不可变消息映射
     */
    @Override
    public Map<String, String> getMessages(Locale locale) {
        Objects.requireNonNull(locale);
        String suffix = locale.toString();
        Assert.isTrue(!suffix.contains("/") && !suffix.contains("\\") && suffix.indexOf('\0') < 0,
                "语言不能包含资源路径分隔符");
        return cache.get(locale, this::loadMessages);
    }

    /**
     * 先执行标准语言回退，再覆盖原 basename_locale.properties 文件，保留 ROOT 下划线文件。
     *
     * @param locale 目标语言
     * @return 消息快照
     */
    private Map<String, String> loadMessages(Locale locale) {
        Map<String, String> values = new HashMap<>();
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(basename, locale, classLoader, control);
            bundle.keySet().forEach(code -> values.put(code, bundle.getString(code)));
        } catch (MissingResourceException exception) {
            if (exception.getCause() instanceof IOException cause) {
                throw new UncheckedIOException("无法读取国际化资源", cause);
            }
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            // 标准资源不存在时仍可能存在原 ROOT/带脚本标签的精确文件。
        }
        URL legacy = classLoader.getResource(basename.replace('.', '/') + "_" + locale + ".properties");
        if (legacy != null) {
            try (Reader reader = utf8(legacy)) {
                Properties properties = new Properties();
                properties.load(reader);
                properties.forEach((code, value) -> values.put(code.toString(), value.toString()));
            } catch (IOException exception) {
                throw new UncheckedIOException("无法读取原格式国际化资源", exception);
            }
        }
        return Map.copyOf(values);
    }

    /**
     * 清空本实例的派生缓存，下次读取重新加载，不清理其他类加载器的资源。
     */
    @Override
    public void refresh() {
        cache.invalidateAll();
    }

    /**
     * 创建必须由调用方关闭的 UTF-8 读取器。
     *
     * @param resource 资源 URL
     * @return 独占资源流的读取器
     * @throws IOException 资源打开失败
     */
    private static Reader utf8(URL resource) throws IOException {
        var connection = resource.openConnection();
        connection.setUseCaches(false);
        return new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
    }
}
