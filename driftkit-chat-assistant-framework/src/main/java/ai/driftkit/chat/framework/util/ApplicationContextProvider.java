package ai.driftkit.chat.framework.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Utility class to provide access to Spring Application Context
 * from non-Spring managed classes.
 */
@Component
public class ApplicationContextProvider implements ApplicationContextAware {
    
    private static ApplicationContext context;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }
    
    /**
     * Get the Spring Application Context
     * @return The application context
     */
    public static ApplicationContext getApplicationContext() {
        return context;
    }
    
    /**
     * Get a bean from the application context
     * @param clazz The class of the bean
     * @return The bean instance
     */
    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }
    
    /**
     * Get a bean from the application context by name
     * @param name The name of the bean
     * @return The bean instance
     */
    public static Object getBean(String name) {
        return context.getBean(name);
    }
    
    /**
     * Get a bean from the application context by name and class
     * @param name The name of the bean
     * @param clazz The class of the bean
     * @return The bean instance
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return context.getBean(name, clazz);
    }
}