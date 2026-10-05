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
        default String render(Map<String, Object> variables) {
            return render(variables, false);
        }
        // escapeXml: substituted VALUES get &, <, > escaped; template text is never touched.
        String render(Map<String, Object> variables, boolean escapeXml);
        boolean isStatic();
    }

    private static String renderAll(List<TemplateNode> nodes, Map<String, Object> variables, boolean escapeXml) {
        StringBuilder sb = new StringBuilder();
        for (TemplateNode node : nodes) {
            sb.append(node.render(variables, escapeXml));
        }
        return sb.toString();
    }

    // Escape a substituted value so it cannot open or close markup tags of the template.
    static String escapeXml(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    // Represents literal text.
    static class TextNode implements TemplateNode {
        private final String text;
        public TextNode(String text) {
            this.text = text;
        }
        @Override
        public String render(Map<String, Object> variables, boolean escapeXml) {
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
        public String render(Map<String, Object> variables, boolean escapeXml) {
            // If the key contains {{ }}, treat it as a nested template
            if (key.contains("{{") && key.contains("}}")) {
                return renderTemplate(key, variables, escapeXml);
            }
            Object value = resolveValue(key, variables);
            if (value == null) return "";
            String text = value.toString();
            return escapeXml ? escapeXml(text) : text;
        }
        @Override
        public boolean isStatic() {
            return false;
        }
    }

    // Represents an if-block with an optional {{else}} branch.
    static class IfNode implements TemplateNode {
        private final String condition;
        private final List<TemplateNode> children;
        private final List<TemplateNode> elseChildren;
        // For memoization if the block is static.
        private String memoized;
        public IfNode(String condition, List<TemplateNode> children) {
            this(condition, children, List.of());
        }
        public IfNode(String condition, List<TemplateNode> children, List<TemplateNode> elseChildren) {
            this.condition = condition;
            this.children = children;
            this.elseChildren = elseChildren == null ? List.of() : elseChildren;
            this.memoized = null;
        }
        @Override
        public String render(Map<String, Object> variables, boolean escapeXml) {
            if (isStatic() && memoized != null) {
                return memoized;
            }
            boolean result = evaluateCondition(condition, variables);
            String output = renderAll(result ? children : elseChildren, variables, escapeXml);
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
            for (TemplateNode node : elseChildren) {
                if (!node.isStatic()) {
                    return false;
                }
            }
            return condStatic;
        }
    }

    // Represents a list block: {{list items as item}} and {{#each items}} (item is {{this}}).
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
        public String render(Map<String, Object> variables, boolean escapeXml) {
            StringBuilder sb = new StringBuilder();
            Object collectionObj = resolveValue(collectionKey, variables);
            Iterable<?> iterable = toIterable(collectionObj);
            if (iterable != null) {
                int index = 0;
                for (Object item : iterable) {
                    Map<String, Object> tempVars = new HashMap<>(variables);
                    tempVars.put(itemName, item);
                    tempVars.put("@index", index++);
                    sb.append(renderAll(children, tempVars, escapeXml));
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
        public String render(Map<String, Object> variables, boolean escapeXml) {
            return renderAll(nodes, variables, escapeXml);
        }
    }

    // **************** Template Parsing (AST Generation) ****************

    // Public method to render a template string with variables using caching.
    public static String renderTemplate(String template, Map<String, Object> variables) {
        return renderTemplate(template, variables, false);
    }

    /**
     * Renders the template; with {@code escapeXml} every substituted value gets {@code &}, {@code <},
     * {@code >} escaped, so a value can never open or close a markup tag of a tag-structured prompt.
     * The template text itself is never escaped.
     */
    public static String renderTemplate(String template, Map<String, Object> variables, boolean escapeXml) {
        Template parsedTemplate = templateCache.get(template);
        if (parsedTemplate == null) {
            parsedTemplate = parseTemplate(template);
            templateCache.put(template, parsedTemplate);
        }
        return parsedTemplate.render(variables == null ? Map.of() : variables, escapeXml);
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
        // true when parsing stopped at {{else}} of the enclosing if-block (not at its {{/if}})
        boolean stoppedAtElse;
        ParseResult(List<TemplateNode> nodes, int nextIndex) {
            this(nodes, nextIndex, false);
        }
        ParseResult(List<TemplateNode> nodes, int nextIndex, boolean stoppedAtElse) {
            this.nodes = nodes;
            this.nextIndex = nextIndex;
            this.stoppedAtElse = stoppedAtElse;
        }
    }

    // Find the matching closing braces "}}" for an opening at position 'open',
    // accounting for nested "{{" ... "}}" pairs.
    private static int findMatchingClose(String template, int open) {
        int depth = 1;
        int pos = open + 2; // Start after the opening "{{"
        while (pos < template.length() - 1) {
            if (template.charAt(pos) == '{' && template.charAt(pos + 1) == '{') {
                depth++;
                pos += 2;
            } else if (template.charAt(pos) == '}' && template.charAt(pos + 1) == '}') {
                depth--;
                if (depth == 0) {
                    return pos;
                }
                pos += 2;
            } else {
                pos++;
            }
        }
        return -1; // No matching close found
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
            int close = findMatchingClose(template, open);
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
                List<TemplateNode> elseNodes = List.of();
                if (inner.stoppedAtElse) {
                    ParseResult elseBranch = parseNodes(template, inner.nextIndex, "/if");
                    elseNodes = elseBranch.nodes;
                    inner = new ParseResult(inner.nodes, elseBranch.nextIndex);
                }
                nodes.add(new IfNode(condition, inner.nodes, elseNodes));
                index = inner.nextIndex;
            } else if (tagContent.equals("else") || tagContent.equals("#else")) {
                if ("/if".equals(endTag)) {
                    return new ParseResult(nodes, index, true);
                }
                // Orphaned else (outside an if-block, or a second else) - ignore it
            } else if (tagContent.startsWith("#each ") || tagContent.startsWith("each ")) {
                String collectionKey = tagContent.substring(tagContent.indexOf(' ') + 1).trim();
                ParseResult inner = parseNodes(template, index, "/each");
                nodes.add(new ListNode(collectionKey, "this", inner.nodes));
                index = inner.nextIndex;
            } else if (tagContent.equals("/each") || tagContent.equals("#/each")) {
                if ("/each".equals(endTag)) {
                    return new ParseResult(nodes, index);
                }
                // Orphaned /each tag - ignore it
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
                    // Malformed list syntax - treat as text and find matching /list to include as text too
                    nodes.add(new TextNode("{{" + tagContent + "}}"));
                    // Find and consume the matching {{/list}} tag to include it as text
                    int listClose = template.indexOf("{{/list}}", index);
                    if (listClose >= 0) {
                        // Add everything between as text
                        if (listClose > index) {
                            nodes.add(new TextNode(template.substring(index, listClose)));
                        }
                        nodes.add(new TextNode("{{/list}}"));
                        index = listClose + 9; // length of "{{/list}}"
                    }
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
                // Orphaned /list tag - ignore it (don't add as text)
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
                    // Extract the path before ".size" (e.g., "order.items" from "order.items.size > 0")
                    int sizeIndex = clause.indexOf(".size");
                    String pathBeforeSize = clause.substring(0, sizeIndex).trim();
                    String rest = clause.substring(sizeIndex + 5).trim(); // everything after ".size"

                    Object obj = resolveValue(pathBeforeSize, variables);
                    int size = getSize(obj);
                    Pattern p = Pattern.compile("^\\s*(>|>=|<|<=|==)\\s*(\\d+)");
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

                    Object actual = resolveValue(varName, variables);
                    if (actual == null || !actual.toString().equals(expectedValue)) {
                        allAndTrue = false;
                        break;
                    }
                } else {
                    // literal true/false: isStatic() treats them as constants, so must the evaluation
                    Object value = clause.equalsIgnoreCase("true") ? Boolean.TRUE
                            : clause.equalsIgnoreCase("false") ? Boolean.FALSE
                            : resolveValue(clause, variables);
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
        if (value instanceof String) {
            // "false" is a value a caller writes for "no" - it must not render the block
            String s = ((String) value).trim();
            return !s.isEmpty() && !s.equalsIgnoreCase("false");
        }
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