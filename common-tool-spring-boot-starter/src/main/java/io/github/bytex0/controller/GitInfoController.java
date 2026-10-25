package io.github.bytex0.controller;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Git 构建信息接口，默认仅展示版本、分支和提交信息，不主动泄露仓库地址或构建用户。
 *
 * @author bytex0
 * @since 2026-10-06 18:02:16
 */
@RestController
public class GitInfoController {

    /**
     * 默认允许公开的构建字段。
     */
    private static final Set<String> PUBLIC_KEYS = Set.of(
            "git.branch", "git.commit.id", "git.commit.id.abbrev", "git.commit.time", "git.build.version");

    /**
     * 当前应用的资源加载器。
     */
    private final ResourceLoader resourceLoader;

    /**
     * 是否显式允许全部 Git 构建信息，默认 false。
     */
    private final boolean includeDetails;

    /**
     * 资源读取字节上限。
     */
    private final int maxResourceBytes;

    /**
     * 不可变元数据快照，无文件时为空；初始化完成后原子替换。
     */
    private volatile Map<String, String> gitPropertiesMap = Map.of();

    /**
     * 保留原单资源加载器构造入口，使用安全字段集和默认容量。
     *
     * @param resourceLoader 当前应用资源加载器
     */
    public GitInfoController(ResourceLoader resourceLoader) {
        this(resourceLoader, false, MetadataResourceReader.DEFAULT_MAX_BYTES);
    }

    /**
     * 配置展示范围与有界资源读取。
     *
     * @param resourceLoader 应用资源加载器
     * @param includeDetails 是否显式展示全部字段，调用方需负责接口访问控制
     * @param maxResourceBytes 最大字节数，范围 1 到十六 MiB
     */
    public GitInfoController(ResourceLoader resourceLoader, boolean includeDetails, int maxResourceBytes) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
        MetadataResourceReader.validateLimit(maxResourceBytes);
        this.includeDetails = includeDetails;
        this.maxResourceBytes = maxResourceBytes;
    }

    /**
     * 加载当前应用 git.properties，缺失时使用空快照，不保留打开的流。
     *
     * @throws IOException 资源读取或格式解析失败
     */
    @PostConstruct
    public void init() throws IOException {
        Resource resource = resourceLoader.getResource("classpath:git.properties");
        if (!resource.exists()) {
            gitPropertiesMap = Map.of();
            return;
        }
        Properties properties = new Properties();
        try (ByteArrayInputStream input = new ByteArrayInputStream(MetadataResourceReader.read(resource, maxResourceBytes))) {
            properties.load(input);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (includeDetails || PUBLIC_KEYS.contains(key)) {
                result.put(key, properties.getProperty(key));
            }
        }
        gitPropertiesMap = Map.copyOf(result);
    }

    /**
     * 返回当前 Git 元数据快照。
     *
     * @return 元数据响应，无资源时为空对象
     */
    @GetMapping("/git-info")
    public ResponseEntity<Map<String, String>> getGitInfo() {
        return ResponseEntity.ok(gitPropertiesMap);
    }
}
