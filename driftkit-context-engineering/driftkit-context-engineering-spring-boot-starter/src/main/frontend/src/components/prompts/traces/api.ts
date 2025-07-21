import axios from 'axios';
import {
    tracesLoading,
    tracesPage,
    availablePromptMethods,
    traceStartTime,
    traceEndTime,
    selectedPromptMethod,
    promptMetrics,
    promptMetricsLoading,
    availableTestSets,
    addingToTestSet,
    excludePurposeFilter,
    messageTasksByContextId,
    promptIdToMethodMap,
    searchMessageId
} from './state';
import { expandedWorkflowSteps, expandedTraces, expandedMessageDetails } from './state';

// Fetch traces data from the API
export const fetchTraces = (p?: unknown) => {
    if (!traceStartTime.value || !traceEndTime.value) {
        return;
    }

    // Handle the case where an Event object is passed instead of a number
    let page: number;
    if (typeof p === 'number') {
        page = p;
    } else {
        page = 0;
    }

    if (page < 0 || page >= tracesPage.value.totalPages) {
        page = 0;
    }

    tracesLoading.value = true;
    const creds = localStorage.getItem('credentials');

    const params: any = {
        page,
        size: 100,
        sort: 'timestamp,desc',
        startTime: traceStartTime.value,
        endTime: traceEndTime.value
    };

    if (selectedPromptMethod.value) {
        params.promptId = selectedPromptMethod.value;
    }

    if (excludePurposeFilter.value.trim()) {
        params.excludePurpose = excludePurposeFilter.value.trim();
    }

    axios.get('/data/v1.0/analytics/traces', {
        params,
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
    }).then(response => {
        // Check if the data is wrapped in a RestResponse object
        if (response.data.data) {
            tracesPage.value = response.data.data;
        } else {
            tracesPage.value = response.data;
        }

        // Log to check if we have chatId in traces
        console.log("Traces received: ", tracesPage.value.content.length);
        const tracesWithChatId = tracesPage.value.content.filter(trace => trace.chatId).length;
        console.log("Traces with chatId: ", tracesWithChatId);

        fetchMessageTasks();
    }).catch(error => {
        console.error('Error fetching traces:', error);
    }).finally(() => {
        tracesLoading.value = false;
    });
};

// Fetch available prompt methods
export const fetchAvailablePromptMethods = () => {
    const creds = localStorage.getItem('credentials');

    axios.get('/data/v1.0/analytics/prompt-methods', {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
    }).then(response => {
        availablePromptMethods.value = response.data.data || response.data;
    }).catch(error => {
        console.error('Error fetching prompt methods:', error);
    });
};

// Fetch prompt metrics
export const fetchPromptMetrics = () => {
    if (!selectedPromptMethod.value) {
        promptMetrics.value = null;
        return;
    }

    promptMetricsLoading.value = true;
    const creds = localStorage.getItem('credentials');

    axios.get('/data/v1.0/analytics/metrics/prompt', {
        params: {
            promptId: selectedPromptMethod.value,
            startTime: traceStartTime.value,
            endTime: traceEndTime.value
        },
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
    }).then(response => {
        if (response.data.data) {
            promptMetrics.value = response.data.data;
        } else {
            promptMetrics.value = response.data;
        }
    }).catch(error => {
        console.error('Error fetching prompt metrics:', error);
    }).finally(() => {
        promptMetricsLoading.value = false;
    });
};

// Fetch message tasks
export const fetchMessageTasks = () => {
    if (tracesPage.value.content.length === 0) {
        return;
    }

    const contextIds = [...new Set(tracesPage.value.content.map(trace => trace.contextId))];
    const chatIds = [...new Set(tracesPage.value.content.filter(trace => trace.chatId).map(trace => trace.chatId))];

    // Combine IDs for query
    const allIds = [...new Set([...contextIds, ...chatIds])];

    if (allIds.length === 0) {
        return;
    }

    const creds = localStorage.getItem('credentials');

    axios.get('/data/v1.0/analytics/message-tasks', {
        params: { contextIds: allIds.join(',') },
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
    }).then(response => {
        const tasks = response.data.data || response.data;

        // Store by both contextId and chatId if available
        messageTasksByContextId.value = tasks.reduce((acc: any, task: any) => {
            if (task.contextId) {
                acc[task.contextId] = task;
            }
            if (task.chatId) {
                acc[task.chatId] = task;
            }
            return acc;
        }, {});
    }).catch(error => {
        console.error('Error fetching message tasks:', error);
    });
};

