package ai.driftkit.cli.converters;

import ai.driftkit.cli.commands.NewCommand.ProjectTemplate;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

public class TemplateConverter implements ITypeConverter<ProjectTemplate> {
    
    @Override
    public ProjectTemplate convert(String value) throws Exception {
        try {
            // Try to convert to uppercase and match enum
            return ProjectTemplate.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // If not found, throw detailed error
            StringBuilder error = new StringBuilder();
            error.append("Invalid template: '").append(value).append("'\n\n");
            error.append("Available templates:\n");
            error.append("  simple       - Basic AI agent project\n");
            error.append("  full_stack   - Full-stack with Context Engineering UI\n");
            error.append("  chatbot      - Chat bot with human-in-loop\n");
            error.append("  rag_pipeline - RAG pipeline with document ingestion\n");
            error.append("  spring_ai    - Spring AI integration");
            
            throw new TypeConversionException(error.toString());
        }
    }
}