package ai.driftkit.cli;

import ai.driftkit.cli.commands.AddCommand;
import ai.driftkit.cli.commands.DevCommand;
import ai.driftkit.cli.commands.HelpCommand;
import ai.driftkit.cli.commands.NewCommand;
import ai.driftkit.cli.commands.RunCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(
    name = "driftkit",
    mixinStandardHelpOptions = true,
    version = "driftkit-cli 0.6.0",
    header = {
        "@|bold,blue ╔╦╗╦═╗╦╔═╗╔╦╗╦╔═╦╔╦╗  ╔═╗╦  ╦|@",
        "@|bold,blue  ║║╠╦╝║╠╣  ║ ╠╩╗║ ║   ║  ║  ║|@",
        "@|bold,blue ═╩╝╩╚═╩╚   ╩ ╩ ╩╩ ╩   ╚═╝╩═╝╩|@",
        ""
    },
    description = {
        "DriftKit CLI - Command line tool for the DriftKit AI Framework.",
        "Build AI-powered applications with workflow orchestration, prompt engineering, and vector search."
    },
    footerHeading = "%nGetting Started:%n",
    footer = {
        "  1. Create a new project:    driftkit new my-app",
        "  2. Navigate to project:     cd my-app",
        "  3. Start development:       driftkit dev",
        "",
        "For detailed help on any command, use: driftkit <command> --help",
        "Documentation: https://docs.driftkit.ai",
        "GitHub: https://github.com/driftkit/framework"
    },
    subcommands = {
        NewCommand.class,
        DevCommand.class,
        AddCommand.class,
        RunCommand.class,
        HelpCommand.class
    },
    usageHelpAutoWidth = true
)
public class DriftKitCLI implements Callable<Integer> {

    @Option(names = {"-v", "--verbose"}, 
        description = "Enable verbose output for debugging",
        scope = CommandLine.ScopeType.INHERIT)
    private boolean verbose;

    public static void main(String[] args) {
        DriftKitCLI cli = new DriftKitCLI();
        CommandLine cmd = new CommandLine(cli);
        
        // Default color scheme will be used automatically
        
        // Set custom exception handlers
        cmd.setParameterExceptionHandler(new CommandLine.IParameterExceptionHandler() {
            @Override
            public int handleParseException(CommandLine.ParameterException ex, String[] args) {
                CommandLine cmd = ex.getCommandLine();
                System.err.println(ex.getMessage());
                
                // If it's a template error, show available templates
                if (ex.getMessage().contains("template")) {
                    System.err.println("\nAvailable templates:");
                    System.err.println("  simple       - Basic AI agent project");
                    System.err.println("  full_stack   - Full-stack with Context Engineering UI");
                    System.err.println("  chatbot      - Chat bot with human-in-loop");
                    System.err.println("  rag_pipeline - RAG pipeline with document ingestion");
                    System.err.println("  spring_ai    - Spring AI integration");
                }
                
                System.err.println();
                cmd.usage(System.err);
                return 1;
            }
        });
        
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        // When called without subcommand, show help
        CommandLine cmd = new CommandLine(this);
        cmd.usage(System.out);
        return 0;
    }
}