package ai.driftkit.context.core.service;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TemplateEngine {

    // Precompiled regex pattern for variable tags (control tags are parsed manually).
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^\\}]+)\\}\\}");

    // Cache for parsed templates (AST) keyed by the original template string.
    private static final ConcurrentHashMap<String, Template> templateCache = new ConcurrentHashMap<>();

    // Reflection cache: key format "ClassName.fieldName"
    private static final ConcurrentHashMap<String, Field> reflectionCache = new ConcurrentHashMap<>();

    public static void clear() {
        templateCache.clear();
        reflectionCache.clear();
    }

    // **************** AST Node Definitions ****************

    interface TemplateNode {
        String render(Map<String, Object> variables);
        boolean isStatic();
    }

    // Represents literal text.
    static class TextNode implements TemplateNode {
        private final String text;
        public TextNode(String text) {
            this.text = text;
        }
        @Override
        public String render(Map<String, Object> variables) {
            return text;
        }
        @Override
        public boolean isStatic() {
            return true;
        }
    }

    // Represents a variable substitution (e.g., {{variable}} or {{object.field}}).
    static class VariableNode implements TemplateNode {
        private final String key;
        public VariableNode(String key) {
            this.key = key;
        }
        @Override
        public String render(Map<String, Object> variables) {
            Object value = resolveValue(key, variables);
            return value != null ? value.toString() : "";
        }
        @Override
        public boolean isStatic() {
            return false;
        }
    }

    // Represents an if-block.
    static class IfNode implements TemplateNode {
        private final String condition;
        private final List<TemplateNode> children;
        // For memoization if the block is static.
        private String memoized;
        public IfNode(String condition, List<TemplateNode> children) {
            this.condition = condition;
            this.children = children;
            this.memoized = null;
        }
        @Override
        public String render(Map<String, Object> variables) {
            if (isStatic() && memoized != null) {
                return memoized;
            }
            boolean result = evaluateCondition(condition, variables);
            StringBuilder sb = new StringBuilder();
            if (result) {
                for (TemplateNode child : children) {
                    sb.append(child.render(variables));
                }
            }
            String output = sb.toString();
            if (isStatic()) {
                memoized = output;
            }
            return output;
        }
        @Override
        public boolean isStatic() {
            boolean condStatic = !condition.matches(".*[a-zA-Z].*") ||
                    condition.trim().equalsIgnoreCase("true") ||
                    condition.trim().equalsIgnoreCase("false");
            for (TemplateNode node : children) {
                if (!node.isStatic()) {
                    return false;
                }
            }
            return condStatic;
        }
    }

    // Represents a list block.
    static class ListNode implements TemplateNode {
        private final String collectionKey;
        private final String itemName;
        private final List<TemplateNode> children;
        public ListNode(String collectionKey, String itemName, List<TemplateNode> children) {
            this.collectionKey = collectionKey;
            this.itemName = itemName;
            this.children = children;
        }
        @Override
        public String render(Map<String, Object> variables) {
            StringBuilder sb = new StringBuilder();
            Object collectionObj = variables.get(collectionKey);
            Iterable<?> iterable = toIterable(collectionObj);
            if (iterable != null) {
                for (Object item : iterable) {
                    Map<String, Object> tempVars = new HashMap<>(variables);
                    tempVars.put(itemName, item);
                    for (TemplateNode child : children) {
                        sb.append(child.render(tempVars));
                    }
                }
            }
            return sb.toString();
        }
        @Override
        public boolean isStatic() {
            return false;
        }
    }

    // Template holds the parsed AST nodes.
    static class Template {
        private final List<TemplateNode> nodes;
        public Template(List<TemplateNode> nodes) {
            this.nodes = nodes;
        }
        public String render(Map<String, Object> variables) {
            StringBuilder sb = new StringBuilder();
            for (TemplateNode node : nodes) {
                sb.append(node.render(variables));
            }
            return sb.toString();
        }
    }

    // **************** Template Parsing (AST Generation) ****************

    // Public method to render a template string with variables using caching.
    public static String renderTemplate(String template, Map<String, Object> variables) {
        Template parsedTemplate = templateCache.get(template);
        if (parsedTemplate == null) {
            parsedTemplate = parseTemplate(template);
            templateCache.put(template, parsedTemplate);
        }
        return parsedTemplate.render(variables);
    }

    // Parse the template into an AST.
    private static Template parseTemplate(String template) {
        ParseResult result = parseNodes(template, 0, null);
        return new Template(result.nodes);
    }

    // Parsing result: list of nodes and the next index.
    private static class ParseResult {
        List<TemplateNode> nodes;
        int nextIndex;
        ParseResult(List<TemplateNode> nodes, int nextIndex) {
            this.nodes = nodes;
            this.nextIndex = nextIndex;
        }
    }

    // Recursively parse nodes until an optional endTag is encountered.
    private static ParseResult parseNodes(String template, int start, String endTag) {
        List<TemplateNode> nodes = new ArrayList<>();
        int index = start;
        while (index < template.length()) {
            int open = template.indexOf("{{", index);
            if (open < 0) {
                nodes.add(new TextNode(template.substring(index)));
                index = template.length();
                break;
            }
            if (open > index) {
                nodes.add(new TextNode(template.substring(index, open)));
            }
            int close = template.indexOf("}}", open);
            if (close < 0) {
                nodes.add(new TextNode(template.substring(open)));
                index = template.length();
                break;
            }
            String tagContent = template.substring(open + 2, close).trim();
            index = close + 2;
            if (tagContent.startsWith("if ") || tagContent.startsWith("#if ")) {
                String condition = tagContent.startsWith("#if ") ? 
                    tagContent.substring(4).trim() : tagContent.substring(3).trim();
                ParseResult inner = parseNodes(template, index, "/if");
                nodes.add(new IfNode(condition, inner.nodes));
                index = inner.nextIndex;
            } else if (tagContent.equals("/if") || tagContent.equals("#/if")) {
                if (endTag != null && endTag.equals("/if")) {
                    return new ParseResult(nodes, index);
                }
                // Don't add the end tag as a text node, it should never be in the output
                // Just ignore it - it's likely a mismatched close tag
            } else if (tagContent.startsWith("list ") || tagContent.startsWith("#list ")) {
                String listContent = tagContent.startsWith("#list ") ? 
                    tagContent.substring(6) : tagContent.substring(5);
                String[] parts = listContent.split("\\s+as\\s+");
                if (parts.length != 2) {
                    nodes.add(new TextNode("{{" + tagContent + "}}"));
                } else {
                    String collectionKey = parts[0].trim();
                    String itemName = parts[1].trim();
                    ParseResult inner = parseNodes(template, index, "/list");
                    nodes.add(new ListNode(collectionKey, itemName, inner.nodes));
                    index = inner.nextIndex;
                }
            } else if (tagContent.equals("/list") || tagContent.equals("#/list")) {
                if (endTag != null && endTag.equals("/list")) {
                    return new ParseResult(nodes, index);
                }
                // Don't add the end tag as a text node, it should never be in the output
                // Just ignore it - it's likely a mismatched close tag
            } else {
                nodes.add(new VariableNode(tagContent));
            }
        }
        return new ParseResult(nodes, index);
    }

    // **************** Utility Methods ****************

    // Resolve dot notation keys (e.g., "user.name") from variables (supports maps and one-level POJOs).
    public static Object resolveValue(String key, Map<String, Object> variables) {
        String[] parts = key.split("\\.");
        Object current = variables.get(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (current == null) return null;
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(parts[i]);
            } else {
                String cacheKey = current.getClass().getName() + "." + parts[i];
                Field field = reflectionCache.get(cacheKey);
                if (field == null) {
                    try {
                        field = getFieldFromClass(current.getClass(), parts[i]);
                        field.setAccessible(true);
                        reflectionCache.put(cacheKey, field);
                    } catch (Exception e) {
                        return null;
                    }
                }
                try {
                    current = field.get(current);
                } catch (IllegalAccessException e) {
                    return null;
                }
            }
        }
        return current;
    }

    private static Field getFieldFromClass(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    // Evaluate conditions supporting both "&&" and "||".
    public static boolean evaluateCondition(String condition, Map<String, Object> variables) {
        String[] orClauses = condition.split("\\|\\|");
        for (String orClause : orClauses) {
            orClause = orClause.trim();
            String[] andClauses = orClause.split("&&");
            boolean allAndTrue = true;
            for (String clause : andClauses) {
                clause = clause.trim();
                if (clause.contains(".size")) {
                    String[] parts = clause.split("\\.");
                    if (parts.length < 2) {
                        allAndTrue = false;
                        break;
                    }
                    String varName = parts[0].trim();
                    String rest = parts[1].trim(); // e.g., "size > 0"
                    Object obj = variables.get(varName);
                    int size = getSize(obj);
                    Pattern p = Pattern.compile("size\\s*(>|>=|<|<=|==)\\s*(\\d+)");
                    Matcher m = p.matcher(rest);
                    if (m.find()) {
                        String operator = m.group(1);
                        int number = Integer.parseInt(m.group(2));
                        if (!compare(size, operator, number)) {
                            allAndTrue = false;
                            break;
                        }
                    } else {
                        allAndTrue = false;
                        break;
                    }
                } else if (clause.contains("==")) {
                    String[] parts = clause.split("==");
                    if (parts.length != 2) {
                        allAndTrue = false;
                        break;
                    }
                    String varName = parts[0].trim();
                    // Handle both normal and escaped quotes
                    String expectedValue = parts[1].trim()
                        .replaceAll("^\"|\"$", "") // Remove surrounding quotes if present
                        .replaceAll("\\\\\"", "\""); // Replace escaped quotes with actual quotes
                    
                    Object actual = variables.get(varName);
                    if (actual == null || !actual.toString().equals(expectedValue)) {
                        allAndTrue = false;
                        break;
                    }
                } else {
                    Object value = variables.get(clause);
                    if (!isTruthy(value)) {
                        allAndTrue = false;
                        break;
                    }
                }
            }
            if (allAndTrue) {
                return true;
            }
        }
        return false;
    }

    // Check if an object is "truthy".
    public static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Collection) return !((Collection<?>) value).isEmpty();
        if (value instanceof Map) return !((Map<?, ?>) value).isEmpty();
        if (value.getClass().isArray()) return Array.getLength(value) > 0;
        if (value instanceof String) return !((String) value).trim().isEmpty();
        return true;
    }

    // Get the size of an object (Collection, array, or Map).
    public static int getSize(Object value) {
        if (value == null) return 0;
        if (value instanceof Collection) return ((Collection<?>) value).size();
        if (value instanceof Map) return ((Map<?, ?>) value).size();
        if (value.getClass().isArray()) return Array.getLength(value);
        return 0;
    }

    // Compare two integers using the given operator.
    public static boolean compare(int a, String operator, int b) {
        switch (operator) {
            case ">":  return a > b;
            case ">=": return a >= b;
            case "<":  return a < b;
            case "<=": return a <= b;
            case "==": return a == b;
            default:   return false;
        }
    }

    // Convert an object to an Iterable (supports Collections and arrays).
    public static Iterable<?> toIterable(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Iterable) return (Iterable<?>) obj;
        if (obj.getClass().isArray()) {
            int length = Array.getLength(obj);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(Array.get(obj, i));
            }
            return list;
        }
        return null;
    }
}