// MongoDB initialization script for Spring AI + DriftKit

db = db.getSiblingDB('spring-ai-driftkit');

// Create user for the application
db.createUser({
  user: 'springai',
  pwd: 'springai123',
  roles: [
    {
      role: 'readWrite',
      db: 'spring-ai-driftkit'
    }
  ]
});

// Create collections for DriftKit prompts
db.createCollection('prompts');
db.createCollection('prompt_versions');
db.createCollection('test_sets');
db.createCollection('evaluation_runs');

// Create indexes
db.prompts.createIndex({ promptId: 1 });
db.prompts.createIndex({ tags: 1 });
db.prompts.createIndex({ createdAt: -1 });

db.prompt_versions.createIndex({ promptId: 1, version: -1 });
db.prompt_versions.createIndex({ language: 1 });

db.test_sets.createIndex({ promptId: 1 });
db.test_sets.createIndex({ createdAt: -1 });

db.evaluation_runs.createIndex({ promptId: 1 });
db.evaluation_runs.createIndex({ timestamp: -1 });

// Insert sample prompts
db.prompts.insertMany([
  {
    promptId: 'greeting.welcome',
    name: 'Welcome Greeting',
    description: 'Multilingual welcome message',
    tags: ['greeting', 'welcome', 'demo'],
    createdAt: new Date(),
    updatedAt: new Date()
  },
  {
    promptId: 'product.description',
    name: 'Product Description Generator',
    description: 'Generate product descriptions with template variables',
    tags: ['product', 'description', 'template'],
    createdAt: new Date(),
    updatedAt: new Date()
  },
  {
    promptId: 'rag.answer',
    name: 'RAG Answer Generation',
    description: 'Generate answers based on retrieved context',
    tags: ['rag', 'answer', 'context'],
    createdAt: new Date(),
    updatedAt: new Date()
  },
  {
    promptId: 'demo.tracing',
    name: 'Tracing Demo Prompt',
    description: 'Demonstrates DriftKit tracing capabilities',
    tags: ['demo', 'tracing'],
    createdAt: new Date(),
    updatedAt: new Date()
  },
  {
    promptId: 'demo.creative',
    name: 'Creative Generation Demo',
    description: 'Demonstrates custom temperature settings',
    tags: ['demo', 'creative'],
    createdAt: new Date(),
    updatedAt: new Date()
  }
]);

// Insert sample prompt versions
db.prompt_versions.insertMany([
  {
    promptId: 'greeting.welcome',
    version: 1,
    language: 'ENGLISH',
    systemMessage: 'You are a friendly assistant that welcomes users.',
    userMessage: 'Generate a warm welcome message for {{userName}}.',
    temperature: 0.7,
    maxTokens: 150,
    active: true,
    createdAt: new Date()
  },
  {
    promptId: 'greeting.welcome',
    version: 1,
    language: 'SPANISH',
    systemMessage: 'Eres un asistente amigable que da la bienvenida a los usuarios.',
    userMessage: 'Genera un mensaje de bienvenida cálido para {{userName}}.',
    temperature: 0.7,
    maxTokens: 150,
    active: true,
    createdAt: new Date()
  },
  {
    promptId: 'product.description',
    version: 1,
    language: 'ENGLISH',
    systemMessage: 'You are a product description expert.',
    userMessage: 'Generate a compelling description for {{productName}} with version {{version}}. Key features: {{features}}',
    temperature: 0.8,
    maxTokens: 300,
    active: true,
    createdAt: new Date()
  },
  {
    promptId: 'rag.answer',
    version: 1,
    language: 'ENGLISH',
    systemMessage: 'You are a helpful assistant that answers questions based on provided context. Only use information from the context.',
    userMessage: 'Context:\n{{context}}\n\nQuestion: {{question}}\n\nAnswer:',
    temperature: 0.3,
    maxTokens: 500,
    active: true,
    createdAt: new Date()
  }
]);

print('Spring AI + DriftKit database initialized successfully');