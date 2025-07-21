import { tracesPage, messageTasksByContextId, groupByChatId } from './state';
import { formatJSON } from '../../../utils/formatting';
import { isJSON } from './utils';
import { ContextDetails } from './types';

// Get the first context in a chat group
export const getFirstContextInChat = (chatTraces: any) => {
    if (!chatTraces || !chatTraces.contextIds || !Array.isArray(chatTraces.contextIds) || chatTraces.contextIds.length === 0) {
        return null;
    }

    // Sort context IDs by timestamp if available
    const sortedContextIds = [...chatTraces.contextIds].sort((a, b) => {
        const aFirstTrace = tracesPage.value.content.find(t => t.contextId === a);
        const bFirstTrace = tracesPage.value.content.find(t => t.contextId === b);

        if (aFirstTrace && bFirstTrace) {
            return aFirstTrace.timestamp - bFirstTrace.timestamp;
        }

        return 0;
    });

    // Get traces for the first context
    const firstContextId = sortedContextIds[0];
    const firstContextTraces = tracesPage.value.content.filter(t => t.contextId === firstContextId);

    // Sort by timestamp to get the very first trace
    if (firstContextTraces.length > 0) {
        return firstContextTraces.sort((a, b) => a.timestamp - b.timestamp)[0];
    }

    return null;
};

// Get the first context details, sorted by timestamp
export const getFirstContextDetails = (chatTraces: any): ContextDetails | null => {
    try {
        if (!chatTraces || !chatTraces.contextIds || !Array.isArray(chatTraces.contextIds) || chatTraces.contextIds.length === 0) {
            return null;
        }

        // Sort context IDs by timestamp
        const contextIds = [...chatTraces.contextIds];
        const sortedContextIds = contextIds.sort((a, b) => {
            const aTraces = tracesPage.value.content.filter(t => t.contextId === a);
            const bTraces = tracesPage.value.content.filter(t => t.contextId === b);

            if (aTraces.length > 0 && bTraces.length > 0) {
                const aTimestamp = Math.min(...aTraces.map(t => t.timestamp));
                const bTimestamp = Math.min(...bTraces.map(t => t.timestamp));
                return aTimestamp - bTimestamp;
            }
            return 0;
        });

        // Get the first context ID
        const firstContextId = sortedContextIds[0];

        // Get all traces for this context
        const traces = tracesPage.value.content.filter(t => t.contextId === firstContextId);

        // Sort traces by timestamp
        traces.sort((a, b) => a.timestamp - b.timestamp);

        // Get the first trace (with prompt template) and first message task
        return {
            firstContextId,
            firstTrace: traces.length > 0 ? traces[0] : null,
            messageTask: messageTasksByContextId.value[firstContextId]
        };
    } catch (error) {
        console.error("Error in getFirstContextDetails:", error);
        return null;
    }
};

// Get the last context details, sorted by timestamp
export const getLastContextDetails = (chatTraces: any): ContextDetails | null => {
    try {
        if (!chatTraces || !chatTraces.contextIds || !Array.isArray(chatTraces.contextIds) || chatTraces.contextIds.length === 0) {
            return null;
        }

        // Sort context IDs by timestamp (descending for last)
        const contextIds = [...chatTraces.contextIds];
        const sortedContextIds = contextIds.sort((a, b) => {
            const aTraces = tracesPage.value.content.filter(t => t.contextId === a);
            const bTraces = tracesPage.value.content.filter(t => t.contextId === b);

            if (aTraces.length > 0 && bTraces.length > 0) {
                const aTimestamp = Math.max(...aTraces.map(t => t.timestamp));
                const bTimestamp = Math.max(...bTraces.map(t => t.timestamp));
                return bTimestamp - aTimestamp; // Descending order
            }
            return 0;
        });

        // Get the last context ID
        const lastContextId = sortedContextIds[0];

        // Get all traces for this context
        const traces = tracesPage.value.content.filter(t => t.contextId === lastContextId);

        // Sort traces by timestamp (descending)
        traces.sort((a, b) => b.timestamp - a.timestamp);

        // Return the last trace and message task
        return {
            lastContextId,
            lastTrace: traces.length > 0 ? traces[0] : null,
            messageTask: messageTasksByContextId.value[lastContextId]
        };
    } catch (error) {
        console.error("Error in getLastContextDetails:", error);
        return null;
    }
};

// Get input message for a chat group from the first context
export const getInputMessageForChatGroup = (chatTraces: any) => {
    console.log("getInputMessageForChatGroup called with:", chatTraces);

    if (!groupByChatId.value || !chatTraces || !chatTraces.contextIds) {
        console.log("Early return from getInputMessageForChatGroup, conditions not met:", {
            groupByChatId: groupByChatId.value,
            hasChatTraces: !!chatTraces,
            hasContextIds: chatTraces ? !!chatTraces.contextIds : false
        });
        return 'N/A';
    }

    // Get first context
    const firstContext = getFirstContextInChat(chatTraces);
    if (!firstContext) {
        return 'No input found for this chat group';
    }

    // Get message task for the first context
    const messageTask = messageTasksByContextId.value[firstContext.contextId];
    if (messageTask && messageTask.message) {
        return messageTask.message;
    }

    // Fall back to the input of the chat
    const chatMessageTask = messageTasksByContextId.value[chatTraces.chatId || firstContext.chatId];
    if (chatMessageTask && chatMessageTask.message) {
        return chatMessageTask.message;
    }

    return 'No input message found';
};

