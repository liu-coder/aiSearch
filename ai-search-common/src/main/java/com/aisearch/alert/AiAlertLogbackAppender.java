package com.aisearch.alert;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class AiAlertLogbackAppender extends AppenderBase<ILoggingEvent> implements ApplicationContextAware {
    private static ApplicationContext context;
    private static String serviceName = "unknown-service";

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
        serviceName = applicationContext.getEnvironment().getProperty("spring.application.name", "unknown-service");
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLevel() != Level.ERROR || context == null) {
            return;
        }
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (!(throwableProxy instanceof ThrowableProxy proxy)) {
            return;
        }
        try {
            context.getBean(AiAlertService.class)
                    .triggerAsync(serviceName, proxy.getThrowable(), event.getFormattedMessage());
        } catch (RuntimeException ignored) {
            // Logback appender 不能反向影响业务日志链路。
        }
    }
}
