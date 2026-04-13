package ai.driftkit.cli.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Command(
    name = "help",
    header = "Show detailed help for DriftKit CLI commands",
    description = {
        "Displays comprehensive help information for all DriftKit CLI commands.",
        "Use 'driftkit help <command>' to see detailed help for a specific command."
    }
)
public class HelpCommand implements Callable<Integer> {

    @CommandLine.ParentCommand
    private Object parent;

    @Parameters(description = "Command to show help for", 
        paramLabel = "<command>",
        arity = "0..1")
    private String command;

    @Override
    public Integer call() throws Exception {
        CommandLine parentCmd = new CommandLine(parent);
        
        if (command == null) {
            // Show general help with all commands
            System.out.println();
            System.out.println("DriftKit CLI - Complete Command Reference");
            System.out.println("========================================");
            System.out.println();
            parentCmd.usage(System.out);
            
            System.out.println("\nDETAILED COMMAND DOCUMENTATION:");
            System.out.println("================================\n");
            
            // Show detailed help for each command
            for (String cmdName : new String[]{"new", "dev", "run", "add"}) {
                CommandLine subCmd = parentCmd.getSubcommands().get(cmdName);
                if (subCmd != null) {
                    System.out.println("\n" + "=".repeat(60));
                    System.out.println("COMMAND: driftkit " + cmdName);
                    System.out.println("=".repeat(60));
                    subCmd.usage(System.out);
                }
            }
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("\nQUICK REFERENCE:");
            System.out.println("================");
            System.out.println();
            System.out.println("Project Creation:");
            System.out.println("  driftkit new my-app --template rag_pipeline --package com.example");
            System.out.println();
            System.out.println("Development:");
            System.out.println("  driftkit dev                    # Start dev server");
            System.out.println("  driftkit run test               # Run tests");
            System.out.println("  driftkit run clean install      # Build project");
            System.out.println("  driftkit add vector-pinecone    # Add dependency");
            System.out.println();
            System.out.println("Templates:");
            System.out.println("  simple       - Basic AI agent");
            System.out.println("  full_stack   - Context Engineering UI + MongoDB");
            System.out.println("  chatbot      - WebSocket chat with human-in-loop");
            System.out.println("  rag_pipeline - Document processing + vector search");
            System.out.println("  spring_ai    - Spring AI integration");
            
        } else {
            // Show help for specific command
            CommandLine subCmd = parentCmd.getSubcommands().get(command);
            if (subCmd != null) {
                subCmd.usage(System.out);
            } else {
                System.err.println("Unknown command: " + command);
                System.err.println("\nAvailable commands: new, dev, run, add, help");
                return 1;
            }
        }
        
        return 0;
    }
}