// First context prompt template
export const getFirstContextPromptTemplate = (chatTraces: any) => {
    try {
        const firstContext = getFirstContextDetails(chatTraces);
        if (!firstContext || !firstContext.firstTrace) {
            return "No prompt template found";
        }

        return firstContext.firstTrace.promptTemplate || "No prompt template available";
    } catch (error) {
        console.error("Error in getFirstContextPromptTemplate:", error);
        return "Error getting prompt template";
    }
};

// First context variables
export const getFirstContextVariables = (chatTraces: any) => {
    try {
        const firstContext = getFirstContextDetails(chatTraces);
        if (!firstContext || !firstContext.firstTrace || !firstContext.firstTrace.variables) {
            return "";
        }

        return formatJSON(firstContext.firstTrace.variables);
    } catch (error) {
        console.error("Error in getFirstContextVariables:", error);
        return "";
    }
};

// Last context response (final result)
export const getLastContextResponse = (chatTraces: any) => {
    try {
        const lastContext = getLastContextDetails(chatTraces);
        if (!lastContext) {
            return "No final response found";
        }

        // Try to get response from the last trace
        if (lastContext.lastTrace && lastContext.lastTrace.response) {
            if (isJSON(lastContext.lastTrace.response)) {
                return formatJSON(JSON.parse(lastContext.lastTrace.response));
            }
            return lastContext.lastTrace.response;
        }

        // Try to get result from message task
        if (lastContext.messageTask && lastContext.messageTask.result) {
            if (isJSON(lastContext.messageTask.result)) {
                return formatJSON(JSON.parse(lastContext.messageTask.result));
            }
            return lastContext.messageTask.result;
        }

        return "No response available";
    } catch (error) {
        console.error("Error in getLastContextResponse:", error);
        return "Error getting response";
    }
};

// Helper to get final result for a chat group from the last context
export const getFinalResultForChatGroup = (chatTraces: any) => {
    console.log("getFinalResultForChatGroup called with:", chatTraces);

    if (!groupByChatId.value || !chatTraces || !chatTraces.contextIds) {
        console.log("Early return from getFinalResultForChatGroup, conditions not met");
        return 'N/A';
    }

    // Sort context IDs by timestamp if available
    const sortedContextIds = [...chatTraces.contextIds].sort((a, b) => {
        const aLastTrace = [...tracesPage.value.content.filter(t => t.contextId === a)]
            .sort((x, y) => y.timestamp - x.timestamp)[0];
        const bLastTrace = [...tracesPage.value.content.filter(t => t.contextId === b)]
            .sort((x, y) => y.timestamp - x.timestamp)[0];

        if (aLastTrace && bLastTrace) {
            return bLastTrace.timestamp - aLastTrace.timestamp;
        }

        return 0;
    });

    // Get latest context ID
    const lastContextId = sortedContextIds[0];

    // Try to get message task for the last context
    const messageTask = messageTasksByContextId.value[lastContextId];
    if (messageTask && messageTask.result) {
        // Format result if it's JSON
        if (isJSON(messageTask.result)) {
            return formatJSON(JSON.parse(messageTask.result));
        }
        return messageTask.result;
    }

    // Try to get the last trace response from the last context
    const lastContextTraces = tracesPage.value.content.filter(t => t.contextId === lastContextId);
    if (lastContextTraces.length > 0) {
        const lastTrace = lastContextTraces.sort((a, b) => b.timestamp - a.timestamp)[0];
        if (lastTrace.response) {
            // Format response if it's JSON
            if (isJSON(lastTrace.response)) {
                return formatJSON(JSON.parse(lastTrace.response));
            }
            return lastTrace.response;
        }
    }

    // Fall back to the result of the chat
    const chatMessageTask = messageTasksByContextId.value[chatTraces.chatId];
    if (chatMessageTask && chatMessageTask.result) {
        // Format result if it's JSON
        if (isJSON(chatMessageTask.result)) {
            return formatJSON(JSON.parse(chatMessageTask.result));
        }
        return chatMessageTask.result;
    }

    return 'No result found';
};

// Helper to get variables for display
export const getVariablesForDisplay = (groupKey: string, contextTraces: any) => {
    try {
        console.log("Getting variables for display:", { groupKey, hasContextTraces: !!contextTraces });

        // If in chat group mode with proper context structure
        if (groupByChatId.value && contextTraces && contextTraces.contextIds) {
            const firstContext = getFirstContextInChat(contextTraces);
            if (firstContext && firstContext.variables) {
                return formatJSON(firstContext.variables);
            }
        }

        // Standard context or fallback
        if (contextTraces && Array.isArray(contextTraces) && contextTraces[0] && contextTraces[0].variables) {
            return formatJSON(contextTraces[0].variables);
        }

        return "";
    } catch (error) {
        console.error("Error in getVariablesForDisplay:", error);
        return "";
    }
};

// Helper to get result for display
export const getResultForDisplay = (groupKey: string, contextTraces: any) => {
    try {
        console.log("Getting result for display:", { groupKey, hasContextTraces: !!contextTraces });

        // If in chat group mode with proper context structure
        if (groupByChatId.value && contextTraces && contextTraces.contextIds) {
            return getFinalResultForChatGroup(contextTraces);
        }

        // Standard context - try to use message task result
        const messageTask = messageTasksByContextId.value[groupKey];
        if (messageTask && messageTask.result) {
            if (isJSON(messageTask.result)) {
                return formatJSON(JSON.parse(messageTask.result));
            }
            return messageTask.result;
        }

        return "N/A";
    } catch (error) {
        console.error("Error in getResultForDisplay:", error);
        return "Error loading result";
    }
};