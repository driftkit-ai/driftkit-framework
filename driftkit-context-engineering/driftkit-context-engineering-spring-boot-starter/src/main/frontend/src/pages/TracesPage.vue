<template>
  <div class="traces-page">
    <!-- Filter Panel -->
    <div class="card mb-3">
      <div class="card-content">
        <div class="d-flex justify-content-between align-items-center mb-2">
          <h5 class="m-0 font-semibold">Model Request Traces</h5>
          <div class="d-flex gap-2 align-items-center">
            <Button v-if="totalSelectedItems > 0" :label="`Add ${totalSelectedItems} to Test Set`" icon="pi pi-plus" size="small" severity="success" @click="openAddToTestSetModal" />
            <SelectButton v-model="traceTimeRange" :options="timeRangeOpts" optionLabel="l" optionValue="v" @change="onTimeRangeChange" />
          </div>
        </div>
        <div class="filter-grid">
          <span class="text-sm text-muted">From</span>
          <InputText v-model="traceStartTime" type="datetime-local" @change="fetchTraces()" />
          <span class="text-sm text-muted">To</span>
          <InputText v-model="traceEndTime" type="datetime-local" @change="fetchTraces()" />
        </div>
        <div class="filter-grid mt-2">
          <Select v-model="selectedPromptMethod" :options="['', ...availablePromptMethods]" placeholder="All Prompts" showClear @change="onPromptMethodChange" />
          <div class="d-flex gap-1">
            <InputText v-model="searchMessageId" placeholder="Message ID" />
            <Button icon="pi pi-search" size="small" severity="secondary" @click="searchByMessageId()" />
          </div>
          <InputText v-model="excludePurposeFilter" placeholder="Exclude purpose" @change="fetchTraces()" />
          <div class="d-flex align-items-center gap-1">
            <Checkbox v-model="groupByChatId" :binary="true" inputId="grp" @change="regroupTraces" />
            <label for="grp" class="text-sm">Group by Chat</label>
          </div>
        </div>
      </div>
    </div>

    <!-- Prompt Metrics -->
    <div v-if="selectedPromptMethod && promptMetrics" class="card mb-3">
      <div class="card-content">
        <div class="d-flex align-items-center gap-2 mb-2">
          <h6 class="m-0">Metrics:</h6>
          <Tag :value="selectedPromptMethod" severity="info" />
        </div>
        <ProgressSpinner v-if="promptMetricsLoading" style="width:24px;height:24px" />
        <div v-else class="metrics-grid">
          <div class="metric-card"><div class="metric-label">Requests</div><div class="metric-value">{{ promptMetrics.totalTraces || 0 }}</div></div>
          <div class="metric-card"><div class="metric-label">Success</div><div class="metric-value">{{ promptMetrics.successRate ? (promptMetrics.successRate*100).toFixed(1)+'%' : '0%' }}</div></div>
          <div class="metric-card"><div class="metric-label">Tokens</div><div class="metric-value">{{ (promptMetrics.totalTokens||0).toLocaleString() }}</div></div>
          <div class="metric-card"><div class="metric-label">p50 Latency</div><div class="metric-value">{{ promptMetrics.latencyPercentiles?.p50 || 'N/A' }}ms</div></div>
        </div>
      </div>
    </div>

    <!-- Traces -->
    <div class="card">
      <div class="card-content">
        <ProgressSpinner v-if="tracesLoading" />
        <Message v-else-if="Object.keys(groupedTraces).length === 0" severity="info" :closable="false">No traces found.</Message>
        <div v-else>
          <!-- Trace groups -->
          <div v-for="(contextTraces, groupKey) in groupedTraces" :key="groupKey" class="trace-group mb-3">
            <!-- Group Header -->
            <div class="group-header" :class="groupByChatId && contextTraces.contextIds ? 'header-chat' : 'header-context'">
              <div class="d-flex align-items-center gap-2 flex-1">
                <Checkbox :binary="true" :modelValue="isMessageTaskSelected(groupKey)" @change="toggleMessageTaskSelection(messageTasksByContextId[groupKey]?.messageId || '')" />
                <Tag :value="groupByChatId && contextTraces.contextIds ? 'Chat' : 'Context'" :severity="groupByChatId && contextTraces.contextIds ? 'info' : 'secondary'" />
                <span class="text-sm font-mono">{{ groupKey.substring(0, 12) }}...</span>
                <Tag v-if="messageTasksByContextId[groupKey]" :value="getPromptMethod(messageTasksByContextId[groupKey])" severity="secondary" />
                <Tag v-if="contextTraces.contextIds" :value="`${contextTraces.contextIds.length} ctx`" severity="info" />
              </div>
              <div class="d-flex align-items-center gap-2">
                <span v-if="getContextPurpose(groupKey, contextTraces)" class="text-xs">
                  <Tag :value="getContextPurpose(groupKey, contextTraces)" :severity="getContextPurpose(groupKey, contextTraces)==='qa_test_pipeline' ? 'warn' : 'secondary'" />
                </span>
                <Tag :value="calculateTotalTime(contextTraces)+'ms'" severity="info" />
                <Tag :value="calculateTotalTokens(contextTraces)+' tok'" severity="info" />
                <Button label="Details" size="small" severity="secondary" text @click="toggleContextWorkflowSteps(groupKey)" />
                <Button label="Message" size="small" severity="secondary" text @click="toggleMessageDetails(groupKey)" />
              </div>
            </div>

            <!-- Message Details Expansion -->
            <div v-if="expandedMessageDetails.includes(groupKey)" class="expansion-panel">
              <div class="three-col">
                <div>
                  <h6 class="text-sm">Input</h6>
                  <pre class="code-block" v-html="highlightVariables(messageTasksByContextId[groupKey]?.message || contextTraces[0]?.promptTemplate || '')"></pre>
                </div>
                <div>
                  <h6 class="text-sm">Variables</h6>
                  <pre class="code-block">{{ JSON.stringify(contextTraces[0]?.variables || {}, null, 2) }}</pre>
                </div>
                <div>
                  <h6 class="text-sm">Result</h6>
                  <pre class="code-block">{{ messageTasksByContextId[groupKey]?.result || contextTraces[contextTraces.length-1]?.response || '' }}</pre>
                </div>
              </div>
            </div>

            <!-- Traces Table -->
            <div v-if="expandedWorkflowSteps.includes(groupKey)">
              <DataTable :value="contextTraces" size="small" stripedRows :rowClass="traceRowClass">
                <Column header="Type" style="width:80px">
                  <template #body="{data}">
                    <Tag :value="data.requestType || 'TEXT'" :severity="data.requestType==='TEXT_TO_IMAGE'?'warn':'secondary'" />
                  </template>
                </Column>
                <Column header="Prompt / Step">
                  <template #body="{data}">
                    <span v-if="data.workflowInfo?.workflowStep" class="text-sm">{{ data.workflowInfo.workflowStep }}</span>
                    <span v-else class="text-sm">{{ data.promptId || 'N/A' }}</span>
                  </template>
                </Column>
                <Column header="Time" style="width:80px">
                  <template #body="{data}">{{ data.trace?.executionTimeMs || 0 }}ms</template>
                </Column>
                <Column header="Tokens" style="width:90px">
                  <template #body="{data}">{{ (data.trace?.promptTokens||0) + (data.trace?.completionTokens||0) }}</template>
                </Column>
                <Column header="Model" style="width:120px">
                  <template #body="{data}">{{ data.trace?.model || data.modelId || '' }}</template>
                </Column>
                <Column header="Status" style="width:70px">
                  <template #body="{data}">
                    <Tag :value="data.trace?.hasError ? 'Error' : 'OK'" :severity="data.trace?.hasError ? 'danger' : 'success'" />
                  </template>
                </Column>
                <Column header="Time" style="width:140px">
                  <template #body="{data}">{{ formatDateTime(data.timestamp) }}</template>
                </Column>
                <Column header="" style="width:70px">
                  <template #body="{data}">
                    <Button label="View" size="small" severity="secondary" text @click="toggleTraceDetails(data.id)" />
                  </template>
                </Column>
              </DataTable>

              <!-- Expanded trace details -->
              <div v-for="trace in contextTraces.filter(t => expandedTraces.includes(t.id))" :key="'detail-'+trace.id" class="expansion-panel">
                <div class="three-col">
                  <div>
                    <h6 class="text-sm">Prompt Template</h6>
                    <pre class="code-block">{{ trace.promptTemplate || '' }}</pre>
                  </div>
                  <div>
                    <h6 class="text-sm">Variables</h6>
                    <pre class="code-block">{{ JSON.stringify(trace.variables || {}, null, 2) }}</pre>
                  </div>
                  <div>
                    <h6 class="text-sm">Response</h6>
                    <pre class="code-block">{{ trace.response || '' }}</pre>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Pagination -->
          <div v-if="tracesPage.totalPages > 1" class="d-flex justify-content-center gap-1 mt-3">
            <Button icon="pi pi-angle-left" size="small" severity="secondary" text :disabled="tracesPage.number === 0" @click="fetchTraces(tracesPage.number - 1)" />
            <Button v-for="p in paginationRange" :key="p" :label="String(p)" size="small" :severity="p === tracesPage.number + 1 ? 'primary' : 'secondary'" :text="p !== tracesPage.number + 1" @click="typeof p === 'number' && fetchTraces(p - 1)" :disabled="p === '...'" />
            <Button icon="pi pi-angle-right" size="small" severity="secondary" text :disabled="tracesPage.number >= tracesPage.totalPages - 1" @click="fetchTraces(tracesPage.number + 1)" />
          </div>
        </div>
      </div>
    </div>

    <!-- Add to Test Set Modal -->
    <Dialog v-model:visible="showAddToTestSetModal" header="Add to Test Set" :modal="true" :style="{width:'500px'}">
      <div class="mb-3">
        <Select v-model="selectedTestSetId" :options="testSetOptions" optionLabel="label" optionValue="value" placeholder="Select or create test set" class="w-full" />
      </div>
      <div v-if="!selectedTestSetId" class="mb-3">
        <InputText v-model="newTestSetName" placeholder="New test set name" class="w-full mb-2" />
        <Textarea v-model="newTestSetDescription" placeholder="Description" rows="2" class="w-full" />
      </div>
      <Message severity="info" :closable="false">
        {{ totalSelectedItems }} items selected ({{ selectedMessageTasks.length }} tasks, {{ selectedTraceSteps.length }} steps, {{ selectedImageTasks.length }} images)
      </Message>
      <template #footer>
        <Button label="Cancel" severity="secondary" @click="closeAddToTestSetModal" />
        <Button label="Add to Test Set" icon="pi pi-plus" :loading="addingToTestSet" @click="handleAddToTestSet" />
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import Button from 'primevue/button';
import SelectButton from 'primevue/selectbutton';
import InputText from 'primevue/inputtext';
import Textarea from 'primevue/textarea';
import Select from 'primevue/select';
import Checkbox from 'primevue/checkbox';
import Tag from 'primevue/tag';
import Message from 'primevue/message';
import ProgressSpinner from 'primevue/progressspinner';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Dialog from 'primevue/dialog';
import { highlightVariables } from '@/utils/formatting';

