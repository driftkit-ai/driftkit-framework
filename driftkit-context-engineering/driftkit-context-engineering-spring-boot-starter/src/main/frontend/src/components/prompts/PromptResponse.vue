<template>
  <div class="card mb-4">
    <div class="card-header">
      <h5 class="m-0 font-semibold">Response</h5>
    </div>
    <div class="card-content p-4">
      <!-- Response Metadata -->
      <DataTable :value="[responseData]" size="small" class="mb-3">
        <Column field="messageId" header="Message ID" />
        <Column header="Prompt IDs">
          <template #body="{ data }">
            <div v-for="id in (data.promptIds || [])" :key="id">
              <Tag :value="id" severity="info" class="mb-1" />
            </div>
          </template>
        </Column>
        <Column field="modelId" header="Model ID" />
        <Column header="Created">
          <template #body="{ data }">{{ formatTime(data.createdTime) }}</template>
        </Column>
        <Column header="Response Time">
          <template #body="{ data }">{{ formatTime(data.responseTime) }}</template>
        </Column>
        <Column header="Duration (ms)">
          <template #body="{ data }">{{ data.responseTime ? data.responseTime - data.createdTime : '' }}</template>
        </Column>
      </DataTable>

      <!-- Checker Response -->
      <template v-if="isCheckerResponse">
        <div class="mb-3">
          <h6 class="mb-2">Correct Message</h6>
          <pre v-if="!isJSON(formattedCorrectMessage)" class="result-pre">{{ formattedCorrectMessage }}</pre>
          <div v-else class="json-content" v-html="formattedCorrectMessage"></div>
        </div>
        <template v-if="isResultDifferent">
          <div class="mb-3">
            <h6 class="mb-2">Fixes</h6>
            <pre v-if="!isJSON(formattedFixes)" class="result-pre">{{ formattedFixes }}</pre>
            <div v-else class="json-content" v-html="formattedFixes"></div>
          </div>
          <div class="mb-3">
            <h6 class="mb-2">Result</h6>
            <pre v-if="!isJSON(formattedResult)" class="result-pre">{{ formattedResult }}</pre>
            <div v-else class="json-content" v-html="formattedResult"></div>
          </div>
        </template>
      </template>

      <!-- Normal Response -->
      <template v-if="!isCheckerResponse">
        <div class="mb-3">
          <h6 class="mb-2">Result</h6>
          <!-- Image result -->
          <div v-if="responseData.imageTaskId" class="image-result">
            <pre class="result-pre">{{ formattedResult || responseData.message || 'Generated image:' }}</pre>
            <img :src="`/data/v1.0/admin/llm/image/${responseData.imageTaskId}/resource/0`" alt="Generated image" class="result-image" />
          </div>
          <pre v-else-if="!resultIsFormatted" class="result-pre">{{ formattedResult }}</pre>
          <div v-else class="json-content" v-html="formattedResult"></div>
        </div>
      </template>

      <!-- Context JSON -->
      <div v-if="responseData.contextJson" class="mb-3">
        <h6 class="mb-2">Context JSON</h6>
        <pre v-if="!contextIsFormatted" class="result-pre">{{ formattedContextJson }}</pre>
        <div v-else class="json-content" v-html="formattedContextJson"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Tag from 'primevue/tag';
import { isJSON } from '@/utils/formatting';

defineProps<{
  response: string;
  responseData: any;
  isCheckerResponse: boolean;
  formattedResult: string;
  formattedCorrectMessage: string;
  formattedFixes: string;
  isResultDifferent: boolean;
  formattedContextJson: string;
  resultIsFormatted: boolean;
  contextIsFormatted: boolean;
}>();

const formatTime = (timestamp: number) => {
  if (!timestamp) return '';
  return new Date(timestamp).toLocaleString();
};
</script>

<style scoped>
.card { background: var(--p-surface-card); border: 1px solid var(--p-surface-border); border-radius: 8px; overflow: hidden; }
.card-header { padding: 1rem 1.25rem; border-bottom: 1px solid var(--p-surface-border); background: var(--p-surface-50); }
.card-content { padding: 1.25rem; }
.result-pre { white-space: pre-wrap; word-break: break-word; background: var(--p-surface-50); padding: 0.75rem; border-radius: 6px; border: 1px solid var(--p-surface-border); font-size: 0.85rem; max-height: 500px; overflow: auto; }
.json-content { font-family: monospace; white-space: pre-wrap; word-break: break-word; padding: 0.75rem; border-radius: 6px; border: 1px solid var(--p-surface-border); background: var(--p-surface-50); max-height: 500px; overflow: auto; font-size: 0.85rem; line-height: 1.5; }
.result-image { max-width: 100%; max-height: 500px; margin-top: 0.5rem; border: 1px solid var(--p-surface-border); border-radius: 6px; object-fit: contain; background: var(--p-surface-50); }
.m-0 { margin: 0; }
.mb-1 { margin-bottom: 0.25rem; }
.mb-2 { margin-bottom: 0.5rem; }
.mb-3 { margin-bottom: 1rem; }
.mb-4 { margin-bottom: 1.5rem; }
.p-4 { padding: 1.25rem; }
.font-semibold { font-weight: 600; }
</style>
