import { Ref } from 'vue';

// Prompt related types
export interface PromptForm {
  promptId: string;
  message: string;
  systemMessage: string;
  variables: string[];
  variableValues: Record<string, string>;
  workflow: string;
  modelId: string;
  temperature: number | null;
  language: string;
  jsonRequest: boolean;
  jsonResponse: boolean;
  purpose: string;
}

// Test set run related types
export interface TestRun {
  name: string;
  description: string;
  alternativePromptId: string;
  alternativePromptTemplate: string;
  modelId: string;
  workflow: string;
  temperature: number | null;
}

// Grouped prompts type
export interface GroupedPrompt {
  currentPrompt: any;
  otherPrompts: any[];
}

// Types for the component state
export interface PromptsState {
  prompts: Ref<any[]>;
  loading: Ref<boolean>;
  searchQuery: Ref<string>;
  expandedMethods: Ref<string[]>;
  currentFolder: Ref<string>;
  selectedPrompts: Ref<string[]>;
  workflows: Ref<any[]>;
  selectedPromptIdRef: Ref<string>;
  promptForm: Ref<PromptForm>;
  response: Ref<string>;
  responseData: Ref<any>;
  savePrompt: Ref<boolean>;
  saveForAllLanguages: Ref<boolean>;
  codeMirrorEditor: Ref<any>;
  showPromptPreview: Ref<boolean>;
  loadingRandomTask: Ref<boolean>;
}

// Test run state
export interface TestRunState {
  showTestRunsModal: Ref<boolean>;
  testRunsLoading: Ref<boolean>;
  testSets: Ref<any[]>;
  selectedTestSetId: Ref<string>;
  selectedTestSet: Ref<any>;
  testSetItemCount: Ref<number>;
  executionMethodType: Ref<string>;
  currentTestRun: Ref<any>;
  newTestRun: Ref<TestRun>;
}

// Prop and emit types
export interface PromptsTabProps {
  selectedPromptId: string;
}

export type PromptsTabEmits = {
  'update:selectedPromptId': [id: string];
  'prompt-selected': [id: string];
}