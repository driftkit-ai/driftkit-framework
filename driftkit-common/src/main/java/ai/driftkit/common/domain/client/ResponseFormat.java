package ai.driftkit.common.domain.client;

import ai.driftkit.common.utils.JsonSchemaGenerator;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseFormat {
    private ResponseType type;

    private JsonSchema jsonSchema;
    
    public enum ResponseType {
        TEXT("text"),
        JSON_OBJECT("json_object"),
        JSON_SCHEMA("json_schema");
        
        private final String value;
        
        ResponseType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        @JsonValue
        public String toString() {
            return value;
        }
        
        @JsonCreator
        public static ResponseType fromValue(String value) {
            for (ResponseType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown ResponseType: " + value);
        }
    }

    public static <T> ResponseFormat jsonObject(T obj) {
        if (obj == null) {
            return new ResponseFormat(ResponseType.JSON_OBJECT, null);
        }

        Class<?> clazz;

        if (obj instanceof Class<?>) {
            clazz = (Class<?>) obj;
        } else {
            clazz = obj.getClass();
        }

        return new ResponseFormat(ResponseType.JSON_SCHEMA, JsonSchemaGenerator.generateSchema(clazz));
    }

    public static <T> ResponseFormat jsonSchema(Class<T> clazz) {
        return new ResponseFormat(ResponseType.JSON_SCHEMA, JsonSchemaGenerator.generateSchema(clazz));
    }

    public static ResponseFormat jsonObject() {
        return new ResponseFormat(ResponseType.JSON_OBJECT, null);
    }

    public static ResponseFormat text() {
        return new ResponseFormat(ResponseType.TEXT, null);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JsonSchema {
        @JsonProperty("title")
        private String title;

        @JsonProperty("type")
        private String type;

        @JsonProperty("properties")
        private Map<String, SchemaProperty> properties;

        @JsonProperty("required")
        private List<String> required;

        @JsonProperty("additionalProperties")
        private Boolean additionalProperties;
        
        @JsonProperty("strict")
        private Boolean strict;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchemaProperty {
        @JsonProperty("type")
        private String type;

        @JsonProperty("description")
        private String description;

        @JsonProperty("enum")
        private List<String> enumValues;

        @JsonProperty("properties")
        private Map<String, SchemaProperty> properties;

        @JsonProperty("required")
        private List<String> required;

        @JsonProperty("items")
        private SchemaProperty items;

        @JsonProperty("additionalProperties")
        private Object additionalProperties;
    }
}
