package ai.driftkit.common.utils;

import java.util.UUID;

public class AIUtils {
    
    public static String generateId() {
        return UUID.randomUUID().toString();
    }
}