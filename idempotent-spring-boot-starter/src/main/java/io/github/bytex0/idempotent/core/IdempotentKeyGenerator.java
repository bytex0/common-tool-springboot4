package io.github.bytex0.idempotent.core;

import io.github.bytex0.util.MethodExpressionEvaluator;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.CodeSignature;
import org.springframework.util.Assert;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 幂等键(IdempotentKeyGenerator)稳定参数表示与SHA-256命名空间
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
public class IdempotentKeyGenerator implements BeanFactoryAware {

    /**
     * 当前容器，只在构造或生命周期回调中绑定一次。
     */
    private final AtomicReference<BeanFactory> beans = new AtomicReference<>();

    /**
     * 非 Spring 环境的无参实例仍支持无 Bean 引用的表达式。
     */
    private final BeanFactory fallbackBeans = new DefaultListableBeanFactory();

    /**
     * 不加载出站脱敏/字典模块的确定性参数映射器
     */
    private final JsonMapper canonical = JsonMapper.builder().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

    /**
     * 原四参数接口使用显式传入的 BeanFactoryResolver，无参实例不依赖全局容器。
     */
    public IdempotentKeyGenerator() {
    }

    /**
     * 为新的方法描述符接口提供当前 Bean 工厂。
     *
     * @param beans 当前容器
     */
    public IdempotentKeyGenerator(BeanFactory beans) {
        this.beans.set(Objects.requireNonNull(beans));
    }

    /**
     * 为原无参自定义子类绑定所属容器，不替换显式提供的工厂。
     *
     * @param beanFactory 所属容器
     */
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        beans.compareAndSet(null, beanFactory);
    }

    /**
     * 兼容原四参数扩展点；若用户同时覆盖新旧接口，优先使用新接口。
     *
     * @param expression 可信表达式
     * @param prefix 命名空间
     * @param point 当前调用
     * @param method 实际目标方法
     * @return 用户键或默认稳定摘要
     */
    public String generateInvocationKey(String expression, String prefix, ProceedingJoinPoint point, Method method) {
        try {
            Method modern = getClass().getMethod("generateKey", String.class, String.class,
                    Object.class, Method.class, Object[].class);
            Method legacy = getClass().getMethod("generateKey", String.class, String.class,
                    ProceedingJoinPoint.class, BeanFactoryResolver.class);
            if (modern.getDeclaringClass() == IdempotentKeyGenerator.class
                    && legacy.getDeclaringClass() != IdempotentKeyGenerator.class) {
                return generateKey(expression, prefix, point, new BeanFactoryResolver(beanFactory()));
            }
            return generateKey(expression, prefix, point.getTarget(), method, point.getArgs());
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("幂等键扩展接口不可用", exception);
        }
    }

    /**
     * 获取实例所属容器，避免跨应用共享静态上下文。
     *
     * @return 实际或空白工厂
     */
    private BeanFactory beanFactory() {
        BeanFactory current = beans.get();
        return current == null ? fallbackBeans : current;
    }

    /**
     * 生成新作用域使用的稳定摘要，不依赖 DTO 默认 toString 或 Map 遍历顺序。
     *
     * @param expression 可信 SpEL，空值使用参数 JSON
     * @param prefix 业务命名空间
     * @param target 目标对象
     * @param method 实际方法
     * @param args 方法参数
     * @return SHA-256 摘要
     */
    public String generateKey(String expression, String prefix, Object target, Method method, Object[] args) {
        String logical;
        if (expression == null || expression.isBlank()) {
            logical = method.toGenericString() + "\0" + canonical.writeValueAsString(args);
        } else {
            Object value = new MethodExpressionEvaluator(beanFactory()).evaluate(expression, target, method, args, Object.class);
            Assert.isTrue(value instanceof CharSequence || value instanceof Number || value instanceof UUID,
                    "幂等key表达式必须返回稳定标量");
            logical = value.toString();
            Assert.hasText(logical, "幂等key不能为空");
        }
        return MethodExpressionEvaluator.digest(canonical.writeValueAsString(new String[]{prefix, logical}));
    }

    /**
     * 保留原四参数键协议，便于独立检查接口迁移；新注解路径不使用该旧摘要协议。
     *
     * @param keyExpression 可信 SpEL，空串按原参数名与 toString 拼接
     * @param keyPrefix 原键前缀
     * @param joinPoint 当前调用
     * @param resolver 原 Bean 解析器
     * @return 原前缀加 MD5 摘要，不具备密码学认证用途
     */
    public String generateKey(String keyExpression, String keyPrefix, ProceedingJoinPoint joinPoint,
                              BeanFactoryResolver resolver) {
        Object[] args = joinPoint.getArgs();
        String[] names = ((CodeSignature) joinPoint.getSignature()).getParameterNames();
        Assert.isTrue(names != null && names.length == args.length, "请保留方法参数名，启用编译参数-parameters");
        String value;
        if (keyExpression == null || keyExpression.isEmpty()) {
            StringBuilder text = new StringBuilder();
            for (int index = 0; index < names.length; index++) {
                text.append(names[index]).append(args[index]);
            }
            value = text.toString();
        } else {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setBeanResolver(resolver);
            for (int index = 0; index < names.length; index++) {
                context.setVariable(names[index], args[index]);
                context.setVariable("p" + index, args[index]);
                context.setVariable("a" + index, args[index]);
            }
            value = new SpelExpressionParser().parseExpression(keyExpression).getValue(context, String.class);
            Assert.notNull(value, "幂等键表达式不能返回null");
        }
        try {
            return keyPrefix + HexFormat.of().formatHex(
                    MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行时不支持原MD5键协议", exception);
        }
    }
}
