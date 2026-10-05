package io.github.bytex0.oss;

import org.springframework.util.Assert;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 对象存储(OssUtil)对象路径工具
 *
 * @author bytex0
 * @since 2026-10-05 14:42:56
 */
public final class OssUtil {

    /**
     * 按小时归档的对象路径格式
     */
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH");

    /**
     * 工具类不需要实例状态。
     */
    private OssUtil() {
    }

    /**
     * 按当前本地时间归档到年/月/日/小时目录。
     *
     * @param dir 可选前缀
     * @param fileName 非空文件名
     * @return 对象键
     */
    public static String constructObjectName(String dir, String fileName) {
        return constructSimpleObjectName(
                constructSimpleObjectName(dir, LocalDateTime.now().format(DATE_PATH)), fileName);
    }

    /**
     * 组合对象键，规范化前缀边界斜杠，不将其解释为文件系统路径。
     *
     * @param dir 可选前缀
     * @param fileName 文件名，不得只有斜杠
     * @return 对象键
     */
    public static String constructSimpleObjectName(String dir, String fileName) {
        Assert.hasText(fileName, "fileName 不能为空");
        String prefix = dir == null ? "" : dir.replaceAll("^/+|/+$", "");
        String name = fileName.replaceAll("^/+", "");
        Assert.hasText(name, "fileName 不能只包含斜杠");
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    /**
     * 对对象键分段编码并构造普通访问 URL，不生成签名。
     *
     * @param baseUrl 基础 URL
     * @param dir 可选对象前缀
     * @param fileName 对象文件名
     * @return 编码后的 URL
     */
    public static String constructObjectUrl(String baseUrl, String dir, String fileName) {
        Assert.hasText(baseUrl, "baseUrl 不能为空");
        String objectName = constructSimpleObjectName(dir, fileName);
        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment(objectName.split("/", -1)).build().encode().toUriString();
    }
}
