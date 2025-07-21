import axios from 'axios';
import { TestSet, Folder, TestSetItem, Evaluation, Run, RunResult, NewTestSet, NewFolder, NewEvaluation, NewRun } from './types';

// Helper function to get credentials
const getCredentials = () => {
    const creds = localStorage.getItem('credentials');
    return creds ? { Authorization: 'Basic ' + creds } : {};
};

// TestSets and Folders
export const fetchTestSets = async (): Promise<{ folders: Folder[], testSets: TestSet[] }> => {
    const headers = getCredentials();
    
    try {
        // First get folders
        const folderResponse = await axios.get('/data/v1.0/admin/test-sets/folders', { headers });
        const folders = folderResponse.data || [];

        // Then get test sets
        const testSetsResponse = await axios.get('/data/v1.0/admin/test-sets', { headers });
        const testSets = testSetsResponse.data;

        return { folders, testSets };
    } catch (error) {
        console.error('Error fetching test sets or folders:', error);
        
        // If the folders endpoint doesn't exist yet, we'll just use an empty array
        const folders: Folder[] = [];
        
        try {
            // Still try to fetch test sets
            const testSetsResponse = await axios.get('/data/v1.0/admin/test-sets', { headers });
            const testSets = testSetsResponse.data;
            return { folders, testSets };
        } catch (testSetError) {
            console.error('Error fetching test sets:', testSetError);
            return { folders, testSets: [] };
        }
    }
};

export const createFolder = async (folder: NewFolder): Promise<void> => {
    const headers = getCredentials();
    await axios.post('/data/v1.0/admin/test-sets/folders', folder, { headers });
};

export const deleteFolder = async (id: string): Promise<void> => {
    const headers = getCredentials();
    await axios.delete(`/data/v1.0/admin/test-sets/folders/${id}`, { headers });
};

export const moveTestSetsToFolder = async (testSetIds: string[], folderId: string | null): Promise<void> => {
    const headers = getCredentials();
    const moveData = {
        testSetIds,
        folderId: folderId || null // Use null to move to root (no folder)
    };
    await axios.post('/data/v1.0/admin/test-sets/move-to-folder', moveData, { headers });
};

export const createTestSet = async (testSet: NewTestSet): Promise<void> => {
    const headers = getCredentials();
    await axios.post('/data/v1.0/admin/test-sets', testSet, { headers });
};

export const deleteTestSet = async (id: string): Promise<void> => {
    const headers = getCredentials();
    await axios.delete(`/data/v1.0/admin/test-sets/${id}`, { headers });
};

export const fetchTestSetItems = async (testSetId: string): Promise<TestSetItem[]> => {
    const headers = getCredentials();
    const response = await axios.get(`/data/v1.0/admin/test-sets/${testSetId}/items`, { headers });
    return response.data;
};

export const deleteTestSetItem = async (itemId: string, testSetId: string): Promise<void> => {
    const headers = getCredentials();
    await axios.delete(`/data/v1.0/admin/test-sets/${testSetId}/items/${itemId}`, { headers });
};

// Evaluations
export const fetchEvaluations = async (testSetId: string): Promise<Evaluation[]> => {
    const headers = getCredentials();
    const response = await axios.get(`/data/v1.0/admin/test-sets/${testSetId}/evaluations`, { headers });
    return response.data.data || [];
};

export const fetchGlobalEvaluations = async (): Promise<Evaluation[]> => {
    const headers = getCredentials();
    const response = await axios.get('/data/v1.0/admin/evaluations/global', { headers });
    return response.data.data || [];
};

export const createTestSetEvaluation = async (testSetId: string, evaluation: NewEvaluation): Promise<void> => {
    const headers = getCredentials();
    await axios.post(`/data/v1.0/admin/test-sets/${testSetId}/evaluations`, evaluation, { headers });
};

export const createGlobalEvaluation = async (evaluation: NewEvaluation): Promise<void> => {
    const headers = getCredentials();
    await axios.post('/data/v1.0/admin/evaluations/global', evaluation, { headers });
};

export const deleteEvaluation = async (id: string): Promise<void> => {
    const headers = getCredentials();
    await axios.delete(`/data/v1.0/admin/evaluations/${id}`, { headers });
};

