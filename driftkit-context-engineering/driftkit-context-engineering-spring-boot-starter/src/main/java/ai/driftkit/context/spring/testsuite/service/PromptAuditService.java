package ai.driftkit.context.spring.testsuite.service;

import ai.driftkit.common.domain.Language;
import ai.driftkit.context.spring.testsuite.domain.PromptAudit;
import ai.driftkit.context.spring.testsuite.repository.PromptAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for recording prompt lifecycle audit trail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptAuditService {

    private final PromptAuditRepository auditRepository;

    public void record(String method, Language language, int fromVersion, int toVersion,
                       PromptAudit.Action action, String performedBy, String reason, String linkedRunId) {
        PromptAudit audit = PromptAudit.builder()
                .promptMethod(method)
                .language(language)
                .fromVersion(fromVersion)
                .toVersion(toVersion)
                .action(action)
                .performedBy(performedBy)
                .reason(reason)
                .linkedRunId(linkedRunId)
                .timestamp(System.currentTimeMillis())
                .build();

        auditRepository.save(audit);
        log.debug("Audit: {} {} v{} → v{} by {}", action, method, fromVersion, toVersion, performedBy);
    }

    public void record(String method, Language language, PromptAudit.Action action) {
        record(method, language, 0, 0, action, null, null, null);
    }

    public List<PromptAudit> getHistory(String method) {
        return auditRepository.findByPromptMethodOrderByTimestampDesc(method);
    }
}