// Import all state from traces module
import {
  tracesLoading, tracesPage, availablePromptMethods, traceTimeRange,
  traceStartTime, traceEndTime, selectedPromptMethod, promptMetrics,
  promptMetricsLoading, expandedTraces, expandedMessageDetails,
  expandedWorkflowSteps, messageTasksByContextId, searchMessageId,
  excludePurposeFilter, groupByChatId, totalSelectedItems,
  selectedMessageTasks, selectedTraceSteps, selectedImageTasks,
  availableTestSets, selectedTestSetId, newTestSetName, newTestSetDescription,
  addingToTestSet, showAddToTestSetModal, paginationRange,
} from '@/components/prompts/traces/state';

import { groupedTraces } from '@/components/prompts/traces/grouping';

import {
  fetchTraces, fetchAvailablePromptMethods, fetchPromptMetrics,
  fetchPromptsForMapping, searchByMessageId, fetchMessageTasks,
} from '@/components/prompts/traces/api';

import {
  setTraceTimeRange, formatDateTime, getPromptMethod,
  calculateTotalTime, calculateTotalTokens, getContextPurpose,
} from '@/components/prompts/traces/utils';

import {
  isMessageTaskSelected, toggleMessageTaskSelection,
  toggleTraceDetails, toggleMessageDetails, toggleContextWorkflowSteps,
} from '@/components/prompts/traces/selectionUtils';

