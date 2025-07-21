package ai.driftkit.workflows.examples.domain;

import ai.driftkit.workflows.examples.workflows.RouterWorkflow.Route;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a message with embedded routing information in JSON format.
 * Used when a client wants to send a message with explicit routes in a single JSON.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoutedMessage {
    private String message;
    private List<Route> routes;
}