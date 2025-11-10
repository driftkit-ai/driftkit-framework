package ai.driftkit.context.core.service;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for TemplateEngine.
 *
 * Tests cover:
 * - Simple variable substitution
 * - Nested properties (dot notation)
 * - Conditional blocks (if/else)
 * - List iterations
 * - Complex scenarios (nested structures)
 * - Edge cases and error handling
 * - Caching behavior
 */
@DisplayName("TemplateEngine Comprehensive Tests")
class TemplateEngineTest {

    @BeforeEach
    void setUp() {
        TemplateEngine.clear();
    }

    @AfterEach
    void tearDown() {
        TemplateEngine.clear();
    }

    // ============================================================
    // Simple Variable Substitution Tests
    // ============================================================

    @Nested
    @DisplayName("Simple Variable Substitution")
    class SimpleVariableTests {

        @Test
        @DisplayName("Should substitute single variable")
        void shouldSubstituteSingleVariable() {
            String template = "Hello {{name}}!";
            Map<String, Object> vars = Map.of("name", "World");

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Hello World!", result);
        }

        @Test
        @DisplayName("Should substitute multiple variables")
        void shouldSubstituteMultipleVariables() {
            String template = "{{greeting}} {{name}}! You have {{count}} messages.";
            Map<String, Object> vars = Map.of(
                "greeting", "Hello",
                "name", "Alice",
                "count", 5
            );

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Hello Alice! You have 5 messages.", result);
        }

        @Test
        @DisplayName("Should return empty string for missing variable")
        void shouldReturnEmptyForMissingVariable() {
            String template = "Hello {{name}}!";
            Map<String, Object> vars = Map.of();

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Hello !", result);
        }

        @Test
        @DisplayName("Should handle null variable value")
        void shouldHandleNullVariableValue() {
            String template = "Value: {{value}}";
            Map<String, Object> vars = new HashMap<>();
            vars.put("value", null);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Value: ", result);
        }

        @Test
        @DisplayName("Should handle variables with spaces in braces")
        void shouldHandleVariablesWithSpaces() {
            String template = "{{ name }}";
            Map<String, Object> vars = Map.of("name", "Test");

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Test", result);
        }

        @Test
        @DisplayName("Should convert different types to strings")
        void shouldConvertTypesToStrings() {
            String template = "Integer: {{int}}, Boolean: {{bool}}, Double: {{double}}";
            Map<String, Object> vars = Map.of(
                "int", 42,
                "bool", true,
                "double", 3.14
            );

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Integer: 42, Boolean: true, Double: 3.14", result);
        }
    }

    // ============================================================
    // Nested Properties (Dot Notation) Tests
    // ============================================================

    @Nested
    @DisplayName("Nested Properties with Dot Notation")
    class DotNotationTests {

        @Test
        @DisplayName("Should resolve nested Map properties")
        void shouldResolveNestedMapProperties() {
            String template = "User: {{user.name}}, Email: {{user.email}}";
            Map<String, Object> user = Map.of(
                "name", "John Doe",
                "email", "john@example.com"
            );
            Map<String, Object> vars = Map.of("user", user);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("User: John Doe, Email: john@example.com", result);
        }

        @Test
        @DisplayName("Should resolve POJO properties via reflection")
        void shouldResolvePOJOProperties() {
            String template = "Name: {{person.name}}, Age: {{person.age}}";
            TestPerson person = new TestPerson("Alice", 30);
            Map<String, Object> vars = Map.of("person", person);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Name: Alice, Age: 30", result);
        }

        @Test
        @DisplayName("Should resolve deeply nested properties")
        void shouldResolveDeeplyNestedProperties() {
            String template = "City: {{user.address.city}}";
            Map<String, Object> address = Map.of("city", "New York", "zip", "10001");
            Map<String, Object> user = Map.of("name", "Bob", "address", address);
            Map<String, Object> vars = Map.of("user", user);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("City: New York", result);
        }

