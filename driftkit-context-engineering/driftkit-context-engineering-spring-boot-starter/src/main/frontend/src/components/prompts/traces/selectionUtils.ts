import { 
    selectedMessageTasks, 
    selectedTraceSteps, 
    selectedImageTasks, 
    messageTasksByContextId, 
    tracesPage, 
    groupByChatId,
    expandedMessageDetails,
    expandedWorkflowSteps,
    expandedChatContexts,
    expandedTraces
} from './state';

// Check if a message task is selected
export const isMessageTaskSelected = (groupKey: string) => {
    const messageTask = messageTasksByContextId.value[groupKey];
    return messageTask && messageTask.messageId && selectedMessageTasks.value.includes(messageTask.messageId);
};

// Toggle selection for a message task
export const toggleMessageTaskSelection = (messageTaskId: string) => {
    if (!messageTaskId) return;

    if (selectedMessageTasks.value.includes(messageTaskId)) {
        selectedMessageTasks.value = selectedMessageTasks.value.filter(id => id !== messageTaskId);

        // Also remove any trace steps from this context
        const groupKey = Object.keys(messageTasksByContextId.value).find(
            key => messageTasksByContextId.value[key]?.messageId === messageTaskId
        );
        if (groupKey) {
            // If groupByChatId is true, we need to remove steps from all contexts with this chatId
            if (groupByChatId.value) {
                const chatId = messageTasksByContextId.value[groupKey]?.chatId;
                if (chatId) {
                    selectedTraceSteps.value = selectedTraceSteps.value.filter(step => {
                        // Find the trace to check its chatId
                        const trace = tracesPage.value.content.find(t => t.id === step.traceId);
                        return trace?.chatId !== chatId;
                    });
                }
            } else {
                // Standard contextId-based filtering
                selectedTraceSteps.value = selectedTraceSteps.value.filter(step => step.contextId !== groupKey);
            }
        }
    } else {
        selectedMessageTasks.value.push(messageTaskId);
    }
};

// Check if a trace step is selected
export const isTraceStepSelected = (contextId: string, traceId: string) => {
    return selectedTraceSteps.value.some(step => step.contextId === contextId && step.traceId === traceId);
};

// Toggle selection for a trace step
export const toggleTraceStepSelection = (contextId: string, traceId: string) => {
    const isSelected = isTraceStepSelected(contextId, traceId);

    if (isSelected) {
        selectedTraceSteps.value = selectedTraceSteps.value.filter(
            step => !(step.contextId === contextId && step.traceId === traceId)
        );
    } else {
        selectedTraceSteps.value.push({ contextId, traceId });

        // If this is the first trace step selected from this context,
        // remove the message task selection if it exists
        if (selectedTraceSteps.value.filter(step => step.contextId === contextId).length === 1) {
            const messageTask = messageTasksByContextId.value[contextId];
            if (messageTask && messageTask.messageId) {
                selectedMessageTasks.value = selectedMessageTasks.value.filter(id => id !== messageTask.messageId);
            }
        }
    }
};

// Check if an image task is selected
export const isImageTaskSelected = (imageTaskId: string) => {
    return imageTaskId && selectedImageTasks.value.includes(imageTaskId);
};

// Toggle selection for an image task
export const toggleImageTaskSelection = (imageTaskId: string) => {
    if (!imageTaskId) return;

    if (selectedImageTasks.value.includes(imageTaskId)) {
        selectedImageTasks.value = selectedImageTasks.value.filter(id => id !== imageTaskId);
    } else {
        selectedImageTasks.value.push(imageTaskId);
    }
};

