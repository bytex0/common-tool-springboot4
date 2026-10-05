package io.github.bytex0.oss;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对象路径(OssUtilTest)组合与编码测试
 *
 * @author linshiqiang
 * @since 2026-10-05 14:57:26
 */
class OssUtilTest {

    @Test
    void shouldJoinPathsWithoutDuplicateBoundarySlashes() {
        assertThat(OssUtil.constructSimpleObjectName("/files/", "/name.txt")).isEqualTo("files/name.txt");
        assertThat(OssUtil.constructSimpleObjectName(null, "name.txt")).isEqualTo("name.txt");
        assertThat(OssUtil.constructObjectName("files", "name.txt"))
                .matches("files/\\d{4}/\\d{2}/\\d{2}/\\d{2}/name.txt");
    }

    @Test
    void shouldEncodeObjectNameWithoutChangingItsPath() {
        String url = OssUtil.constructObjectUrl("https://example.com/bucket/", "files", "中文 空格#.txt");
        assertThat(URI.create(url).getPath()).isEqualTo("/bucket/files/中文 空格#.txt");
        assertThat(URI.create(url).getFragment()).isNull();
    }
}
