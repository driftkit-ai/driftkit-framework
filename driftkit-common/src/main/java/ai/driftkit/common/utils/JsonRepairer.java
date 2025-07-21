package ai.driftkit.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Slf4j
public class JsonRepairer {
    public static String fixDoubleQuotesAndCommas(String json) {
        List<String> fixedJSON = new ArrayList<>();
        String[] jsonByLines = json.split("\\n");
        for (int i = 0; i < jsonByLines.length; i++) {
            String trimmedLine = jsonByLines[i].trim();

            long noOfDoubleQuote = JsonUtils.countOccurrences(trimmedLine, '"');
            long noOfColon = JsonUtils.countOccurrences(trimmedLine, ':');

            if (isSingleBracket(trimmedLine)) {
                handleSingleBracket(fixedJSON, trimmedLine);
            } else if (isQuotedField(noOfDoubleQuote, noOfColon, trimmedLine)) {
                String fixedDoubleQuotes = getQuoteCorrectedLastField(trimmedLine, jsonByLines, i);
                fixedJSON.add(fixedDoubleQuotes);
            } else if (isIncompleteQuotedField(noOfDoubleQuote)) {
                processIncompleteQuotedField(fixedJSON, trimmedLine, jsonByLines, i);
            }
        }
        return String.join(System.lineSeparator(), fixedJSON);
    }

    private static boolean isSingleBracket(String line) {
        return (line.length() == 1 || line.length() == 2) && JsonUtils.isBracket(line.charAt(0));
    }

    private static void handleSingleBracket(List<String> fixedJSON, String line) {
        if (JsonUtils.isCloseBracket(line.charAt(0))) {
            removeCommaFromPreviousField(fixedJSON);
        }
        fixedJSON.add(line);
    }

    private static boolean isQuotedField(long noOfDoubleQuote, long noOfColon, String line) {
        return noOfDoubleQuote == 4 && (noOfColon >= 1 || line.contains("://"));
    }

    private static boolean isIncompleteQuotedField(long noOfDoubleQuote) {
        return noOfDoubleQuote < 4 && noOfDoubleQuote > 1;
    }

    private static void processIncompleteQuotedField(List<String> fixedJSON, String trimmedLine, String[] jsonByLines, int i) {
        String fixedDoubleQuotes = fixDoubleQuote(trimmedLine);
        String correctedLastField = getQuoteCorrectedLastField(fixedDoubleQuotes, jsonByLines, i);
        String booleanCorrectedField = correctBooleanValues(correctedLastField);
        fixedJSON.add(booleanCorrectedField);
    }

    private static boolean handleClosingBracket(Deque<Character> characterStack, char closingBracket) {
        if (characterStack.isEmpty()) {
            return false;
        }

        char openingBracket = characterStack.pop();
        return JsonUtils.isMatchingBracket(openingBracket, closingBracket);
    }

    static boolean handleOpenBrackets(char jc, Deque<Character> bracketStack) {
        if (JsonUtils.isOpenBracket(jc)) {
            bracketStack.push(jc);
        } else if (JsonUtils.isCloseBracket(jc)) {
            Character topChar = bracketStack.peek();

            if (topChar == null) {
                return false;
            }

            if (JsonUtils.isMatchingBracket(topChar, jc)) {
                bracketStack.pop();
                return true;
            }
        }

        return false;
    }

    public static void appendRemainingClosingBrackets(StringBuilder fixedJson, Deque<Character> bracketStack) {
        while (!bracketStack.isEmpty()) {
            char element = bracketStack.pop();
            appendNewLineAndClosingBracket(fixedJson, element);
        }
    }

    private static void appendNewLineAndClosingBracket(StringBuilder fixedJson, char element) {
        if (JsonUtils.isOpenSquareBracket(element)) {
            fixedJson.append(System.lineSeparator()).append(']');
        } else if (JsonUtils.isOpenCurlyBracket(element)) {
            fixedJson.append(System.lineSeparator()).append('}');
        }
    }


    private static String correctBooleanValues(String boolString) {
        return boolString.replace("false\"", "false").replace("true\"", "true");
    }

    private static String getQuoteCorrectedLastField(String fixedDoubleQuotes, String[] jsonByLines, int i) {
        String lastFieldCorrected = fixedDoubleQuotes;
        if (isLastField(jsonByLines, i)) {
            lastFieldCorrected = fixedDoubleQuotes.replace(",", "");
        }
        return lastFieldCorrected;
    }


    private static boolean isLastField(String[] jsonByLines, int i) {
        return i < jsonByLines.length - 1 && !jsonByLines[i + 1].isEmpty() && JsonUtils.isCloseBracket(jsonByLines[i + 1].trim().charAt(0));
    }

    private static void removeCommaFromPreviousField(List<String> fixedJSON) {
        int lastIndex = fixedJSON.size() - 1;
        if (lastIndex > 0) {
            String lastField = fixedJSON.get(lastIndex).trim();
            if (!lastField.isEmpty()) {
                String prevField = fixedJSON.get(lastIndex).replace(",", "");
                fixedJSON.set(lastIndex, prevField);
            }
        }
    }

    private static String fixDoubleQuote(String jsonField) {
        StringBuilder fixedQuote = new StringBuilder(jsonField);

        ensureLeadingDoubleQuote(fixedQuote);
        appendCommaIfTrailingDoubleQuote(fixedQuote);
        appendCommaIfNeeded(fixedQuote);

        return fixedQuote.toString();
    }

    private static void ensureLeadingDoubleQuote(StringBuilder fixedQuote) {
        if (!startsWithDoubleQuote(fixedQuote)) {
            fixedQuote.insert(0, '"');
        }
    }

    private static boolean startsWithDoubleQuote(StringBuilder fixedQuote) {
        return !fixedQuote.isEmpty() && JsonUtils.isDoubleQuote(fixedQuote.charAt(0));
    }

    private static void appendCommaIfTrailingDoubleQuote(StringBuilder fixedQuote) {
        if (endsWithDoubleQuote(fixedQuote)) {
            fixedQuote.append(',');
        }
    }

    private static boolean endsWithDoubleQuote(StringBuilder fixedQuote) {
        return !fixedQuote.isEmpty() && JsonUtils.isDoubleQuote(fixedQuote.charAt(fixedQuote.length() - 1));
    }

    private static void appendCommaIfNeeded(StringBuilder fixedQuote) {
        char lastChar = fixedQuote.charAt(fixedQuote.length() - 1);

        if (!JsonUtils.isDoubleQuote(lastChar) && !JsonUtils.isComma(lastChar)) {
            if (Character.isDigit(lastChar)) {
                fixedQuote.append(",");
            } else {
                appendCommaBeforeNonBracket(fixedQuote, lastChar);
            }
        }
    }

    private static void appendCommaBeforeNonBracket(StringBuilder fixedQuote, char lastChar) {
        if (!JsonUtils.isOpenBracket(lastChar)) {
            fixedQuote.append("\",");
        }
    }
}