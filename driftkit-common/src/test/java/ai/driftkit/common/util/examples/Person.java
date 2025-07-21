package ai.driftkit.common.util.examples;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Example POJO class for demonstrating structured output support.
 * This class represents a person with various properties that can be used
 * to test JSON schema generation and structured output functionality.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Person {

    @JsonProperty(value = "name", required = true)
    @JsonPropertyDescription("The person's full name")
    private String name;

    @JsonProperty(value = "age", required = true)
    @JsonPropertyDescription("The person's age in years")
    private int age;

    @JsonProperty("email")
    @JsonPropertyDescription("The person's email address")
    private String email;

    @JsonProperty("occupation")
    @JsonPropertyDescription("The person's current job or profession")
    private String occupation;

    @JsonProperty("skills")
    @JsonPropertyDescription("List of skills the person has")
    private List<String> skills;

    @JsonProperty("marital_status")
    @JsonPropertyDescription("The person's marital status")
    private MaritalStatus maritalStatus;

    @JsonProperty("address")
    @JsonPropertyDescription("The person's address information")
    private Address address;

    /**
     * Enum for marital status options
     */
    public enum MaritalStatus {
        SINGLE,
        MARRIED,
        DIVORCED,
        WIDOWED
    }

    /**
     * Nested class for address information
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        
        @JsonProperty(value = "street", required = true)
        @JsonPropertyDescription("Street address")
        private String street;
        
        @JsonProperty(value = "city", required = true)
        @JsonPropertyDescription("City name")
        private String city;
        
        @JsonProperty("state")
        @JsonPropertyDescription("State or province")
        private String state;
        
        @JsonProperty(value = "postal_code", required = true)
        @JsonPropertyDescription("Postal or ZIP code")
        private String postalCode;
        
        @JsonProperty("country")
        @JsonPropertyDescription("Country name")
        private String country;
    }
}