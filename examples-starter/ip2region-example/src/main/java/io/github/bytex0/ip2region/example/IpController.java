package io.github.bytex0.ip2region.example;

import io.github.bytex0.ip2region.core.Ip2RegionTemplate;
import io.github.bytex0.ip2region.core.RegionResult;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通过自动装配模板提供真实数据库查询接口。
 *
 * @author bytex0
 * @since 2026-10-05 19:17:15
 */
@RestController
public class IpController {

    private final Ip2RegionTemplate template;

    public IpController(Ip2RegionTemplate template) {
        this.template = template;
    }

    @GetMapping("/api/ip/search")
    public Map<String, Object> search(@RequestParam String ip) {
        RegionResult result = template.search(ip);
        return Map.of("code", 0, "data", result == null ? Map.of() : result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> invalid() {
        return Map.of("code", 400, "message", "Invalid IPv4 address");
    }
}
