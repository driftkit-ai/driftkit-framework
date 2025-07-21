// Default prompt templates and configurations

/**
 * Default evaluation prompt template for LLM-based evaluations.
 * This is the standard prompt used for LLM-based evaluations across the application.
 */
export const DEFAULT_LLM_EVALUATION_PROMPT = 
`You are an expert evaluator. Your task is to evaluate if the model's response is appropriate for the given task.

Task: {{task}}
Expected result: {{expected}}
Actual response: {{actual}}

Evaluate if the actual response meets the requirements. First analyze the response, then provide your verdict if it PASSES or FAILS the evaluation.`;

/**
 * Default manual evaluation instructions for human reviewers
 */
export const DEFAULT_MANUAL_REVIEW_INSTRUCTIONS = 
`Please review this result and determine if it meets the requirements.`;

/**
 * Default LLM configuration for evaluations.
 * This is used both in the evaluation creation form and as the initial value for LLM-based evaluations.
 */
export const DEFAULT_LLM_CONFIG = {
    evaluationPrompt: DEFAULT_LLM_EVALUATION_PROMPT,
    modelId: '',
    generateFeedback: true,
    temperature: 0.1
};

/**
 * Default JSON Schema configuration
 */
export const DEFAULT_JSON_SCHEMA_CONFIG = {
    validateJsonOnly: false,
    jsonSchema: ''
};

/**
 * Default keywords matching configuration
 */
export const DEFAULT_KEYWORDS_CONFIG = {
    keywords: [] as string[],
    matchType: 'ALL',
    caseSensitive: false
};

/**
 * Default manual evaluation configuration 
 */
export const DEFAULT_MANUAL_EVAL_CONFIG = {
    reviewInstructions: DEFAULT_MANUAL_REVIEW_INSTRUCTIONS
};