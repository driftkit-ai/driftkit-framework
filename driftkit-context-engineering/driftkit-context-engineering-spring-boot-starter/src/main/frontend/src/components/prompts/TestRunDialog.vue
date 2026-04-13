<template>
  <Dialog
    :visible="visible"
    @update:visible="$emit('update:visible', $event)"
    header="Run Prompt with Test Set"
    :modal="true"
    :style="{ width: '700px' }"
    :closable="true"
    @hide="$emit('close')"
  >
    <div v-if="testRunsLoading" class="text-center py-4">
      <ProgressSpinner style="width: 40px; height: 40px" />
    </div>
    <div v-else>
      <!-- Test Set Selection -->
      <div class="field mb-3">
        <label class="field-label">Select Test Set</label>
        <Select
          :modelValue="selectedTestSetId"
          @update:modelValue="$emit('update:selectedTestSetId', $event); $emit('load-evaluations')"
          :options="testSetOptions"
          optionLabel="label"
          optionValue="value"
          placeholder="-- Select Test Set --"
          class="w-full"
        />
      </div>

      <!-- Test Set Details -->
      <div v-if="selectedTestSetId && selectedTestSet" class="info-card mb-3">
        <h6 class="m-0">{{ selectedTestSet.name }}</h6>
        <p class="text-sm text-muted mb-1">{{ selectedTestSet.description }}</p>
        <Tag :value="`${testSetItemCount} test items`" severity="secondary" />
      </div>

      <!-- Run Configuration -->
      <div v-if="selectedTestSetId">
        <h6 class="mb-2">Run Configuration</h6>

        <div class="field mb-3">
          <label class="field-label">Run Name</label>
          <InputText v-model="newTestRun.name" :placeholder="'Run with ' + promptForm.method + ' - ' + new Date().toLocaleString()" class="w-full" />
        </div>

        <div class="field mb-3">
          <label class="field-label">Description</label>
          <Textarea v-model="newTestRun.description" placeholder="Description of this run" rows="2" class="w-full" />
        </div>

        <!-- Execution Method -->
        <div class="field mb-3">
          <label class="field-label">Execution Method</label>
          <div class="d-flex flex-column gap-2">
            <div class="d-flex align-items-center gap-2">
              <RadioButton :modelValue="executionMethodType" value="modelId" @update:modelValue="$emit('update:executionMethodType', $event)" inputId="runModelId" />
              <label for="runModelId">Use Model ID</label>
            </div>
            <InputText v-if="executionMethodType === 'modelId'" v-model="newTestRun.modelId" :placeholder="promptForm.modelId || 'Enter model ID'" class="w-full ms-4" />

            <div class="d-flex align-items-center gap-2">
              <RadioButton :modelValue="executionMethodType" value="workflow" @update:modelValue="$emit('update:executionMethodType', $event)" inputId="runWorkflow" />
              <label for="runWorkflow">Use Workflow</label>
            </div>
            <InputText v-if="executionMethodType === 'workflow'" v-model="newTestRun.workflow" :placeholder="promptForm.workflow || 'Enter workflow ID'" class="w-full ms-4" />
          </div>
        </div>

        <div class="field mb-3">
          <label class="field-label">Temperature (optional)</label>
          <InputNumber v-model="newTestRun.temperature" :min="0" :max="2" :step="0.01" :placeholder="String(promptForm.temperature || 0.7)" class="w-full" />
        </div>
      </div>

      <!-- Current Test Run Status -->
      <div v-if="currentTestRun" class="status-card mt-3">
        <h6 class="mb-2">Current Test Run Status</h6>
        <Message :severity="statusSeverity" :closable="false">
          <div class="d-flex justify-content-between align-items-center">
            <div>
              <strong>Run:</strong> {{ currentTestRun.name || 'Unnamed Run' }}
              <div class="mt-1">
                <Tag :value="currentTestRun.status || 'Unknown'" :severity="statusSeverity" class="me-2" />
                <span v-if="currentTestRun.statusCounts" class="text-sm">
                  <span v-if="currentTestRun.statusCounts.PASSED" class="text-green-500 me-2">{{ currentTestRun.statusCounts.PASSED }} passed</span>
                  <span v-if="currentTestRun.statusCounts.FAILED" class="text-red-500 me-2">{{ currentTestRun.statusCounts.FAILED }} failed</span>
                  <span v-if="currentTestRun.statusCounts.ERROR" class="text-yellow-500 me-2">{{ currentTestRun.statusCounts.ERROR }} errors</span>
                  <span v-if="currentTestRun.statusCounts.PENDING" class="text-yellow-500">{{ currentTestRun.statusCounts.PENDING }} pending</span>
                </span>
              </div>
            </div>
            <Button label="View Results" icon="pi pi-external-link" size="small" severity="secondary" @click="$emit('view-results', currentTestRun.id)" />
          </div>
        </Message>
      </div>
    </div>

    <template #footer>
      <Button label="Cancel" severity="secondary" @click="$emit('close')" />
      <Button label="Create Run" icon="pi pi-play" :disabled="!canCreateTestRun" @click="$emit('create-run')" />
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Dialog from 'primevue/dialog';
import Select from 'primevue/select';
import InputText from 'primevue/inputtext';
import InputNumber from 'primevue/inputnumber';
import Textarea from 'primevue/textarea';
import RadioButton from 'primevue/radiobutton';
import Button from 'primevue/button';
import Tag from 'primevue/tag';
import Message from 'primevue/message';
import ProgressSpinner from 'primevue/progressspinner';

