export interface TestSet {
    id: string;
    name: string;
    description: string;
    folderId?: string;
    createdAt?: number;
    updatedAt?: number;
}

export interface TestSetItem {
    id: string;
    testSetId: string;
    
    originalTraceId?: string;
    originalMessageTaskId?: string;
    originalImageTaskId?: string; // Used for image display
    
    message?: string;
    result?: string;
    variables?: Record<string, string>;
    
    isImageTask?: boolean; // Flag to indicate this is an image task
    
    model?: string;
    temperature?: number;
    workflowType?: string;
    promptId?: string;
    
    createdAt?: number;
}

export interface Folder {
    id: string;
    name: string;
    description: string;
}

export interface Evaluation {
    id: string;
    name: string;
    description: string;
    type: string;
    config: any;
}

export interface Run {
    id: string;
    name: string;
    description: string;
    status: string;
    createdAt?: number;
    updatedAt?: number;
    alternativePromptId?: string;
    alternativePromptTemplate?: string;
    modelId?: string;
    workflow?: string;
    temperature?: number | null;
}

export interface RunResult {
    id: string;
    testSetItemId: string;
    evaluationId: string;
    status: string;
    details: any;
}

export interface NewTestSet {
    name: string;
    description: string;
    folderId: string;
}

export interface NewFolder {
    name: string;
    description: string;
}

export interface NewEvaluation {
    name: string;
    description: string;
    type: string;
    config: {
        negateResult: boolean;
        [key: string]: any;
    };
}

export interface NewRun {
    name: string;
    description: string;
    alternativePromptId: string;
    alternativePromptTemplate: string;
    modelId: string;
    workflow: string;
    temperature: number | null;
}

export interface JsonSchemaConfig {
    validateJsonOnly: boolean;
    jsonSchema: string;
}

export interface KeywordsConfig {
    keywords: string[];
    matchType: string;
    caseSensitive: boolean;
}

export interface LlmConfig {
    evaluationPrompt: string;
    modelId: string;
    generateFeedback: boolean;
    temperature: number;
}

export interface FolderExecutionConfig {
    modelId: string;
    workflow: string;
    useModel: boolean;
    regenerateImages: boolean;
}