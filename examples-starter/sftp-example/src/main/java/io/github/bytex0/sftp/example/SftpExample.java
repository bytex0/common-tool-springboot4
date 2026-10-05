package io.github.bytex0.sftp.example;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import io.github.bytex0.sftp.SftpProperties;
import io.github.bytex0.sftp.SftpTemplate;
import io.github.bytex0.sftp.core.JschConnectionPool;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 仅操作测试上传目录的 SFTP 流式接口。
 *
 * @author bytex0
 * @since 2026-10-05 19:47:49
 */
@SpringBootApplication
public class SftpExample {

    /**
     * 启动真实集成 SFTP Starter 的示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SftpExample.class, args);
    }

    /**
     * 普通示例接口，测试 SSH 服务进程不装配该控制器。
     *
     * @author bytex0
     * @since 2026-10-05 19:47:49
     */
    @RestController
    @Profile("!sftp-fixture")
    static class Controller {

        /**
         * 实际操作模板。
         */
        private final SftpTemplate template;

        /**
         * 实际命名池管理器。
         */
        private final JschConnectionPool pools;

        /**
         * 固定测试服务配置，不允许请求提供远端地址或凭据。
         */
        private final SftpProperties properties;

        /**
         * 注入三个由 Starter 提供的组件。
         *
         * @param template 操作模板
         * @param pools 池管理器
         * @param properties 测试配置
         */
        Controller(SftpTemplate template, JschConnectionPool pools, SftpProperties properties) {
            this.template = template;
            this.pools = pools;
            this.properties = properties;
        }

        /**
         * 上传测试文件，Servlet 输入流仍由容器关闭。
         *
         * @param name 文件名
         * @param request HTTP 请求
         * @return 借用归还后的活动数
         * @throws Exception 上传失败
         */
        @PutMapping("/api/sftp/file/{name}")
        Map<String, Object> upload(@PathVariable String name, HttpServletRequest request) throws Exception {
            template.upload(path(name), request.getInputStream());
            return Map.of("code", 0, "data", Map.of("active", template.activeConnections()));
        }

        /**
         * 下载测试内容，不缓冲整个文件。
         *
         * @param name 文件名
         * @param response HTTP 输出
         * @throws Exception 下载失败
         */
        @GetMapping("/api/sftp/file/{name}")
        void download(@PathVariable String name, HttpServletResponse response) throws Exception {
            String remote = path(name);
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            template.download(remote, response.getOutputStream());
        }

        /**
         * 删除本次测试文件。
         *
         * @param name 文件名
         * @return 操作结果
         * @throws Exception 删除失败
         */
        @DeleteMapping("/api/sftp/file/{name}")
        Map<String, Object> delete(@PathVariable String name) throws Exception {
            template.delete(path(name));
            return Map.of("code", 0);
        }

        /**
         * 查询测试目录及当前活动借用。
         *
         * @return 名称列表与活动数
         * @throws Exception 目录查询失败
         */
        @GetMapping("/api/sftp/files")
        Map<String, Object> files() throws Exception {
            return Map.of("code", 0, "data", Map.of("names", template.list("upload"),
                    "active", template.activeConnections()));
        }

        /**
         * 基于既有测试身份创建一个单连接命名池。
         *
         * @param name 非默认池名称
         * @return 当前名称集合
         */
        @PostMapping("/api/sftp/pools/{name}")
        Map<String, Object> createPool(@PathVariable String name) {
            validatePoolName(name);
            SftpProperties configured = properties.toBuilder().maxTotal(1).maxIdle(1).minIdle(0)
                    .maxWait(Duration.ofMillis(100)).evictionInterval(Duration.ZERO).build();
            pools.buildPool(name, configured);
            return poolNames();
        }

        /**
         * 列举已注册的测试池。
         *
         * @return 不包含连接配置或密码的名称集合
         */
        @GetMapping("/api/sftp/pools")
        Map<String, Object> poolNames() {
            return Map.of("code", 0, "data", Map.of("names", pools.names()));
        }

        /**
         * 查询命名池计数与生效容量。
         *
         * @param name 池名称
         * @return 普通 JSON 池统计
         */
        @GetMapping("/api/sftp/pools/{name}")
        Map<String, Object> poolStats(@PathVariable String name) {
            validatePoolName(name);
            GenericObjectPool<ChannelSftp> pool = pools.getPool(name);
            Assert.notNull(pool, "测试池不存在");
            return Map.of("code", 0, "data", Map.of("active", pool.getNumActive(), "idle", pool.getNumIdle(),
                    "maxTotal", pool.getMaxTotal(), "maxIdle", pool.getMaxIdle(), "minIdle", pool.getMinIdle()));
        }

