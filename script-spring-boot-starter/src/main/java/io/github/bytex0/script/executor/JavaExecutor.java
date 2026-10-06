package io.github.bytex0.script.executor;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.exception.ScriptCompileException;
import io.github.bytex0.script.exception.ScriptExecuteException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.springframework.util.Assert;

/**
 * JDK Java 脚本编译执行器，通过语法树识别类型，编译产物独立保存并显式释放。
 *
 * @author bytex0
 * @since 2026-10-06 13:55:16
 */
public class JavaExecutor implements ScriptExecutor {

    /**
     * 原版默认执行方法，参数类型固定为 Map。
     */
    private static final String DEFAULT_METHOD = "execute";

    /**
     * 编译结果与项目 Java 基线一致。
     */
    private static final String JAVA_RELEASE = "21";

    /**
     * 当前进程 JDK 编译器，不支持只含 JRE 的运行环境。
     */
    private final JavaCompiler compiler;

    /**
     * 检查当前进程具备 JDK 编译器。
     *
     * @throws IllegalStateException 未找到 JDK 编译器
     */
    public JavaExecutor() {
        compiler = ToolProvider.getSystemJavaCompiler();
        Assert.state(compiler != null, "Java scripts require a JDK compiler");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScriptType getType() {
        return ScriptType.JAVA;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object compile(String script) {
        Assert.hasText(script, "Script source is required");
        Path directory = null;
        try {
            String className = findClassName(script);
            directory = Files.createTempDirectory("bytex0-java-script-");
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager manager = compiler.getStandardFileManager(
                    diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
                List<String> options = List.of("-proc:none", "--release", JAVA_RELEASE, "-d", directory.toString());
                boolean success = compiler.getTask(null, manager, diagnostics, options, null,
                        List.of(new SourceFile(className, script))).call();
                if (!success) {
                    throw new ScriptCompileException("Java compilation failed: " + diagnostics.getDiagnostics());
                }
            }
            return new CompiledScript(this, className, directory);
        } catch (IOException | RuntimeException exception) {
            if (directory != null) {
                try {
                    deleteDirectory(directory);
                } catch (IOException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw new ScriptCompileException("Java compilation failed", exception);
        }
    }

    /**
     * 使用 javac 语法树定位唯一公开顶层类，不触发注解处理器。
     *
     * @param script 受信源码
     * @return 包含包名的类型名称
     * @throws IOException 编译器文件管理器关闭失败
     */
    private String findClassName(String script) throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"),
                    null, List.of(new SourceFile("Script", script)));
            String name = null;
            for (CompilationUnitTree unit : task.parse()) {
                for (Tree declaration : unit.getTypeDecls()) {
                    if (declaration instanceof ClassTree type
                            && type.getKind() == Tree.Kind.CLASS
                            && type.getModifiers().getFlags().contains(Modifier.PUBLIC)) {
                        if (name != null) {
                            throw new ScriptCompileException("Exactly one public top-level class is required");
                        }
                        String packageName = unit.getPackageName() == null ? "" : unit.getPackageName() + ".";
                        name = packageName + type.getSimpleName();
                    }
                }
            }
            if (name == null) {
                throw new ScriptCompileException("A public top-level class is required");
            }
            return name;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object executeCompiled(Object compiledScript, Map<String, Object> params) {
        return executeCompiledMethod(compiledScript, DEFAULT_METHOD, params);
    }

    /**
     * 每次执行使用独立类加载器，使静态字段也不会跨调用串状态。
     *
     * @param compiledScript 本执行器创建的未关闭产物
     * @param methodName 公开方法名，签名接收 Map
     * @param params 非空参数映射
     * @return 方法结果，可为空；建议返回父加载器可见的 JDK 数据类型
     */
    @Override
    public Object executeCompiledMethod(Object compiledScript, String methodName, Map<String, Object> params) {
        Assert.hasText(methodName, "Method name is required");
        Assert.notNull(params, "Script parameters are required");
        Assert.isTrue(compiledScript instanceof CompiledScript, "Invalid Java compiled script");
        CompiledScript compiled = (CompiledScript) compiledScript;
        Assert.isTrue(compiled.owner == this, "Compiled script belongs to another executor");
        Assert.state(!compiled.closed.get(), "Compiled script is closed");
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{compiled.directory.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> type = loader.loadClass(compiled.className);
            Assert.state(type.getClassLoader() == loader, "Script class conflicts with an application class");
            Method method = type.getMethod(methodName, Map.class);
            Object instance = type.getDeclaredConstructor().newInstance();
            return method.invoke(instance, new HashMap<>(params));
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ScriptExecuteException("Java script method failed", cause);
        } catch (ReflectiveOperationException | IOException exception) {
            throw new ScriptExecuteException("Java script execution failed", exception);
        }
    }

    /**
     * 从叶子开始删除编译文件；只用于执行器自己创建的随机目录。
     *
     * @param directory 编译目录
     * @throws IOException 遍历或删除失败
     */
    private static void deleteDirectory(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * 内存源码文件，避免通过源码中的类名拼接磁盘写入路径。
     *
     * @author bytex0
     * @since 2026-10-06 13:55:16
     */
    private static final class SourceFile extends SimpleJavaFileObject {

        /**
         * 本次编译源码。
         */
        private final String source;

        /**
         * 创建与声明类名匹配的虚拟文件。
         *
         * @param className 全限定类型名
         * @param source 源码
         */
        private SourceFile(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        /**
         * 返回内存中的 Unicode 源码，不涉及外部字符集解码。
         *
         * @param ignoreEncodingErrors 是否忽略编码错误，此实现无需使用
         * @return 源码字符序列
         */
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    /**
     * 编译文件的所有权句柄，调用者应在所有执行结束后关闭。
     *
     * @author bytex0
     * @since 2026-10-06 13:55:16
     */
    private static final class CompiledScript implements AutoCloseable {

        /**
         * 编译产物所属执行器。
         */
        private final JavaExecutor owner;

        /**
         * 包含包名的入口类型名称。
         */
        private final String className;

        /**
         * 独立编译输出目录。
         */
        private final Path directory;

        /**
         * 关闭标记，不替代调用方的执行生命周期协调。
         */
        private final AtomicBoolean closed = new AtomicBoolean();

        /**
         * 接管成功编译的目录。
         *
         * @param owner 所属执行器
         * @param className 入口类名
         * @param directory 编译目录
         */
        private CompiledScript(JavaExecutor owner, String className, Path directory) {
            this.owner = owner;
            this.className = className;
            this.directory = directory;
        }

        /**
         * 幂等删除编译产物，失败允许再次尝试清理。
         *
         * @throws IOException 删除失败
         */
        @Override
        public void close() throws IOException {
            if (closed.compareAndSet(false, true)) {
                try {
                    deleteDirectory(directory);
                } catch (IOException exception) {
                    closed.set(false);
                    throw exception;
                }
            }
        }
    }
}
