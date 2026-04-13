package ai.driftkit.cli.converters;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

public class TemplateTypeConverter implements ITypeConverter<String> {
    
    private static final String[] VALID_TEMPLATES = {
        "simple", "full_stack", "chatbot", "rag_pipeline", "spring_ai"
    };
    
    @Override
    public String convert(String value) throws Exception {
        String lowerValue = value.toLowerCase();
        
        // Check if it's a valid template
        for (String template : VALID_TEMPLATES) {
            if (template.equals(lowerValue)) {
                return lowerValue;
            }
        }
        
        // If not valid, throw exception with helpful message
        throw new TypeConversionException(
            String.format("Invalid template: '%s'%n%nAvailable templates:%n%s", 
                value, 
                getAvailableTemplatesMessage())
        );
    }
    
    private String getAvailableTemplatesMessage() {
        return "  simple       - Basic AI agent project\n" +
               "  full_stack   - Full-stack with Context Engineering UI and MongoDB\n" +
               "  chatbot      - Chat bot with human-in-loop and WebSocket support\n" +
               "  rag_pipeline - RAG pipeline with document ingestion and vector search\n" +
               "  spring_ai    - Spring AI integration with DriftKit prompt management";
    }
}