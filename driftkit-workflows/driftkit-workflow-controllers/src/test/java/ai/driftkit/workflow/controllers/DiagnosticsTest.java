package ai.driftkit.workflow.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;

@SpringBootTest(
    classes = {test.app.TestApplication.class, TestApplicationConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("integration-test")
public class DiagnosticsTest {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Test
    public void printAllBeans() {
        System.out.println("\n=== CHECKING CRITICAL BEANS ===\n");
        
        // Check MongoDB
        try {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            System.out.println("✅ MongoTemplate is available");
            System.out.println("   MongoDB URI: " + mongoTemplate.getDb().getName());
        } catch (Exception e) {
            System.out.println("❌ MongoTemplate NOT available: " + e.getMessage());
        }
        
        // Check repositories
        String[] repoNames = {
            "asyncTaskRepository",
            "modelRequestTraceRepository"
        };
        
        for (String name : repoNames) {
            try {
                Object bean = applicationContext.getBean(name);
                System.out.println("✅ " + name + " is available: " + bean.getClass().getName());
            } catch (Exception e) {
                System.out.println("❌ " + name + " NOT available: " + e.getMessage());
            }
        }
        
        // Check services
        String[] serviceNames = {
            "asyncTaskService",
            "workflowAnalyticsService"
        };
        
        for (String name : serviceNames) {
            try {
                Object bean = applicationContext.getBean(name);
                System.out.println("✅ " + name + " is available: " + bean.getClass().getName());
            } catch (Exception e) {
                System.out.println("❌ " + name + " NOT available: " + e.getMessage());
            }
        }
        
        // Check configurations
        System.out.println("\n=== CHECKING CONFIGURATIONS ===\n");
        String[] configClasses = {
            "ai.driftkit.workflow.engine.spring.autoconfigure.WorkflowMongoRepositoriesAutoConfiguration",
            "ai.driftkit.workflow.engine.spring.autoconfigure.WorkflowTracingAutoConfiguration",
            "ai.driftkit.workflow.controllers.autoconfigure.ControllersAutoConfiguration"
        };
        
        for (String className : configClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                String[] beanNames = applicationContext.getBeanNamesForType(clazz);
                if (beanNames.length > 0) {
                    System.out.println("✅ " + className + " is loaded");
                } else {
                    System.out.println("❌ " + className + " NOT loaded");
                }
            } catch (Exception e) {
                System.out.println("❌ " + className + " class not found: " + e.getMessage());
            }
        }
        
        // Print all MongoDB-related beans
        System.out.println("\n=== ALL MONGODB-RELATED BEANS ===\n");
        Arrays.stream(applicationContext.getBeanDefinitionNames())
            .filter(name -> name.toLowerCase().contains("mongo") || 
                           name.toLowerCase().contains("repository"))
            .sorted()
            .forEach(name -> {
                try {
                    Object bean = applicationContext.getBean(name);
                    System.out.println("  " + name + " -> " + bean.getClass().getName());
                } catch (Exception e) {
                    System.out.println("  " + name + " -> ERROR: " + e.getMessage());
                }
            });
    }
}