        @Test
        @DisplayName("Should handle null in property chain")
        void shouldHandleNullInPropertyChain() {
            String template = "Value: {{user.address.city}}";
            Map<String, Object> user = new HashMap<>();
            user.put("name", "Test");
            user.put("address", null);
            Map<String, Object> vars = Map.of("user", user);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Value: ", result);
        }

        @Test
        @DisplayName("Should handle non-existent nested property")
        void shouldHandleNonExistentNestedProperty() {
            String template = "Value: {{user.nonexistent}}";
            Map<String, Object> user = Map.of("name", "Test");
            Map<String, Object> vars = Map.of("user", user);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Value: ", result);
        }

        @Test
        @DisplayName("Should resolve nested POJO with inheritance")
        void shouldResolveNestedPOJOWithInheritance() {
            String template = "Manager: {{manager.name}}, Department: {{manager.department}}";
            TestManager manager = new TestManager("Carol", 45, "Engineering");
            Map<String, Object> vars = Map.of("manager", manager);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Manager: Carol, Department: Engineering", result);
        }
    }

    // ============================================================
    // Conditional Blocks Tests
    // ============================================================

    @Nested
    @DisplayName("Conditional Blocks (if/else)")
    class ConditionalTests {

        @Test
        @DisplayName("Should render if block when condition is true")
        void shouldRenderIfBlockWhenTrue() {
            String template = "{{if isPremium}}Premium User{{/if}}";
            Map<String, Object> vars = Map.of("isPremium", true);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Premium User", result);
        }

        @Test
        @DisplayName("Should not render if block when condition is false")
        void shouldNotRenderIfBlockWhenFalse() {
            String template = "{{if isPremium}}Premium User{{/if}}";
            Map<String, Object> vars = Map.of("isPremium", false);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("", result);
        }

        @Test
        @DisplayName("Should support #if syntax")
        void shouldSupportHashIfSyntax() {
            String template = "{{#if active}}Active{{#/if}}";
            Map<String, Object> vars = Map.of("active", true);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Active", result);
        }

        @Test
        @DisplayName("Should treat non-empty collection as truthy")
        void shouldTreatNonEmptyCollectionAsTruthy() {
            String template = "{{if items}}Has items{{/if}}";
            Map<String, Object> vars = Map.of("items", List.of(1, 2, 3));

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Has items", result);
        }

        @Test
        @DisplayName("Should treat empty collection as falsy")
        void shouldTreatEmptyCollectionAsFalsy() {
            String template = "{{if items}}Has items{{/if}}";
            Map<String, Object> vars = Map.of("items", List.of());

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("", result);
        }

        @Test
        @DisplayName("Should treat non-empty Map as truthy")
        void shouldTreatNonEmptyMapAsTruthy() {
            String template = "{{if config}}Has config{{/if}}";
            Map<String, Object> vars = Map.of("config", Map.of("key", "value"));

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Has config", result);
        }

        @Test
        @DisplayName("Should treat non-empty array as truthy")
        void shouldTreatNonEmptyArrayAsTruthy() {
            String template = "{{if arr}}Has array{{/if}}";
            String[] array = {"a", "b", "c"};
            Map<String, Object> vars = Map.of("arr", array);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Has array", result);
        }

        @Test
        @DisplayName("Should treat empty array as falsy")
        void shouldTreatEmptyArrayAsFalsy() {
            String template = "{{if arr}}Has array{{/if}}";
            String[] array = {};
            Map<String, Object> vars = Map.of("arr", array);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("", result);
        }

        @Test
        @DisplayName("Should treat non-empty string as truthy")
        void shouldTreatNonEmptyStringAsTruthy() {
            String template = "{{if message}}Has message{{/if}}";
            Map<String, Object> vars = Map.of("message", "Hello");

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Has message", result);
        }

        @Test
        @DisplayName("Should treat empty string as falsy")
        void shouldTreatEmptyStringAsFalsy() {
            String template = "{{if message}}Has message{{/if}}";
            Map<String, Object> vars = Map.of("message", "");

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("", result);
        }

