package ai.driftkit.workflow.controllers.service;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.workflow.engine.spring.tracing.domain.AsyncTaskEntity;
import ai.driftkit.workflow.engine.spring.tracing.domain.ModelRequestTrace;
import ai.driftkit.workflow.engine.spring.tracing.repository.AsyncTaskRepository;
import ai.driftkit.workflow.engine.spring.tracing.repository.CoreModelRequestTraceRepository;
import ai.driftkit.workflow.engine.spring.dto.AnalyticsDtos.*;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnBean(MongoTemplate.class)
@ConditionalOnProperty(
    prefix = "driftkit.workflow.tracing",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class WorkflowAnalyticsService {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    private CoreModelRequestTraceRepository modelRequestTraceRepository;

    @Autowired(required = false)
    private AsyncTaskRepository asyncTaskRepository;

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
        if (promptService == null) {
            return new ArrayList<>();
        }
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
        if (asyncTaskRepository == null) {
            // If AsyncTaskRepository is not available, return empty list
            return new ArrayList<>();
        }
        
        // Get tasks by task IDs (contextIds are taskIds)
        List<AsyncTaskEntity> entities = asyncTaskRepository.findByTaskIdIn(contextIds);
        
        return entities.stream()
                .map(task -> TaskVariables.builder()
                        .messageId(task.getTaskId())
                        .contextId(task.getTaskId()) // contextId is taskId
                        .message(task.getRequestBody())
                        .result(task.getResult())
                        .modelId(task.getModelId())
                        .variables(task.getVariables())
                        .createdTime(task.getCreatedAt())
                        .responseTime(task.getExecutionTimeMs())
                        .promptIds(task.getPromptId() != null ? List.of(task.getPromptId()) : null)
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
        if (!promptService.isConfigured()) {
            return new DailyMetricsResponse();
        }

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
        
        // Query async tasks if repository is available
        List<AsyncTaskEntity> asyncTasks = new ArrayList<>();
        if (asyncTaskRepository != null) {
            Criteria taskCriteria = Criteria.where("createdAt").gte(startTimestamp).lte(endTimestamp);
            Query taskQuery = Query.query(taskCriteria);
            asyncTasks = mongoTemplate.find(taskQuery, AsyncTaskEntity.class);
        }
        
        // Query model request traces for token usage data
        // The contextId in trace equals messageId in MessageTask
        Criteria traceCriteria = Criteria.where("timestamp").gte(startTimestamp).lte(endTimestamp);
        Query traceQuery = Query.query(traceCriteria);
        List<ModelRequestTrace> traces = mongoTemplate.find(traceQuery, ModelRequestTrace.class);
        
        // Create a map of messageId to traces for easy lookup
        Map<String, List<ModelRequestTrace>> tracesByMessageId = traces.stream()
                .collect(Collectors.groupingBy(ModelRequestTrace::getContextId));
        
        // Get all prompt IDs from tasks to load their corresponding Prompt records
        List<String> allPromptIds = asyncTasks.stream()
                .filter(t -> StringUtils.isNotBlank(t.getPromptId()))
                .map(AsyncTaskEntity::getPromptId)
                .distinct()
                .collect(Collectors.toList());
        
        // Load all Prompt objects needed for the method field
        List<Prompt> prompts = promptService != null ? promptService.getPromptsByIds(allPromptIds) : new ArrayList<>();
        
        // Create a map of promptId -> method for conversion
        Map<String, String> promptIdToMethodMap = prompts.stream()
                .collect(Collectors.toMap(Prompt::getId, Prompt::getMethod, (m1, m2) -> m1));
        
        // Calculate overall metrics
        Map<String, Object> metrics = new HashMap<>();
        
        // --- Overall metrics ---
        // Request counts
        metrics.put("totalTasks", asyncTasks.size());
        
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
        List<Long> latencies = asyncTasks.stream()
                .filter(t -> t.getExecutionTimeMs() != null && t.getExecutionTimeMs() > 0)
                .map(AsyncTaskEntity::getExecutionTimeMs)
                .sorted()
                .collect(Collectors.toList());
        
        metrics.put("latencyPercentiles", calculatePercentiles(latencies));
        
        // --- Group metrics ---
        // By Model
        Map<String, Long> tasksByModel = asyncTasks.stream()
                .filter(t -> StringUtils.isNotBlank(t.getModelId()))
                .collect(Collectors.groupingBy(AsyncTaskEntity::getModelId, Collectors.counting()));
        
        metrics.put("tasksByModel", tasksByModel);
        
        // Extract all promptIds from tasks, map them to method names, and count occurrences
        Map<String, Long> tasksByPromptMethod = new HashMap<>();
        
        // Iterate through tasks to count by prompt method
        asyncTasks.stream()
                .filter(t -> StringUtils.isNotBlank(t.getPromptId()))
                .forEach(task -> {
                    // Convert promptId to method name (if available)
                    String method = promptIdToMethodMap.getOrDefault(task.getPromptId(), task.getPromptId());
                    tasksByPromptMethod.merge(method, 1L, Long::sum);
                });
        
        metrics.put("tasksByPromptMethod", tasksByPromptMethod);
        
        // --- Detailed metrics with group breakdowns ---
        
        // 1. Token usage by prompt method
        Map<String, Map<String, Integer>> tokensByPromptMethod = new HashMap<>();
        tokensByPromptMethod.put("promptTokens", new HashMap<>());
        tokensByPromptMethod.put("completionTokens", new HashMap<>());
        
        // Process tasks and their associated traces to get token usage by prompt method
        asyncTasks.forEach(task -> {
            if (StringUtils.isNotBlank(task.getPromptId())) {
                // Get traces associated with this task via messageId = contextId
                List<ModelRequestTrace> taskTraces = tracesByMessageId.getOrDefault(task.getTaskId(), Collections.emptyList());
                
                // Calculate total tokens for this task
                int taskPromptTokens = taskTraces.stream()
                        .filter(t -> t.getTrace() != null)
                        .mapToInt(t -> t.getTrace().getPromptTokens())
                        .sum();
                
                int taskCompletionTokens = taskTraces.stream()
                        .filter(t -> t.getTrace() != null)
                        .mapToInt(t -> t.getTrace().getCompletionTokens())
                        .sum();
                
                // Add tokens for this task's prompt method
                if (taskPromptTokens > 0 || taskCompletionTokens > 0) {
                    String method = promptIdToMethodMap.getOrDefault(task.getPromptId(), task.getPromptId());
                    
                    // Add prompt tokens
                    if (taskPromptTokens > 0) {
                        tokensByPromptMethod.get("promptTokens").merge(method, taskPromptTokens, Integer::sum);
                    }
                    
                    // Add completion tokens
                    if (taskCompletionTokens > 0) {
                        tokensByPromptMethod.get("completionTokens").merge(method, taskCompletionTokens, Integer::sum);
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
        asyncTasks.forEach(task -> {
            if (StringUtils.isNotBlank(task.getPromptId()) && 
                StringUtils.isNotBlank(task.getModelId())) {
                
                // Get traces associated with this task
                List<ModelRequestTrace> taskTraces = tracesByMessageId.getOrDefault(task.getTaskId(), Collections.emptyList());
                
                // Calculate total tokens for this task
                int taskPromptTokens = taskTraces.stream()
                        .filter(t -> t.getTrace() != null)
                        .mapToInt(t -> t.getTrace().getPromptTokens())
                        .sum();
                
                int taskCompletionTokens = taskTraces.stream()
                        .filter(t -> t.getTrace() != null)
                        .mapToInt(t -> t.getTrace().getCompletionTokens())
                        .sum();
                
                // Add tokens for prompt method + model combination
                if (taskPromptTokens > 0 || taskCompletionTokens > 0) {
                    // Convert promptId to method name
                    String method = promptIdToMethodMap.getOrDefault(task.getPromptId(), task.getPromptId());
                    String statKey = method + ":" + task.getModelId();
                    
                    // Add tokens
                    if (taskPromptTokens > 0) {
                        tokensByPromptMethodModel.get("promptTokens").merge(statKey, taskPromptTokens, Integer::sum);
                    }
                    
                    if (taskCompletionTokens > 0) {
                        tokensByPromptMethodModel.get("completionTokens").merge(statKey, taskCompletionTokens, Integer::sum);
                    }
                }
            }
        });
        
        metrics.put("tokensByPromptMethodModel", tokensByPromptMethodModel);
        
        // 3. Latency by prompt method
        Map<String, Map<String, Long>> latencyByPromptMethod = new HashMap<>();
        
        // Group tasks by prompt method
        Map<String, List<AsyncTaskEntity>> tasksByPromptMethodGroup = new HashMap<>();
        
        asyncTasks.forEach(task -> {
            if (StringUtils.isNotBlank(task.getPromptId())) {
                // Convert promptId to method
                String method = promptIdToMethodMap.getOrDefault(task.getPromptId(), task.getPromptId());
                
                // Add task to the method's list
                if (!tasksByPromptMethodGroup.containsKey(method)) {
                    tasksByPromptMethodGroup.put(method, new ArrayList<>());
                }
                tasksByPromptMethodGroup.get(method).add(task);
            }
        });
        
        // Calculate latency percentiles for each prompt method
        tasksByPromptMethodGroup.forEach((method, methodTasks) -> {
            List<Long> methodLatencies = methodTasks.stream()
                    .filter(t -> t.getExecutionTimeMs() != null && t.getExecutionTimeMs() > 0)
                    .map(AsyncTaskEntity::getExecutionTimeMs)
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
                .filter(t -> StringUtils.isNotBlank(t.getContextId()))
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
        Map<String, List<AsyncTaskEntity>> tasksByPromptMethodModelGroup = new HashMap<>();
        
        // Process tasks to get latency by prompt method and model
        asyncTasks.forEach(task -> {
            if (StringUtils.isNotBlank(task.getPromptId()) && 
                StringUtils.isNotBlank(task.getModelId()) &&
                task.getExecutionTimeMs() != null && task.getExecutionTimeMs() > 0) {
                
                // Convert promptId to method
                String method = promptIdToMethodMap.getOrDefault(task.getPromptId(), task.getPromptId());
                String statKey = method + ":" + task.getModelId();
                
                // Add task to the method+model combination
                if (!tasksByPromptMethodModelGroup.containsKey(statKey)) {
                    tasksByPromptMethodModelGroup.put(statKey, new ArrayList<>());
                }
                tasksByPromptMethodModelGroup.get(statKey).add(task);
            }
        });
        
        // Calculate latency percentiles for each prompt method+model combination
        tasksByPromptMethodModelGroup.forEach((statKey, methodModelTasks) -> {
            List<Long> methodModelLatencies = methodModelTasks.stream()
                    .filter(t -> t.getExecutionTimeMs() != null && t.getExecutionTimeMs() > 0)
                    .map(AsyncTaskEntity::getExecutionTimeMs)
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
                .totalTasks(asyncTasks.size())
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
                .filter(t -> StringUtils.isNotBlank(t.getModelId()))
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
                .filter(t -> StringUtils.isNotBlank(t.getContextId()))
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
        if (promptService == null) {
            return promptId;
        }
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
}