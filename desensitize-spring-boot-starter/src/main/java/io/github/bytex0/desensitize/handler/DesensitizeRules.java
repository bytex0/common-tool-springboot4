package io.github.bytex0.desensitize.handler;

import com.google.common.net.InetAddresses;
import io.github.bytex0.desensitize.enums.DesensitizeType;
import org.springframework.util.Assert;

import java.net.URI;

/**
 * 内置规则(DesensitizeRules)集中处理固定格式和码点边界，供原独立处理器复用。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:25:51
 */
final class DesensitizeRules {

    /**
     * 通用默认替换字符。
     */
    private static final String MASK = "*";

    /**
     * 原密码及全遮蔽策略的固定输出，不暴露密码长度。
     */
    private static final String FULL_MASK = "******";

    /**
     * 范围替换文本的码点上限。
     */
    private static final int MAX_MASK_LENGTH = 8;

    /**
     * 工具类不允许实例化。
     */
    private DesensitizeRules() {
    }

    /**
     * 分派明确的默认规则；无效输入采用全字符遮蔽，不使用原来的漏脱敏回退。
     *
     * @param value 文本
     * @param type 内置类型
     * @return 脱敏文本
     */
    static String apply(String value, DesensitizeType type) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return switch (type) {
            case PHONE -> value.matches("[0-9]{11}") ? keep(value, 3, 4, 4) : hide(value);
            case NAME -> length(value) == 2 ? keep(value, 1, 0, 1) : keep(value, 1, 1, 0);
            case ID_CARD -> value.matches("(?:[0-9]{15}|[0-9]{17}[0-9Xx])") ? keep(value, 4, 4, 0) : hide(value);
            case BANK_CARD -> value.matches("[0-9]{9,}") ? keep(value, 4, 4, 4) : hide(value);
            case ADDRESS -> keep(value, 6, 2, 4);
            case PASSWORD, MASK_ALL -> FULL_MASK;
            case CAR_NUMBER -> keep(value, 2, 1, 4);
            case PASSPORT, MILITARY_ID -> keep(value, 2, 2, 4);
            case CNAPS_CODE -> value.matches("[0-9]{12}") ? keep(value, 4, 4, 4) : hide(value);
            case EMAIL -> email(value);
            case FIXED_PHONE -> fixedPhone(value);
            case IPV4 -> ipv4(value);
            case IPV6 -> ipv6(value);
            case DOMAIN -> domain(value);
            case CUSTOM -> throw new IllegalArgumentException("CUSTOM需要指定处理器");
        };
    }

    /**
     * Unicode 码点范围替换，非法空范围明确失败。
     *
     * @param value 文本
     * @param startIndex 包含的起点
     * @param endIndex 不包含的终点，-1 特指末尾
     * @param token 替换文本
     * @return 脱敏文本
     */
    static String range(String value, int startIndex, int endIndex, String token) {
        validateToken(token);
        if (value == null || value.isEmpty()) {
            return value;
        }
        int size = length(value);
        int start = Math.max(0, Math.min(size, startIndex < 0 ? size + startIndex : startIndex));
        int end = endIndex == -1 ? size : Math.max(0, Math.min(size, endIndex < 0 ? size + endIndex : endIndex));
        Assert.isTrue(start < end, "脱敏范围必须覆盖至少一个字符");
        return value.substring(0, value.offsetByCodePoints(0, start)) + token.repeat(end - start)
                + value.substring(value.offsetByCodePoints(0, end));
    }

    /**
     * 校验所有策略共用的替换文本，即使自定义处理器不采用范围也不能配置空文本。
     *
     * @param token 替换文本
     */
    static void validateToken(String token) {
        Assert.hasLength(token, "脱敏字符不能为空");
        Assert.isTrue(length(token) <= MAX_MASK_LENGTH, "脱敏字符最多8个码点");
    }

    /**
     * 保留两端并使用固定数量星号，零表示按实际隐藏码点数量替换。
     *
     * @param value 文本
     * @param left 左侧保留码点数
     * @param right 右侧保留码点数
     * @param count 固定星号数，0 表示隐藏长度
     * @return 脱敏文本，太短时全部遮蔽
     */
    private static String keep(String value, int left, int right, int count) {
        int size = length(value);
        if (size <= left + right) {
            return hide(value);
        }
        return value.substring(0, value.offsetByCodePoints(0, left))
                + MASK.repeat(count == 0 ? size - left - right : count)
                + value.substring(value.offsetByCodePoints(0, size - right));
    }

    /**
     * 保留邮箱域和首个本地码点；单字符本地名也必须遮蔽。
     *
     * @param value 邮箱文本
     * @return 脱敏邮箱
     */
    private static String email(String value) {
        int at = value.indexOf('@');
        if (at <= 0 || at == value.length() - 1 || at != value.lastIndexOf('@')) {
            return hide(value);
        }
        String local = value.substring(0, at);
        return (length(local) == 1 ? MASK : keep(local, 1, 0, 4)) + value.substring(at);
    }

    /**
     * 固话保留区号和最后四位，非法格式不返回原文。
     *
     * @param value 固话文本
     * @return 脱敏文本
     */
    private static String fixedPhone(String value) {
        int separator = value.indexOf('-');
        if (separator > 0 && value.substring(0, separator).matches("[0-9]+")) {
            String number = value.substring(separator + 1);
            if (number.matches("[0-9]{5,}")) {
                return value.substring(0, separator + 1) + "****" + number.substring(number.length() - 4);
            }
        }
        return hide(value);
    }

    /**
     * IPv4 保留首末段以及可选端口，使用不触发 DNS 的解析检查。
     *
     * @param value IPv4 或 IPv4:port
     * @return 脱敏地址
     */
    private static String ipv4(String value) {
        URI uri = authority(value);
        if (uri == null || !InetAddresses.isInetAddress(uri.getHost()) || uri.getHost().contains(":")) {
            return hide(value);
        }
        String[] parts = uri.getHost().split("\\.");
        return parts[0] + ".*.*." + parts[3] + port(uri);
    }

    /**
     * IPv6 校验后仅保留首个显式段；压缩前缀没有可见段时全遮蔽。
     *
     * @param value IPv6 字面值
     * @return 脱敏地址
     */
    private static String ipv6(String value) {
        if (!value.contains(":") || !InetAddresses.isInetAddress(value)) {
            return hide(value);
        }
        int colon = value.indexOf(':');
        return colon > 0 ? value.substring(0, colon) + ":****" : FULL_MASK;
    }

    /**
     * 域名隐藏首段并保留其余域及端口，IP 域名按 IP 规则处理。
     *
     * @param value 主机名及可选端口
     * @return 脱敏域名
     */
    private static String domain(String value) {
        URI uri = authority(value);
        if (uri == null) {
            return hide(value);
        }
        if (InetAddresses.isInetAddress(uri.getHost())) {
            return ipv4(value);
        }
        int dot = uri.getHost().indexOf('.');
        return dot > 0 ? "****" + uri.getHost().substring(dot) + port(uri) : hide(value);
    }

    /**
     * 解析纯主机与端口，不允许凭据、路径、查询串或片段。
     *
     * @param value 原始地址
     * @return 合法 authority，非法时 null
     */
    private static URI authority(String value) {
        try {
            URI uri = URI.create("http://" + value);
            if (uri.getHost() != null && uri.getUserInfo() == null && uri.getPath().isEmpty()
                    && uri.getQuery() == null && uri.getFragment() == null
                    && uri.getPort() != 0 && uri.getPort() <= 65535) {
                return uri;
            }
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return null;
    }

    /**
     * 保留已验证的可选端口。
     *
     * @param uri 地址
     * @return 端口后缀或空串
     */
    private static String port(URI uri) {
        return uri.getPort() < 0 ? "" : ":" + uri.getPort();
    }

    /**
     * 隐藏每一个原始码点。
     *
     * @param value 文本
     * @return 等码点数量的星号
     */
    private static String hide(String value) {
        return MASK.repeat(length(value));
    }

    /**
     * 获取码点数，避免截断代理对。
     *
     * @param value 文本
     * @return 码点数量
     */
    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