        @Test
        @DisplayName("Should treat whitespace-only string as falsy")
        void shouldTreatWhitespaceStringAsFalsy() {
            String template = "{{if message}}Has message{{/if}}";
            Map<String, Object> vars = Map.of("message", "   ");

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("", result);
        }

        @Test
        @DisplayName("Should support equality comparison with strings")
        void shouldSupportEqualityComparison() {
            String template = "{{if status == \"active\"}}Active Status{{/if}}";
            Map<String, Object> vars = Map.of("status", "active");

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Active Status", result);
        }

        @Test
        @DisplayName("Should handle escaped quotes in equality comparison")
        void shouldHandleEscapedQuotes() {
            String template = "{{if name == \"O\\\"Brien\"}}Found{{/if}}";
            Map<String, Object> vars = Map.of("name", "O\"Brien");

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Found", result);
        }

        @Test
        @DisplayName("Should support size comparison with >")
        void shouldSupportSizeGreaterThan() {
            String template = "{{if items.size > 0}}Has items{{/if}}";
            Map<String, Object> vars = Map.of("items", List.of(1, 2, 3));

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Has items", result);
        }

        @Test
        @DisplayName("Should support size comparison with >=")
        void shouldSupportSizeGreaterThanOrEqual() {
            String template = "{{if items.size >= 3}}Enough items{{/if}}";
            Map<String, Object> vars = Map.of("items", List.of(1, 2, 3));

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Enough items", result);
        }

        @Test
        @DisplayName("Should support size comparison with <")
        void shouldSupportSizeLessThan() {
            String template = "{{if items.size < 5}}Few items{{/if}}";
            Map<String, Object> vars = Map.of("items", List.of(1, 2));

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Few items", result);
        }

        @Test
        @DisplayName("Should support size comparison with <=")
        void shouldSupportSizeLessThanOrEqual() {
            String template = "{{if items.size <= 2}}Max two items{{/if}}";
            Map<String, Object> vars = Map.of("items", List.of(1, 2));

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Max two items", result);
        }

        @Test
        @DisplayName("Should support size comparison with ==")
        void shouldSupportSizeEquals() {
            String template = "{{if items.size == 3}}Exactly three{{/if}}";
            Map<String, Object> vars = Map.of("items", List.of(1, 2, 3));

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Exactly three", result);
        }

        @Test
        @DisplayName("Should support AND operator (&&)")
        void shouldSupportAndOperator() {
            String template = "{{if isPremium && isActive}}Premium and Active{{/if}}";
            Map<String, Object> vars = Map.of("isPremium", true, "isActive", true);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Premium and Active", result);
        }

        @Test
        @DisplayName("Should fail AND when one condition is false")
        void shouldFailAndWhenOneConditionFalse() {
            String template = "{{if isPremium && isActive}}Premium and Active{{/if}}";
            Map<String, Object> vars = Map.of("isPremium", true, "isActive", false);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("", result);
        }

        @Test
        @DisplayName("Should support OR operator (||)")
        void shouldSupportOrOperator() {
            String template = "{{if isPremium || isActive}}Special User{{/if}}";
            Map<String, Object> vars = Map.of("isPremium", false, "isActive", true);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Special User", result);
        }

        @Test
        @DisplayName("Should fail OR when both conditions are false")
        void shouldFailOrWhenBothConditionsFalse() {
            String template = "{{if isPremium || isActive}}Special User{{/if}}";
            Map<String, Object> vars = Map.of("isPremium", false, "isActive", false);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("", result);
        }

        @Test
        @DisplayName("Should support complex condition with && and ||")
        void shouldSupportComplexConditions() {
            String template = "{{if isAdmin || isPremium && isActive}}Access Granted{{/if}}";
            Map<String, Object> vars = Map.of("isAdmin", false, "isPremium", true, "isActive", true);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Access Granted", result);
        }