// Fetch all prompts to build the promptId to method map
export const fetchPromptsForMapping = () => {
    const creds = localStorage.getItem('credentials');

    axios.get('/data/v1.0/admin/prompt/', {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
    }).then(response => {
        const promptsData = response.data.data || [];
        console.log('Fetched prompts for mapping:', promptsData);

        // Build the map of promptId to method
        promptsData.forEach((prompt: { id: string; method: string }) => {
            if (prompt.id && prompt.method) {
                promptIdToMethodMap.value[prompt.id] = prompt.method;
            }
        });

        console.log('Built promptId to method map:', promptIdToMethodMap.value);
    }).catch(error => {
        console.error('Error fetching prompts for mapping:', error);
    });
};

// Search traces by message ID
export const searchByMessageId = () => {
    if (!searchMessageId.value || searchMessageId.value.trim() === '') return;
    
    tracesLoading.value = true;
    const creds = localStorage.getItem('credentials');

    axios.get(`/data/v1.0/analytics/traces/${searchMessageId.value}`, {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
    }).then(response => {
        const traces = response.data.data || response.data || [];

        if (traces.length > 0) {
            const contextId = traces[0].contextId;

            // Create a page with just this context
            tracesPage.value.content = traces;
            tracesPage.value.totalElements = traces.length;
            tracesPage.value.totalPages = 1;
            tracesPage.value.number = 0;

            // Automatically expand the details
            if (!expandedMessageDetails.value.includes(contextId)) {
                expandedMessageDetails.value.push(contextId);
            }

            if (!expandedWorkflowSteps.value.includes(contextId)) {
                expandedWorkflowSteps.value.push(contextId);
            }

            // Fetch message task for this context
            fetchMessageTasks();
        } else {
            alert('No traces found for this message ID');
            tracesPage.value.content = [];
        }
    }).catch(error => {
        console.error('Error searching traces by message ID:', error);
        alert('Error searching traces. Please try again.');
    }).finally(() => {
        tracesLoading.value = false;
    });
};

// Fetch available test sets
export const fetchAvailableTestSets = () => {
    const creds = localStorage.getItem('credentials');

    axios.get('/data/v1.0/admin/test-sets', {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
    }).then(response => {
        availableTestSets.value = response.data;
    }).catch(error => {
        console.error('Error fetching test sets:', error);
    });
};

// Add traces to test set
export const addToTestSet = async (
    testSetId: string, 
    newTestSetName: string, 
    newTestSetDescription: string, 
    selectedMessageTasks: string[], 
    selectedTraceSteps: any[], 
    selectedImageTasks: string[]
) => {
    if (selectedMessageTasks.length === 0 && selectedTraceSteps.length === 0 && selectedImageTasks.length === 0) {
        alert('Please select at least one item');
        return;
    }

    let finalTestSetId = testSetId;

    // If creating a new test set
    if (!finalTestSetId) {
        if (!newTestSetName) {
            alert('Please enter a name for the new test set');
            return;
        }

        try {
            addingToTestSet.value = true;
            const creds = localStorage.getItem('credentials');

            const response = await axios.post('/data/v1.0/admin/test-sets', {
                name: newTestSetName,
                description: newTestSetDescription,
            }, {
                headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
            });

            finalTestSetId = response.data.id;
        } catch (error) {
            console.error('Error creating test set:', error);
            alert('Error creating test set');
            addingToTestSet.value = false;
            return;
        }
    }

    // Add items to the test set
    try {
        const creds = localStorage.getItem('credentials');

        await axios.post('/data/v1.0/admin/test-sets/add-items', {
            messageTaskIds: selectedMessageTasks,
            traceSteps: selectedTraceSteps,
            imageTaskIds: selectedImageTasks,
            testSetId: finalTestSetId
        }, {
            headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });

        return true; // Success
    } catch (error) {
        console.error('Error adding items to test set:', error);
        alert('Error adding items to test set');
        return false;
    } finally {
        addingToTestSet.value = false;
    }
};