export const addEvaluationToTestSet = async (testSetId: string, evaluationId: string): Promise<void> => {
    const headers = getCredentials();
    await axios.post(`/data/v1.0/admin/test-sets/${testSetId}/evaluations/add/${evaluationId}`, {}, { headers });
};

export const copyEvaluation = async (targetTestSetId: string, evaluationId: string): Promise<void> => {
    const headers = getCredentials();
    await axios.post(`/data/v1.0/admin/test-sets/${targetTestSetId}/evaluations/copy/${evaluationId}`, {}, { headers });
};

export const updateEvaluation = async (evaluationId: string, evaluation: NewEvaluation): Promise<void> => {
    const headers = getCredentials();
    await axios.put(`/data/v1.0/admin/evaluations/${evaluationId}`, evaluation, { headers });
};

export const updateTestSetEvaluation = async (testSetId: string, evaluationId: string, evaluation: NewEvaluation): Promise<void> => {
    const headers = getCredentials();
    await axios.put(`/data/v1.0/admin/test-sets/${testSetId}/evaluations/${evaluationId}`, evaluation, { headers });
};

// Runs
export const fetchRuns = async (testSetId: string): Promise<Run[]> => {
    const headers = getCredentials();
    const response = await axios.get(`/data/v1.0/admin/test-sets/${testSetId}/runs`, { headers });
    return response.data.data || [];
};

export const createRun = async (testSetId: string, run: NewRun): Promise<any> => {
    const headers = getCredentials();
    const response = await axios.post(`/data/v1.0/admin/test-sets/${testSetId}/runs`, run, { headers });
    return response.data;
};

export const startRun = async (runId: string): Promise<void> => {
    const headers = getCredentials();
    await axios.post(`/data/v1.0/admin/test-sets/runs/${runId}/start`, {}, { headers });
};

export const quickRun = async (testSetId: string): Promise<void> => {
    const headers = getCredentials();
    await axios.post(`/data/v1.0/admin/test-sets/${testSetId}/quick-run`, {}, { headers });
};

export const deleteRun = async (runId: string): Promise<void> => {
    const headers = getCredentials();
    await axios.delete(`/data/v1.0/admin/test-sets/runs/${runId}`, { headers });
};

export const fetchRunResults = async (runId: string): Promise<RunResult[]> => {
    const headers = getCredentials();
    const response = await axios.get(`/data/v1.0/admin/test-sets/runs/${runId}/results`, { headers });
    return response.data.data || [];
};

// Folder execution
export const executeFolderTests = async (
    folderId: string, 
    modelId?: string, 
    workflow?: string, 
    regenerateImages?: boolean
): Promise<any> => {
    const headers = getCredentials();
    
    // Build query parameters
    const queryParams = new URLSearchParams();
    
    // Add model ID or workflow
    if (modelId) {
        queryParams.append('modelId', modelId);
    } else if (workflow) {
        queryParams.append('workflow', workflow);
    }
    
    // Add regenerateImages option if true
    if (regenerateImages) {
        queryParams.append('regenerateImages', 'true');
    }
    
    // Format the query string
    const params = queryParams.toString() ? `?${queryParams.toString()}` : '';
    
    const response = await axios.post(`/data/v1.0/admin/test-sets/folders/${folderId}/execute${params}`, {}, { headers });
    return response.data;
};

// Helper functions
export const getImageUrl = (imageTaskId: string, index: number = 0): string | null => {
    if (!imageTaskId) return null;
    return `/data/v1.0/admin/llm/image/${imageTaskId}/resource/${index}`;
};

// Check if an image exists before trying to display it
export const checkImageExists = async (imageTaskId: string): Promise<{
    exists: boolean;
    imagesCount: number;
    hasData: boolean;
    createdTime: number | null;
}> => {
    if (!imageTaskId) {
        return { exists: false, imagesCount: 0, hasData: false, createdTime: null };
    }
    
    const headers = getCredentials();
    try {
        const response = await axios.get(`/data/v1.0/admin/llm/image/${imageTaskId}/exists`, { headers });
        return response.data.data || { exists: false, imagesCount: 0, hasData: false, createdTime: null };
    } catch (error) {
        console.error(`Error checking if image exists for ID ${imageTaskId}:`, error);
        return { exists: false, imagesCount: 0, hasData: false, createdTime: null };
    }
};