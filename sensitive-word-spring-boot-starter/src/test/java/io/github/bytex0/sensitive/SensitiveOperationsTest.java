package io.github.bytex0.sensitive;

import io.github.bytex0.sensitive.core.DfaSensitiveWordFilter;
import io.github.bytex0.sensitive.core.HandleType;
import io.github.bytex0.sensitive.core.MatchType;
import io.github.bytex0.sensitive.core.SensitiveWordException;
import io.github.bytex0.sensitive.core.SensitiveWordFilter;
import io.github.bytex0.sensitive.core.SensitiveWordOperations;
import io.github.bytex0.sensitive.core.SensitiveWordOptions;
import io.github.bytex0.sensitive.core.SensitiveWordResult;
import io.github.bytex0.sensitive.util.SensitiveWordUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 服务契约(SensitiveOperationsTest)验证原管理入口、白名单一致性及资源失败原子性。
 *
 * @author linshiqiang
 * @since 2026-10-06 09:10:08
 */
class SensitiveOperationsTest {

    /**
     * 隔离测试词库目录。
     */
    @TempDir
    Path temporary;

    /**
     * 白名单同时影响查询、字符/字符串替换、高亮与拒绝，重叠白名单覆盖整个联合范围。
     */
    @Test
    void appliesDynamicWhitelistAcrossEveryOperation() {
        SensitiveWordOptions options = new SensitiveWordOptions();
        options.setWords(Set.of("bad", "d"));
        options.setWhiteList(Set.of("badge"));
        options.setReplaceStr("[hidden]");
        SensitiveWordOperations service = service(options);
        assertThat(service.replace("BADGE bad")).isEqualTo("BADGE ***");
        assertThat(service.replaceWithStr("badge bad")).isEqualTo("badge [hidden]");
        assertThat(service.highlight("badge bad", "<b>", "</b>")).isEqualTo("badge <b>bad</b>");
        assertThat(service.process("badge", HandleType.EXCEPTION, MatchType.MIN_MATCH, '*', "rejected")).isEqualTo("badge");
        service.addWhiteList(Set.of("abc", "bcd"));
        assertThat(service.contains("abcd")).isFalse();
        service.removeWhiteList("badge");
        assertThat(service.contains("badge")).isTrue();
        service.replaceWhiteList(Set.of());
        assertThat(service.findFirst("bad").getEndIndex()).isEqualTo(2);
        service.clear();
        assertThat(service.size()).isZero();
    }

    /**
     * 原词库文件和 classpath 路径可用，读取或解析失败不丢失已注册数据。
     *
     * @throws Exception 准备资源失败
     */
    @Test
    void loadsOriginalResourcePathsAndPreservesDataOnFailure() throws Exception {
        SensitiveWordOperations service = service(new SensitiveWordOptions());
        Path dictionary = Files.writeString(temporary.resolve("words # %.txt"), "\uFEFF# comment\nloaded\n\n");
        service.loadFromFile(dictionary.toString());
        service.loadFromClasspath("sensitive/default.txt");
        assertThat(service.contains("loaded 敏感词测试")).isTrue();
        int before = service.size();
        assertThatThrownBy(() -> service.loadFromFile(temporary.resolve("missing").toString()))
                .isInstanceOf(UncheckedIOException.class);
        Files.writeString(dictionary, "good\n" + "x".repeat(129));
        assertThatThrownBy(() -> service.loadFromFile(dictionary.toString())).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.size()).isEqualTo(before);
        assertThat(service.contains("good")).isFalse();
    }

    /**
     * 自定义过滤器 Bean 仍是实际执行边界，不被内置过滤器偷偷替换。
     */
    @Test
    void respectsCustomFilterBean() {
        SensitiveWordFilter filter = mock(SensitiveWordFilter.class);
        when(filter.findAll("input", MatchType.MIN_MATCH))
                .thenReturn(List.of(new SensitiveWordResult("custom", 0, 4, "external")));
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SensitiveWordAutoConfiguration.class))
                .withBean(SensitiveWordFilter.class, () -> filter)
                .run(context -> {
                    SensitiveWordOperations service = context.getBean(SensitiveWordOperations.class);
                    assertThat(service.getFilter()).isSameAs(filter);
                    assertThat(service.findFirst("input").getCategory()).isEqualTo("external");
                });
    }

    /**
     * 两种配置入口共享有效设置，运行时默认替换选项不会分别漂移。
     */
    @Test
    void sharesConfigurationBetweenCompatibilityViews() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SensitiveWordAutoConfiguration.class))
                .withPropertyValues("sensitive-word.words[0]=bad")
                .run(context -> {
                    SensitiveWordProperties properties = context.getBean(SensitiveWordProperties.class);
                    properties.setReplaceChar('#');
                    assertThat(context.getBean(SensitiveWordOperations.class).replace("bad")).isEqualTo("###");
                });
    }

    /**
     * 原异常保留原因与词条查询，不向调用方暴露内部可变结果。
     */
    @Test
    void preservesExceptionConstructorsAndDefensiveResults() {
        SensitiveWordResult match = new SensitiveWordResult("bad", 1, 3, "category");
        IllegalStateException cause = new IllegalStateException("cause");
        SensitiveWordException failure = new SensitiveWordException("message", cause, List.of(match));
        match.setWord("changed");
        failure.getSensitiveWords().getFirst().setWord("changed-again");
        assertThat(failure.getFirstSensitiveWord()).isEqualTo("bad");
        assertThat(failure.getAllSensitiveWords()).containsExactly("bad");
        assertThat(failure.getCause()).isSameAs(cause);
        assertThat(new SensitiveWordException("empty", null).getSensitiveWords()).isNull();
    }

    /**
     * 实例工具没有全局词库串扰，替换过滤器只改变当前工具。
     */
    @Test
    void isolatesUtilityInstancesAndConfiguredModes() {
        SensitiveWordUtil first = new SensitiveWordUtil();
        SensitiveWordUtil second = new SensitiveWordUtil();
        first.addWord("bad", "category");
        assertThat(first.findFirst("bad").getCategory()).isEqualTo("category");
        assertThat(second.contains("bad")).isFalse();
        first.setFilter(null);
        assertThat(first.contains("bad")).isFalse();
        SensitiveWordOptions options = new SensitiveWordOptions();
        options.setWords(Set.of("bad", "badly"));
        options.setMatchType(MatchType.MAX_MATCH);
        options.setReplaceStr("[x]");
        SensitiveWordOperations service = service(options);
        assertThat(service.process("badly")).isEqualTo("[x]");
        options.setHandleType(HandleType.DETECT_ONLY);
        assertThat(service.process("badly")).isEqualTo("badly");
        options.setHandleType(HandleType.EXCEPTION);
        assertThatThrownBy(() -> service.process("badly")).isInstanceOf(SensitiveWordException.class);
    }

    /**
     * 创建已完成初始化的独立业务服务。
     *
     * @param options 配置
     * @return 服务
     */
    private SensitiveWordOperations service(SensitiveWordOptions options) {
        SensitiveWordOperations service = new SensitiveWordOperations(new DfaSensitiveWordFilter(options), options);
        service.afterPropertiesSet();
        return service;
    }
}
