package ai.driftkit.vector.spring.service;

import ai.driftkit.vector.spring.domain.Index;
import ai.driftkit.vector.spring.domain.IndexTask;
import ai.driftkit.vector.spring.domain.IndexTask.TaskStatus;
import ai.driftkit.vector.spring.domain.IndexTask.DocumentSaveResult;
import ai.driftkit.vector.spring.parser.UnifiedParser.ParserInput;
import ai.driftkit.vector.spring.repository.IndexRepository;
import ai.driftkit.vector.spring.repository.IndexTaskRepository;
import ai.driftkit.common.utils.AIUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

@Slf4j
@Service
public class IndexService {

    @Autowired
    private IndexTaskRepository indexTaskRepository;

    @Autowired
    private IndexRepository indexRepository;

    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        this.executorService = new ThreadPoolExecutor(
                0,
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public void deleteIndex(String id) {
        if (indexRepository != null) {
            indexRepository.deleteById(id);
        }
    }

    public Index save(Index index) {
        if (indexRepository != null) {
            return indexRepository.save(index);
        }
        return index;
    }

    public List<Index> getIndexList() {
        if (indexRepository != null) {
            return indexRepository.findAll();
        }
        return List.of();
    }

    public Page<IndexTask> getTasks(int page, int limit) {
        if (indexTaskRepository != null) {
            return indexTaskRepository.findAll(PageRequest.of(page, limit, Sort.by(Direction.DESC, "createdTime")));
        }
        return Page.empty();
    }

    public IndexTask getTask(String taskId) {
        if (indexTaskRepository != null) {
            return indexTaskRepository.findById(taskId).orElse(null);
        }
        return null;
    }

    public String submitIndexingTask(String indexId, ParserInput input) {
        IndexTask task = IndexTask.builder()
                .taskId(AIUtils.generateId())
                .indexId(indexId)
                .parserInput(input)
                .status(TaskStatus.PENDING)
                .createdTime(System.currentTimeMillis())
                .build();

        executeTask(task);

        return task.getTaskId();
    }

    public void executeTask(IndexTask task) {
        if (indexTaskRepository != null) {
            indexTaskRepository.save(task);
        }

        if (indexRepository != null) {
            Optional<Index> indexOpt = indexRepository.findById(task.getIndexId());
            if (indexOpt.isEmpty()) {
                task.setStatus(TaskStatus.FAILED);
                task.setErrorMessage("Index not found: " + task.getIndexId());
                if (indexTaskRepository != null) {
                    indexTaskRepository.save(task);
                }
                return;
            }
        }

        executorService.submit(() -> {
            try {
                task.setStatus(TaskStatus.IN_PROGRESS);
                if (indexTaskRepository != null) {
                    indexTaskRepository.save(task);
                }

                // Simplified processing - in real implementation would use workflows
                DocumentSaveResult result = DocumentSaveResult.builder()
                        .saved(1)
                        .failed(0)
                        .build();

                task.setResult(result);
                task.setCompletedTime(System.currentTimeMillis());
                task.setStatus(TaskStatus.COMPLETED);
                
                if (indexTaskRepository != null) {
                    indexTaskRepository.save(task);
                }

                log.info("Indexing task [{}] completed successfully.", task.getTaskId());
            } catch (Exception e) {
                task.setStatus(TaskStatus.FAILED);
                task.setErrorMessage(e.getMessage());
                task.setCompletedTime(System.currentTimeMillis());
                
                if (indexTaskRepository != null) {
                    indexTaskRepository.save(task);
                }
                log.error("Indexing task [{}] failed: {}", task.getTaskId(), e.getMessage(), e);
            }
        });
    }
}