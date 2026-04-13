# DriftKit CLI

Command-line interface for the DriftKit AI Framework that simplifies project creation, development, and management of AI-powered applications.

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Commands](#commands)
- [Project Templates](#project-templates)
- [Working with Projects](#working-with-projects)
- [Examples](#examples)
- [Configuration](#configuration)
- [Troubleshooting](#troubleshooting)

## Installation

### Prerequisites

- Java 21 or higher
- Maven 3.8+ or Gradle 8+
- Docker (optional, for templates with databases)

### Installing DriftKit CLI

#### Option 1: Automated Installation (Recommended)

**macOS/Linux:**
```bash
# Clone the repository
git clone https://github.com/driftkit/framework.git
cd framework/driftkit-cli

# Run install script
./install.sh

# Or with sudo if needed
sudo ./install.sh
```

**Windows (PowerShell as Administrator):**
```powershell
# Clone the repository
git clone https://github.com/driftkit/framework.git
cd framework/driftkit-cli

# Run install script
.\install.ps1
```

#### Option 2: Manual Installation

```bash
# Build the CLI
cd framework/driftkit-cli
mvn clean package

# Create installation directory
sudo mkdir -p /usr/local/lib/driftkit

# Copy JAR
sudo cp target/driftkit-cli-*-jar-with-dependencies.jar /usr/local/lib/driftkit/driftkit-cli.jar

# Create executable script
sudo tee /usr/local/bin/driftkit > /dev/null << 'EOF'
#!/bin/bash
exec java -jar /usr/local/lib/driftkit/driftkit-cli.jar "$@"
EOF

# Make executable
sudo chmod +x /usr/local/bin/driftkit
```

#### Option 3: Using Homebrew (macOS - Coming Soon)

```bash
brew tap driftkit/tap
brew install driftkit-cli
```

### Verify Installation

```bash
driftkit --version
# Output: driftkit-cli 0.6.0
```

## Quick Start

### Create Your First Project

```bash
# Create a simple AI agent project
driftkit new my-ai-app

# Create with specific package and template
driftkit new my-rag-app --package com.mycompany.ai --template rag_pipeline

# Navigate to project
cd my-ai-app

# Start development server
driftkit dev
```

## Commands

### `driftkit new`

Creates a new DriftKit project with specified template and configuration.

```bash
driftkit new <project-name> [options]
```

**Options:**
- `--template <template>` - Project template (default: simple)
  - `simple` - Basic AI agent project
  - `full_stack` - Full-stack app with Context Engineering UI
  - `chatbot` - Chat bot with human-in-loop
  - `rag_pipeline` - RAG pipeline with document ingestion
  - `spring_ai` - Spring AI integration
- `--package <package>` - Java package name (default: com.example)
- `--build <system>` - Build system: MAVEN or GRADLE (default: MAVEN)

**Examples:**
```bash
# Simple project
driftkit new my-agent

# RAG application with custom package
driftkit new doc-search --package com.acme.search --template rag_pipeline

# Chatbot with Gradle
driftkit new support-bot --template chatbot --build gradle
```

### `driftkit dev`

Starts the development server for your DriftKit application.

```bash
driftkit dev [options]
```

**Options:**
- `-p, --port <port>` - Server port (default: 4111)
- `-d, --directory <dir>` - Project directory (default: current directory)

**Examples:**
```bash
# Start dev server in current directory
driftkit dev

# Start on different port
driftkit dev --port 8080

# Start from specific directory
driftkit dev -d ./my-project
```

### `driftkit run`

Executes Maven or Gradle commands within your project. Automatically detects the build system.

```bash
driftkit run <command> [options]
```

**Options:**
- `-s, --skip-tests` - Skip test execution
- `-p, --profile <profile>` - Activate build profile
- `-D <property=value>` - Define system properties
- `-d, --directory <dir>` - Project directory

**Examples:**
```bash
# Build project
driftkit run clean install

# Build without tests
driftkit run clean install -s

# Run with profile
driftkit run package -p production

# Run tests only
driftkit run test

# Custom Maven goals
driftkit run dependency:tree
driftkit run versions:display-dependency-updates
```

### `driftkit add`

Adds DriftKit modules or dependencies to your project.

```bash
driftkit add <module> [options]
```

**Available Modules:**
- `embedding-openai` - OpenAI embeddings support
- `embedding-cohere` - Cohere embeddings support
- `vector-pinecone` - Pinecone vector store
- `vector-qdrant` - Qdrant vector store
- `clients-gemini` - Google Gemini AI client
- `clients-claude` - Anthropic Claude client
- `audio-transcription` - Audio processing capabilities

**Examples:**
```bash
# Add OpenAI embeddings
driftkit add embedding-openai

# Add multiple modules
driftkit add vector-pinecone clients-gemini
```

## Project Templates

### 1. Simple Template

Basic AI agent project with minimal setup.

**Features:**
- Basic Spring Boot application
- Simple echo agent example
- OpenAI client integration
- Ready-to-use project structure

**Usage:**
```bash
driftkit new my-agent --template simple
cd my-agent
export OPENAI_API_KEY=your-key-here
driftkit dev
```

### 2. Full-Stack Template

Complete application with Context Engineering UI for prompt management.

**Features:**
- MongoDB for prompt storage
- Vue.js frontend for prompt engineering
- REST API for prompt management
- Docker Compose setup
- Workflow examples

**Usage:**
```bash
driftkit new my-fullstack-app --template full_stack
cd my-fullstack-app
docker-compose up -d  # Start MongoDB
driftkit dev
# Access UI at http://localhost:8080/context-engineering
```

### 3. Chatbot Template

Real-time chat application with human-in-the-loop capabilities.

**Features:**
- WebSocket (STOMP) communication
- Multi-step conversational workflows
- Human review system
- Role-based access (user/reviewer)
- Session management

**Usage:**
```bash
driftkit new my-chatbot --template chatbot
cd my-chatbot
driftkit dev
# Chat interface at http://localhost:8080
# Reviewer interface at http://localhost:8080?reviewer=true
```

### 4. RAG Pipeline Template

Document processing and retrieval-augmented generation system.

**Features:**
- Document upload and processing (PDF, DOCX, TXT, etc.)
- Vector storage for semantic search
- REST API for document management
- RAG workflow implementation
- MongoDB + optional Qdrant

**Usage:**
```bash
driftkit new my-rag-app --template rag_pipeline
cd my-rag-app
docker-compose up -d  # Start databases
driftkit dev

# Upload documents
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@document.pdf"

# Search documents
curl "http://localhost:8080/api/documents/search?query=your+search+query"
```

### 5. Spring AI Template

Integration with Spring AI framework using DriftKit's prompt management.

**Features:**
- Spring AI ChatClient integration
- DriftKit prompt management
- Multi-language support
- Structured output generation
- Tracing and monitoring

**Usage:**
```bash
driftkit new my-spring-ai-app --template spring_ai
cd my-spring-ai-app
export OPENAI_API_KEY=your-key-here
docker-compose up -d  # Start MongoDB
driftkit dev

# Test endpoints
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello AI!"}'
```

## Working with Projects

### Project Structure

All DriftKit projects follow a standard structure:

```
my-project/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/myproject/
│   │   │       ├── Application.java
│   │   │       ├── config/
│   │   │       ├── controller/
│   │   │       ├── service/
│   │   │       └── workflow/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── static/
│   └── test/
├── pom.xml (or build.gradle)
├── docker-compose.yml (if applicable)
└── README.md
```

### Running Commands in Project Root

Once you're in a DriftKit project directory, you can run all CLI commands without specifying the directory:

```bash
cd my-project

# All commands work in project context
driftkit dev                    # Start dev server
driftkit run test               # Run tests
driftkit run clean package      # Build project
driftkit add vector-pinecone    # Add dependencies
```

### Common Development Workflow

1. **Create Project**
   ```bash
   driftkit new awesome-ai --package com.company.ai --template full_stack
   cd awesome-ai
   ```

2. **Set Up Environment**
   ```bash
   # Copy example env file if exists
   cp .env.example .env
   
   # Edit configuration
   vi src/main/resources/application.yml
   
   # Start required services
   docker-compose up -d
   ```

3. **Development**
   ```bash
   # Start dev server with hot reload
   driftkit dev
   
   # In another terminal - run tests continuously
   driftkit run test
   
   # Check for dependency updates
   driftkit run versions:display-dependency-updates
   ```

4. **Add Features**
   ```bash
   # Add vector storage
   driftkit add vector-pinecone
   
   # Add audio capabilities
   driftkit add audio-transcription
   ```

5. **Build and Package**
   ```bash
   # Full build with tests
   driftkit run clean install
   
   # Package for deployment
   driftkit run clean package -p production
   ```

## Examples

### Building a Document Q&A System

```bash
# 1. Create RAG project
driftkit new doc-qa --package com.example.qa --template rag_pipeline

cd doc-qa

# 2. Start services
docker-compose up -d

# 3. Configure API keys
export OPENAI_API_KEY=sk-...
export PINECONE_API_KEY=...  # Optional

# 4. Start application
driftkit dev

# 5. Upload documents
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@manual.pdf" \
  -F "metadata[category]=user-manual"

# 6. Ask questions
curl -X POST http://localhost:8080/api/documents/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "How do I configure the system?",
    "maxSources": 3
  }'
```

### Creating a Customer Support Bot

```bash
# 1. Create chatbot project
driftkit new support-bot --package com.company.support --template chatbot

cd support-bot

# 2. Customize workflow
vi src/main/java/com/company/support/workflow/SupportWorkflow.java

# 3. Run development server
driftkit dev

# 4. Test chat interface
open http://localhost:8080

# 5. Test reviewer interface
open "http://localhost:8080?reviewer=true"
```

### Integrating with Spring AI

```bash
# 1. Create Spring AI project
driftkit new ai-service --package com.company.ai --template spring_ai

cd ai-service

# 2. Start MongoDB for prompts
docker-compose up -d

# 3. Configure prompts via UI
driftkit dev
open http://localhost:8080/api/prompts

# 4. Use in your code
curl -X POST http://localhost:8080/api/ai/generate \
  -H "Content-Type: application/json" \
  -d '{
    "promptId": "product.description",
    "variables": {
      "productName": "DriftKit",
      "features": ["AI Integration", "Workflow Engine"]
    }
  }'
```

## Configuration

### Environment Variables

DriftKit projects support configuration through environment variables:

```bash
# AI Provider Keys
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
export GEMINI_API_KEY=...

# Vector Store Configuration
export PINECONE_API_KEY=...
export PINECONE_ENVIRONMENT=us-east-1

# Database Configuration
export MONGODB_URI=mongodb://localhost:27017/myapp

# Server Configuration
export SERVER_PORT=8080
```

### Application Configuration

Edit `src/main/resources/application.yml`:

```yaml
driftkit:
  clients:
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4
      temperature: 0.7
      
  embedding:
    default-model: openai
    
  vector:
    default-store: in-memory
```

### Build Profiles

Use profiles for different environments:

```bash
# Development
driftkit run spring-boot:run

# Production build
driftkit run clean package -p production

# Test environment
driftkit run test -p test
```

## Troubleshooting

### Common Issues

1. **"Not a valid DriftKit project directory"**
   - Ensure you're in a directory with `pom.xml` or `build.gradle`
   - Run `driftkit new` to create a project first

2. **"OPENAI_API_KEY not set"**
   - Export the environment variable: `export OPENAI_API_KEY=your-key`
   - Or add it to `application.yml`

3. **Port already in use**
   - Change port: `driftkit dev --port 8081`
   - Or kill the process using the port

4. **MongoDB connection failed**
   - Ensure Docker is running: `docker ps`
   - Start MongoDB: `docker-compose up -d`

5. **Build failures**
   - Check Java version: `java -version` (requires Java 21+)
   - Clear Maven cache: `driftkit run clean`
   - Update dependencies: `driftkit run dependency:resolve`

### Debug Mode

Enable verbose output for debugging:

```bash
# Verbose CLI output
driftkit -v new my-app

# Maven debug output
driftkit run compile -X

# Spring Boot debug logging
driftkit dev -Dlogging.level.root=DEBUG
```

### Getting Help

```bash
# General help
driftkit --help

# Command-specific help
driftkit new --help
driftkit run --help

# Version information
driftkit --version
```

## Advanced Usage

### Custom Templates

Create your own project templates:

1. Fork the DriftKit CLI repository
2. Add templates to `src/main/resources/templates/`
3. Update `ProjectTemplate` enum in `NewCommand.java`
4. Build and install your custom CLI

### CI/CD Integration

Use DriftKit CLI in your CI/CD pipelines:

```yaml
# GitHub Actions example
- name: Setup DriftKit
  run: |
    curl -L ${{ env.DRIFTKIT_URL }} -o driftkit-cli.jar
    echo "java -jar $(pwd)/driftkit-cli.jar \"\$@\"" > driftkit
    chmod +x driftkit
    
- name: Build Project
  run: ./driftkit run clean install -s
  
- name: Run Tests
  run: ./driftkit run test
```

### Docker Integration

Build and run your DriftKit app in Docker:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine as build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

## Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

DriftKit CLI is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

## Support

- Documentation: https://docs.driftkit.ai
- GitHub Issues: https://github.com/driftkit/framework/issues
- Discord: https://discord.gg/driftkit
- Email: support@driftkit.ai