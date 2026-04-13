// MongoDB initialization script for RAG Pipeline

db = db.getSiblingDB('rag-pipeline');

// Create user for the application
db.createUser({
  user: 'raguser',
  pwd: 'ragpass123',
  roles: [
    {
      role: 'readWrite',
      db: 'rag-pipeline'
    }
  ]
});

// Create collections
db.createCollection('documents');
db.createCollection('document_metadata');
db.createCollection('ingestion_logs');

// Create indexes
db.documents.createIndex({ documentId: 1 });
db.documents.createIndex({ filename: 1 });
db.documents.createIndex({ ingestionTime: -1 });

db.document_metadata.createIndex({ documentId: 1 });
db.document_metadata.createIndex({ fileType: 1 });

db.ingestion_logs.createIndex({ timestamp: -1 });
db.ingestion_logs.createIndex({ status: 1 });

print('RAG Pipeline database initialized successfully');