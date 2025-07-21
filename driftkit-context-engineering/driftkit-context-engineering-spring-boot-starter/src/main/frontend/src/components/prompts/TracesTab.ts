import { defineComponent, onMounted } from 'vue';
import {
    // State
    tracesLoading, traces, tracesPage, availablePromptMethods, traceTimeRange, 
    traceStartTime, traceEndTime, selectedPromptMethod, promptMetrics, 
    promptMetricsLoading, expandedTraces, expandedMessageDetails, expandedWorkflowSteps, 
    expandedChatContexts, showWorkflowSteps, messageTasksByContextId, searchMessageId, 
    excludePurposeFilter, groupedTraces, paginationRange, promptIdToMethodMap, groupByChatId,
    
    // Test set selection state
    selectedMessageTasks, selectedTraceSteps, selectedImageTasks, totalSelectedItems, 
    availableTestSets, selectedTestSetId, newTestSetName, newTestSetDescription, 
    addingToTestSet, showAddToTestSetModal,

    // API functions
    fetchTraces, fetchAvailablePromptMethods, fetchPromptMetrics, 
    fetchMessageTasks, fetchPromptsForMapping, searchByMessageId, fetchAvailableTestSets,

    // Utility functions
    setTraceTimeRange as baseSetTraceTimeRange, formatDateTime, isJSON, hasWorkflowSteps,
    getPromptMethod, calculateTotalTime, calculateTotalTokens, getContextPurpose,
    countUniqueContextIds, getTracesForContext, getInterleavedRows, isImageGenerationTrace,
    getImageUrl, hasImageTask, hasImageInMessageTask, getImageTaskIdFromTrace,

    // Context utility functions
    getFirstContextInChat, getInputMessageForChatGroup, getFinalResultForChatGroup,
    getVariablesForDisplay, getResultForDisplay, getFirstContextDetails, getLastContextDetails,
    getFirstContextPromptTemplate, getFirstContextVariables, getLastContextResponse,

    // Selection utility functions
    isMessageTaskSelected, toggleMessageTaskSelection, isTraceStepSelected, toggleTraceStepSelection,
    toggleTraceDetails, toggleMessageDetails, toggleContextWorkflowSteps, toggleChatContexts,
    isImageTaskSelected, toggleImageTaskSelection,

    // Test set utility functions
    openAddToTestSetModal, closeAddToTestSetModal, handleAddToTestSet as baseHandleAddToTestSet
} from './traces';

import { formatJSON, highlightVariables } from '../../utils/formatting';
import axios from 'axios';

export default defineComponent({
    name: 'TracesTab',
    setup() {
        // Wrapper for setTraceTimeRange to inject dependencies
        const setTraceTimeRange = (range: string) => {
            baseSetTraceTimeRange(range, traceTimeRange, traceStartTime, traceEndTime, fetchTraces);
        };

        const changePage = (pageOrEvent: unknown) => {
            fetchTraces(pageOrEvent);
        };

        // Wrapper for prompt method change
        const onPromptMethodChange = () => {
            fetchTraces();
            fetchPromptMetrics();
        };

        // Wrapper for adding to test set - now we can call it without parameters
        const handleAddToTestSet = async () => {
            await baseHandleAddToTestSet();
        };

        // Wrapper for clearing exclude purpose filter
        const clearExcludePurposeFilter = () => {
            excludePurposeFilter.value = '';
            fetchTraces();
        };

        // Regroup traces based on groupByChatId
        const regroupTraces = () => {
            console.log("Regrouping traces by: " + (groupByChatId.value ? "chatId" : "contextId"));
            console.log("Current groupByChatId value:", groupByChatId.value);
            console.log("Number of traces with chatId:", tracesPage.value.content.filter(t => t.chatId).length);

            // Force recomputation of computed prop - we need to trigger reactive updates
            // to make Vue recompute the groupedTraces
            const tempArr = [...tracesPage.value.content];
            tracesPage.value.content = [];
            setTimeout(() => {
                tracesPage.value.content = tempArr;
            }, 0);

            // Clear existing selections and expansions as they might no longer be valid
            expandedMessageDetails.value = [];
            expandedWorkflowSteps.value = [];
            expandedTraces.value = [];
            expandedChatContexts.value = [];

            // Clear selections
            selectedMessageTasks.value = [];
            selectedTraceSteps.value = [];
            selectedImageTasks.value = [];

            // Re-fetch message tasks to make sure we have all needed data
            fetchMessageTasks();
        };

        // Initialize component on mount
        onMounted(() => {
            const now = new Date();
            const yesterday = new Date(now);
            yesterday.setDate(now.getDate() - 1);

            traceStartTime.value = yesterday.toISOString().slice(0, 16);
            traceEndTime.value = now.toISOString().slice(0, 16);

            // First fetch prompts for building the mapping
            fetchPromptsForMapping();

            // Then fetch prompt methods and traces
            fetchAvailablePromptMethods();
            fetchTraces();
        });

        return {
            // State
            tracesLoading,
            traces,
            tracesPage,
            availablePromptMethods,
            traceTimeRange,
            traceStartTime,
            traceEndTime,
            selectedPromptMethod,
            promptMetrics,
            promptMetricsLoading,
            expandedTraces,
            expandedMessageDetails,
            expandedWorkflowSteps,
            expandedChatContexts,
            showWorkflowSteps,
            messageTasksByContextId,
            searchMessageId,
            excludePurposeFilter,
            groupedTraces,
            paginationRange,
            promptIdToMethodMap,
            groupByChatId,

            // Test set selection
            selectedMessageTasks,
            selectedTraceSteps,
            selectedImageTasks,
            totalSelectedItems,
            availableTestSets,
            selectedTestSetId,
            newTestSetName,
            newTestSetDescription,
            addingToTestSet,
            showAddToTestSetModal,

            // Methods
            setTraceTimeRange,
            fetchTraces,
            changePage,
            onPromptMethodChange,
            toggleTraceDetails,
            toggleMessageDetails,
            toggleContextWorkflowSteps,
            hasWorkflowSteps,
            getPromptMethod,
            fetchPromptsForMapping,
            calculateTotalTime,
            calculateTotalTokens,
            getContextPurpose,
            formatDateTime,
            isJSON,
            formatJSON,
            highlightVariables,
            searchByMessageId,
            clearExcludePurposeFilter,
            countUniqueContextIds,
            regroupTraces,
            toggleChatContexts,
            getTracesForContext,
            getInterleavedRows,
            getFirstContextInChat,
            getInputMessageForChatGroup,
            getFinalResultForChatGroup,
            getVariablesForDisplay,
            getResultForDisplay,
            getFirstContextDetails,
            getLastContextDetails,
            getFirstContextPromptTemplate,
            getFirstContextVariables,
            getLastContextResponse,

            // Test set methods
            isMessageTaskSelected,
            toggleMessageTaskSelection,
            isTraceStepSelected,
            toggleTraceStepSelection,
            openAddToTestSetModal,
            closeAddToTestSetModal,
            fetchAvailableTestSets,
            handleAddToTestSet,

            // Image methods
            isImageGenerationTrace,
            isImageTaskSelected,
            toggleImageTaskSelection,
            getImageUrl,
            hasImageTask,
            hasImageInMessageTask,
            getImageTaskIdFromTrace
        };
    }
});