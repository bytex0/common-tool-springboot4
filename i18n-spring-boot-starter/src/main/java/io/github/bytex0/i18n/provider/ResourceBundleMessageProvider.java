package io.github.bytex0.i18n.provider;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 资源消息(ResourceBundleMessageProvider)有界缓存及标准语言回退
 *
 * @author bytex0
 * @since 2026-10-05 15:49:14
 */
public class ResourceBundleMessageProvider implements I18nMessageProvider {

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
        @Override
        public List<String> getFormats(String baseName) { return FORMAT_PROPERTIES; }

        @Override
        public long getTimeToLive(String baseName, Locale locale) { return TTL_DONT_CACHE; }

        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) { return null; }
    };

    public ResourceBundleMessageProvider(String basename) {
        this(basename, 3600, Thread.currentThread().getContextClassLoader());
    }

    public ResourceBundleMessageProvider(String basename, int cacheSeconds, ClassLoader classLoader) {
        Assert.hasText(basename, "i18n.basename不能为空");
        Assert.isTrue(cacheSeconds >= -1, "i18n.cache-seconds必须大于等于-1");
        this.basename = basename.replace('/', '.');
        this.classLoader = classLoader == null ? getClass().getClassLoader() : classLoader;
        Caffeine<Object, Object> builder = Caffeine.newBuilder().maximumSize(256);
        if (cacheSeconds >= 0) {
            builder.expireAfterWrite(Duration.ofSeconds(cacheSeconds));
        }
        this.cache = builder.build();
    }

    @Override
    public Map<String, String> getMessages(Locale locale) {
        return cache.get(locale, key -> {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle(basename, key, classLoader, control);
                Map<String, String> values = new HashMap<>();
                bundle.keySet().forEach(code -> values.put(code, bundle.getString(code)));
                return Map.copyOf(values);
            } catch (MissingResourceException exception) {
                return Map.of();
            }
        });
    }

    @Override
    public void refresh() {
        cache.invalidateAll();
    }
}
