package io.github.bytex0.script;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import groovy.transform.ThreadInterrupt;
import java.util.HashMap;
import java.util.Map;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.runtime.InvokerHelper;

/**
 * 每次调用独立类加载器和 Binding，不提供不可信代码沙箱。
 *
 * @author bytex0
 * @since 2026-10-05 20:00:38
 */
public class GroovyScriptExecutor implements ScriptExecutor {

    /**
     * {@inheritDoc}
     */
    @Override
    public String language() {
        return "groovy";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object execute(String source, Map<String, Object> parameters) throws Exception {
        CompilerConfiguration config = new CompilerConfiguration();
        config.setSourceEncoding("UTF-8");
        config.addCompilationCustomizers(new ASTTransformationCustomizer(ThreadInterrupt.class));
        try (GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader(), config)) {
            Class<? extends Script> type = loader.parseClass(source).asSubclass(Script.class);
            try {
                Script script = InvokerHelper.createScript(type, new Binding(new HashMap<>(parameters)));
                return script.run();
            } finally {
                InvokerHelper.removeClass(type);
            }
        }
    }
}
