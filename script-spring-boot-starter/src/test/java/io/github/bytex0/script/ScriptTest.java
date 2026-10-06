package io.github.bytex0.script;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 脚本隔离、状态更新、异常与超时的公开接口测试。
 *
 * @author bytex0
 * @since 2026-10-05 20:00:38
 */
class ScriptTest {

    /**
     * 验证显式启用、用户服务覆盖和无效配置拒绝行为。
     */
    @Test
    void configurationIsOptInAndOverridable() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ScriptAutoConfiguration.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(ScriptService.class));
        runner.withPropertyValues("script.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(ScriptService.class));
        runner.withPropertyValues("script.enabled=true").run(context -> assertThat(context).hasSingleBean(ScriptService.class));
        ScriptService custom = mock(ScriptService.class);
        runner.withPropertyValues("script.enabled=true").withBean(ScriptService.class, () -> custom)
                .run(context -> assertThat(context.getBean(ScriptService.class)).isSameAs(custom));
        runner.withPropertyValues("script.enabled=true", "script.parallelism=0").run(context -> assertThat(context).hasFailed());
    }

    /**
     * 验证源码变化重新编译及并发绑定隔离。
     *
     * @throws Exception 执行或等待失败
     */
    @Test
    void recompilesSourceAndIsolatesConcurrentBindings() throws Exception {
        try (ScriptService service = new ScriptService(List.of(new GroovyScriptExecutor()), 2, 8, 10000);
             var clients = Executors.newFixedThreadPool(2)) {
            assertThat(service.execute("groovy", "return a + 1", Map.of("a", 2))).isEqualTo(3);
            assertThat(service.execute("groovy", "return a + 2", Map.of("a", 2))).isEqualTo(4);
            var first = clients.submit(() -> service.execute("groovy", "return a", Map.of("a", 10)));
            var second = clients.submit(() -> service.execute("groovy", "return a", Map.of("a", 20)));
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(10);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(20);
            assertThatThrownBy(() -> service.execute("groovy", "return missing", Map.of())).isInstanceOf(Exception.class);
        }
    }

    /**
     * 验证 Groovy 循环取消后工作线程仍能处理新调用。
     *
     * @throws Exception 执行失败
     */
    @Test
    void interruptedLoopDoesNotPreventLaterWork() throws Exception {
        try (ScriptService service = new ScriptService(List.of(new GroovyScriptExecutor()), 1, 2, 3000)) {
            assertThatThrownBy(() -> service.execute("groovy", "while (true) { }", Map.of()))
                    .isInstanceOf(TimeoutException.class);
            assertThat(service.execute("groovy", "return 42", Map.of())).isEqualTo(42);
        }
    }
}
