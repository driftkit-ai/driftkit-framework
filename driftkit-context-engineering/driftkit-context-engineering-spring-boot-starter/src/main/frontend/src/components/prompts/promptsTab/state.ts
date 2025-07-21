import { ref, Ref } from 'vue';
import { PromptsState, TestRunState } from './types';

// Initialize the prompts state
export function usePromptsState(): PromptsState {
  const prompts = ref<any[]>([]);
  const loading = ref(false);
  const searchQuery = ref('');
  const expandedMethods = ref<string[]>([]);
  const currentFolder = ref('');
  const selectedPrompts = ref<string[]>([]);
  const workflows = ref<any[]>([]);
  const selectedPromptIdRef = ref('');
  const savePrompt = ref(false);
  const saveForAllLanguages = ref(false);
  const codeMirrorEditor = ref(null);
  const showPromptPreview = ref(true);
  const loadingRandomTask = ref(false);
  
  // Prompt form state with default values
  const promptForm = ref({
    promptId: '',
    message: '',
    systemMessage: '',
    variables: [] as string[],
    variableValues: {} as Record<string, string>,
    workflow: '',
    modelId: '',
    temperature: null as number | null,
    language: 'GENERAL',
    jsonRequest: false,
    jsonResponse: false,
    purpose: 'prompt_engineering'
  });
  
  // Response data
  const response = ref('');
  const responseData = ref<any>({});

  return {
    prompts,
    loading,
    searchQuery,
    expandedMethods,
    currentFolder,
    selectedPrompts,
    workflows,
    selectedPromptIdRef,
    promptForm,
    response,
    responseData,
    savePrompt,
    saveForAllLanguages,
    codeMirrorEditor,
    showPromptPreview,
    loadingRandomTask
  };
}

// Initialize the test run state
export function useTestRunState(): TestRunState {
  const showTestRunsModal = ref(false);
  const testRunsLoading = ref(false);
  const testSets = ref<any[]>([]);
  const selectedTestSetId = ref('');
  const selectedTestSet = ref<any>(null);
  const testSetItemCount = ref(0);
  const executionMethodType = ref('modelId');
  const currentTestRun = ref<any>(null);
  
  // New test run form
  const newTestRun = ref({
    name: '',
    description: '',
    alternativePromptId: '',
    alternativePromptTemplate: '',
    modelId: '',
    workflow: '',
    temperature: null as number | null
  });

  return {
    showTestRunsModal,
    testRunsLoading,
    testSets,
    selectedTestSetId,
    selectedTestSet,
    testSetItemCount,
    executionMethodType,
    currentTestRun,
    newTestRun
  };
}