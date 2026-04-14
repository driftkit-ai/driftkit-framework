<template>
  <div class="prompts-page">
    <!-- Prompt List -->
    <PromptList
      :prompts="prompts"
      :loading="loading"
      :searchQuery="searchQuery"
      :currentFolder="currentFolder"
      :groupedPrompts="groupedPrompts"
      :filteredPrompts="filteredPrompts"
      :selectedPrompts="selectedPrompts"
      :selectedPromptIdRef="selectedPromptIdRef"
      :expandedMethods="expandedMethods"
      :isAllSelected="isAllSelected"
      @update:searchQuery="searchQuery = $event"
      @select-prompt="selectPrompt"
      @delete-prompt="deletePrompt"
      @delete-selected="deleteSelectedPrompts"
      @clear-selection="clearSelection"
      @toggle-all="toggleAllSelection(groupedPrompts)"
      @toggle-method="toggleMethod"
      @open-folder="openFolder"
      @go-to-root="goToRoot"
      @update:selectedPrompts="selectedPrompts = $event"
      @update:selectedPromptIdRef="selectedPromptIdRef = $event"
    />

    <!-- Prompt Editor -->
    <PromptEditor
      :promptForm="promptForm"
      :workflows="workflows"
      :showPreview="showPromptPreview"
      :highlightedPrompt="highlightedPrompt"
      :loadingRandomTask="loadingRandomTask"
      :savePrompt="savePrompt"
      :saveForAllLanguages="saveForAllLanguages"
      @update:promptForm="promptForm = $event"
      @update:showPreview="showPromptPreview = $event"
      @update:savePrompt="savePrompt = $event"
      @update:saveForAllLanguages="saveForAllLanguages = $event"
      @execute="executePrompt"
      @save="savePromptOnly"
      @publish="publishPrompt"
      @submit-for-testing="submitForTesting"
      @open-test-runs="openTestRunsModal"
      @load-random="loadRandomTaskVariables"
    />

    <!-- Version History -->
    <VersionHistory :method="promptForm.method" :language="promptForm.language" @restored="fetchPrompts" />

    <!-- Response Display -->
    <PromptResponse
      v-if="response || responseData.imageTaskId"
      :response="response"
      :responseData="responseData"
      :isCheckerResponse="isCheckerResponse"
      :formattedResult="formattedResult"
      :formattedCorrectMessage="formattedCorrectMessage"
      :formattedFixes="formattedFixes"
      :isResultDifferent="isResultDifferent"
      :formattedContextJson="formattedContextJson"
      :resultIsFormatted="resultIsFormatted"
      :contextIsFormatted="contextIsFormatted"
    />

    <!-- Test Run Dialog -->
    <TestRunDialog
      :visible="showTestRunsModal"
      :testRunsLoading="testRunsLoading"
      :testSets="testSets"
      :selectedTestSetId="selectedTestSetId"
      :selectedTestSet="selectedTestSet"
      :testSetItemCount="testSetItemCount"
      :newTestRun="newTestRun"
      :executionMethodType="executionMethodType"
      :currentTestRun="currentTestRun"
      :canCreateTestRun="canCreateTestRun"
      :promptForm="promptForm"
      @update:visible="showTestRunsModal = $event"
      @update:selectedTestSetId="selectedTestSetId = $event"
      @update:executionMethodType="executionMethodType = $event"
      @load-evaluations="loadTestSetEvaluations"
      @create-run="createTestRun(canCreateTestRun)"
      @close="closeTestRunsModal"
      @view-results="viewTestResults"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import axios from 'axios';
import PromptList from '@/components/prompts/PromptList.vue';
import PromptEditor from '@/components/prompts/PromptEditor.vue';
import PromptResponse from '@/components/prompts/PromptResponse.vue';
import VersionHistory from '@/components/prompts/VersionHistory.vue';
import TestRunDialog from '@/components/prompts/TestRunDialog.vue';
import { usePromptsState, useTestRunState } from '@/components/prompts/promptsTab/state';
import { usePromptsComputed, useTestRunComputed } from '@/components/prompts/promptsTab/computed';
import { usePromptMethods } from '@/components/prompts/promptsTab/promptMethods';
import { useTestRunMethods } from '@/components/prompts/promptsTab/testRunMethods';

const router = useRouter();
const toast = useToast();

// State
const promptsState = usePromptsState();
const testRunState = useTestRunState();

// Computed
const promptsComputed = usePromptsComputed(promptsState);
const testRunComputed = useTestRunComputed(testRunState);

// Methods - use a no-op emit since we no longer need parent communication
const emit = (event: string, ...args: any[]) => {};
const promptMethods = usePromptMethods(promptsState, emit);
const testRunMethods = useTestRunMethods(testRunState, promptsState.promptForm, router);

// Destructure for template
const {
  prompts, loading, searchQuery, expandedMethods, currentFolder, selectedPrompts,
  workflows, selectedPromptIdRef, promptForm, response, responseData,
  savePrompt, saveForAllLanguages, showPromptPreview, loadingRandomTask,
} = promptsState;

const {
  showTestRunsModal, testRunsLoading, testSets, selectedTestSetId,
  selectedTestSet, testSetItemCount, executionMethodType, currentTestRun, newTestRun,
} = testRunState;

const {
  filteredPrompts, groupedPrompts, isAllSelected, isCheckerResponse,
  formattedResult, formattedCorrectMessage, formattedFixes, isResultDifferent,
  formattedContextJson, resultIsFormatted, contextIsFormatted, highlightedPrompt,
} = promptsComputed;

const { canCreateTestRun } = testRunComputed;

const {
  fetchPrompts, fetchWorkflows, selectPrompt, toggleMethod, openFolder, goToRoot,
  toggleAllSelection, clearSelection, deletePrompt, deleteSelectedPrompts,
  executePrompt, savePromptOnly, loadRandomTaskVariables,
} = promptMethods;

const {
  openTestRunsModal, closeTestRunsModal, loadTestSetEvaluations,
  createTestRun, viewTestResults, cleanupPolling,
} = testRunMethods;

// Lifecycle actions
const publishPrompt = async () => {
  if (!promptForm.value.method) return;
  if (!confirm(`Publish "${promptForm.value.method}" to production? This will replace the current version.`)) return;
  try {
    await savePromptOnly();
    await axios.post(`/data/v1.0/admin/prompt/${encodeURIComponent(promptForm.value.method)}/publish`, null, {
      params: { language: promptForm.value.language },
    });
    fetchPrompts();
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Publish Error', detail: e.response?.data?.message || e.message, life: 5000 });
  }
};

const submitForTesting = async () => {
  if (!promptForm.value.method) return;
  try {
    await savePromptOnly();
    await axios.post(`/data/v1.0/admin/prompt/${encodeURIComponent(promptForm.value.method)}/submit-for-testing`, null, {
      params: { language: promptForm.value.language },
    });
    fetchPrompts();
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Testing Error', detail: e.response?.data?.message || e.message, life: 5000 });
  }
};

onMounted(() => {
  fetchPrompts();
  fetchWorkflows();
});

onBeforeUnmount(() => {
  cleanupPolling();
});
</script>
