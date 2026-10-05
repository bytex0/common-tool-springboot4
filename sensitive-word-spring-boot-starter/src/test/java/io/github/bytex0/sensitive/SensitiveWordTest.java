package io.github.bytex0.sensitive;

import java.util.Locale;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * 白名单、Unicode 和原子词库更新的行为测试。
 *
 * @author bytex0
 * @since 2026-10-05 19:21:42
 */
class SensitiveWordTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SensitiveWordAutoConfiguration.class));

    @Test
    void configurationSupportsDisableOverrideAndMissingResourceFailure() {
        runner.run(context -> assertThat(context).hasSingleBean(SensitiveWordService.class));
        runner.withPropertyValues("sensitive-word.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SensitiveWordService.class));
        SensitiveWordService service = new SensitiveWordService(new SensitiveWordProperties());
        runner.withBean(SensitiveWordService.class, () -> service).run(context ->
                assertThat(context.getBean(SensitiveWordService.class)).isSameAs(service));
        runner.withPropertyValues("sensitive-word.dict-paths[0]=classpath:missing.txt")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void whitelistAndOriginalIndicesAreConsistent() {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setWords(Set.of("bad", "badly"));
        properties.setWhiteList(Set.of("badge"));
        SensitiveWordService service = new SensitiveWordService(properties);
        assertThat(service.replace(" badge B A D!")).isEqualTo(" badge *****!");
        assertThat(service.findAll(" badly", false).getFirst())
                .isEqualTo(new SensitiveWordService.Match("bad", 1, 4));
        assertThat(service.findAll(" badly", true).getFirst().word()).isEqualTo("badly");
        assertThat(service.contains("BADGE")).isFalse();
    }

    @Test
    void unicodeMappingDoesNotDependOnDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            SensitiveWordProperties properties = new SensitiveWordProperties();
            properties.setWords(Set.of("i", "😀"));
            SensitiveWordService service = new SensitiveWordService(properties);
            assertThat(service.replace(" I 😀!")).isEqualTo(" * *!");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void updatesAreAtomicAndInvalidUpdatesPreservePreviousDictionary() throws Exception {
        SensitiveWordService service = new SensitiveWordService(new SensitiveWordProperties());
        service.addWord("bad");
        assertThatIllegalArgumentException().isThrownBy(() -> service.replaceWords(Set.of(" ")));
        assertThat(service.contains("bad")).isTrue();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<Future<?>>();
            for (int index = 0; index < 100; index++) {
                futures.add(executor.submit(() -> {
                    service.addWord("bad");
                    assertThat(service.contains("bad")).isTrue();
                }));
            }
            for (var future : futures) {
                future.get();
            }
        }
        service.removeWord("BAD");
        assertThat(service.size()).isZero();
        assertThatIllegalArgumentException().isThrownBy(() -> service.contains("x".repeat(65537)));
    }
}
