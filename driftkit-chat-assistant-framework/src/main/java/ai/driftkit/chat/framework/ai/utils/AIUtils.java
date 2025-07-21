package ai.driftkit.chat.framework.ai.utils;

import ai.driftkit.chat.framework.ai.domain.MessageTask.MessageRestResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AIUtils {
    public static final String REASONING_LITE = "reasoning-lite";
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static <T> T parseModelResponse(MessageRestResponse msg, Class<T> clazz) throws JsonProcessingException {
        try {
            String result = msg.getData().getResult();
            return OBJECT_MAPPER.readValue(result, clazz);
        } catch (Exception e) {
            log.error("[generate] Couldn't parse incoming json [{}]", msg, e);
            throw e;
        }
    }
}