        @Test
        @DisplayName("Should handle nested if blocks")
        void shouldHandleNestedIfBlocks() {
            String template = "{{if outer}}Outer{{if inner}} and Inner{{/if}}{{/if}}";
            Map<String, Object> vars = Map.of("outer", true, "inner", true);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Outer and Inner", result);
        }
    }

    // ============================================================
    // List Iteration Tests
    // ============================================================

    @Nested
    @DisplayName("List Iterations")
    class ListIterationTests {

        @Test
        @DisplayName("Should iterate over simple list")
        void shouldIterateOverSimpleList() {
            String template = "{{list items as item}}{{item}} {{/list}}";
            Map<String, Object> vars = Map.of("items", List.of("A", "B", "C"));

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("A B C ", result);
        }

        @Test
        @DisplayName("Should support #list syntax")
        void shouldSupportHashListSyntax() {
            String template = "{{#list items as item}}{{item}},{{#/list}}";
            Map<String, Object> vars = Map.of("items", List.of(1, 2, 3));

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("1,2,3,", result);
        }

        @Test
        @DisplayName("Should iterate over array")
        void shouldIterateOverArray() {
            String template = "{{list arr as item}}[{{item}}]{{/list}}";
            String[] array = {"x", "y", "z"};
            Map<String, Object> vars = Map.of("arr", array);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("[x][y][z]", result);
        }

        @Test
        @DisplayName("Should handle empty collection")
        void shouldHandleEmptyCollection() {
            String template = "{{list items as item}}{{item}}{{/list}}Empty";
            Map<String, Object> vars = Map.of("items", List.of());

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Empty", result);
        }

        @Test
        @DisplayName("Should handle null collection")
        void shouldHandleNullCollection() {
            String template = "{{list items as item}}{{item}}{{/list}}Done";
            Map<String, Object> vars = new HashMap<>();
            vars.put("items", null);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Done", result);
        }

        @Test
        @DisplayName("Should access item properties in list")
        void shouldAccessItemPropertiesInList() {
            String template = "{{list users as user}}{{user.name}} {{/list}}";
            List<Map<String, Object>> users = List.of(
                Map.of("name", "Alice"),
                Map.of("name", "Bob"),
                Map.of("name", "Charlie")
            );
            Map<String, Object> vars = Map.of("users", users);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Alice Bob Charlie ", result);
        }

        @Test
        @DisplayName("Should iterate over POJO list")
        void shouldIterateOverPOJOList() {
            String template = "{{list people as person}}{{person.name}}:{{person.age}} {{/list}}";
            List<TestPerson> people = List.of(
                new TestPerson("Alice", 30),
                new TestPerson("Bob", 25)
            );
            Map<String, Object> vars = Map.of("people", people);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Alice:30 Bob:25 ", result);
        }

        @Test
        @DisplayName("Should support nested lists")
        void shouldSupportNestedLists() {
            String template = "{{list outer as o}}({{list o.inner as i}}{{i}}{{/list}}){{/list}}";
            List<Map<String, Object>> outer = List.of(
                Map.of("inner", List.of(1, 2)),
                Map.of("inner", List.of(3, 4))
            );
            Map<String, Object> vars = Map.of("outer", outer);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("(12)(34)", result);
        }

        @Test
        @DisplayName("Should support list inside conditional")
        void shouldSupportListInsideConditional() {
            String template = "{{if hasItems}}Items: {{list items as item}}{{item}} {{/list}}{{/if}}";
            Map<String, Object> vars = Map.of(
                "hasItems", true,
                "items", List.of("A", "B", "C")
            );

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Items: A B C ", result);
        }

        @Test
        @DisplayName("Should support conditional inside list")
        void shouldSupportConditionalInsideList() {
            String template = "{{list items as item}}{{if item.active}}{{item.name}} {{/if}}{{/list}}";
            List<Map<String, Object>> items = List.of(
                Map.of("name", "A", "active", true),
                Map.of("name", "B", "active", false),
                Map.of("name", "C", "active", true)
            );
            Map<String, Object> vars = Map.of("items", items);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("A C ", result);
        }
    }

