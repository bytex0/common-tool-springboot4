package io.github.bytex0.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Objects;

/**
 * 本地地址与请求地址工具，默认不信任客户端自行提供的代理头。
 *
 * @author bytex0
 * @since 2026-10-06 14:54:20
 */
public final class NetworkUtil {

    /**
     * 原版公开本地地址常量，在类初始化时获取一次；动态读取使用 getLocalIp。
     */
    public static final String LOCAL_SERVER_IP = getLocalIp();

    /**
     * 本地地址覆盖变量名称。
     */
    private static final String SERVER_IP = "SERVER_IP";

    /**
     * 保留原公开无参构造入口。
     */
    public NetworkUtil() {
    }

    /**
     * 优先读取 SERVER_IP，否则选择非回环 IPv4，再回退到其他非回环地址或回环地址。
     *
     * @return 本机地址
     */
    public static String getLocalIp() {
        String configured = getSystemProperty(SERVER_IP);
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        try {
            InetAddress fallback = null;
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                        if (address instanceof Inet4Address) {
                            return address.getHostAddress();
                        }
                        fallback = address;
                    }
                }
            }
            return fallback == null ? InetAddress.getLoopbackAddress().getHostAddress() : fallback.getHostAddress();
        } catch (SocketException exception) {
            throw new IllegalStateException("Cannot inspect local network addresses", exception);
        }
    }

    /**
     * 保留环境变量优先于 JVM 属性的原顺序，空环境变量视为未设置。
     *
     * @param key 属性名称
     * @return 配置值，未设置时为空
     */
    public static String getSystemProperty(String key) {
        String value = System.getenv(key);
        return value == null || value.isEmpty() ? System.getProperty(key) : value;
    }

    /**
     * 获取实际对端地址，默认不信任 X-Forwarded-For 或 X-Real-IP。
     *
     * @param request Servlet 请求
     * @return 对端地址
     */
    public static String getClientIp(HttpServletRequest request) {
        return getClientIp(request, false);
    }

    /**
     * 显式信任已受控代理时，恢复原代理头提取能力。
     * 调用方必须先验证对端属于可信代理，不能直接把用户参数作为此开关。
     *
     * @param request Servlet 请求
     * @param trustedProxy 是否已经确认对端为可信代理
     * @return 代理头首个有效地址或实际对端地址
     */
    public static String getClientIp(HttpServletRequest request, boolean trustedProxy) {
        Objects.requireNonNull(request, "request");
        if (trustedProxy) {
            for (String header : new String[]{"X-Forwarded-For", "X-Real-IP"}) {
                String value = request.getHeader(header);
                if (value != null && !value.isBlank()) {
                    String first = value.split(",", 2)[0].trim();
                    if (!first.isEmpty() && !"unknown".equalsIgnoreCase(first)) {
                        return first;
                    }
                }
            }
        }
        return request.getRemoteAddr();
    }
}