import {
  openAddToTestSetModal, closeAddToTestSetModal,
  handleAddToTestSet as baseHandleAddToTestSet,
} from '@/components/prompts/traces/testSetUtils';

const timeRangeOpts = [
  { l: '1H', v: '1hour' }, { l: '3H', v: '3hours' }, { l: '8H', v: '8hours' },
  { l: '1D', v: '1day' }, { l: '1W', v: '1week' },
];

const testSetOptions = computed(() => [
  { label: '+ Create New', value: '' },
  ...availableTestSets.value.map((ts: any) => ({ label: ts.name, value: ts.id })),
]);

const onTimeRangeChange = () => {
  setTraceTimeRange(traceTimeRange.value, traceTimeRange, traceStartTime, traceEndTime, fetchTraces);
};

const onPromptMethodChange = () => {
  fetchTraces();
  fetchPromptMetrics();
};

const regroupTraces = () => {
  const temp = [...tracesPage.value.content];
  tracesPage.value.content = [];
  setTimeout(() => {
    tracesPage.value.content = temp;
    expandedMessageDetails.value = [];
    expandedWorkflowSteps.value = [];
    expandedTraces.value = [];
    fetchMessageTasks();
  }, 0);
};

const handleAddToTestSet = async () => { await baseHandleAddToTestSet(); };

