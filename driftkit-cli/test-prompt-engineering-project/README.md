# test-prompt-engineering-project

A DriftKit AI application.

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.8+

### Running the Application

```bash
# Using DriftKit CLI
driftkit dev

# Or using Maven
mvn spring-boot:run
```

### Configuration

Set your OpenAI API key:

```bash
export OPENAI_API_KEY=your-api-key-here
```

Or configure it in `src/main/resources/application.yml`.

### Adding Dependencies

Use the DriftKit CLI to add modules:

```bash
driftkit add embedding-openai
driftkit add vector-pinecone
```