        /**
         * 关闭并移除本次命名池，不允许关闭默认文件测试池。
         *
         * @param name 池名称
         * @return 当前名称集合
         */
        @DeleteMapping("/api/sftp/pools/{name}")
        Map<String, Object> closePool(@PathVariable String name) {
            validatePoolName(name);
            pools.close(name);
            return poolNames();
        }

        /**
         * 用原借还 API 重命名测试文件，验证通道成品操作能力。
         *
         * @param name 池名称
         * @param source 原文件名
         * @param target 新文件名
         * @return 操作结果
         * @throws Exception 重命名失败
         */
        @PostMapping("/api/sftp/pools/{name}/rename")
        Map<String, Object> rename(@PathVariable String name, @RequestParam String source, @RequestParam String target)
                throws Exception {
            validatePoolName(name);
            String from = path(source);
            String to = path(target);
            ChannelSftp channel = pools.borrowObject(name);
            try {
                channel.rename(from, to);
            } finally {
                pools.returnObject(name, channel);
            }
            return Map.of("code", 0);
        }

        /**
         * 验证作用域回调与工作目录重置。
         *
         * @param name 池名称
         * @param change 是否改变本次通道的工作目录
         * @return 当前调用观察到的目录
         * @throws Exception 远端操作失败
         */
        @GetMapping("/api/sftp/pools/{name}/directory")
        Map<String, Object> directory(@PathVariable String name, @RequestParam(defaultValue = "false") boolean change)
                throws Exception {
            validatePoolName(name);
            String directory = template.execute(name, channel -> {
                if (change) {
                    channel.cd("upload");
                }
                return channel.pwd();
            });
            return Map.of("code", 0, "data", Map.of("directory", directory));
        }

        /**
         * 验证断线归还会销毁关联 Session，而不是遗留活动连接。
         *
         * @param name 池名称
         * @return Session 是否仍连接
         * @throws Exception 通道操作失败
         */
        @PostMapping("/api/sftp/pools/{name}/disconnect")
        Map<String, Object> disconnect(@PathVariable String name) throws Exception {
            validatePoolName(name);
            ChannelSftp channel = pools.borrowObject(name);
            Session session;
            try {
                session = channel.getSession();
                channel.disconnect();
            } finally {
                pools.returnObject(name, channel);
            }
            return Map.of("code", 0, "data", Map.of("sessionConnected", session.isConnected(),
                    "active", template.activeConnections(name)));
        }

        /**
         * 持有通道一段有界时间，验证池耗尽与关闭期间的在途归还。
         *
         * @param name 池名称
         * @param delay 持有毫秒数，0 至 2000
         * @return 操作中观察到的目录
         * @throws Exception 获取或等待失败
         */
        @GetMapping("/api/sftp/pools/{name}/hold")
        Map<String, Object> hold(@PathVariable String name, @RequestParam(defaultValue = "800") long delay) throws Exception {
            validatePoolName(name);
            Assert.isTrue(delay >= 0 && delay <= 2000, "示例延时不合法");
            String directory = template.execute(name, channel -> {
                Thread.sleep(delay);
                return channel.pwd();
            });
            return Map.of("code", 0, "data", Map.of("directory", directory));
        }

        /**
         * 将输入限制在本次上传目录内，不接受路径分隔符。
         *
         * @param name 文件名
         * @return 远端相对路径
         */
        private String path(String name) {
            Assert.isTrue(name.matches("[a-z0-9-]{1,80}\\.bin"), "Invalid test filename");
            return "upload/" + name;
        }

        /**
         * 防止请求操作默认池或任意路径。
         *
         * @param name 池名称
         */
        private void validatePoolName(String name) {
            Assert.isTrue(name.matches("[a-z0-9-]{1,64}") && !JschConnectionPool.DEFAULT_KEY.equals(name), "Invalid pool name");
        }

        /**
         * 参数错误返回 JSON 400。
         *
         * @return 错误响应
         */
        @ExceptionHandler(IllegalArgumentException.class)
        ResponseEntity<Map<String, Integer>> invalid() {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(Map.of("code", 400));
        }

        /**
         * 池耗尽返回 JSON 429，不隐藏为无限等待。
         *
         * @return 容量拒绝响应
         */
        @ExceptionHandler(NoSuchElementException.class)
        ResponseEntity<Map<String, Integer>> exhausted() {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("code", 429));
        }

        /**
         * 仅暴露协议状态，不返回内部路径、连接配置或凭据。
         *
         * @param exception SFTP 业务异常
         * @return JSON 错误响应
         */
        @ExceptionHandler(SftpException.class)
        ResponseEntity<Map<String, Integer>> failed(SftpException exception) {
            int status = exception.id == ChannelSftp.SSH_FX_NO_SUCH_FILE ? 404 : 502;
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(Map.of("code", status));
        }
    }
}
