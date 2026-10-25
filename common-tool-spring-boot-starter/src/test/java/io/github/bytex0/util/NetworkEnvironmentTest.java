package io.github.bytex0.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网络地址环境覆盖测试，使用独立 JVM 验证真实环境变量优先级，不修改测试进程全局环境。
 *
 * @author bytex0
 * @since 2026-10-25 10:45:11
 */
class NetworkEnvironmentTest {

    /**
     * 单个探测进程的退出等待上限，单位秒。
     */
    private static final int PROCESS_TIMEOUT_SECONDS = 10;

    /**
     * 验证环境变量优先于 JVM 属性、空环境回退，以及 IPv6 地址和静态常量的一致性。
     *
     * @throws IOException 启动或读取探测进程失败
     * @throws InterruptedException 当前测试被中断
     */
    @Test
    void preservesEnvironmentAndPropertyPrecedence() throws IOException, InterruptedException {
        assertThat(probe(null)).isEqualTo("192.0.2.1|192.0.2.1|192.0.2.1");
        assertThat(probe("")).isEqualTo("192.0.2.1|192.0.2.1|192.0.2.1");
        assertThat(probe("192.0.2.2")).isEqualTo("192.0.2.2|192.0.2.2|192.0.2.2");
        assertThat(probe("2001:db8::1")).isEqualTo("2001:db8::1|2001:db8::1|2001:db8::1");
    }

    /**
     * 启动隔离 JVM 探测；失败和超时都会终止本次创建的子进程。
     *
     * @param environmentIp 环境地址，null 表示删除该环境变量
     * @return 属性、动态地址、初始化地址组成的结果
     * @throws IOException 启动或读取失败
     * @throws InterruptedException 等待被中断
     */
    private String probe(String environmentIp) throws IOException, InterruptedException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(java, "-DSERVER_IP=192.0.2.1", "-cp", classPath,
                AddressProbe.class.getName());
        builder.environment().remove("SERVER_IP");
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        if (environmentIp != null) {
            builder.environment().put("SERVER_IP", environmentIp);
        }
        Process process = builder.redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            assertThat(process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } finally {
            process.destroyForcibly();
            process.getInputStream().close();
            process.getOutputStream().close();
            process.getErrorStream().close();
        }
    }

    /**
     * 独立 JVM 的固定地址探测入口，不连接网络、不读取其他环境变量。
     *
     * @author bytex0
     * @since 2026-10-25 10:45:11
     */
    public static final class AddressProbe {

        /**
         * 输出固定三个字段供父进程断言。
         *
         * @param args 未使用的进程参数
         */
        public static void main(String[] args) {
            System.out.println(NetworkUtil.getSystemProperty("SERVER_IP") + "|"
                    + NetworkUtil.getLocalIp() + "|" + NetworkUtil.LOCAL_SERVER_IP);
        }
    }
}
