import { 
    selectedMessageTasks, 
    selectedTraceSteps, 
    selectedImageTasks, 
    newTestSetName, 
    newTestSetDescription, 
    showAddToTestSetModal,
    messageTasksByContextId,
    promptIdToMethodMap,
    selectedTestSetId
} from './state';

import { fetchAvailableTestSets, addToTestSet } from './api';

// Open the modal for adding to a test set
export const openAddToTestSetModal = () => {
    // Fetch available test sets
    fetchAvailableTestSets();

    // Reset form
    selectedTestSetId.value = '';

    // Generate default name based on promptIds from selected traces and message tasks
    const selectedPromptIds = new Set<string>();

    // Check message tasks first
    selectedMessageTasks.value.forEach(messageTaskId => {
        // Find the corresponding context ID
        const contextId = Object.keys(messageTasksByContextId.value).find(
            key => messageTasksByContextId.value[key]?.messageId === messageTaskId
        );

        if (contextId) {
            const messageTask = messageTasksByContextId.value[contextId];

            // Add promptIds from array if available
            if (messageTask.promptIds) {
                if (Array.isArray(messageTask.promptIds)) {
                    messageTask.promptIds.forEach(id => selectedPromptIds.add(id));
                } else if (typeof messageTask.promptIds === 'string') {
                    selectedPromptIds.add(messageTask.promptIds);
                }
            }

            // Add individual promptId if available
            if (messageTask.promptId) {
                selectedPromptIds.add(messageTask.promptId);
            }
        }
    });

    // Get prompt methods from the map for each selected prompt ID
    const promptMethods = Array.from(selectedPromptIds).map(id => {
        // Use the method name if available, otherwise use the ID
        return promptIdToMethodMap.value[id] || id;
    });

    // Set the default name
    if (promptMethods.length > 0) {
        // Use first method as name
        const mainPromptMethod = promptMethods[0];
        // Use the full method name including path
        newTestSetName.value = `Test Set - ${mainPromptMethod}`;

        // Add description with all methods if there are multiple
        if (promptMethods.length > 1) {
            newTestSetDescription.value = `Test set created from traces using methods: ${promptMethods.join(', ')}`;
        } else {
            newTestSetDescription.value = `Test set created from traces using method: ${mainPromptMethod}`;
        }
    } else {
        newTestSetName.value = `Test Set - ${new Date().toLocaleDateString()}`;
        newTestSetDescription.value = '';
    }

    // Show modal
    showAddToTestSetModal.value = true;
    // Prevent page scrolling when modal is open
    document.body.classList.add('modal-open');
};

// Close the test set modal
export const closeAddToTestSetModal = () => {
    showAddToTestSetModal.value = false;
    document.body.classList.remove('modal-open');
};

// Add selected items to a test set
export const handleAddToTestSet = async (selectedTestSetIdInput?: string) => {
    // Use provided ID or get from state
    const testSetId = selectedTestSetIdInput || selectedTestSetId.value;
    // This function can now be called without parameters
    
    if (selectedMessageTasks.value.length === 0 && 
        selectedTraceSteps.value.length === 0 && 
        selectedImageTasks.value.length === 0) {
        alert('Please select at least one item');
        return;
    }

    const success = await addToTestSet(
        testSetId, 
        newTestSetName.value, 
        newTestSetDescription.value, 
        selectedMessageTasks.value, 
        selectedTraceSteps.value, 
        selectedImageTasks.value
    );

    if (success) {
        // Success - close modal and clear selection
        closeAddToTestSetModal();

        selectedMessageTasks.value = [];
        selectedTraceSteps.value = [];
        selectedImageTasks.value = [];
        
        alert('Successfully added items to test set');
    }
};