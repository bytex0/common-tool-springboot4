package io.github.bytex0.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 应用版本与 ping 接口，资源缓存属于当前控制器实例，不跨应用共享。
 *
 * @author bytex0
 * @since 2026-10-06 18:02:16
 */
@RestController
public class VersionController {

    /**
     * 当前应用资源加载器。
     */
    private final ResourceLoader resourceLoader;

    /**
     * 资源读取上限，单位字节。
     */
    private final int maxResourceBytes;

    /**
     * 成功读取后的版本文本，null 表示尚未加载。
     */
    private final AtomicReference<String> version = new AtomicReference<>();

    /**
     * 保留原无参构造入口，使用当前类路径资源。
     */
    public VersionController() {
        this(new DefaultResourceLoader(), MetadataResourceReader.DEFAULT_MAX_BYTES);
    }

    /**
     * 使用应用资源加载器创建版本控制器。
     *
     * @param resourceLoader 应用资源加载器
     * @param maxResourceBytes 读取上限，范围 1 到十六 MiB
     */
    public VersionController(ResourceLoader resourceLoader, int maxResourceBytes) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
        MetadataResourceReader.validateLimit(maxResourceBytes);
        this.maxResourceBytes = maxResourceBytes;
    }

    /**
     * 获取 version.json 原始 UTF-8 内容，不在持锁期间读取资源。
     *
     * @return 版本文本
     * @throws IOException 资源读取失败或超限
     */
    @GetMapping("/get-version")
    public String getVersion() throws IOException {
        String cached = version.get();
        if (cached != null) {
            return cached;
        }
        Resource resource = resourceLoader.getResource("classpath:version.json");
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Version metadata is not available");
        }
        String loaded = new String(MetadataResourceReader.read(resource, maxResourceBytes), StandardCharsets.UTF_8);
        version.compareAndSet(null, loaded);
        return version.get();
    }

    /**
     * 保留原 ping 文本协议。
     *
     * @return OK
     */
    @GetMapping("/ping")
    public String ping() {
        return HttpStatus.OK.getReasonPhrase();
    }
}
