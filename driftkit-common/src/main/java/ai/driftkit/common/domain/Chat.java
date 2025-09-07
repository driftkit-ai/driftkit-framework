package ai.driftkit.common.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Chat {

    @NotNull
    private String chatId;

    @NotNull
    private String name;

    private String systemMessage;

    @NotNull
    private Language language = Language.ENGLISH;

    @NotNull
    private int memoryLength;

    private ModelRole modelRole = ModelRole.ABTEST;

    @NotNull
    private long createdTime;

    @NotNull
    private boolean hidden;
}