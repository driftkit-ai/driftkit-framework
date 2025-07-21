import { tracesPage, messageTasksByContextId, promptIdToMethodMap, expandedTraces } from './state';
import { MessageTaskType } from './types';
import { formatJSON } from '../../../utils/formatting';

// Set trace time range and update time values
export const setTraceTimeRange = (range: string, traceTimeRange: any, traceStartTime: any, traceEndTime: any, fetchTraces: Function) => {
    traceTimeRange.value = range;
    const now = new Date();
    const startTime = new Date(now);

    switch (range) {
        case '1hour':
            startTime.setHours(now.getHours() - 1);
            break;
        case '3hours':
            startTime.setHours(now.getHours() - 3);
            break;
        case '8hours':
            startTime.setHours(now.getHours() - 8);
            break;
        case '1day':
            startTime.setDate(now.getDate() - 1);
            break;
        case '1week':
            startTime.setDate(now.getDate() - 7);
            break;
    }

    traceStartTime.value = startTime.toISOString().slice(0, 16);
    traceEndTime.value = now.toISOString().slice(0, 16);

    fetchTraces();
};

// Format date/time from timestamp
export const formatDateTime = (timestamp: number) => {
    if (!timestamp) return 'N/A';
    return new Date(timestamp).toLocaleString();
};

// Check if string is valid JSON
export const isJSON = (str: string) => {
    if (!str) return false;
    try {
        JSON.parse(str);
        return true;
    } catch (e) {
        return false;
    }
};

// Calculate total execution time for traces
export const calculateTotalTime = (traces: any[]) => {
    return traces.reduce((sum, trace) => sum + (trace.trace?.executionTimeMs || 0), 0);
};

// Calculate total tokens for traces
export const calculateTotalTokens = (traces: any[]) => {
    return traces.reduce((sum, trace) => {
        if (trace.trace) {
            return sum + (trace.trace.promptTokens || 0) + (trace.trace.completionTokens || 0);
        }
        return sum;
    }, 0);
};

// Get purpose for a context or chat group
export const getContextPurpose = (groupKey: string, traces: any[]) => {
    // First check if we have a message task with purpose
    if (messageTasksByContextId.value[groupKey]?.purpose) {
        return messageTasksByContextId.value[groupKey].purpose;
    }

    // Otherwise check the first trace
    if (traces.length > 0 && traces[0].purpose) {
        return traces[0].purpose;
    }

    return null;
};

// Check if a trace has workflow steps
export const hasWorkflowSteps = (traces: any[]) => {
    return traces.some(trace => trace.workflowInfo && trace.workflowInfo.workflowStep);
};

// Get method name from prompt ID
export const getPromptMethod = (messageTask: MessageTaskType | null | undefined) => {
    if (!messageTask) return 'N/A';

    // Check if we have promptIds
    if (messageTask.promptIds && (typeof messageTask.promptIds === 'string' || messageTask.promptIds.length > 0)) {
        const promptIds = Array.isArray(messageTask.promptIds) ? messageTask.promptIds : [messageTask.promptIds];

        // Try to get method names from the map for each promptId
        const methods = promptIds
            .map((id: string) => promptIdToMethodMap.value[id] || id)
            .filter(Boolean);

        return methods.join(', ') || 'N/A';
    }

    // Fallback to promptId if no promptIds array
    return messageTask.promptId ? (promptIdToMethodMap.value[messageTask.promptId] || messageTask.promptId) : 'N/A';
};

// Count unique context IDs in a collection of traces
export const countUniqueContextIds = (traces: any[]) => {
    const uniqueContextIds = new Set();

    traces.forEach(trace => {
        if (trace.contextId) {
            uniqueContextIds.add(trace.contextId);
        }
    });

    return uniqueContextIds.size;
};

// Get traces for a specific context - used to avoid filter in v-for
export const getTracesForContext = (traces: any[], contextId: string) => {
    // First ensure traces is an array and contextId exists
    if (!Array.isArray(traces) || !contextId) {
        console.warn("Invalid arguments to getTracesForContext:", { traces, contextId });
        return [];
    }

    // Then filter traces, ensuring each trace has an id and contextId
    return traces.filter(t => t && t.id && t.contextId === contextId);
};

// Get interleaved rows for rendering
export const getInterleavedRows = (traces: any[]) => {
    if (!traces || !Array.isArray(traces)) {
        console.warn("Invalid traces array for interleaving:", traces);
        return [];
    }

    const result: any[] = [];

    traces.forEach((trace) => {
        if (!trace) return;

        // Add main row
        result.push({
            type: 'main',
            trace: trace,
            key: 'main-' + (trace.id || Math.random())
        });

        // Add detail row if expanded
        if (trace.id && expandedTraces.value.includes(trace.id)) {
            result.push({
                type: 'detail',
                trace: trace,
                key: 'detail-' + (trace.id || Math.random())
            });
        }
    });

    return result;
};

// Check if a trace is an image generation trace
export const isImageGenerationTrace = (trace: any) => {
    return trace && trace.requestType === 'TEXT_TO_IMAGE';
};

// Helper to check if a message task has an associated image
export const hasImageInMessageTask = (messageTask: MessageTaskType | null | undefined) => {
    return messageTask && messageTask.contextId;
};

// Helper to check if trace has an associated image task
export const hasImageTask = (trace: any) => {
    return (isImageGenerationTrace(trace) && trace.contextId) ||
        (trace && trace.contextId && messageTasksByContextId.value[trace.contextId]?.imageTaskId);
};

// Helper to get the correct imageTaskId from a trace
export const getImageTaskIdFromTrace = (trace: any): string | null => {
    if (!trace) return null;
    
    // If trace is a direct image generation trace, the messageTask should contain the imageTaskId
    if (isImageGenerationTrace(trace) && trace.contextId) {
        const messageTask = messageTasksByContextId.value[trace.contextId];
        if (messageTask?.imageTaskId) {
            return messageTask.imageTaskId || null;
        }
    }
    
    // If the trace has a contextId and there's a message task with an imageTaskId
    if (trace.contextId && messageTasksByContextId.value[trace.contextId]?.imageTaskId) {
        return messageTasksByContextId.value[trace.contextId].imageTaskId || null;
    }
    
    return null;
};

// Helper to get image URL for a specific trace or message task
export const getImageUrl = (traceOrContextId: any, index = 0) => {
    // If we got a string directly (contextId)
    if (typeof traceOrContextId === 'string') {
        return `/data/v1.0/admin/llm/image/${traceOrContextId}/resource/${index}`;
    }

    // If we got a trace object
    if (traceOrContextId && traceOrContextId.contextId) {
        return `/data/v1.0/admin/llm/image/${traceOrContextId.contextId}/resource/${index}`;
    }

    return null;
};