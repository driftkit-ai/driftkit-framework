package ai.driftkit.clients.openai.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingsRequest {
    private String model;
    private List<String> input;
    private String user;
}