package io.github.bytex0.controller;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Git 与版本元数据的字段策略、资源关闭、缓存及边界测试。
 *
 * @author bytex0
 * @since 2026-10-06 18:05:07
 */
class MetadataControllerTest {

    /**
     * 测试使用的安全资源大小上限，单位字节。
     */
    private static final int LIMIT = 1024;

    /**
     * 验证默认字段过滤、显式完整字段展示及每次加载关闭资源。
     *
     * @throws IOException 资源加载失败
     */
    @Test
    void filtersGitDetailsUnlessExplicitlyEnabled() throws IOException {
        TrackingResource resource = new TrackingResource(
                "git.branch=main\ngit.commit.id.abbrev=1234567\ngit.build.user.name=fixture-user\n");
        ResourceLoader loader = loader("classpath:git.properties", resource);
        GitInfoController safe = new GitInfoController(loader);
        safe.init();
        assertThat(safe.getGitInfo().getBody()).containsEntry("git.branch", "main")
                .doesNotContainKey("git.build.user.name");
        GitInfoController explicit = new GitInfoController(loader, true, LIMIT);
        explicit.init();
        assertThat(explicit.getGitInfo().getBody()).containsEntry("git.build.user.name", "fixture-user");
        assertThat(resource.opened.get()).isEqualTo(2);
        assertThat(resource.closed.get()).isEqualTo(2);
    }

    /**
     * 验证版本文本缓存属于实例，资源流读取后关闭。
     *
     * @throws IOException 读取失败
     */
    @Test
    void cachesVersionPerControllerWithoutKeepingStreamsOpen() throws IOException {
        TrackingResource firstResource = new TrackingResource("{\"version\":\"one\"}");
        VersionController first = new VersionController(loader("classpath:version.json", firstResource), LIMIT);
        assertThat(first.getVersion()).isEqualTo("{\"version\":\"one\"}");
        assertThat(first.getVersion()).isEqualTo("{\"version\":\"one\"}");
        VersionController second = new VersionController(
                loader("classpath:version.json", new TrackingResource("{\"version\":\"two\"}")), LIMIT);
        assertThat(second.getVersion()).isEqualTo("{\"version\":\"two\"}");
        assertThat(firstResource.opened.get()).isEqualTo(1);
        assertThat(firstResource.closed.get()).isEqualTo(1);
        assertThat(first.ping()).isEqualTo("OK");
    }

    /**
     * 验证资源缺失和超限路径，不将缺失当作成功，也不泄露超限流。
     *
     * @throws IOException Git 空资源初始化失败
     */
    @Test
    void handlesMissingAndOversizedResources() throws IOException {
        Resource missing = mock(Resource.class);
        GitInfoController git = new GitInfoController(loader("classpath:git.properties", missing));
        git.init();
        assertThat(git.getGitInfo().getBody()).isEmpty();
        VersionController absent = new VersionController(loader("classpath:version.json", missing), LIMIT);
        assertThatThrownBy(absent::getVersion).isInstanceOf(ResponseStatusException.class)
                .satisfies(failure -> assertThat(((ResponseStatusException) failure).getStatusCode().value())
                        .isEqualTo(404));
        TrackingResource oversized = new TrackingResource("oversized");
        VersionController limited = new VersionController(loader("classpath:version.json", oversized), 2);
        assertThatThrownBy(limited::getVersion).isInstanceOf(IOException.class);
        assertThat(oversized.closed.get()).isEqualTo(1);
    }

    /**
     * 构造单资源加载器，不访问真实文件。
     *
     * @param location 资源路径
     * @param resource 测试资源
     * @return 资源加载器
     */
    private ResourceLoader loader(String location, Resource resource) {
        ResourceLoader loader = mock(ResourceLoader.class);
        when(loader.getResource(location)).thenReturn(resource);
        return loader;
    }

    /**
     * 记录资源流打开关闭次数的测试资源。
     *
     * @author bytex0
     * @since 2026-10-06 18:05:07
     */
    private static final class TrackingResource extends ByteArrayResource {

        /**
         * 打开流次数。
         */
        private final AtomicInteger opened = new AtomicInteger();

        /**
         * 关闭流次数。
         */
        private final AtomicInteger closed = new AtomicInteger();

        /**
         * 创建 UTF-8 文本资源。
         *
         * @param text 资源内容
         */
        private TrackingResource(String text) {
            super(text.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * 返回带关闭计数的独立流。
         *
         * @return 本次读取流
         * @throws IOException 底层流打开失败
         */
        @Override
        public InputStream getInputStream() throws IOException {
            opened.incrementAndGet();
            return new FilterInputStream(super.getInputStream()) {
                /**
                 * 记录关闭并关闭底层流。
                 *
                 * @throws IOException 关闭失败
                 */
                @Override
                public void close() throws IOException {
                    closed.incrementAndGet();
                    super.close();
                }
            };
        }
    }
}
