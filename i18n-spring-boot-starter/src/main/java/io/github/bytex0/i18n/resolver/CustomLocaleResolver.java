package io.github.bytex0.i18n.resolver;

import io.github.bytex0.i18n.provider.I18nManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * 保留原语言解析器入口，以 Spring 标准实现处理带权重的 Accept-Language。
 *
 * @author bytex0
 * @since 2026-10-06 00:50:56
 */
public class CustomLocaleResolver extends AcceptHeaderLocaleResolver {

    /**
     * 配置没有语言请求头时的默认 Locale；语言不能通过 setLocale 修改。
     *
     * @param defaultLocale 默认语言标签，支持下划线和连字符
     */
    public CustomLocaleResolver(String defaultLocale) {
        setDefaultLocale(I18nManager.parseLocale(defaultLocale));
    }

    /**
     * 原单值下划线请求头先转换为 Locale，其余请求交给 Servlet/Spring 的标准权重协商。
     *
     * @param request 当前请求
     * @return 实际选择的语言
     */
    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (header == null || !header.contains("_") || header.contains(",") || header.contains(";")) {
            return super.resolveLocale(request);
        }
        Locale locale;
        try {
            locale = I18nManager.parseLocale(header.strip());
        } catch (IllegalArgumentException exception) {
            Locale fallback = getDefaultLocale();
            return fallback == null ? request.getLocale() : fallback;
        }
        return super.resolveLocale(new HttpServletRequestWrapper(request) {
            /**
             * 返回原格式请求头解析出的首选语言。
             *
             * @return 首选语言
             */
            @Override
            public Locale getLocale() {
                return locale;
            }

            /**
             * 保留 Spring 支持语言列表的选择规则。
             *
             * @return 原单值请求头中的语言枚举
             */
            @Override
            public Enumeration<Locale> getLocales() {
                return Collections.enumeration(List.of(locale));
            }
        });
    }
}
