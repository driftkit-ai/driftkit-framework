# DriftKit CLI

Command-line interface for the DriftKit AI Framework.

## Overview

DriftKit CLI is the primary tool for developers to create and manage DriftKit projects. It provides commands for:
- Creating new DriftKit projects with pre-configured templates
- Running a local development server with hot reload
- Managing project dependencies

## Installation

### Using Pre-built Binary (Recommended)

Download the appropriate binary for your platform from the releases page.

### Building from Source

```bash
# Clone the repository
git clone https://github.com/driftkit-ai/driftkit.git
cd driftkit/driftkit-cli

# Build with Maven
mvn clean package

# Create native executable (requires GraalVM)
mvn clean package -Pnative
```

## Usage

### Create a New Project

```bash
driftkit new my-ai-app

# With Gradle build system
driftkit new my-ai-app --build=gradle
```

This creates a new DriftKit project with:
- Spring Boot application structure
- Basic DriftKit dependencies
- Sample EchoAgent implementation
- Configuration files

### Start Development Server

```bash
cd my-ai-app
driftkit dev

# Custom port
driftkit dev --port=8080
```

This starts the Spring Boot application with hot reload enabled.

### Add Dependencies

```bash
# Add a vector store
driftkit add vector-pinecone

# Add embedding support
driftkit add embedding-openai

# Add AI client
driftkit add clients-claude
```

Available modules:
- **Vector Stores**: `vector-pinecone`, `vector-spring-ai`
- **Embeddings**: `embedding-openai`, `embedding-cohere`, `embedding-spring-ai`
- **AI Clients**: `clients-openai`, `clients-claude`, `clients-gemini`, `clients-spring-ai`
- **Other**: `context-engineering`, `audio`, `chat-assistant`, `rag`

## Project Structure

Projects created with `driftkit new` have the following structure:

```
my-ai-app/
├── pom.xml (or build.gradle)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/myaiapp/
│   │   │       ├── MyAiAppApplication.java
│   │   │       └── EchoAgent.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/
├── .gitignore
└── README.md
```

## Configuration

The CLI respects the following environment variables:
- `OPENAI_API_KEY` - OpenAI API key for AI operations
- `SERVER_PORT` - Override default server port (4111)

## Development

### Running Tests

```bash
mvn test
```

### Building Native Image

Requires GraalVM 21 or later:

```bash
mvn clean package -Pnative
```

The native executable will be created in `target/driftkit`.

## Troubleshooting

### Command Not Found

Ensure the `driftkit` binary is in your PATH:

```bash
export PATH=$PATH:/path/to/driftkit-cli/target
```

### Build Failures

Make sure you have:
- Java 21 or later
- Maven 3.8+ (for Maven projects)
- Gradle 8+ (for Gradle projects)

### Native Image Build Issues

Ensure GraalVM is properly installed and configured:

```bash
java -version  # Should show GraalVM
native-image --version
```

## Contributing

Please refer to the main DriftKit repository for contribution guidelines.

## License

Apache License, Version 2.0 - see the LICENSE file in the root repository.