package ai.driftkit.cli.converters;

import ai.driftkit.cli.commands.NewCommand.BuildSystem;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

public class BuildSystemConverter implements ITypeConverter<BuildSystem> {
    
    @Override
    public BuildSystem convert(String value) throws Exception {
        try {
            // Try to convert to uppercase and match enum
            return BuildSystem.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new TypeConversionException(
                "Invalid build system: '" + value + "'. Valid options are: maven, gradle"
            );
        }
    }
}