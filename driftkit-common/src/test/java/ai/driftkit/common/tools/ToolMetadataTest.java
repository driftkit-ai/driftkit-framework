package ai.driftkit.common.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolMetadataTest {

    static class Fixture {
        @Tool(description = "Get weather", whenToUse = "User asks about current weather",
                whenNotToUse = "For forecasts beyond today", readOnly = true,
                concurrencySafe = true, maxResultChars = 1000)
        public String getWeather(String city) {
            return "sunny";
        }

        @Tool(description = "Delete a record", destructive = true)
        public String deleteRecord(String key) {
            return "deleted";
        }

        @Tool(description = "Plain tool")
        public String plain(String input) {
            return input;
        }
    }

    @Test
    void annotationMetadataIsCapturedInToolInfo() {
        List<ToolInfo> tools = ToolAnalyzer.analyzeClass(Fixture.class, new Fixture());

        ToolInfo weather = tools.stream().filter(t -> t.getFunctionName().equals("getWeather")).findFirst().orElseThrow();
        assertTrue(weather.getMetadata().isReadOnly());
        assertTrue(weather.getMetadata().isConcurrencySafe());
        assertFalse(weather.getMetadata().isDestructive());
        assertEquals(1000, weather.getMetadata().getMaxResultChars());

        ToolInfo delete = tools.stream().filter(t -> t.getFunctionName().equals("deleteRecord")).findFirst().orElseThrow();
        assertTrue(delete.getMetadata().isDestructive());
        assertFalse(delete.getMetadata().isConcurrencySafe(), "default must be serial");
    }

    @Test
    void modelFacingDescriptionIncludesUsageGuidance() {
        List<ToolInfo> tools = ToolAnalyzer.analyzeClass(Fixture.class, new Fixture());

        ToolInfo weather = tools.stream().filter(t -> t.getFunctionName().equals("getWeather")).findFirst().orElseThrow();
        String modelFacing = weather.getToolDefinition().getFunction().getDescription();
        assertTrue(modelFacing.contains("Get weather"));
        assertTrue(modelFacing.contains("When to use: User asks about current weather"));
        assertTrue(modelFacing.contains("When NOT to use: For forecasts beyond today"));

        // Domain description stays clean
        assertEquals("Get weather", weather.getDescription());

        ToolInfo plain = tools.stream().filter(t -> t.getFunctionName().equals("plain")).findFirst().orElseThrow();
        assertEquals("Plain tool", plain.getToolDefinition().getFunction().getDescription());
    }

    @Test
    void composeDescriptionPureUnit() {
        // Independent of ToolAnalyzer: spec of the composition itself
        ToolMetadata both = ToolMetadata.builder().whenToUse("for X").whenNotToUse("for Y").build();
        assertEquals("base\nWhen to use: for X\nWhen NOT to use: for Y", both.composeDescription("base"));

        ToolMetadata onlyWhen = ToolMetadata.builder().whenToUse("for X").build();
        assertEquals("base\nWhen to use: for X", onlyWhen.composeDescription("base"));

        assertEquals("base", ToolMetadata.defaults().composeDescription("base"));
        assertEquals("\nWhen to use: for X", onlyWhen.composeDescription(null),
                "null base must not turn into the string 'null'");
    }

    @Test
    void defaultsAreReturnedWhenMetadataAbsent() {
        ToolInfo info = ToolInfo.builder().functionName("x").build();
        assertNotNull(info.getMetadata());
        assertFalse(info.getMetadata().isConcurrencySafe());
        assertEquals(ToolMetadata.DEFAULT_MAX_RESULT_CHARS, info.getMetadata().getMaxResultChars());
    }
}