    // ============================================================
    // Complex Scenarios
    // ============================================================

    @Nested
    @DisplayName("Complex Scenarios")
    class ComplexScenariosTests {

        @Test
        @DisplayName("Should handle complex nested structure")
        void shouldHandleComplexNestedStructure() {
            String template = """
                User: {{user.name}}
                {{if user.isPremium}}
                Premium Features:
                {{list user.features as feature}}
                  - {{feature.name}}: {{feature.description}}
                {{/list}}
                {{/if}}
                """;

            List<Map<String, Object>> features = List.of(
                Map.of("name", "Feature1", "description", "Desc1"),
                Map.of("name", "Feature2", "description", "Desc2")
            );

            Map<String, Object> user = Map.of(
                "name", "Alice",
                "isPremium", true,
                "features", features
            );

            Map<String, Object> vars = Map.of("user", user);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertTrue(result.contains("Alice"));
            assertTrue(result.contains("Premium Features:"));
            assertTrue(result.contains("Feature1: Desc1"));
            assertTrue(result.contains("Feature2: Desc2"));
        }

        @Test
        @DisplayName("Should handle multiple independent if blocks")
        void shouldHandleMultipleIndependentIfBlocks() {
            String template = "{{if a}}A{{/if}}{{if b}}B{{/if}}{{if c}}C{{/if}}";
            Map<String, Object> vars = Map.of("a", true, "b", false, "c", true);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("AC", result);
        }

        @Test
        @DisplayName("Should handle real-world email template")
        void shouldHandleRealWorldEmailTemplate() {
            String template = """
                Hello {{recipient.name}},

                {{if order.items.size > 0}}
                Your order #{{order.id}} contains:
                {{list order.items as item}}
                - {{item.product}} x{{item.quantity}} = ${{item.total}}
                {{/list}}

                Total: ${{order.total}}
                {{/if}}

                {{if order.isPremium}}
                Thank you for being a premium customer!
                {{/if}}

                Best regards,
                The Team
                """;

            List<Map<String, Object>> items = List.of(
                Map.of("product", "Widget", "quantity", 2, "total", 50),
                Map.of("product", "Gadget", "quantity", 1, "total", 30)
            );

            Map<String, Object> order = Map.of(
                "id", "12345",
                "items", items,
                "total", 80,
                "isPremium", true
            );

            Map<String, Object> recipient = Map.of("name", "John Doe");

            Map<String, Object> vars = Map.of("order", order, "recipient", recipient);

            String result = TemplateEngine.renderTemplate(template, vars);

            assertTrue(result.contains("Hello John Doe"));
            assertTrue(result.contains("order #12345"));
            assertTrue(result.contains("Widget x2 = $50"));
            assertTrue(result.contains("Gadget x1 = $30"));
            assertTrue(result.contains("Total: $80"));
            assertTrue(result.contains("premium customer"));
        }
    }

    // ============================================================
    // Edge Cases and Error Handling
    // ============================================================

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle empty template")
        void shouldHandleEmptyTemplate() {
            String template = "";
            Map<String, Object> vars = Map.of();

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("", result);
        }

        @Test
        @DisplayName("Should handle template without variables")
        void shouldHandleTemplateWithoutVariables() {
            String template = "This is a static template.";
            Map<String, Object> vars = Map.of();

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("This is a static template.", result);
        }

