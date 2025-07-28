package ai.driftkit.context.spring.testsuite.service;

import ai.driftkit.common.domain.ImageMessageTask;
import ai.driftkit.common.domain.MessageTask;
import ai.driftkit.context.core.util.PromptUtils;
import ai.driftkit.context.spring.testsuite.domain.TestSet;
import ai.driftkit.context.spring.testsuite.domain.TestSetItem;
import ai.driftkit.context.spring.testsuite.domain.archive.TestSetItemImpl;
import ai.driftkit.context.spring.testsuite.repository.TestSetItemRepository;
import ai.driftkit.context.spring.testsuite.repository.TestSetRepository;
import ai.driftkit.workflows.spring.domain.ImageMessageTaskEntity;
import ai.driftkit.workflows.spring.domain.ModelRequestTrace;
import ai.driftkit.workflows.spring.repository.ImageTaskRepository;
import ai.driftkit.workflows.spring.repository.MessageTaskRepository;
import ai.driftkit.workflows.spring.domain.MessageTaskEntity;
import ai.driftkit.workflows.spring.repository.ModelRequestTraceRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TestSetService {

    private final TestSetRepository testSetRepository;
    private final TestSetItemRepository testSetItemRepository;
    private final MessageTaskRepository messageTaskRepository;
    private final ModelRequestTraceRepository modelRequestTraceRepository;
    private final ImageTaskRepository imageTaskRepository;
    
    public List<TestSet> getAllTestSets() {
        return testSetRepository.findAllByOrderByCreatedAtDesc();
    }
    
    public List<TestSet> getTestSetsByFolder(String folderId) {
        if (folderId == null) {
            return testSetRepository.findByFolderIdIsNullOrderByCreatedAtDesc();
        }
        return testSetRepository.findByFolderIdOrderByCreatedAtDesc(folderId);
    }
    
    public Optional<TestSet> getTestSetById(String id) {
        return testSetRepository.findById(id);
    }
    
    public List<TestSetItem> getTestSetItems(String testSetId) {
        return testSetItemRepository.findByTestSetIdOrderByCreatedAtDesc(testSetId).stream()
                .map(TestSetItem.class::cast)
                .toList();
    }
    
    public TestSet createTestSet(TestSet testSet) {
        testSet.setId(null);
        testSet.setCreatedAt(System.currentTimeMillis());
        testSet.setUpdatedAt(System.currentTimeMillis());
        return testSetRepository.save(testSet);
    }
    
    public Optional<TestSet> updateTestSet(String id, TestSet testSet) {
        if (!testSetRepository.existsById(id)) {
            return Optional.empty();
        }
        testSet.setId(id);
        testSet.setUpdatedAt(System.currentTimeMillis());
        return Optional.of(testSetRepository.save(testSet));
    }
    
    public boolean deleteTestSet(String id) {
        if (!testSetRepository.existsById(id)) {
            return false;
        }
        testSetItemRepository.deleteByTestSetId(id);
        testSetRepository.deleteById(id);
        return true;
    }
    
    public AddItemsResult addItemsToTestSet(
            List<String> messageTaskIds, 
            List<TraceStep> traceSteps,
            List<String> imageTaskIds,
            String testSetId) {
        
        Optional<TestSet> testSetOpt = testSetRepository.findById(testSetId).map(TestSet.class::cast);
        if (testSetOpt.isEmpty()) {
            return new AddItemsResult(Status.TEST_SET_NOT_FOUND, null, 0);
        }
        
        TestSet testSet = testSetOpt.get();
        List<TestSetItemImpl> testSetItems = new ArrayList<>();
        
        // Process message tasks if provided
        if (messageTaskIds != null && !messageTaskIds.isEmpty()) {
            List<MessageTask> messageTasks = messageTaskRepository.findAllById(messageTaskIds)
                .stream()
                .map(MessageTaskEntity::toMessageTask)
                .collect(java.util.stream.Collectors.toList());
            
            for (MessageTask messageTask : messageTasks) {
                boolean isImageMessage = messageTask.getMessage() != null && 
                        messageTask.getMessage().toLowerCase().startsWith("image:");
                boolean hasImageTaskId = messageTask.getImageTaskId() != null;
                
                TestSetItemImpl.TestSetItemImplBuilder builder = TestSetItemImpl.builder()
                        .testSetId(testSet.getId())
                        .originalMessageTaskId(messageTask.getMessageId())
                        .message(messageTask.getMessage())
                        .result(messageTask.getResult())
                        .variables(messageTask.getVariables() != null ? 
                                PromptUtils.convertVariables(messageTask.getVariables()) : null
                        )
                        .model(messageTask.getModelId())
                        .temperature(messageTask.getTemperature())
                        .workflowType(messageTask.getWorkflow())
                        .promptId(messageTask.getPromptIds() != null && !messageTask.getPromptIds().isEmpty() ? 
                                messageTask.getPromptIds().get(0) : null)
                        .isImageTask(isImageMessage || hasImageTaskId)
                        .createdAt(System.currentTimeMillis());
                
                if (hasImageTaskId) {
                    builder.originalImageTaskId(messageTask.getImageTaskId());
                }
                
                TestSetItemImpl testSetItem = builder.build();
                testSetItems.add(testSetItem);
            }
        }
        
        // Process image tasks if provided
        if (imageTaskIds != null && !imageTaskIds.isEmpty()) {
            List<ImageMessageTaskEntity> imageTasks = imageTaskRepository.findAllById(imageTaskIds);
            
            for (ImageMessageTask imageTask : imageTasks) {
                TestSetItemImpl testSetItem = TestSetItemImpl.builder()
                        .testSetId(testSet.getId())
                        .originalImageTaskId(imageTask.getMessageId())
                        .message(imageTask.getMessage())
                        .result(String.format("Generated %d image(s)", 
                                imageTask.getImages() != null ? imageTask.getImages().size() : 0))
                        .variables(imageTask.getVariables() != null ? 
                                PromptUtils.convertVariables(imageTask.getVariables()) : null
                        )
                        .model(null)
                        .workflowType(imageTask.getWorkflow())
                        .isImageTask(true)
                        .createdAt(System.currentTimeMillis())
                        .build();
                
                testSetItems.add(testSetItem);
            }
        }
        
        // Process trace steps if provided
        if (traceSteps != null && !traceSteps.isEmpty()) {
            for (TraceStep step : traceSteps) {
                Optional<ModelRequestTrace> traceOpt = modelRequestTraceRepository.findById(step.getTraceId());
                
                if (traceOpt.isPresent()) {
                    ModelRequestTrace trace = traceOpt.get();
                    
                    boolean isImageGeneration = trace.getRequestType() == ModelRequestTrace.RequestType.TEXT_TO_IMAGE;
                    boolean isImageMessage = trace.getPromptTemplate() != null && 
                            trace.getPromptTemplate().toLowerCase().startsWith("image:");
                    boolean isImageTaskContext = trace.getContextType() == ModelRequestTrace.ContextType.IMAGE_TASK;
                    boolean isImageTask = isImageGeneration || isImageMessage || isImageTaskContext;
                    
                    TestSetItemImpl.TestSetItemImplBuilder builder = TestSetItemImpl.builder()
                            .testSetId(testSet.getId())
                            .originalTraceId(trace.getId())
                            .message(trace.getPromptTemplate())
                            .result(trace.getResponse())
                            .variables(trace.getVariables())
                            .model(trace.getModelId())
                            .promptId(trace.getPromptId())
                            .workflowType(trace.getWorkflowInfo() != null ? trace.getWorkflowInfo().getWorkflowType() : null)
                            .isImageTask(isImageTask)
                            .createdAt(System.currentTimeMillis());
                    
                    if (isImageTask && trace.getContextId() != null) {
                        builder.originalImageTaskId(trace.getContextId());
                    }
                    
                    TestSetItemImpl testSetItem = builder.build();
                    testSetItems.add(testSetItem);
                }
            }
        }
        
        if (testSetItems.isEmpty()) {
            return new AddItemsResult(Status.NO_ITEMS_FOUND, testSet, 0);
        }
        
        testSetItemRepository.saveAll(testSetItems);
        
        return new AddItemsResult(Status.SUCCESS, testSet, testSetItems.size());
    }
    
    public boolean deleteTestSetItem(String testSetId, String itemId) {
        Optional<TestSetItemImpl> itemOpt = testSetItemRepository.findById(itemId).map(TestSetItemImpl.class::cast);
        if (itemOpt.isEmpty() || !itemOpt.get().getTestSetId().equals(testSetId)) {
            return false;
        }
        
        testSetItemRepository.deleteById(itemId);
        return true;
    }
    
    public boolean moveTestSetsToFolder(List<String> testSetIds, String folderId) {
        if (testSetIds == null || testSetIds.isEmpty()) {
            return false;
        }
        
        List<TestSet> testSets = testSetRepository.findAllById(testSetIds).stream()
                .map(TestSet.class::cast)
                .toList();
        if (testSets.isEmpty()) {
            return false;
        }
        
        for (TestSet testSet : testSets) {
            testSet.setFolderId(folderId);
            testSet.setUpdatedAt(System.currentTimeMillis());
        }
        
        testSetRepository.saveAll(testSets);
        return true;
    }
    
    @Data
    public static class TraceStep {
        private String traceId;
    }
    
    public enum Status {
        SUCCESS,
        TEST_SET_NOT_FOUND,
        NO_MESSAGE_TASKS_FOUND,
        NO_ITEMS_FOUND
    }
    
    @Data
    public static class AddItemsResult {
        private final Status status;
        private final TestSet testSet;
        private final int itemsAdded;
        private final String message;
        
        public AddItemsResult(Status status, TestSet testSet, int itemsAdded) {
            this.status = status;
            this.testSet = testSet;
            this.itemsAdded = itemsAdded;
            
            this.message = switch (status) {
                case SUCCESS -> "Added " + itemsAdded + " items to TestSet";
                case TEST_SET_NOT_FOUND -> "TestSet not found";
                case NO_MESSAGE_TASKS_FOUND -> "No valid message tasks found";
                case NO_ITEMS_FOUND -> "No valid items found";
            };
        }
    }
}