package ai.driftkit.workflow.engine.spring.autoconfigure;

import ai.driftkit.workflow.engine.annotations.Workflow;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * BeanPostProcessor that automatically registers beans annotated with @Workflow
 * in the WorkflowEngine.
 * 
 * <p>This processor ensures that all workflow beans are automatically discovered
 * and registered without requiring manual registration.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class WorkflowBeanPostProcessor implements BeanPostProcessor {
    
    private final WorkflowEngine workflowEngine;
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // Check if the bean class is annotated with @Workflow
        Workflow workflowAnnotation = AnnotationUtils.findAnnotation(bean.getClass(), Workflow.class);
        
        if (workflowAnnotation != null) {
            try {
                log.info("Auto-registering workflow bean: {} (id: {})", 
                    beanName, workflowAnnotation.id());
                
                // Register the workflow with the engine
                workflowEngine.register(bean);
                
                log.debug("Successfully registered workflow: {} (version: {})", 
                    workflowAnnotation.id(), workflowAnnotation.version());
                    
            } catch (Exception e) {
                log.error("Failed to register workflow bean: {} (id: {})", 
                    beanName, workflowAnnotation.id(), e);
                // Re-throw as BeansException to fail fast
                throw new BeansException("Failed to register workflow: " + workflowAnnotation.id(), e) {};
            }
        }
        
        return bean;
    }
}