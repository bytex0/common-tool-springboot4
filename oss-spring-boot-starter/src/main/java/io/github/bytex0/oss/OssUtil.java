package io.github.bytex0.oss;

import org.springframework.util.Assert;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 对象存储(OssUtil)对象路径工具
 *
 * @author linshiqiang
 * @since 2026-10-05 14:42:56
 */
public final class OssUtil {

    /**
     * 按小时归档的对象路径格式
     */
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH");

    private OssUtil() {
    }

    public static String constructObjectName(String dir, String fileName) {
        return constructSimpleObjectName(
                constructSimpleObjectName(dir, LocalDateTime.now().format(DATE_PATH)), fileName);
    }

    public static String constructSimpleObjectName(String dir, String fileName) {
        Assert.hasText(fileName, "fileName 不能为空");
        String prefix = dir == null ? "" : dir.replaceAll("^/+|/+$", "");
        String name = fileName.replaceAll("^/+", "");
        Assert.hasText(name, "fileName 不能只包含斜杠");
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    public static String constructObjectUrl(String baseUrl, String dir, String fileName) {
        Assert.hasText(baseUrl, "baseUrl 不能为空");
        String objectName = constructSimpleObjectName(dir, fileName);
        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment(objectName.split("/", -1)).build().encode().toUriString();
    }
}
