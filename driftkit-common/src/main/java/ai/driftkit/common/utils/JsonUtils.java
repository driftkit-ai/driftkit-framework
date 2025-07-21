package ai.driftkit.common.utils;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class JsonUtils {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ObjectReader mapperRelaxed = mapper.reader()
            .with(Feature.ALLOW_COMMENTS)
            .with(Feature.ALLOW_MISSING_VALUES)
            .with(Feature.ALLOW_TRAILING_COMMA)
            .with(Feature.ALLOW_COMMENTS)
            .with(Feature.ALLOW_SINGLE_QUOTES)
            .with(Feature.ALLOW_YAML_COMMENTS)
            .with(Feature.ALLOW_UNQUOTED_FIELD_NAMES)
            .with(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .with(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION);

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[^\\{\\}]*\\}");
    private static final Pattern EXTRACT_JSON_PATTERN = Pattern.compile("\\{");
    public static final String JSON_PREFIX = "```json";
    public static final String JSON_POSTFIX = "```";

    private JsonUtils() {
    }

    public static boolean isContainJson(String text) {
        Matcher matcher = JSON_PATTERN.matcher(text);
        return matcher.find();
    }

    public static String extractJsonFromText(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }

        Matcher matcher = EXTRACT_JSON_PATTERN.matcher(text);
        Stack<Character> braceStack = new Stack<>();
        int startIndex = -1;
        int endIndex = -1;

        // Search for the first curly brace that starts the JSON
        while (matcher.find()) {
            startIndex = matcher.start();
            braceStack.push('{');

            // Traverse through the text and balance the curly braces
            for (int i = startIndex + 1; i < text.length(); i++) {
                char currentChar = text.charAt(i);

                if (currentChar == '{') {
                    braceStack.push('{');
                } else if (currentChar == '}') {
                    braceStack.pop();
                }

                // When the stack is empty, we've found the matching closing brace
                if (braceStack.isEmpty()) {
                    endIndex = i;
                    break;
                }
            }

            // If we found a matching closing brace, extract the JSON
            if (endIndex != -1) {
                return text.substring(startIndex, endIndex + 1);
            }
        }

        return null;
    }

    public static boolean isOpenCurlyBracket(char c) {
        return c == '{';
    }

    public static boolean isCloseCurlyBracket(char c) {
        return c == '}';
    }

    public static boolean isOpenSquareBracket(char c) {
        return c == '[';
    }

    public static boolean isCloseSquareBracket(char c) {
        return c == ']';
    }

    public static boolean isDoubleQuote(char c) {
        return c == '"';
    }

    public static boolean isDoubleQuote(int c) {
        return c == '"';
    }

    public static boolean isComma(char c) {
        return c == ',';
    }

    public static boolean isColon(int c) {
        return c == ':';
    }

    public static boolean isMatchingBracket(char opening, char closing) {
        return (opening == '{' && closing == '}') || (opening == '[' && closing == ']');
    }

    public static boolean isOpenBracket(char c) {
        return isOpenCurlyBracket(c) || isOpenSquareBracket(c);
    }

    public static boolean isCloseBracket(char c) {
        return isCloseCurlyBracket(c) || isCloseSquareBracket(c);
    }

    public static boolean isBracket(char c) {
        return isOpenBracket(c) || isCloseBracket(c);
    }

    public static long countOccurrences(String line, char character) {
        return line.chars().filter(c -> c == character).count();
    }

    public static boolean isJSON(String jsonString) {
        return jsonString != null && (jsonString.startsWith("\"{") || jsonString.contains(JSON_PREFIX) || jsonString.startsWith("json") || jsonString.startsWith("{") || jsonString.startsWith("["));
    }

    public static boolean isValidJSON(String jsonString) {
        try {
            mapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 1. Add missing brackets
     * 2. Remove uncompleted fields
     * 3. Add correct double quotes
     * 4. Remove extra comma
     */
    @SneakyThrows
    public static String fixIncompleteJSON(String jsonString) {
        if (isValidJSON(jsonString) && !jsonString.startsWith("\"{")) {
            return jsonString;
        }

        if (jsonString.contains(JSON_PREFIX)) {
            jsonString = jsonString.substring(jsonString.lastIndexOf(JSON_PREFIX));
        }

        if (jsonString.startsWith("\"{") || jsonString.startsWith("\"[")) {
            jsonString = jsonString.replace("\\\"", "\"").substring(1);
            jsonString = jsonString.substring(0, jsonString.length() - 1);
        }

        jsonString = jsonString
                .replace(JSON_PREFIX, "")
                .replace(JSON_POSTFIX, "");
        //.replace("\\\"", "\"");

        for (int i = 4; i > 0; i--) {
            String open = StringUtils.repeat('[', i);
            String close = StringUtils.repeat(']', i);

            if (jsonString.startsWith(open)) {
                jsonString = jsonString.substring(open.length());
            }

            if (jsonString.endsWith(close)) {
                jsonString = jsonString.substring(0, jsonString.length() - close.length());
            }
        }

        if (jsonString.startsWith("{") && !jsonString.endsWith("}")) {
            jsonString += "}";
        }

        StringBuilder fixedJson = new StringBuilder();
        Deque<Character> bracketStack = new ArrayDeque<>();

        char[] jsonChars = jsonString.toCharArray();

        for (char jc : jsonChars) {
            JsonRepairer.handleOpenBrackets(jc, bracketStack);

            //Fix absent ] in array
            if (jc == ':' && bracketStack.peek() != null && bracketStack.peek() == '[') {
                String arrayContent = fixedJson.substring(fixedJson.lastIndexOf("["));

                if (!arrayContent.contains("\":")) {
                    int commaIndex = fixedJson.lastIndexOf(",");

                    if (commaIndex > 0) {
                        fixedJson.replace(commaIndex, commaIndex + 1, "],");
                        bracketStack.pop();
                    }
                }
            }

            fixedJson.append(jc);
        }

        JsonRepairer.appendRemainingClosingBrackets(fixedJson, bracketStack);

        //String fixed = fixDoubleQuotesAndCommas(fixedJson.toString());
        String result = fixedJson.toString().trim();

        String fixed = null;

        if (JsonUtils.isValidJSON(result)) {
            fixed = result;
        } else {
            String[] lines = result.split("\n");

            int startIdx = -1;
            int endIdx = -1;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();

                if (endIdx < 0 && line.startsWith("{")) {
                    startIdx = i;
                }

                if (startIdx > 0 && line.endsWith("}")) {
                    endIdx = i;
                }
            }

            if (startIdx > 0 && endIdx > 0) {
                String currentResult = Arrays.asList(lines).subList(startIdx, endIdx + 1).stream().collect(Collectors.joining("\n"));

                if (JsonUtils.isValidJSON(currentResult)) {
                    fixed = currentResult;
                }
            }

            if (fixed == null) {
                fixed = extractJsonFromText(fixed);
            }
        }

        try {
            JsonNode jsonNode = mapperRelaxed.readTree(fixed);

            return mapper.writeValueAsString(jsonNode);
        } catch (Exception e) {
            log.error("[json-repair] Failed json repair input: [%s], output: [%s]".formatted(jsonString, fixed), e);
            throw e;
        }
    }

    public static String toJson(Object context) throws JsonProcessingException {
        return mapper.writeValueAsString(context);
    }

    public static <T> T fromJson(String str, Class<T> cls) throws JsonProcessingException {
        return mapper.readValue(str, cls);
    }

    /**
     * Safely parse JSON regardless of its format
     * @param json String containing potential JSON
     * @param cls Class to parse into
     * @return Parsed object or null if parsing fails
     */
    public static <T> T safeParse(String json, Class<T> cls) {
        try {
            if (isJSON(json)) {
                String fixedJson = fixIncompleteJSON(json);
                return fromJson(fixedJson, cls);
            }
        } catch (Exception e) {
            log.error("Failed to parse JSON: {}", e.getMessage());
        }
        return null;
    }
}