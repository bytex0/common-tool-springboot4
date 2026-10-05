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

    /**
     * 实际自动装配的查询模板。
     */
    private final Ip2RegionTemplate template;

    /**
     * 注入真实模板，不在示例中复制查询实现。
     *
     * @param template 查询模板
     */
    public IpController(Ip2RegionTemplate template) {
        this.template = template;
    }

    /**
     * 返回指定 IPv4 地址的数据库归属地。
     *
     * @param ip IPv4 字面量
     * @return 查询结果，未知记录返回空数据对象
     */
    @GetMapping("/api/ip/search")
    public Map<String, Object> search(@RequestParam String ip) {
        RegionResult result = template.search(ip);
        return Map.of("code", 0, "data", result == null ? Map.of() : result);
    }

    /**
     * 映射非法地址为参数错误，不泄漏底层异常。
     *
     * @return HTTP 400 业务体
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> invalid() {
        return Map.of("code", 400, "message", "Invalid IPv4 address");
    }
}