const traceRowClass = (data: any) => {
  if (data.trace?.hasError) return 'row-error';
  return '';
};

onMounted(() => {
  const now = new Date();
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  traceStartTime.value = yesterday.toISOString().slice(0, 16);
  traceEndTime.value = now.toISOString().slice(0, 16);
  fetchPromptsForMapping();
  fetchAvailablePromptMethods();
  fetchTraces();
});
</script>

<style scoped>
.traces-page { max-width: 1400px; }
.card { background: var(--p-surface-card); border: 1px solid var(--p-surface-border); border-radius: 8px; }
.card-content { padding: 1rem; }
.filter-grid { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; }
.metrics-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.5rem; }
.metric-card { background: var(--p-surface-50); border: 1px solid var(--p-surface-border); border-radius: 6px; padding: 0.5rem; text-align: center; }
.metric-label { font-size: 0.75rem; color: var(--p-text-muted-color); }
.metric-value { font-size: 1.1rem; font-weight: 700; }
.trace-group { border: 1px solid var(--p-surface-border); border-radius: 6px; overflow: hidden; }
.group-header { display: flex; justify-content: space-between; align-items: center; padding: 0.5rem 0.75rem; flex-wrap: wrap; gap: 0.5rem; }
.header-chat { background: var(--p-blue-50); }
.header-context { background: var(--p-surface-50); }
.expansion-panel { padding: 0.75rem; border-top: 1px solid var(--p-surface-border); background: var(--p-surface-50); }
.three-col { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 0.75rem; }
.code-block { white-space: pre-wrap; word-break: break-word; font-family: monospace; font-size: 0.8rem; background: var(--p-surface-0); border: 1px solid var(--p-surface-border); border-radius: 4px; padding: 0.5rem; max-height: 200px; overflow: auto; }
.font-mono { font-family: monospace; }
:deep(.row-error) { background: var(--p-red-50) !important; }
.d-flex { display: flex; }
.flex-1 { flex: 1; }
.justify-content-between { justify-content: space-between; }
.justify-content-center { justify-content: center; }
.align-items-center { align-items: center; }
.gap-1 { gap: 0.25rem; }
.gap-2 { gap: 0.5rem; }
.m-0 { margin: 0; }
.mb-2 { margin-bottom: 0.5rem; }
.mb-3 { margin-bottom: 1rem; }
.mt-2 { margin-top: 0.5rem; }
.mt-3 { margin-top: 1rem; }
.w-full { width: 100%; }
.text-sm { font-size: 0.875rem; }
.text-xs { font-size: 0.75rem; }
.text-muted { color: var(--p-text-muted-color); }
.font-semibold { font-weight: 600; }
@media (max-width: 768px) { .three-col { grid-template-columns: 1fr; } .metrics-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
