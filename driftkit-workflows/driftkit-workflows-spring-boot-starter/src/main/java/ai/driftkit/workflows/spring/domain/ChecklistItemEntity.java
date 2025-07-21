package ai.driftkit.workflows.spring.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Entity to store individual checklist items from reasoning workflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "checklist_items")
public class ChecklistItemEntity {
    @Id
    private String id;
    
    // Checklist item content
    private String description;
    private String severity; // CRITICAL, HIGH, MEDIUM, LOW
    
    // Reference information
    @Indexed
    private String promptId;
    
    @Indexed
    private String query;
    
    // Workflow type information
    private String workflowType;
    
    // Metadata
    private LocalDateTime createdAt;
    private int useCount;
    
    // For similarity detection
    private String normalizedDescription;
    
    // Reference to similar item
    private String similarToId;  // ID of the checklist item this one is similar to
    private Double similarityScore; // Score of similarity (0.0 to 1.0)
}