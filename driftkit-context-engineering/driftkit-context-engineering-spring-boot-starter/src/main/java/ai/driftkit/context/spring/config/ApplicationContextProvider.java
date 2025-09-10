package ai.driftkit.context.spring.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component("promptContextProvider")
public class ApplicationContextProvider implements ApplicationContextAware {

    private static ApplicationContext context;

    /**
     * Sets the ApplicationContext. Called by Spring during context initialization.
     *
     * @param applicationContext the Spring ApplicationContext
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        context = applicationContext;
    }

    /**
     * Retrieves the stored ApplicationContext.
     *
     * @return the Spring ApplicationContext
     */
    public static ApplicationContext getApplicationContext() {
        if (context == null) {
            throw new IllegalStateException("ApplicationContext has not been initialized yet.");
        }
        return context;
    }
}