        @Test
        @DisplayName("Should handle unclosed variable tag")
        void shouldHandleUnclosedVariableTag() {
            String template = "Hello {{name";
            Map<String, Object> vars = Map.of("name", "World");

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Hello {{name", result);
        }

        @Test
        @DisplayName("Should handle malformed list syntax")
        void shouldHandleMalformedListSyntax() {
            String template = "{{list items}}{{/list}}";
            Map<String, Object> vars = Map.of("items", List.of(1, 2, 3));

            String result = TemplateEngine.renderTemplate(template, vars);

            // Should treat as text since 'as' keyword is missing
            assertEquals("{{list items}}{{/list}}", result);
        }

        @Test
        @DisplayName("Should handle mismatched closing tags")
        void shouldHandleMismatchedClosingTags() {
            String template = "{{if condition}}Text{{/list}}";
            Map<String, Object> vars = Map.of("condition", true);

            String result = TemplateEngine.renderTemplate(template, vars);

            // The /list is ignored as it doesn't match the if block
            assertEquals("Text", result);
        }

        @Test
        @DisplayName("Should handle empty variable name")
        void shouldHandleEmptyVariableName() {
            String template = "Value: {{}}";
            Map<String, Object> vars = Map.of();

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Value: ", result);
        }

        @Test
        @DisplayName("Should handle consecutive braces")
        void shouldHandleConsecutiveBraces() {
            String template = "{{{{name}}}}";
            Map<String, Object> vars = Map.of("name", "Test", "{{name", "Invalid");

            String result = TemplateEngine.renderTemplate(template, vars);

            // First {{ starts a tag, finds {{name}}, result is Test, then }}
            assertTrue(result.contains("Test"));
        }

        @Test
        @DisplayName("Should handle special characters in values")
        void shouldHandleSpecialCharactersInValues() {
            String template = "Message: {{msg}}";
            Map<String, Object> vars = Map.of("msg", "Hello <>&\"' World");

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Message: Hello <>&\"' World", result);
        }

        @Test
        @DisplayName("Should handle newlines in template")
        void shouldHandleNewlinesInTemplate() {
            String template = "Line 1\n{{var}}\nLine 3";
            Map<String, Object> vars = Map.of("var", "Line 2");

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Line 1\nLine 2\nLine 3", result);
        }

        @Test
        @DisplayName("Should handle tabs and special whitespace")
        void shouldHandleTabsAndWhitespace() {
            String template = "Start\t{{var}}\t\nEnd";
            Map<String, Object> vars = Map.of("var", "Middle");

            String result = TemplateEngine.renderTemplate(template, vars);

            assertEquals("Start\tMiddle\t\nEnd", result);
        }
    }

    // ============================================================
    // Caching Tests
    // ============================================================

    @Nested
    @DisplayName("Template Caching")
    class CachingTests {

        @Test
        @DisplayName("Should cache parsed templates")
        void shouldCacheParsedTemplates() {
            String template = "Hello {{name}}!";
            Map<String, Object> vars1 = Map.of("name", "Alice");
            Map<String, Object> vars2 = Map.of("name", "Bob");

            String result1 = TemplateEngine.renderTemplate(template, vars1);
            String result2 = TemplateEngine.renderTemplate(template, vars2);

            assertEquals("Hello Alice!", result1);
            assertEquals("Hello Bob!", result2);
        }

        @Test
        @DisplayName("Should clear template cache")
        void shouldClearTemplateCache() {
            String template = "Hello {{name}}!";
            Map<String, Object> vars = Map.of("name", "World");

            String result1 = TemplateEngine.renderTemplate(template, vars);
            TemplateEngine.clear();
            String result2 = TemplateEngine.renderTemplate(template, vars);

            assertEquals(result1, result2);
        }

        @Test
        @DisplayName("Should cache reflection field lookups")
        void shouldCacheReflectionFieldLookups() {
            String template = "{{person.name}} - {{person.age}}";
            TestPerson person = new TestPerson("Alice", 30);
            Map<String, Object> vars = Map.of("person", person);

            // First call - caches field lookups
            String result1 = TemplateEngine.renderTemplate(template, vars);

            // Second call - uses cached fields
            TestPerson person2 = new TestPerson("Bob", 25);
            Map<String, Object> vars2 = Map.of("person", person2);
            String result2 = TemplateEngine.renderTemplate(template, vars2);

            assertEquals("Alice - 30", result1);
            assertEquals("Bob - 25", result2);
        }
    }

