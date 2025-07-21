package ai.driftkit.workflows.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Example of flexible (non-strict) schema with only some required fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlexiblePersonExtraction {
    
    @NotNull
    @JsonProperty("name")
    @JsonPropertyDescription("The person's full name")
    private String name;

    @JsonProperty("age")
    @JsonPropertyDescription("The person's age in years")
    private Integer age;  // Using Integer instead of int to make it optional

    @JsonProperty("occupation")
    @JsonPropertyDescription("The person's job or profession")
    private String occupation;

    @JsonProperty("skills")
    @JsonPropertyDescription("List of skills the person has")
    private List<String> skills;

    @JsonProperty("contact")
    @JsonPropertyDescription("Contact information")
    private ContactInfo contact;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContactInfo {
        @NotNull
        @JsonProperty("email")
        @JsonPropertyDescription("Email address")
        private String email;
        
        @JsonProperty("phone")
        @JsonPropertyDescription("Phone number")
        private String phone;
    }
}