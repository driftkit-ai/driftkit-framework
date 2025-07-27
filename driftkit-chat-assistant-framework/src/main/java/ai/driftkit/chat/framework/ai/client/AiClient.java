package ai.driftkit.chat.framework.ai.client;

import ai.driftkit.chat.framework.ai.domain.*;
import ai.driftkit.chat.framework.ai.domain.Chat.ChatRestResponse;
import ai.driftkit.chat.framework.ai.domain.MessageTask.MessageIdResponse;
import ai.driftkit.chat.framework.ai.domain.MessageTask.MessageRestResponse;
import ai.driftkit.common.domain.*;
import lombok.SneakyThrows;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "aiClient", url = "${ai-props.host:}", configuration = FeignConfig.class)
public interface AiClient {

    @GetMapping("/data/v1.0/admin/llm/chat/{chatId}")
    List<HistoryMessage> getMessages(@PathVariable("chatId") String chatId);

    @PostMapping("/data/v1.0/admin/llm/chat")
    ChatRestResponse createChat(@RequestBody ChatRequest chatRequest);

    @GetMapping("/data/v1.0/admin/llm/message/{messageId}")
    MessageRestResponse getMessage(@PathVariable("messageId") String messageId);

    @PostMapping("/data/v1.0/admin/llm/prompt/message")
    MessageIdResponse sendPromptMessageAsync(@RequestBody PromptRequest promptRequest);

    @PostMapping("/data/v1.0/admin/llm/prompt/message/sync")
    MessageRestResponse sendPromptMessage(@RequestBody PromptRequest promptRequest);
    
    @PostMapping("/data/v1.0/admin/llm/message")
    MessageRestResponse sendDirectMessageAsync(@RequestBody MessageRequest messageRequest);

    @GetMapping("/data/v1.0/admin/llm/image/{imageTaskId}/resource/0")
    byte[] getImage(@PathVariable("imageTaskId") String imageTaskId);

    @GetMapping("/data/v1.0/admin/dictionary/")
    RestResponse<List<DictionaryItem>> getDictionaries();

    @PostMapping("/data/v1.0/admin/dictionary/items")
    RestResponse<List<DictionaryItem>> saveDictionaries(@RequestBody List<DictionaryItem> items);

    @PostMapping("/data/v1.0/admin/dictionary/group/")
    RestResponse<DictionaryGroup> saveDictionaryGroup(@RequestBody DictionaryGroup group);

    @GetMapping("/data/v1.0/admin/dictionary/group/{groupId}")
    RestResponse<DictionaryGroup> getDictionaryGroup(@PathVariable String groupId);
    
    @PostMapping("/data/v1.0/admin/prompt/create-if-not-exists")
    RestResponse<Prompt> createPromptIfNotExists(@RequestBody CreatePromptRequest createPromptRequest, @RequestParam Boolean forceRepoVersion);

    @SneakyThrows
    default MessageRestResponse sendDirectMessageAndWait(@RequestBody MessageRequest promptRequest) {
        MessageRestResponse messageIdResponse = sendDirectMessageAsync(promptRequest);

        if (!messageIdResponse.isSuccess()) {
            return new MessageRestResponse(false, null);
        }

        String messageId = messageIdResponse.getData().getMessageId();

        MessageRestResponse message = getMessage(messageId);

        int count = 300;

        while (message != null && message.getData().getResult() == null && count-- > 0) {
            message = getMessage(messageId);

            Thread.sleep(1000);
        }

        return message;
    }

    @SneakyThrows
    default MessageRestResponse sendPromptMessageAndWait(@RequestBody PromptRequest promptRequest) {
        MessageIdResponse messageIdResponse = sendPromptMessageAsync(promptRequest);

        if (!messageIdResponse.isSuccess()) {
            return new MessageRestResponse(false, null);
        }

        String messageId = messageIdResponse.getData().getMessageId();

        MessageRestResponse message = getMessage(messageId);

        int count = 300;

        while (message != null && message.getData().getResult() == null && count-- > 0) {
            message = getMessage(messageId);

            Thread.sleep(1000);
        }

        return message;
    }
}