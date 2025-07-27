package ai.driftkit.chat.framework.dto;

import ai.driftkit.chat.framework.model.ChatDomain.ChatMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;

@Schema(
        title = "Pageable Response with Chat Messages"
)
@Data
@EqualsAndHashCode(callSuper = true)
public class PageableResponseWithChatMessage extends PageableResponse<ChatMessage> {

    public PageableResponseWithChatMessage(
            final @NotNull HttpServletRequest request,
            final @NotNull Page<ChatMessage> page
    ) {
        super(request, page);
    }

}