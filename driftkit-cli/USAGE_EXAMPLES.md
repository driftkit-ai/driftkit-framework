# DriftKit CLI Usage Examples

## Error Handling for Invalid Templates

When you specify an invalid template, the CLI will show available options:

```bash
$ driftkit new my-app --template wrong-template

Invalid value for option '--template': expected one of [SIMPLE, FULL_STACK, CHATBOT, RAG_PIPELINE, SPRING_AI] (case-sensitive) but was 'wrong-template'

Available templates:
  simple       - Basic AI agent project
  full_stack   - Full-stack with Context Engineering UI
  chatbot      - Chat bot with human-in-loop
  rag_pipeline - RAG pipeline with document ingestion
  spring_ai    - Spring AI integration

Usage: driftkit new <project-name> [--build=<buildSystem>] [--package=<package>] [--template=<template>]
...
```

## Comprehensive Help Documentation

### General Help
```bash
$ driftkit help

DriftKit CLI - Complete Command Reference
========================================

Usage: driftkit [-hV] [-v] [COMMAND]
...

DETAILED COMMAND DOCUMENTATION:
================================

============================================================
COMMAND: driftkit new
============================================================
Create a new DriftKit project
...

============================================================
COMMAND: driftkit dev
============================================================
Start the DriftKit development server
...

============================================================
COMMAND: driftkit run
============================================================
Run Maven or Gradle commands in the project
...

============================================================
COMMAND: driftkit add
============================================================
Add DriftKit module dependencies to your project
...
```

### Command-Specific Help
```bash
$ driftkit new --help

Create a new DriftKit project

Usage: driftkit new <project-name> [--build=<buildSystem>] [--package=<package>] [--template=<template>]

Creates a new DriftKit AI project with the specified template and configuration.
Choose from various templates optimized for different AI use cases.

Parameters:
      <project-name>    Project name (will be used as directory name)

Options:
      --build=<buildSystem>
                        Build system to use: MAVEN, GRADLE (default: MAVEN)
      --package=<package>
                        Java package name for the project (default: com.example)
      --template=<template>
                        Project template to use. See footer for available templates (default: simple)

Examples:
  driftkit new my-app
  driftkit new my-rag --template rag_pipeline --package com.mycompany.ai
  driftkit new chatbot --template chatbot --build gradle

Available templates:
  simple       - Basic AI agent project
  full_stack   - Full-stack with Context Engineering UI
  chatbot      - Chat bot with human-in-loop
  rag_pipeline - RAG pipeline with document ingestion
  spring_ai    - Spring AI integration
```

## Working in Project Root

All commands work from within a DriftKit project:

```bash
cd my-project

# Start development server
driftkit dev

# Run tests
driftkit run test

# Build project
driftkit run clean install

# Add dependencies
driftkit add vector-pinecone

# Get help
driftkit help
```

## Key Features

1. **Single Source of Truth**: All documentation comes from command annotations
2. **Template Validation**: Invalid templates show available options immediately
3. **Context-Aware**: Commands detect project type (Maven/Gradle) automatically
4. **Detailed Examples**: Every command includes usage examples in its help
5. **Comprehensive Documentation**: `driftkit help` shows complete documentation for all commands