// Toggle trace details visibility
export const toggleTraceDetails = (id: string, contextId?: string) => {
    console.log("Toggling trace details for:", id);

    if (expandedTraces.value.includes(id)) {
        console.log("Hiding trace details:", id);
        expandedTraces.value = expandedTraces.value.filter(traceId => traceId !== id);
    } else {
        console.log("Showing trace details:", id);
        expandedTraces.value.push(id);

        // If this trace is within a chat context, ensure the chat and context are expanded
        if (groupByChatId.value) {
            // If contextId was provided directly (from the template), use it
            if (contextId) {
                // Find the parent chatId for this context
                const chatId = tracesPage.value.content.find(t => t.contextId === contextId)?.chatId;

                if (chatId && !expandedChatContexts.value.includes(chatId)) {
                    console.log("Auto-expanding parent chat for trace (direct):", chatId);
                    expandedChatContexts.value.push(chatId);
                }

                // Make sure the context is expanded
                if (!expandedWorkflowSteps.value.includes(contextId)) {
                    console.log("Auto-expanding context for trace (direct):", contextId);
                    expandedWorkflowSteps.value.push(contextId);
                }
            } else {
                // Fall back to finding the trace in the page content
                const trace = tracesPage.value.content.find(t => t.id === id);
                if (trace) {
                    // First, ensure the parent chat is expanded
                    const chatId = trace.chatId;
                    if (chatId && !expandedChatContexts.value.includes(chatId)) {
                        console.log("Auto-expanding parent chat for trace:", chatId);
                        expandedChatContexts.value.push(chatId);
                    }

                    // Then ensure the context is expanded
                    const traceContextId = trace.contextId;
                    if (traceContextId && !expandedWorkflowSteps.value.includes(traceContextId)) {
                        console.log("Auto-expanding context for trace:", traceContextId);
                        expandedWorkflowSteps.value.push(traceContextId);
                    }
                }
            }
        }
    }
};

// Toggle message details visibility
export const toggleMessageDetails = (contextId: string) => {
    console.log("Toggle message details for context:", contextId);
    console.log("Message task for this context:", messageTasksByContextId.value[contextId]);

    if (groupByChatId.value) {
        console.log("In groupByChatId mode, checking if chat with id", contextId, "exists");
        const chatTraces = tracesPage.value.content.filter(t => t.chatId === contextId);
        console.log("Found", chatTraces.length, "traces with this chatId");
    }

    if (expandedMessageDetails.value.includes(contextId)) {
        console.log("Hiding message details for:", contextId);
        expandedMessageDetails.value = expandedMessageDetails.value.filter(id => id !== contextId);
    } else {
        console.log("Showing message details for:", contextId);
        expandedMessageDetails.value.push(contextId);

        // Make sure this doesn't interfere with workflow steps display
        // In chat mode, we need to keep context expanded with chat
        if (groupByChatId.value) {
            // Ensure the chat is expanded
            if (!expandedChatContexts.value.includes(contextId)) {
                expandedChatContexts.value.push(contextId);
            }
        }
    }
};

// Toggle context workflow steps visibility
export const toggleContextWorkflowSteps = (contextId: string) => {
    console.log("Toggling workflow steps for context:", contextId);

    // If context is inside a chat group, make sure the chat is expanded
    if (groupByChatId.value) {
        // Find which chat this context belongs to
        const chatId = tracesPage.value.content.find(t => t.contextId === contextId)?.chatId;

        if (chatId && !expandedChatContexts.value.includes(chatId)) {
            console.log("Auto-expanding parent chat:", chatId);
            expandedChatContexts.value.push(chatId);
        }
    }

    if (expandedWorkflowSteps.value.includes(contextId)) {
        console.log("Hiding steps for context:", contextId);
        expandedWorkflowSteps.value = expandedWorkflowSteps.value.filter(id => id !== contextId);
    } else {
        console.log("Showing steps for context:", contextId);
        expandedWorkflowSteps.value.push(contextId);
    }
};

// Toggle chat contexts visibility
export const toggleChatContexts = (chatId: string) => {
    console.log("Toggling chat contexts for chatId:", chatId);

    if (expandedChatContexts.value.includes(chatId)) {
        console.log("Hiding contexts for chat:", chatId);
        expandedChatContexts.value = expandedChatContexts.value.filter(id => id !== chatId);
    } else {
        console.log("Showing contexts for chat:", chatId);
        expandedChatContexts.value.push(chatId);
    }
};