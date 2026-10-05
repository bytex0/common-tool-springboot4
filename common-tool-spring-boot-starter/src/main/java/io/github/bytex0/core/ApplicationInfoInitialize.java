package io.github.bytex0.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * 应用信息(ApplicationInfoInitialize)启动日志初始化
 *
 * @author linshiqiang
 * @since 2026-10-05 14:26:50
 */
public class ApplicationInfoInitialize implements ApplicationListener<ApplicationReadyEvent> {

    /**
     * 应用启动日志
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationInfoInitialize.class);

    /**
     * 当前应用运行环境
     */
    private final Environment environment;

    public ApplicationInfoInitialize(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getEnvironment() != environment) {
            return;
        }
        String applicationName = environment.getProperty("spring.application.name", "application");
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        LOGGER.info("Application ready: name={}, profiles={}", applicationName, String.join(",", profiles));
    }
}
