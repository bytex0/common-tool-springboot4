package io.github.bytex0.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 四类请求异常全部构造器的反射兼容测试。
 *
 * @author bytex0
 * @since 2026-10-06 14:47:34
 */
class RequestExceptionTest {

    /**
     * 验证八个构造入口、请求 ID、模板、原因链及无堆栈行为。
     *
     * @param type 异常类型
     * @throws ReflectiveOperationException 构造器缺失或构造失败
     */
    @ParameterizedTest
    @ValueSource(classes = {BizException.class, AuthException.class, ParamsException.class, BaseRequestException.class})
    void preservesAllConstructors(Class<? extends AbstractRequestException> type) throws ReflectiveOperationException {
        Throwable cause = new IllegalStateException("cause");
        assertThat(type.getConstructor().newInstance().getMessage()).isNull();
        assertThat(type.getConstructor(String.class).newInstance("message").getMessage()).isEqualTo("message");
        AbstractRequestException withId = type.getConstructor(String.class, String.class)
                .newInstance("request", "message");
        assertThat(withId.getRequestId()).isEqualTo("request");
        AbstractRequestException withCause = type.getConstructor(String.class, String.class, Throwable.class)
                .newInstance("request", "message", cause);
        assertThat(withCause.getCause()).isSameAs(cause);
        assertThat(withCause.getStackTrace()).isEmpty();
        assertThat(type.getConstructor(Throwable.class).newInstance(cause).getMessage())
                .isEqualTo("IllegalStateException: cause");
        assertThat(type.getConstructor(String.class, Object[].class)
                .newInstance("number {}", new Object[]{3}).getMessage()).isEqualTo("number 3");
        assertThat(type.getConstructor(String.class, Throwable.class).newInstance("message", cause).getCause())
                .isSameAs(cause);
        AbstractRequestException formatted = type.getConstructor(Throwable.class, String.class, Object[].class)
                .newInstance(cause, "number {}", new Object[]{4});
        assertThat(formatted.getMessage()).isEqualTo("number 4");
        assertThat(formatted.getCause()).isSameAs(cause);
    }
}
