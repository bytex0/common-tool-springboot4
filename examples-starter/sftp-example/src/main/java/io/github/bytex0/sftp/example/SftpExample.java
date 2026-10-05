package io.github.bytex0.sftp.example;

import com.jcraft.jsch.SftpException;
import io.github.bytex0.sftp.SftpTemplate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅操作测试上传目录的 SFTP 流式接口。
 *
 * @author bytex0
 * @since 2026-10-05 19:47:49
 */
@SpringBootApplication
public class SftpExample {

    public static void main(String[] args) { SpringApplication.run(SftpExample.class, args); }

    /**
     * 普通示例接口，测试 SSH 服务进程不装配该控制器。
     *
     * @author bytex0
     * @since 2026-10-05 19:47:49
     */
    @RestController
    @Profile("!sftp-fixture")
    static class Controller {

    private final SftpTemplate template;

    Controller(SftpTemplate template) { this.template = template; }

    @PutMapping("/api/sftp/file/{name}")
    Map<String, Object> upload(@PathVariable String name, HttpServletRequest request) throws Exception {
        template.upload(path(name), request.getInputStream());
        return Map.of("code", 0, "data", Map.of("active", template.activeConnections()));
    }

    @GetMapping("/api/sftp/file/{name}")
    void download(@PathVariable String name, HttpServletResponse response) throws Exception {
        response.setContentType("application/octet-stream");
        template.download(path(name), response.getOutputStream());
    }

    @DeleteMapping("/api/sftp/file/{name}")
    Map<String, Object> delete(@PathVariable String name) throws Exception {
        template.delete(path(name));
        return Map.of("code", 0);
    }

    @GetMapping("/api/sftp/files")
    Map<String, Object> files() throws Exception {
        return Map.of("code", 0, "data", Map.of("names", template.list("upload"),
                "active", template.activeConnections()));
    }

    private String path(String name) {
        if (!name.matches("[a-z0-9-]{1,80}\\.bin")) {
            throw new IllegalArgumentException("Invalid test filename");
        }
        return "upload/" + name;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Integer> invalid() { return Map.of("code", 400); }

    @ExceptionHandler(SftpException.class)
    void failed(SftpException exception, HttpServletResponse response) throws Exception {
        int status = exception.id == 2 ? 404 : 502;
        response.setStatus(status);
        response.setContentType("application/json");
        response.getOutputStream().write(("{\"code\":" + status + "}").getBytes(StandardCharsets.UTF_8));
    }
    }
}
