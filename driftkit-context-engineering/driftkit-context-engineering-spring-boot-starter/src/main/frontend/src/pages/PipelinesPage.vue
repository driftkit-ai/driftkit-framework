<template>
  <div class="pipelines-page">
    <div class="card mb-4">
      <div class="card-header">
        <h5 class="m-0 font-semibold">Registered Pipelines</h5>
      </div>
      <div class="card-content p-4">
        <ProgressSpinner v-if="loading" />
        <Message v-else-if="pipelines.length === 0" severity="info" :closable="false">
          No pipelines registered. Pipelines are auto-registered from @Workflow annotations and named agents.
        </Message>
        <DataTable v-else :value="pipelines" stripedRows size="small" @row-click="selectPipeline">
          <Column field="id" header="ID" sortable />
          <Column field="type" header="Type" style="width: 160px">
            <template #body="{ data }">
              <Tag :value="data.type" :severity="typeSeverity(data.type)" />
            </template>
          </Column>
          <Column field="version" header="Version" style="width: 100px" />
          <Column header="Steps" style="width: 80px">
            <template #body="{ data }">{{ data.steps?.length || 0 }}</template>
          </Column>
        </DataTable>
      </div>
    </div>

    <!-- Pipeline Detail -->
    <div v-if="selectedPipeline" class="card mb-4">
      <div class="card-header d-flex justify-content-between align-items-center">
        <h5 class="m-0 font-semibold">{{ selectedPipeline.id }} <Tag :value="selectedPipeline.type" :severity="typeSeverity(selectedPipeline.type)" class="ms-2" /></h5>
        <Button label="Close" icon="pi pi-times" severity="secondary" text size="small" @click="selectedPipeline = null" />
      </div>
      <div class="card-content p-4">
        <!-- Step Flow -->
        <div class="step-flow">
          <div v-for="(step, i) in selectedPipeline.steps" :key="step.stepId" class="step-node">
            <div class="step-card" :class="stepClass(step)">
              <div class="step-header">
                <span class="step-id">{{ step.stepId }}</span>
                <Tag :value="step.type" severity="secondary" class="text-xs" />
              </div>
              <div v-if="step.agentName" class="text-sm text-muted">Agent: {{ step.agentName }}</div>
              <div v-if="step.promptMethod" class="text-sm text-muted">Prompt: {{ step.promptMethod }}</div>
              <div v-if="step.inputType" class="text-xs text-muted">{{ step.inputType }} → {{ step.outputType }}</div>
            </div>
            <div v-if="i < selectedPipeline.steps.length - 1" class="step-arrow">
              <i class="pi pi-arrow-down"></i>
            </div>
          </div>
        </div>

        <!-- Config -->
        <div v-if="selectedPipeline.config" class="mt-3">
          <h6>Configuration</h6>
          <pre class="config-pre">{{ JSON.stringify(selectedPipeline.config, null, 2) }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import axios from 'axios';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Tag from 'primevue/tag';
import Button from 'primevue/button';
import Message from 'primevue/message';
import ProgressSpinner from 'primevue/progressspinner';

const pipelines = ref<any[]>([]);
const selectedPipeline = ref<any>(null);
const loading = ref(false);

const fetchPipelines = async () => {
  loading.value = true;
  try {
    const res = await axios.get('/data/v1.0/admin/pipelines/');
    pipelines.value = res.data.data || [];
  } catch { pipelines.value = []; }
  finally { loading.value = false; }
};

const selectPipeline = (event: any) => {
  selectedPipeline.value = event.data;
};

const typeSeverity = (type: string) => {
  switch (type) {
    case 'WORKFLOW': return 'info';
    case 'SEQUENTIAL_AGENT': return 'success';
    case 'LOOP_AGENT': return 'warn';
    default: return 'secondary';
  }
};

const stepClass = (step: any) => {
  switch (step.type) {
    case 'BRANCH': return 'step-branch';
    case 'LOOP_WORKER': return 'step-worker';
    case 'LOOP_EVALUATOR': return 'step-evaluator';
    case 'ASYNC': return 'step-async';
    default: return '';
  }
};

onMounted(fetchPipelines);
</script>

<style scoped>
.pipelines-page { max-width: 1200px; }
.card { background: var(--p-surface-card); border: 1px solid var(--p-surface-border); border-radius: 8px; overflow: hidden; }
.card-header { padding: 1rem 1.25rem; border-bottom: 1px solid var(--p-surface-border); background: var(--p-surface-50); }
.card-content { padding: 1.25rem; }
.step-flow { display: flex; flex-direction: column; align-items: center; gap: 0; }
.step-node { display: flex; flex-direction: column; align-items: center; }
.step-card { padding: 0.75rem 1.25rem; border: 2px solid var(--p-surface-border); border-radius: 8px; background: var(--p-surface-card); min-width: 250px; text-align: center; }
.step-card.step-branch { border-color: var(--p-blue-300); background: var(--p-blue-50); }
.step-card.step-worker { border-color: var(--p-green-300); background: var(--p-green-50); }
.step-card.step-evaluator { border-color: var(--p-yellow-300); background: var(--p-yellow-50); }
.step-card.step-async { border-color: var(--p-purple-300); background: var(--p-purple-50); }
.step-header { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; margin-bottom: 0.25rem; }
.step-id { font-weight: 600; font-size: 0.9rem; }
.step-arrow { padding: 0.25rem 0; color: var(--p-text-muted-color); }
.config-pre { background: var(--p-surface-50); padding: 0.75rem; border-radius: 6px; border: 1px solid var(--p-surface-border); font-size: 0.85rem; }
.d-flex { display: flex; }
.justify-content-between { justify-content: space-between; }
.align-items-center { align-items: center; }
.m-0 { margin: 0; }
.ms-2 { margin-left: 0.5rem; }
.mb-4 { margin-bottom: 1.5rem; }
.mt-3 { margin-top: 1rem; }
.p-4 { padding: 1.25rem; }
.text-sm { font-size: 0.875rem; }
.text-xs { font-size: 0.75rem; }
.text-muted { color: var(--p-text-muted-color); }
.font-semibold { font-weight: 600; }
</style>
