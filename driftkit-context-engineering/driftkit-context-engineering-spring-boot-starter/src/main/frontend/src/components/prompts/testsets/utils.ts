import { TestSet, Evaluation, Run, RunResult } from './types';

// Format a timestamp into a readable date/time
export const formatDateTime = (timestamp: number): string => {
    if (!timestamp) return 'N/A';
    return new Date(timestamp).toLocaleString();
};

// Check if a string is valid JSON
export const isJSON = (str: string): boolean => {
    if (!str) return false;
    try {
        JSON.parse(str);
        return true;
    } catch (e) {
        return false;
    }
};

// Format evaluation type for display
export const formatEvaluationType = (type: string): string => {
    switch (type) {
        case 'JSON_SCHEMA': return 'JSON Schema';
        case 'CONTAINS_KEYWORDS': return 'Contains Keywords';
        case 'EXACT_MATCH': return 'Exact Match';
        case 'LLM_EVALUATION': return 'LLM Evaluation';
        case 'WORD_COUNT': return 'Word Count';
        case 'ARRAY_LENGTH': return 'Array Length';
        case 'FIELD_VALUE_CHECK': return 'Field Value Check';
        case 'REGEX_MATCH': return 'Regex Match';
        case 'MANUAL_EVALUATION': return 'Manual Evaluation';
        default: return type;
    }
};

// Get CSS class for run status
export const getRunStatusClass = (status: string): string => {
    switch (status) {
        case 'COMPLETED': return 'text-success';
        case 'RUNNING': return 'text-primary';
        case 'FAILED': return 'text-danger';
        case 'CANCELLED': return 'text-muted';
        case 'PENDING': return 'text-warning';
        default: return '';
    }
};

// Get CSS class for result status
export const getResultStatusClass = (status: string): string => {
    switch (status) {
        case 'PASSED': return 'badge bg-success';
        case 'FAILED': return 'badge bg-danger';
        case 'ERROR': return 'badge bg-warning';
        case 'SKIPPED': return 'badge bg-secondary';
        case 'PENDING': return 'badge bg-info';
        default: return '';
    }
};

// Helper to find test set name by ID
export const getTestSetName = (id: string, testSets: TestSet[]): string => {
    const ts = testSets.find(t => t.id === id);
    return ts ? ts.name : id;
};

// Helper to find evaluation name by ID
export const getEvaluationName = (evaluationId: string, evaluations: Evaluation[]): string => {
    const evaluation = evaluations.find(e => e.id === evaluationId);
    return evaluation ? evaluation.name : evaluationId;
};

// Helper to get test item identifier
export const getTestItemIdentifier = (testSetItemId: string, testSetItems: any[]): string => {
    const item = testSetItems.find(i => i.id === testSetItemId);
    return item ? `${item.message?.substring(0, 30)}...` : testSetItemId;
};

// Function to filter test sets by folder
export const getTestSetsInFolder = (testSets: TestSet[], folderId: string | null): TestSet[] => {
    // If folderId is null, return test sets that don't have a folder
    if (folderId === null) {
        return testSets.filter(ts => !ts.folderId);
    }
    return testSets.filter(ts => ts.folderId === folderId);
};