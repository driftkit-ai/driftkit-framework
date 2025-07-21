import { defineComponent, ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { highlightVariables, formatJSON, isJSON } from '../../../utils/formatting';

// Import our sub-modules
import { usePromptsState, useTestRunState } from './state';
import { usePromptsComputed, useTestRunComputed } from './computed';
import { usePromptMethods } from './promptMethods';
import { useTestRunMethods } from './testRunMethods';
import { PromptsTabProps, PromptsTabEmits } from './types';

export default defineComponent({
  name: 'PromptsTab',
  props: {
    selectedPromptId: {
      type: String,
      default: ''
    }
  },
  emits: ['update:selectedPromptId', 'prompt-selected'],
  setup(props: PromptsTabProps, { emit }) {
    const router = useRouter();
    
    // Initialize state
    const promptsState = usePromptsState();
    const testRunState = useTestRunState();
    
    // Set initial value from props - using watch to avoid reactivity issues
    // Note: We don't directly set the value here to avoid the eslint vue/no-setup-props-destructure rule
    // Instead we use the watch below which fires immediately
    
    // Initialize computed properties
    const promptsComputed = usePromptsComputed(promptsState);
    const testRunComputed = useTestRunComputed(testRunState);
    
    // Initialize methods
    const promptMethods = usePromptMethods(promptsState, emit);
    const testRunMethods = useTestRunMethods(testRunState, promptsState.promptForm, router);
    
    // Lifecycle hooks
    onMounted(() => {
      console.log('🚀 Component mounted, fetching data...');
      promptMethods.fetchPrompts();
      promptMethods.fetchWorkflows();
    });
    
    onBeforeUnmount(() => {
      testRunMethods.cleanupPolling();
    });
    
    // Watch for changes in selectedPromptId from parent
    watch(() => props.selectedPromptId, (newId) => {
      if (newId && newId !== promptsState.selectedPromptIdRef.value) {
        promptMethods.selectPrompt(newId);
      }
    }, { immediate: true });
    
    // Watch for changes in selectedPromptIdRef to emit update event
    watch(promptsState.selectedPromptIdRef, (newId) => {
      emit('update:selectedPromptId', newId);
    });
    
    // Return all state, computed properties, and methods for the template
    return {
      // State
      ...promptsState,
      ...testRunState,
      
      // Computed properties
      ...promptsComputed,
      ...testRunComputed,
      
      // Methods
      ...promptMethods,
      ...testRunMethods,
      
      // Special method for the router
      viewTestResults: testRunMethods.viewTestResults
    };
  }
});