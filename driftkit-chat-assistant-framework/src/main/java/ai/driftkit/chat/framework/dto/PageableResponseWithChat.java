package ai.driftkit.chat.framework.dto;

import ai.driftkit.chat.framework.model.ChatSession;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;

@Schema(
        title = "Pageable Response with Chat Sessions"
)
@Data
@EqualsAndHashCode(callSuper = true)
public class PageableResponseWithChat extends PageableResponse<ChatSession> {

    public PageableResponseWithChat(
            final @NotNull HttpServletRequest request,
            final @NotNull Page<ChatSession> page
    ) {
        super(request, page);
    }

}