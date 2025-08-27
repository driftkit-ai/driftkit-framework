package ai.driftkit.workflow.test.junit;

import ai.driftkit.workflow.test.core.WorkflowTestBase;
import ai.driftkit.workflow.test.core.WorkflowTestContext;
import ai.driftkit.workflow.test.core.WorkflowTestOrchestrator;
import ai.driftkit.workflow.test.core.ExecutionTracker;
import ai.driftkit.workflow.test.core.MockRegistry;
import ai.driftkit.workflow.test.core.WorkflowTestInterceptor;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.MockUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JUnit 5 extension for workflow testing.
 * Provides automatic setup and injection of test components.
 */
@Slf4j
public class WorkflowTestExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver, TestInstancePostProcessor {
    
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(WorkflowTestExtension.class);
    private static final String TEST_CONTEXT_KEY = "workflow.test.context";
    private static final String ORCHESTRATOR_KEY = "workflow.test.orchestrator";
    private static final String ENGINE_KEY = "workflow.test.engine";
    private static final String MOCKITO_SESSION_KEY = "mockito.session";
    
    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        Objects.requireNonNull(testInstance, "testInstance cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        
        // Initialize Mockito annotations
        AutoCloseable mockitoSession = MockitoAnnotations.openMocks(testInstance);
        context.getStore(NAMESPACE).put(MOCKITO_SESSION_KEY, mockitoSession);
        
        log.debug("Initialized Mockito for test instance: {}", testInstance.getClass().getName());
    }
    
    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Objects.requireNonNull(context, "context cannot be null");
        
