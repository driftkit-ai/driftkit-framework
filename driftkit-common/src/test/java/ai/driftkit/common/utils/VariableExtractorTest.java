package ai.driftkit.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Inputs of a TemplateEngine template: every control-tag form is skipped, loop-bound names are not inputs. */
class VariableExtractorTest {

    private final VariableExtractor extractor = new VariableExtractor();

    @Test
    @DisplayName("Plain, dotted and conditional variables")
    void plainAndConditions() {
        assertEquals(Set.of("name", "user", "flag", "items", "mode"), extractor.extractVariables(
                "Hi {{name}} {{user.email}}{{if flag && items.size > 0}}x{{/if}}{{if mode == \"a\"}}y{{/if}}"));
    }

    @Test
    @DisplayName("Dotted condition variables yield their root, like dotted tags")
    void dottedConditionRoot() {
        assertEquals(Set.of("user", "order"), extractor.extractVariables(
                "{{if user.active && order.status == \"paid\"}}ok{{/if}}"));
    }

    @Test
    @DisplayName("Hash forms #if, #list, #/if are control tags, not variables")
    void hashForms() {
        assertEquals(Set.of("a", "xs"), extractor.extractVariables(
                "{{#if a}}{{#list xs as x}}{{x}}{{#/list}}{{#/if}}"));
    }

    @Test
    @DisplayName("else is a control tag in both forms")
    void elseIsNotAVariable() {
        assertEquals(Set.of("a", "b", "c"), extractor.extractVariables(
                "{{if a}}{{b}}{{else}}{{c}}{{/if}}{{#if a}}{{#else}}{{/if}}"));
    }

    @Test
    @DisplayName("each: the collection is an input, this and @index are not")
    void each() {
        assertEquals(Set.of("people", "prefix"), extractor.extractVariables(
                "{{#each people}}{{@index}} {{prefix}} {{this.name}} {{this}}{{/each}}{{each people}}{{/each}}"));
    }

    @Test
    @DisplayName("The item of list xs as it is not an input, even when used with dot notation")
    void listItemNotInput() {
        assertEquals(Set.of("items"), extractor.extractVariables(
                "{{list items as it}}{{it.name}}{{if it.ok}}+{{/if}}{{@index}}{{/list}}"));
    }
}
