package ai.driftkit.common.domain.client;

import ai.driftkit.common.utils.JsonSchemaGenerator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseFormat {
    private String type;

    private JsonSchema jsonSchema;

    public static <T> ResponseFormat jsonObject(T obj) {
        if (obj == null) {
            return new ResponseFormat("json_object", null);
        }

        Class<?> clazz;

        if (obj instanceof Class<?>) {
            clazz = (Class<?>) obj;
        } else {
            clazz = obj.getClass();
        }

        return new ResponseFormat("json_schema", JsonSchemaGenerator.generateSchema(clazz));
    }

    public static <T> ResponseFormat jsonSchema(Class<T> clazz) {
        return new ResponseFormat("json_schema", JsonSchemaGenerator.generateSchema(clazz));
    }

    public static ResponseFormat jsonObject() {
        return new ResponseFormat("json_object", null);
    }

    public static ResponseFormat text() {
        return new ResponseFormat("text", null);
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
