package ai.driftkit.clients.openai.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioTranscriptionRequest {
    private File file;
    private String model;
    private String prompt;
    private String responseFormat;
    private Float temperature;
    private String language;
}