const props = defineProps<{
  visible: boolean;
  testRunsLoading: boolean;
  testSets: any[];
  selectedTestSetId: string;
  selectedTestSet: any;
  testSetItemCount: number;
  newTestRun: any;
  executionMethodType: string;
  currentTestRun: any;
  canCreateTestRun: boolean;
  promptForm: any;
}>();

defineEmits([
  'update:visible', 'update:selectedTestSetId', 'update:executionMethodType',
  'load-evaluations', 'create-run', 'close', 'view-results',
]);

const testSetOptions = computed(() =>
  props.testSets.map((ts: any) => ({ label: ts.name, value: ts.id }))
);

const statusSeverity = computed(() => {
  const status = props.currentTestRun?.status;
  if (status === 'COMPLETED') return 'success';
  if (status === 'FAILED') return 'error';
  if (status === 'RUNNING' || status === 'QUEUED') return 'info';
  return 'warn';
});
</script>

<style scoped>
.field { display: flex; flex-direction: column; gap: 0.375rem; }
.field-label { font-size: 0.875rem; font-weight: 500; color: var(--p-text-color); }
.info-card { padding: 0.75rem; background: var(--p-surface-50); border-radius: 6px; border: 1px solid var(--p-surface-border); }
.status-card { padding: 0.75rem; border-radius: 6px; }
.d-flex { display: flex; }
.flex-column { flex-direction: column; }
.justify-content-between { justify-content: space-between; }
.align-items-center { align-items: center; }
.gap-2 { gap: 0.5rem; }
.m-0 { margin: 0; }
.mb-1 { margin-bottom: 0.25rem; }
.mb-2 { margin-bottom: 0.5rem; }
.mb-3 { margin-bottom: 1rem; }
.mt-1 { margin-top: 0.25rem; }
.mt-3 { margin-top: 1rem; }
.me-2 { margin-right: 0.5rem; }
.ms-4 { margin-left: 1.5rem; }
.py-4 { padding-top: 1.5rem; padding-bottom: 1.5rem; }
.w-full { width: 100%; }
.text-center { text-align: center; }
.text-sm { font-size: 0.875rem; }
.text-muted { color: var(--p-text-muted-color); }
</style>
