package io.github.bytex0.desensitize.handler;

import io.github.bytex0.desensitize.annotation.Desensitize;
import io.github.bytex0.desensitize.annotation.DesensitizeFor;
import io.github.bytex0.desensitize.enums.DesensitizeType;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.Assert;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 脱敏策略(DesensitizeHandlerFactory)完整默认策略和Spring扩展
 *
 * @author bytex0
 * @since 2026-10-05 15:57:28
 */
public class DesensitizeHandlerFactory implements SmartInitializingSingleton {

    /**
     * 当前上下文Bean工厂
     */
    private final ListableBeanFactory beanFactory;

    /**
     * 可并发更新的自定义策略
     */
    private final Map<DesensitizeType, DesensitizeHandler> handlers = new ConcurrentHashMap<>();

    public DesensitizeHandlerFactory(ListableBeanFactory beanFactory) { this.beanFactory = beanFactory; }

    @Override
    public void afterSingletonsInstantiated() {
        beanFactory.getBeansOfType(DesensitizeHandler.class).values().forEach(handler -> {
            DesensitizeFor annotation = AnnotatedElementUtils.findMergedAnnotation(AopUtils.getTargetClass(handler), DesensitizeFor.class);
            if (annotation != null) {
                registerHandler(annotation.value(), handler);
            }
        });
    }

    public void registerHandler(DesensitizeType type, DesensitizeHandler handler) {
        handlers.put(type, handler);
    }

    public String mask(String value, Desensitize annotation) {
        if (value == null || value.isEmpty()) { return value; }
        Assert.isTrue(!annotation.maskChar().isEmpty() && annotation.maskChar().length() <= 8, "脱敏字符长度必须在1至8之间");
        if (annotation.startIndex() != 0 || annotation.endIndex() != -1) {
            return maskRange(value, annotation.startIndex(), annotation.endIndex(), annotation.maskChar());
        }
        if (annotation.type() == DesensitizeType.CUSTOM) {
            Assert.isTrue(annotation.handler() != DesensitizeHandler.class, "CUSTOM必须指定处理器Bean类型");
            return beanFactory.getBean(annotation.handler()).desensitize(value, annotation);
        }
        DesensitizeHandler handler = handlers.get(annotation.type());
        return handler == null ? builtin(value, annotation.type(), annotation.maskChar()) : handler.desensitize(value, annotation);
    }

    public String mask(String value, DesensitizeType type) {
        return value == null || value.isEmpty() ? value : builtin(value, type, "*");
    }

    public String maskRange(String value, int startIndex, int endIndex, String token) {
        Assert.hasLength(token, "脱敏字符不能为空");
        int length = value.codePointCount(0, value.length());
        int start = Math.max(0, Math.min(length, startIndex < 0 ? length + startIndex : startIndex));
        int end = endIndex == -1 ? length : Math.max(0, Math.min(length, endIndex < 0 ? length + endIndex : endIndex));
        Assert.isTrue(start < end, "脱敏范围必须覆盖至少一个字符");
        return value.substring(0, value.offsetByCodePoints(0, start)) + token.repeat(end - start)
                + value.substring(value.offsetByCodePoints(0, end));
    }

    private String keep(String value, int left, int right, String token) {
        int length = value.codePointCount(0, value.length());
        return length <= left + right ? token.repeat(length) : maskRange(value, left, length - right, token);
    }

    private String builtin(String value, DesensitizeType type, String token) {
        return switch (type) {
            case PHONE -> keep(value, 3, 4, token);
            case NAME -> keep(value, 1, 0, token);
            case ID_CARD, BANK_CARD -> keep(value, 4, 4, token);
            case ADDRESS -> keep(value, 6, 0, token);
            case CAR_NUMBER, PASSPORT -> keep(value, 2, 2, token);
            case FIXED_PHONE -> keep(value, 3, 2, token);
            case EMAIL -> {
                int at = value.indexOf('@');
                yield at > 0 ? keep(value.substring(0, at), 1, 0, token) + value.substring(at)
                        : keep(value, 0, 0, token);
            }
            case IPV4 -> maskIp(value, token);
            case DOMAIN -> maskDomain(value, token);
            case CUSTOM -> throw new IllegalArgumentException("CUSTOM必须通过字段注解指定处理器");
            default -> keep(value, 0, 0, token);
        };
    }

    private String maskIp(String value, String token) {
        String[] parts = value.split("\\.", -1);
        if (parts.length == 4) {
            try {
                for (String part : parts) {
                    int number = Integer.parseInt(part);
                    if (number < 0 || number > 255) { return keep(value, 0, 0, token); }
                }
                return parts[0] + "." + token + "." + token + "." + parts[3];
            } catch (NumberFormatException ignored) {
                return keep(value, 0, 0, token);
            }
        }
        return keep(value, 0, 0, token);
    }

    private String maskDomain(String value, String token) {
        try {
            URI uri = URI.create("http://" + value);
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || !uri.getPath().isEmpty()
                    || uri.getQuery() != null || uri.getFragment() != null) {
                return keep(value, 0, 0, token);
            }
            int dot = host.indexOf('.');
            if (dot < 1) { return keep(value, 0, 0, token); }
            if (host.matches("[0-9.]+")) {
                return maskIp(host, token) + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
            }
            return token.repeat(dot) + host.substring(dot) + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException exception) {
            return keep(value, 0, 0, token);
        }
    }
}