        Object testInstance = context.getRequiredTestInstance();
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        
        try {
            // Create test components
            WorkflowTestContext testContext = createTestContext();
            WorkflowEngine engine = createWorkflowEngine();
            WorkflowTestOrchestrator orchestrator = createOrchestrator(testContext, engine);
            
            // Store components
            store.put(TEST_CONTEXT_KEY, testContext);
            store.put(ENGINE_KEY, engine);
            store.put(ORCHESTRATOR_KEY, orchestrator);
            
            // Inject into test instance if it extends WorkflowTestBase
            if (testInstance instanceof WorkflowTestBase base) {
                injectTestBase(base, testContext, engine);
            }
            
            // Process workflow mock annotations
            processWorkflowMockAnnotations(testInstance, testContext);
            
            // Process workflow under test annotations
            processWorkflowUnderTestAnnotations(testInstance, engine);
            
            log.info("Workflow test setup completed for: {}", context.getDisplayName());
            
        } catch (Exception e) {
            log.error("Failed to setup workflow test", e);
            throw new ExtensionConfigurationException("Failed to setup workflow test", e);
        }
    }
    
    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        Objects.requireNonNull(context, "context cannot be null");
        
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        
        try {
            // Cleanup test context
            WorkflowTestContext testContext = store.get(TEST_CONTEXT_KEY, WorkflowTestContext.class);
            if (testContext != null) {
                testContext.reset();
                log.debug("Test context reset completed");
            }
            
            // Cleanup Mockito session
            AutoCloseable mockitoSession = store.get(MOCKITO_SESSION_KEY, AutoCloseable.class);
            if (mockitoSession != null) {
                mockitoSession.close();
                log.debug("Mockito session closed");
            }
            
            // Clear store
            store.remove(TEST_CONTEXT_KEY);
            store.remove(ENGINE_KEY);
            store.remove(ORCHESTRATOR_KEY);
            store.remove(MOCKITO_SESSION_KEY);
            
        } catch (Exception e) {
            log.error("Failed to cleanup workflow test", e);
            throw new RuntimeException("Failed to cleanup workflow test", e);
        }
    }
    
    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Objects.requireNonNull(parameterContext, "parameterContext cannot be null");
        Objects.requireNonNull(extensionContext, "extensionContext cannot be null");
        
        Class<?> type = parameterContext.getParameter().getType();
        return type == WorkflowTestContext.class || 
               type == WorkflowTestOrchestrator.class ||
               type == WorkflowEngine.class;
    }
    
    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Objects.requireNonNull(parameterContext, "parameterContext cannot be null");
        Objects.requireNonNull(extensionContext, "extensionContext cannot be null");
        
        Class<?> type = parameterContext.getParameter().getType();
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        
        if (type == WorkflowTestContext.class) {
            return Objects.requireNonNull(
                store.get(TEST_CONTEXT_KEY, WorkflowTestContext.class),
                "WorkflowTestContext not initialized"
            );
        }
        
        if (type == WorkflowTestOrchestrator.class) {
            return Objects.requireNonNull(
                store.get(ORCHESTRATOR_KEY, WorkflowTestOrchestrator.class),
                "WorkflowTestOrchestrator not initialized"
            );
        }
        
        if (type == WorkflowEngine.class) {
            return Objects.requireNonNull(
                store.get(ENGINE_KEY, WorkflowEngine.class),
                "WorkflowEngine not initialized"
            );
        }
        
        throw new ParameterResolutionException("Unsupported parameter type: " + type);
    }
    
    private WorkflowTestContext createTestContext() {
        return new WorkflowTestContext();
    }
    
    private WorkflowEngine createWorkflowEngine() {
        WorkflowEngine engine = new WorkflowEngine();
        return engine;
    }
    
    private WorkflowTestOrchestrator createOrchestrator(WorkflowTestContext testContext, WorkflowEngine engine) {
        // Create and set the test interceptor
        WorkflowTestInterceptor interceptor = new WorkflowTestInterceptor(
            testContext.getMockRegistry(),
            testContext.getExecutionTracker()
        );
        testContext.setTestInterceptor(interceptor);
        
        return new WorkflowTestOrchestrator(
            testContext.getMockRegistry(),
            testContext.getExecutionTracker(),
            interceptor,
            engine
        );
    }
    
    private void injectTestBase(WorkflowTestBase base, WorkflowTestContext testContext, WorkflowEngine engine) {
        Objects.requireNonNull(base, "base cannot be null");
        Objects.requireNonNull(testContext, "testContext cannot be null");
        Objects.requireNonNull(engine, "engine cannot be null");
        
        try {
            // Use reflection to call protected setup method
            Method setupMethod = WorkflowTestBase.class.getDeclaredMethod("setup", WorkflowTestContext.class, WorkflowEngine.class);
            setupMethod.setAccessible(true);
            setupMethod.invoke(base, testContext, engine);
            
            log.debug("Successfully injected test base for: {}", base.getClass().getName());
            
        } catch (NoSuchMethodException e) {
            throw new ExtensionConfigurationException("WorkflowTestBase.setup method not found", e);
        } catch (Exception e) {
            throw new ExtensionConfigurationException("Failed to inject test base", e);
        }
    }
    
    private void processWorkflowMockAnnotations(Object testInstance, WorkflowTestContext testContext) {
        Objects.requireNonNull(testInstance, "testInstance cannot be null");
        Objects.requireNonNull(testContext, "testContext cannot be null");
        
        Class<?> testClass = testInstance.getClass();
        List<Field> mockFields = findAnnotatedFields(testClass, WorkflowMock.class);
        
        for (Field field : mockFields) {
            WorkflowMock mockAnnotation = field.getAnnotation(WorkflowMock.class);
            
            try {
                field.setAccessible(true);
                Object fieldValue = field.get(testInstance);
                
                // Verify it's a Mockito mock
                if (fieldValue == null || !MockUtil.isMock(fieldValue)) {
                    throw new ExtensionConfigurationException(
                        "Field annotated with @WorkflowMock must be a Mockito mock: " + field.getName()
                    );
                }
                
                // Register workflow mock
                String workflowId = mockAnnotation.workflow();
                String stepId = mockAnnotation.step();
                
                if (workflowId.isEmpty() || stepId.isEmpty()) {
                    throw new ExtensionConfigurationException(
                        "@WorkflowMock must specify both workflow and step: " + field.getName()
                    );
                }
                
                // Create mock configuration
                testContext.getMockBuilder()
                    .workflow(workflowId)
                    .step(stepId)
                    .mockWith(fieldValue)
                    .register();
                
                log.debug("Registered workflow mock: {}.{} -> {}", workflowId, stepId, field.getName());
                
            } catch (IllegalAccessException e) {
                throw new ExtensionConfigurationException(
                    "Failed to access @WorkflowMock field: " + field.getName(), e
                );
            }
        }
    }
    
    private void processWorkflowUnderTestAnnotations(Object testInstance, WorkflowEngine engine) {
        Objects.requireNonNull(testInstance, "testInstance cannot be null");
        Objects.requireNonNull(engine, "engine cannot be null");
        
        Class<?> testClass = testInstance.getClass();
        List<Field> workflowFields = findAnnotatedFields(testClass, WorkflowUnderTest.class);
        
        if (workflowFields.size() > 1) {
            throw new ExtensionConfigurationException(
                "Only one field can be annotated with @WorkflowUnderTest"
            );
        }
        
        for (Field field : workflowFields) {
            try {
                field.setAccessible(true);
                Object workflow = field.get(testInstance);
                
                if (workflow == null) {
                    // Create instance if null
                    workflow = createWorkflowInstance(field.getType());
                    field.set(testInstance, workflow);
                }
                
                // Register with engine
                engine.register(workflow);
                
                // Inject mocks into workflow
                injectMocksIntoWorkflow(workflow, testInstance);
                
                log.debug("Registered workflow under test: {}", workflow.getClass().getName());
                
            } catch (Exception e) {
                throw new ExtensionConfigurationException(
                    "Failed to process @WorkflowUnderTest field: " + field.getName(), e
                );
            }
        }
    }
    
    private Object createWorkflowInstance(Class<?> workflowClass) {
        try {
            return workflowClass.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            throw new ExtensionConfigurationException(
                "Workflow class must have a no-args constructor: " + workflowClass.getName(), e
            );
        } catch (Exception e) {
            throw new ExtensionConfigurationException(
                "Failed to create workflow instance: " + workflowClass.getName(), e
            );
        }
    }
    
    private void injectMocksIntoWorkflow(Object workflow, Object testInstance) {
        Objects.requireNonNull(workflow, "workflow cannot be null");
        Objects.requireNonNull(testInstance, "testInstance cannot be null");
        
        Class<?> workflowClass = workflow.getClass();
        Class<?> testClass = testInstance.getClass();
        
        // Find all mocks in test instance
        for (Field testField : testClass.getDeclaredFields()) {
            if (testField.isAnnotationPresent(Mock.class)) {
                try {
                    testField.setAccessible(true);
                    Object mockValue = testField.get(testInstance);
                    
                    if (mockValue != null) {
                        // Try to inject into workflow
                        injectMockIntoWorkflowField(workflow, workflowClass, testField.getType(), mockValue);
                    }
                    
                } catch (IllegalAccessException e) {
                    log.warn("Failed to access mock field: {}", testField.getName(), e);
                }
            }
        }
    }
    
    private void injectMockIntoWorkflowField(Object workflow, Class<?> workflowClass, Class<?> mockType, Object mockValue) {
        // Look for matching field in workflow
        for (Field workflowField : workflowClass.getDeclaredFields()) {
            if (workflowField.getType().isAssignableFrom(mockType)) {
                try {
                    workflowField.setAccessible(true);
                    workflowField.set(workflow, mockValue);
                    log.debug("Injected mock into workflow field: {}", workflowField.getName());
                    return;
                } catch (IllegalAccessException e) {
                    log.warn("Failed to inject mock into workflow field: {}", workflowField.getName(), e);
                }
            }
        }
    }
    
    private List<Field> findAnnotatedFields(Class<?> clazz, Class<? extends java.lang.annotation.Annotation> annotationClass) {
        List<Field> fields = new ArrayList<>();
        
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(annotationClass)) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        
        return fields;
    }
}