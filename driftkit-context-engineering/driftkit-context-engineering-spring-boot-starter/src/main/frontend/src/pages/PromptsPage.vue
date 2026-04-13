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
      @open-test-runs="openTestRunsModal"
      @load-random="loadRandomTaskVariables"
    />

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
import { onMounted, onBeforeUnmount, watch } from 'vue';
import { useRouter } from 'vue-router';
import PromptList from '@/components/prompts/PromptList.vue';
import PromptEditor from '@/components/prompts/PromptEditor.vue';
import PromptResponse from '@/components/prompts/PromptResponse.vue';
import TestRunDialog from '@/components/prompts/TestRunDialog.vue';
import { usePromptsState, useTestRunState } from '@/components/prompts/promptsTab/state';
import { usePromptsComputed, useTestRunComputed } from '@/components/prompts/promptsTab/computed';
import { usePromptMethods } from '@/components/prompts/promptsTab/promptMethods';
import { useTestRunMethods } from '@/components/prompts/promptsTab/testRunMethods';

const router = useRouter();

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

onMounted(() => {
  fetchPrompts();
  fetchWorkflows();
});

onBeforeUnmount(() => {
  cleanupPolling();
});
</script>
