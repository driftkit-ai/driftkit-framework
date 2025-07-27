package ai.driftkit.workflows.spring.service;


import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.MessageTask;
import ai.driftkit.workflows.spring.domain.MessageTaskEntity;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.workflows.spring.domain.ModelRequestTrace;
import ai.driftkit.workflows.spring.repository.MessageTaskRepository;
import ai.driftkit.workflows.spring.repository.ModelRequestTraceRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    private ModelRequestTraceRepository modelRequestTraceRepository;

    @Autowired
    private MessageTaskRepository messageTaskRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private PromptService promptService;
    private Map<String, DailyMetricsResponse> dailyMetricsCache;

    @PostConstruct
    public void init() {
        this.dailyMetricsCache = new ConcurrentHashMap<>();
    }

    @Scheduled(fixedRate = 8 * 60000)
    public void dailyMetrics() {
        getDailyMetrics(LocalDate.now().minusDays(1), LocalDate.now());
    }

    /**
     * Get model request traces within a time range
     * @param startTime Start of time range
     * @param endTime End of time range
     * @param promptId Optional prompt method filter
     * @param excludePurpose Optional comma-separated list of purpose keywords to exclude
     * @param page Page number
     * @param size Page size
     * @return Page of traces
     */
    public Page<ModelRequestTrace> getTraces(LocalDateTime startTime, LocalDateTime endTime, String promptId, String excludePurpose, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());

        // Default to today if not specified
        Criteria criteria = getDatesCriteria(startTime, endTime);

        // Add promptId filter if specified
        if (StringUtils.isNotBlank(promptId)) {
            promptId = getPromptIdByMethod(promptId);
            criteria = criteria.and("promptId").is(promptId);
        }
        
        // Add purpose exclusion filter if specified
        if (StringUtils.isNotBlank(excludePurpose)) {
            List<String> purposesToExclude = Arrays.asList(excludePurpose.split(","));
            
            // Create criteria that matches documents where either:
            // 1. purpose field doesn't exist, OR
            // 2. purpose field exists but doesn't match any of the excluded values
            Criteria purposeCriteria = new Criteria().orOperator(
                Criteria.where("purpose").exists(false),
                Criteria.where("purpose").not().regex(String.join("|", purposesToExclude), "i")
            );
            
            criteria = criteria.andOperator(purposeCriteria);
        }

        List<ModelRequestTrace> traces = getTraces(criteria, pageable);

        // Count total for pagination
        long total = mongoTemplate.count(
                Query.query(criteria),
                ModelRequestTrace.class);

        return new PageImpl<>(
                traces, pageable, total);
    }

    @NotNull
    private List<ModelRequestTrace> getTraces(Criteria criteria, Pageable pageable) {
        Aggregation agg = pageable == null ? Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.sort(Sort.by("timestamp").descending())
        ) : Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.sort(Sort.by("timestamp").descending()),
                Aggregation.skip((long) pageable.getPageNumber() * pageable.getPageSize()),
                Aggregation.limit(pageable.getPageSize())
        );

        AggregationResults<ModelRequestTrace> results = mongoTemplate.aggregate(
                agg, "model_request_traces", ModelRequestTrace.class);

        List<ModelRequestTrace> traces = results.getMappedResults();
        return traces;
    }

    @NotNull
    private static Criteria getDatesCriteria(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        // Convert dates to timestamps
        long startTimestamp = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - TimeUnit.DAYS.toMillis(1);
        long endTimestamp = endTime.plusDays(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;

        // Use MongoDB Criteria to find traces within time range
        Criteria criteria = Criteria.where("timestamp").gte(startTimestamp).lte(endTimestamp);
        return criteria;
    }

    /**
     * Get traces by context ID
     */
    public List<ModelRequestTrace> getTracesByContextId(String contextId) {
        List<ModelRequestTrace> traces = modelRequestTraceRepository.findByContextId(contextId);
        traces.sort(Comparator.comparing(ModelRequestTrace::getTimestamp));
        return traces;
    }

    /**
     * Get available prompt methods for analytics
     */
    public List<String> getAvailablePromptMethods() {
        List<Prompt> prompts = promptService.getPrompts();
        return prompts.stream()
                .map(Prompt::getMethod)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
    
    /**
     * Get message tasks by context IDs
     * 
     * @param contextIds List of context IDs (same as messageIds)
     * @return List of TaskVariables objects containing task data
     */
    public List<TaskVariables> getMessageTasksByContextIds(List<String> contextIds) {
        List<MessageTaskEntity> entities = messageTaskRepository.findAllByMessageIdIn(contextIds);
        List<MessageTask> tasks = entities.stream()
                .map(MessageTaskEntity::toMessageTask)
                .collect(Collectors.toList());
        
        return tasks.stream()
                .map(task -> TaskVariables.builder()
                        .messageId(task.getMessageId())
                        .contextId(task.getMessageId()) // Same as messageId for use in the UI
                        .message(task.getMessage())
                        .result(task.getResult())
                        .modelId(task.getModelId())
                        .variables(task.getVariables())
                        .createdTime(task.getCreatedTime())
                        .responseTime(task.getResponseTime())
                        .promptIds(task.getPromptIds())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get daily metrics for the dashboard based on MessageTask and promptIds.
     * 
     * @param startDate Start date for the metrics range
     * @param endDate End date for the metrics range
     * @return DailyMetricsResponse object containing all metrics
     */
    public DailyMetricsResponse getDailyMetrics(LocalDate startDate, LocalDate endDate) {
        // Default to today if not specified
        if (startDate == null) {
            startDate = LocalDate.now();
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        String dailyCacheKey = startDate + " " + endDate;

        DailyMetricsResponse dailyMetricsResponse = dailyMetricsCache.get(dailyCacheKey);

        if (dailyMetricsResponse != null && System.currentTimeMillis() - dailyMetricsResponse.getTimestamp() < 10 * 60 * 1000) {
            return dailyMetricsResponse;
        }

        // Convert dates to timestamps
        long startTimestamp = startDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endTimestamp = endDate.plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;
        
        // Query message tasks
        Criteria taskCriteria = Criteria.where("createdTime").gte(startTimestamp).lte(endTimestamp);
        Query taskQuery = Query.query(taskCriteria);
        List<MessageTaskEntity> entities = mongoTemplate.find(taskQuery, MessageTaskEntity.class);
        List<MessageTask> tasks = entities.stream()
                .map(MessageTaskEntity::toMessageTask)
                .collect(Collectors.toList());
        
        // Query model request traces for token usage data
        // The contextId in trace equals messageId in MessageTask
        Criteria traceCriteria = Criteria.where("timestamp").gte(startTimestamp).lte(endTimestamp);
        Query traceQuery = Query.query(traceCriteria);
        List<ModelRequestTrace> traces = mongoTemplate.find(traceQuery, ModelRequestTrace.class);
        
        // Create a map of messageId to traces for easy lookup
        Map<String, List<ModelRequestTrace>> tracesByMessageId = traces.stream()
                .collect(Collectors.groupingBy(ModelRequestTrace::getContextId));
        
        // Get all prompt IDs from tasks to load their corresponding Prompt records
        List<String> allPromptIds = tasks.stream()
                .filter(t -> t.getPromptIds() != null && !t.getPromptIds().isEmpty())
                .flatMap(t -> t.getPromptIds().stream())
                .distinct()
                .collect(Collectors.toList());
        
        // Load all Prompt objects needed for the method field
        List<Prompt> prompts = promptService.getPromptsByIds(allPromptIds);
        
        // Create a map of promptId -> method for conversion
        Map<String, String> promptIdToMethodMap = prompts.stream()
                .collect(Collectors.toMap(Prompt::getId, Prompt::getMethod, (m1, m2) -> m1));
        
        // Calculate overall metrics
        Map<String, Object> metrics = new HashMap<>();
        
        // --- Overall metrics ---
        // Request counts
        metrics.put("totalTasks", tasks.size());
        
        // Token usage from traces
        int totalPromptTokens = traces.stream()
                .filter(t -> t.getTrace() != null)
                .mapToInt(t -> t.getTrace().getPromptTokens())
                .sum();
        
        int totalCompletionTokens = traces.stream()
                .filter(t -> t.getTrace() != null)
                .mapToInt(t -> t.getTrace().getCompletionTokens())
                .sum();
        
        metrics.put("totalPromptTokens", totalPromptTokens);
        metrics.put("totalCompletionTokens", totalCompletionTokens);
        
        // --- Latency metrics ---
        // Overall latency calculated from tasks
        List<Long> latencies = tasks.stream()
                .filter(t -> t.getResponseTime() > 0 && t.getCreatedTime() > 0)
                .map(t -> t.getResponseTime() - t.getCreatedTime())
                .sorted()
                .collect(Collectors.toList());
        
        metrics.put("latencyPercentiles", calculatePercentiles(latencies));
        
        // --- Group metrics ---
        // By Model
        Map<String, Long> tasksByModel = tasks.stream()
                .filter(t -> t.getModelId() != null && !t.getModelId().isEmpty())
                .collect(Collectors.groupingBy(MessageTask::getModelId, Collectors.counting()));
        
        metrics.put("tasksByModel", tasksByModel);
        
        // Extract all promptIds from tasks, map them to method names, and count occurrences
        Map<String, Long> tasksByPromptMethod = new HashMap<>();
        
        // Iterate through tasks to count by prompt method
        tasks.stream()
                .filter(t -> t.getPromptIds() != null && !t.getPromptIds().isEmpty())
                .forEach(task -> {
                    for (String promptId : task.getPromptIds()) {
                        // Convert promptId to method name (if available)
                        String method = promptIdToMethodMap.getOrDefault(promptId, promptId);
                        tasksByPromptMethod.merge(method, 1L, Long::sum);
                    }
                });
        
        metrics.put("tasksByPromptMethod", tasksByPromptMethod);
        
        // --- Detailed metrics with group breakdowns ---
        
        // 1. Token usage by prompt method
        Map<String, Map<String, Integer>> tokensByPromptMethod = new HashMap<>();
        tokensByPromptMethod.put("promptTokens", new HashMap<>());
        tokensByPromptMethod.put("completionTokens", new HashMap<>());
        
        // Process tasks and their associated traces to get token usage by prompt method
        tasks.forEach(task -> {
            if (task.getPromptIds() != null && !task.getPromptIds().isEmpty()) {
                // Get traces associated with this task via messageId = contextId
                List<ModelRequestTrace> taskTraces = tracesByMessageId.getOrDefault(task.getMessageId(), Collections.emptyList());
                
                // Calculate total tokens for this task
                int taskPromptTokens = taskTraces.stream()
                        .filter(t -> t.getTrace() != null)
                        .mapToInt(t -> t.getTrace().getPromptTokens())
                        .sum();
                
                int taskCompletionTokens = taskTraces.stream()
                        .filter(t -> t.getTrace() != null)
                        .mapToInt(t -> t.getTrace().getCompletionTokens())
                        .sum();
                
                // Distribute tokens equally among prompt methods for this task
                if (!task.getPromptIds().isEmpty() && (taskPromptTokens > 0 || taskCompletionTokens > 0)) {
                    for (String promptId : task.getPromptIds()) {
                        // Convert promptId to method name
                        String method = promptIdToMethodMap.getOrDefault(promptId, promptId);
                        
                        // Add proportional prompt tokens (divided by number of prompts in the task)
                        if (taskPromptTokens > 0) {
                            int promptTokenPerMethod = taskPromptTokens / task.getPromptIds().size();
                            tokensByPromptMethod.get("promptTokens").merge(method, promptTokenPerMethod, Integer::sum);
                        }
                        
                        // Add proportional completion tokens
                        if (taskCompletionTokens > 0) {
                            int completionTokenPerMethod = taskCompletionTokens / task.getPromptIds().size();
                            tokensByPromptMethod.get("completionTokens").merge(method, completionTokenPerMethod, Integer::sum);
                        }
                    }
                }
            }
        });
        
        metrics.put("tokensByPromptMethod", tokensByPromptMethod);
        
        // 2. Token usage by promptMethod+model
        Map<String, Map<String, Integer>> tokensByPromptMethodModel = new HashMap<>();
        tokensByPromptMethodModel.put("promptTokens", new HashMap<>());
        tokensByPromptMethodModel.put("completionTokens", new HashMap<>());
        
        // Process tasks for token usage by prompt method and model
        tasks.forEach(task -> {
            if (task.getPromptIds() != null && !task.getPromptIds().isEmpty() && 
                task.getModelId() != null && !task.getModelId().isEmpty()) {
                
                // Get traces associated with this task
                List<ModelRequestTrace> taskTraces = tracesByMessageId.getOrDefault(task.getMessageId(), Collections.emptyList());
                
                // Calculate total tokens for this task
                int taskPromptTokens = taskTraces.stream()
                        .filter(t -> t.getTrace() != null)
                        .mapToInt(t -> t.getTrace().getPromptTokens())
                        .sum();
                
                int taskCompletionTokens = taskTraces.stream()
                        .filter(t -> t.getTrace() != null)
                        .mapToInt(t -> t.getTrace().getCompletionTokens())
                        .sum();
                
                // Add tokens for each prompt method + model combination
                if (taskPromptTokens > 0 || taskCompletionTokens > 0) {
                    for (String promptId : task.getPromptIds()) {
                        // Convert promptId to method name
                        String method = promptIdToMethodMap.getOrDefault(promptId, promptId);
                        String statKey = method + ":" + task.getModelId();
                        
                        // Add proportional tokens
                        if (taskPromptTokens > 0) {
                            int promptTokenPerMethod = taskPromptTokens / task.getPromptIds().size();
                            tokensByPromptMethodModel.get("promptTokens").merge(statKey, promptTokenPerMethod, Integer::sum);
                        }
                        
                        if (taskCompletionTokens > 0) {
                            int completionTokenPerMethod = taskCompletionTokens / task.getPromptIds().size();
                            tokensByPromptMethodModel.get("completionTokens").merge(statKey, completionTokenPerMethod, Integer::sum);
                        }
                    }
                }
            }
        });
        
        metrics.put("tokensByPromptMethodModel", tokensByPromptMethodModel);
        
        // 3. Latency by prompt method
        Map<String, Map<String, Long>> latencyByPromptMethod = new HashMap<>();
        
        // Group tasks by prompt method
        Map<String, List<MessageTask>> tasksByPromptMethodGroup = new HashMap<>();
        
        tasks.forEach(task -> {
            if (task.getPromptIds() != null && !task.getPromptIds().isEmpty()) {
                for (String promptId : task.getPromptIds()) {
                    // Convert promptId to method
                    String method = promptIdToMethodMap.getOrDefault(promptId, promptId);
                    
                    // Add task to the method's list
                    if (!tasksByPromptMethodGroup.containsKey(method)) {
                        tasksByPromptMethodGroup.put(method, new ArrayList<>());
                    }
                    tasksByPromptMethodGroup.get(method).add(task);
                }
            }
        });
        
        // Calculate latency percentiles for each prompt method
        tasksByPromptMethodGroup.forEach((method, methodTasks) -> {
            List<Long> methodLatencies = methodTasks.stream()
                    .filter(t -> t.getResponseTime() > 0 && t.getCreatedTime() > 0)
                    .map(t -> t.getResponseTime() - t.getCreatedTime())
                    .sorted()
                    .collect(Collectors.toList());
            
            Map<String, Long> percentiles = calculatePercentiles(methodLatencies);
            if (!percentiles.isEmpty()) {
                latencyByPromptMethod.put(method, percentiles);
            }
        });
        
        metrics.put("latencyByPromptMethod", latencyByPromptMethod);
        
        // 3.1. Success/Error counts by prompt method, grouped by contextId
        Map<String, Long> successByPromptMethod = new HashMap<>(); 
        Map<String, Long> errorsByPromptMethod = new HashMap<>();
        
        // First group traces by contextId to count each context as a single unit
        Map<String, List<ModelRequestTrace>> allTracesByContextId = traces.stream()
                .filter(t -> t.getContextId() != null && !t.getContextId().isEmpty())
                .collect(Collectors.groupingBy(ModelRequestTrace::getContextId));
        
        // Group context-based traces by prompt method
        Map<String, Map<String, List<ModelRequestTrace>>> contextsByPromptMethod = new HashMap<>();
        
        // For each context, create a mapping of prompt methods to traces
        allTracesByContextId.forEach((contextId, contextTraces) -> {
            contextTraces.forEach(trace -> {
                if (trace.getPromptId() != null && trace.getTrace() != null) {
                    // Convert promptId to method
                    String method = promptIdToMethodMap.getOrDefault(trace.getPromptId(), trace.getPromptId());
                    
                    // Initialize nested map if needed
                    if (!contextsByPromptMethod.containsKey(method)) {
                        contextsByPromptMethod.put(method, new HashMap<>());
                    }
                    
                    // Add this trace to the mapping for this method and contextId
                    if (!contextsByPromptMethod.get(method).containsKey(contextId)) {
                        contextsByPromptMethod.get(method).put(contextId, new ArrayList<>());
                    }
                    contextsByPromptMethod.get(method).get(contextId).add(trace);
                }
            });
        });
        
        // Count success/error for each prompt method based on contexts
        contextsByPromptMethod.forEach((method, contextMap) -> {
            long methodSuccessCount = 0;
            long methodErrorCount = 0;
            
            // For each context, check if any trace has an error
            for (List<ModelRequestTrace> contextTraces : contextMap.values()) {
                boolean hasError = contextTraces.stream()
                        .anyMatch(t -> t.getTrace() != null && t.getTrace().isHasError());
                
                if (hasError) {
                    methodErrorCount++;
                } else {
                    methodSuccessCount++;
                }
            }
            
            successByPromptMethod.put(method, methodSuccessCount);
            errorsByPromptMethod.put(method, methodErrorCount);
        });
        
        // Calculate success rate for each prompt method
        Map<String, Double> successRateByPromptMethod = new HashMap<>();
        
        for (String method : contextsByPromptMethod.keySet()) {
            long methodSuccess = successByPromptMethod.getOrDefault(method, 0L);
            long methodError = errorsByPromptMethod.getOrDefault(method, 0L);
            long total = methodSuccess + methodError;
            
            double rate = total > 0 ? (double) methodSuccess / total : 0.0;
            successRateByPromptMethod.put(method, rate);
        }
        
        // Add maps to metrics
        metrics.put("successByPromptMethod", successByPromptMethod);
        metrics.put("errorsByPromptMethod", errorsByPromptMethod);
        metrics.put("successRateByPromptMethod", successRateByPromptMethod);
        
        // Calculate overall success/error metrics based on contexts
        long successCount = 0;
        long errorCount = 0;
        
        for (List<ModelRequestTrace> contextTraces : allTracesByContextId.values()) {
            boolean hasError = contextTraces.stream()
                    .anyMatch(t -> t.getTrace() != null && t.getTrace().isHasError());
            
            if (hasError) {
                errorCount++;
            } else {
                successCount++;
            }
        }
        
        metrics.put("successCount", successCount);
        metrics.put("errorCount", errorCount);
        
        // Add success rate for better metrics
        double successRate = (successCount + errorCount) > 0 ? 
            (double) successCount / (successCount + errorCount) : 0;
        metrics.put("successRate", successRate);
        
        // 4. Latency by promptMethod+model
        Map<String, Map<String, Long>> latencyByPromptMethodModel = new HashMap<>();
        Map<String, List<MessageTask>> tasksByPromptMethodModelGroup = new HashMap<>();
        
        // Process tasks to get latency by prompt method and model
        tasks.forEach(task -> {
            if (task.getPromptIds() != null && !task.getPromptIds().isEmpty() && 
                task.getModelId() != null && !task.getModelId().isEmpty() &&
                task.getResponseTime() > 0 && task.getCreatedTime() > 0) {
                
                for (String promptId : task.getPromptIds()) {
                    // Convert promptId to method
                    String method = promptIdToMethodMap.getOrDefault(promptId, promptId);
                    String statKey = method + ":" + task.getModelId();
                    
                    // Add task to the method+model combination
                    if (!tasksByPromptMethodModelGroup.containsKey(statKey)) {
                        tasksByPromptMethodModelGroup.put(statKey, new ArrayList<>());
                    }
                    tasksByPromptMethodModelGroup.get(statKey).add(task);
                }
            }
        });
        
        // Calculate latency percentiles for each prompt method+model combination
        tasksByPromptMethodModelGroup.forEach((statKey, methodModelTasks) -> {
            List<Long> methodModelLatencies = methodModelTasks.stream()
                    .filter(t -> t.getResponseTime() > 0 && t.getCreatedTime() > 0)
                    .map(t -> t.getResponseTime() - t.getCreatedTime())
                    .sorted()
                    .collect(Collectors.toList());
            
            Map<String, Long> percentiles = calculatePercentiles(methodModelLatencies);
            if (!percentiles.isEmpty()) {
                latencyByPromptMethodModel.put(statKey, percentiles);
            }
        });
        
        // Create TokensByCategory objects
        TokensByCategory tokensByCategoryPromptMethod = TokensByCategory.builder()
                .promptTokens(tokensByPromptMethod.get("promptTokens"))
                .completionTokens(tokensByPromptMethod.get("completionTokens"))
                .build();
                
        TokensByCategory tokensByCategoryPromptMethodModel = TokensByCategory.builder()
                .promptTokens(tokensByPromptMethodModel.get("promptTokens"))
                .completionTokens(tokensByPromptMethodModel.get("completionTokens"))
                .build();
        
        // Convert latency percentiles maps to proper objects
        Map<String, LatencyPercentiles> latencyPercentilesByPromptMethod = new HashMap<>();
        latencyByPromptMethod.forEach((method, percentileMap) -> {
            latencyPercentilesByPromptMethod.put(method, LatencyPercentiles.fromMap(percentileMap));
        });
        
        Map<String, LatencyPercentiles> latencyPercentilesByPromptMethodModel = new HashMap<>();
        latencyByPromptMethodModel.forEach((method, percentileMap) -> {
            latencyPercentilesByPromptMethodModel.put(method, LatencyPercentiles.fromMap(percentileMap));
        });
        
        // Build and return a properly structured object
        DailyMetricsResponse response = DailyMetricsResponse.builder()
                .totalTasks(tasks.size())
                .totalPromptTokens(totalPromptTokens)
                .totalCompletionTokens(totalCompletionTokens)
                .latencyPercentiles(LatencyPercentiles.fromMap(calculatePercentiles(latencies)))
                .tasksByModel(tasksByModel)
                .tasksByPromptMethod(tasksByPromptMethod)
                .tokensByPromptMethod(tokensByCategoryPromptMethod)
                .tokensByPromptMethodModel(tokensByCategoryPromptMethodModel)
                .latencyByPromptMethod(latencyPercentilesByPromptMethod)
                .successByPromptMethod(successByPromptMethod)
                .errorsByPromptMethod(errorsByPromptMethod)
                .successRateByPromptMethod(successRateByPromptMethod)
                .successCount(successCount)
                .errorCount(errorCount)
                .successRate(successRate)
                .latencyByPromptMethodModel(latencyPercentilesByPromptMethodModel)
                .timestamp(System.currentTimeMillis())
                .build();

        this.dailyMetricsCache.put(dailyCacheKey, response);

        return response;
    }
    
    /**
     * Get metrics for a specific prompt method
     * @param startTime Start of time range
     * @param endTime End of time range
     * @param promptId The prompt method to get metrics for
     * @return PromptMetricsResponse containing all metrics
     */
    public PromptMetricsResponse getPromptMetrics(LocalDateTime startTime, LocalDateTime endTime, String promptId) {
        // Default to the last 24 hours if not specified
        Criteria criteria = getDatesCriteria(startTime, endTime);

        // Only add promptId filter if specified
        if (StringUtils.isNotBlank(promptId)) {
            promptId = getPromptIdByMethod(promptId);

            criteria = criteria.and("promptId").is(promptId);
        }

        List<ModelRequestTrace> traces = getTraces(criteria, null);

        // Basic counts
        long totalTraces = traces.size();
        
        // Token usage
        int totalPromptTokens = traces.stream()
                .filter(t -> t.getTrace() != null)
                .mapToInt(t -> t.getTrace().getPromptTokens())
                .sum();
        
        int totalCompletionTokens = traces.stream()
                .filter(t -> t.getTrace() != null)
                .mapToInt(t -> t.getTrace().getCompletionTokens())
                .sum();
        
        // Latency metrics
        List<Long> latencies = traces.stream()
                .filter(t -> t.getTrace() != null)
                .map(t -> t.getTrace().getExecutionTimeMs())
                .sorted()
                .collect(Collectors.toList());
        
        // By Model breakdown
        Map<String, Long> tracesByModel = traces.stream()
                .filter(t -> t.getModelId() != null && !t.getModelId().isEmpty())
                .collect(Collectors.groupingBy(ModelRequestTrace::getModelId, Collectors.counting()));
        
        // Token usage by model
        Map<String, Integer> promptTokensByModel = new HashMap<>();
        Map<String, Integer> completionTokensByModel = new HashMap<>();
        
        traces.stream()
                .filter(t -> t.getModelId() != null && !t.getModelId().isEmpty() && t.getTrace() != null)
                .forEach(t -> {
                    String model = t.getModelId();
                    // Add prompt tokens
                    promptTokensByModel.merge(model, t.getTrace().getPromptTokens(), Integer::sum);
                    // Add completion tokens
                    completionTokensByModel.merge(model, t.getTrace().getCompletionTokens(), Integer::sum);
                });
        
        // Create TokensByCategory for models
        TokensByCategory tokensByModel = TokensByCategory.builder()
                .promptTokens(promptTokensByModel)
                .completionTokens(completionTokensByModel)
                .build();
        
        // Latency by model
        Map<String, LatencyPercentiles> latencyPercentilesByModel = new HashMap<>();
        
        // Group traces by model
        Map<String, List<ModelRequestTrace>> tracesByModelGroup = traces.stream()
                .filter(t -> t.getModelId() != null && !t.getModelId().isEmpty() && t.getTrace() != null)
                .collect(Collectors.groupingBy(ModelRequestTrace::getModelId));
        
        // Calculate percentiles for each model
        tracesByModelGroup.forEach((model, modelTraces) -> {
            List<Long> modelLatencies = modelTraces.stream()
                    .map(t -> t.getTrace().getExecutionTimeMs())
                    .sorted()
                    .collect(Collectors.toList());
            
            Map<String, Long> percentiles = calculatePercentiles(modelLatencies);
            if (!percentiles.isEmpty()) {
                latencyPercentilesByModel.put(model, LatencyPercentiles.fromMap(percentiles));
            }
        });
        
        // Group traces by contextId
        Map<String, List<ModelRequestTrace>> tracesByContextId = traces.stream()
                .filter(t -> t.getContextId() != null && !t.getContextId().isEmpty())
                .collect(Collectors.groupingBy(ModelRequestTrace::getContextId));
        
        // Total unique contexts
        long uniqueContexts = tracesByContextId.size();
        
        // Count contexts with errors vs. successful contexts
        long errorCount = 0;
        long successCount = 0;
        
        for (List<ModelRequestTrace> contextTraces : tracesByContextId.values()) {
            // Check if any trace in this context has an error
            boolean hasError = contextTraces.stream()
                    .anyMatch(t -> t.getTrace() != null && t.getTrace().isHasError());
            
            if (hasError) {
                errorCount++;
            } else {
                successCount++;
            }
        }
        
        // Success rate calculation
        double successRate = uniqueContexts == 0 ? 0 : (double) successCount / uniqueContexts;
        
        // Daily counts (useful for graph visualization)
        Map<String, Long> dailyCounts = traces.stream()
                .collect(Collectors.groupingBy(
                        t -> DATE_FORMAT.format(new Date(t.getTimestamp())),
                        Collectors.counting()));
        
        // Build and return the response object
        return PromptMetricsResponse.builder()
                .totalTraces(uniqueContexts)
                .totalPromptTokens(totalPromptTokens)
                .totalCompletionTokens(totalCompletionTokens)
                .totalTokens(totalPromptTokens + totalCompletionTokens)
                .latencyPercentiles(LatencyPercentiles.fromMap(calculatePercentiles(latencies)))
                .tracesByModel(tracesByModel)
                .tokensByModel(tokensByModel)
                .latencyByModel(latencyPercentilesByModel)
                .successCount(successCount)
                .errorCount(errorCount)
                .successRate(successRate)
                .dailyCounts(dailyCounts)
                .build();
    }

    private String getPromptIdByMethod(String promptId) {
        Optional<Prompt> prompt = promptService.getCurrentPrompt(promptId, Language.GENERAL);

        promptId = prompt.map(Prompt::getId).orElse(promptId);
        return promptId;
    }

    /**
     * Calculate percentiles for a list of values
     */
    private Map<String, Long> calculatePercentiles(List<Long> values) {
        Map<String, Long> percentiles = new HashMap<>();
        if (values.isEmpty()) {
            return percentiles;
        }
        
        int size = values.size();
        percentiles.put("p25", values.get(Math.max(0, (int)(size * 0.25) - 1)));
        percentiles.put("p50", values.get(Math.max(0, (int)(size * 0.50) - 1)));
        percentiles.put("p75", values.get(Math.max(0, (int)(size * 0.75) - 1)));
        percentiles.put("p90", values.get(Math.max(0, (int)(size * 0.90) - 1)));
        
        return percentiles;
    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TaskVariables {
        private String messageId;
        private String contextId; // same as messageId for use in UI
        private String message;
        private String result;
        private String modelId;
        private long createdTime;
        private long responseTime;
        private List<String> promptIds;
        private Map<String, Object> variables;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LatencyPercentiles {
        private Long p25;
        private Long p50;
        private Long p75;
        private Long p90;

        public static LatencyPercentiles fromMap(Map<String, Long> map) {
            if (map == null) {
                return null;
            }
            return LatencyPercentiles.builder()
                    .p25(map.get("p25"))
                    .p50(map.get("p50"))
                    .p75(map.get("p75"))
                    .p90(map.get("p90"))
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TokensByCategory {
        private Map<String, Integer> promptTokens;
        private Map<String, Integer> completionTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DailyMetricsResponse {
        private long totalTasks;
        private int totalPromptTokens;
        private int totalCompletionTokens;
        private LatencyPercentiles latencyPercentiles;
        private Map<String, Long> tasksByModel;
        private Map<String, Long> tasksByPromptMethod;
        private TokensByCategory tokensByPromptMethod;
        private TokensByCategory tokensByPromptMethodModel;
        private Map<String, LatencyPercentiles> latencyByPromptMethod;
        private Map<String, Long> successByPromptMethod;
        private Map<String, Long> errorsByPromptMethod;
        private Map<String, Double> successRateByPromptMethod;
        private long successCount;
        private long errorCount;
        private double successRate;
        private long timestamp;
        private Map<String, LatencyPercentiles> latencyByPromptMethodModel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PromptMetricsResponse {
        private long totalTraces;
        private int totalPromptTokens;
        private int totalCompletionTokens;
        private int totalTokens;
        private LatencyPercentiles latencyPercentiles;
        private Map<String, Long> tracesByModel;
        private TokensByCategory tokensByModel;
        private Map<String, LatencyPercentiles> latencyByModel;
        private long successCount;
        private long errorCount;
        private double successRate;
        private Map<String, Long> dailyCounts;
    }
}