    // ============================================================
    // Utility Method Tests
    // ============================================================

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethodsTests {

        @Test
        @DisplayName("resolveValue should handle simple keys")
        void resolveValueShouldHandleSimpleKeys() {
            Map<String, Object> vars = Map.of("name", "Test");
            Object result = TemplateEngine.resolveValue("name", vars);
            assertEquals("Test", result);
        }

        @Test
        @DisplayName("resolveValue should handle nested Map keys")
        void resolveValueShouldHandleNestedMapKeys() {
            Map<String, Object> nested = Map.of("city", "NYC");
            Map<String, Object> vars = Map.of("address", nested);
            Object result = TemplateEngine.resolveValue("address.city", vars);
            assertEquals("NYC", result);
        }

        @Test
        @DisplayName("isTruthy should correctly evaluate different types")
        void isTruthyShouldEvaluateCorrectly() {
            assertTrue(TemplateEngine.isTruthy(true));
            assertFalse(TemplateEngine.isTruthy(false));
            assertFalse(TemplateEngine.isTruthy(null));
            assertTrue(TemplateEngine.isTruthy("text"));
            assertFalse(TemplateEngine.isTruthy(""));
            assertFalse(TemplateEngine.isTruthy("   "));
            assertTrue(TemplateEngine.isTruthy(List.of(1)));
            assertFalse(TemplateEngine.isTruthy(List.of()));
            assertTrue(TemplateEngine.isTruthy(Map.of("k", "v")));
            assertFalse(TemplateEngine.isTruthy(Map.of()));
            assertTrue(TemplateEngine.isTruthy(new String[]{"a"}));
            assertFalse(TemplateEngine.isTruthy(new String[]{}));
            assertTrue(TemplateEngine.isTruthy(42));
        }

        @Test
        @DisplayName("getSize should return correct sizes")
        void getSizeShouldReturnCorrectSizes() {
            assertEquals(3, TemplateEngine.getSize(List.of(1, 2, 3)));
            assertEquals(0, TemplateEngine.getSize(List.of()));
            assertEquals(2, TemplateEngine.getSize(Map.of("a", 1, "b", 2)));
            assertEquals(4, TemplateEngine.getSize(new int[]{1, 2, 3, 4}));
            assertEquals(0, TemplateEngine.getSize(null));
        }

        @Test
        @DisplayName("compare should handle all operators")
        void compareShouldHandleAllOperators() {
            assertTrue(TemplateEngine.compare(5, ">", 3));
            assertFalse(TemplateEngine.compare(3, ">", 5));

            assertTrue(TemplateEngine.compare(5, ">=", 5));
            assertTrue(TemplateEngine.compare(5, ">=", 3));

            assertTrue(TemplateEngine.compare(3, "<", 5));
            assertFalse(TemplateEngine.compare(5, "<", 3));

            assertTrue(TemplateEngine.compare(5, "<=", 5));
            assertTrue(TemplateEngine.compare(3, "<=", 5));

            assertTrue(TemplateEngine.compare(5, "==", 5));
            assertFalse(TemplateEngine.compare(5, "==", 3));
        }

        @Test
        @DisplayName("toIterable should convert arrays to iterables")
        void toIterableShouldConvertArrays() {
            String[] array = {"a", "b", "c"};
            Iterable<?> result = TemplateEngine.toIterable(array);
            assertNotNull(result);

            List<Object> list = new ArrayList<>();
            result.forEach(list::add);
            assertEquals(3, list.size());
            assertEquals("a", list.get(0));
        }

        @Test
        @DisplayName("toIterable should handle null")
        void toIterableShouldHandleNull() {
            assertNull(TemplateEngine.toIterable(null));
        }
    }

    // ============================================================
    // Test Helper Classes
    // ============================================================

    static class TestPerson {
        private String name;
        private int age;

        public TestPerson(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public int getAge() { return age; }
    }

    static class TestManager extends TestPerson {
        private String department;

        public TestManager(String name, int age, String department) {
            super(name, age);
            this.department = department;
        }

        public String getDepartment() { return department; }
    }
}
