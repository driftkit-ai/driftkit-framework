<template>
  <div class="playground-page">
    <div class="card mb-4">
      <div class="card-header">
        <h5 class="m-0 font-semibold">Prompt Playground</h5>
      </div>
      <div class="card-content p-4">
        <div class="playground-grid">
          <!-- Panel A -->
          <div class="panel">
            <h6>Prompt A</h6>
            <div class="field mb-2">
              <label class="text-sm">System Message</label>
              <Textarea v-model="panelA.systemMessage" rows="2" class="w-full" placeholder="System message" />
            </div>
            <div class="field mb-2">
              <label class="text-sm">Prompt</label>
              <Textarea v-model="panelA.message" rows="6" class="w-full font-mono" placeholder="Enter prompt..." />
            </div>
            <div class="field mb-2">
              <label class="text-sm">Model</label>
              <InputText v-model="panelA.modelId" class="w-full" placeholder="gpt-4" />
            </div>
            <div class="field mb-2">
              <label class="text-sm">Temperature</label>
              <InputNumber v-model="panelA.temperature" :min="0" :max="2" :step="0.01" class="w-full" />
            </div>
            <div v-if="panelA.result" class="result-box mt-2">
              <div class="d-flex justify-content-between text-sm text-muted mb-1">
                <span>Tokens: {{ panelA.tokens }}</span>
                <span>{{ panelA.latency }}ms</span>
              </div>
              <pre class="result-pre">{{ panelA.result }}</pre>
            </div>
          </div>

          <!-- Panel B -->
          <div class="panel">
            <h6>Prompt B</h6>
            <div class="field mb-2">
              <label class="text-sm">System Message</label>
              <Textarea v-model="panelB.systemMessage" rows="2" class="w-full" placeholder="System message" />
            </div>
            <div class="field mb-2">
              <label class="text-sm">Prompt</label>
              <Textarea v-model="panelB.message" rows="6" class="w-full font-mono" placeholder="Enter prompt..." />
            </div>
            <div class="field mb-2">
              <label class="text-sm">Model</label>
              <InputText v-model="panelB.modelId" class="w-full" placeholder="gpt-4" />
            </div>
            <div class="field mb-2">
              <label class="text-sm">Temperature</label>
              <InputNumber v-model="panelB.temperature" :min="0" :max="2" :step="0.01" class="w-full" />
            </div>
            <div v-if="panelB.result" class="result-box mt-2">
              <div class="d-flex justify-content-between text-sm text-muted mb-1">
                <span>Tokens: {{ panelB.tokens }}</span>
                <span>{{ panelB.latency }}ms</span>
              </div>
              <pre class="result-pre">{{ panelB.result }}</pre>
            </div>
          </div>
        </div>

        <!-- Shared Variables -->
        <div class="mt-3">
          <h6>Variables (shared)</h6>
          <Textarea v-model="variablesJson" rows="3" class="w-full font-mono" placeholder='{"key": "value"}' />
        </div>

        <div class="d-flex gap-2 mt-3">
          <Button label="Execute A" icon="pi pi-play" @click="executePanel('A')" :loading="loadingA" />
          <Button label="Execute B" icon="pi pi-play" severity="secondary" @click="executePanel('B')" :loading="loadingB" />
          <Button label="Execute Both" icon="pi pi-forward" severity="info" @click="executeBoth" :loading="loadingA || loadingB" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import axios from 'axios';
import Textarea from 'primevue/textarea';
import InputText from 'primevue/inputtext';
import InputNumber from 'primevue/inputnumber';
import Button from 'primevue/button';

const panelA = reactive({ systemMessage: '', message: '', modelId: '', temperature: 0.7, result: '', tokens: 0, latency: 0 });
const panelB = reactive({ systemMessage: '', message: '', modelId: '', temperature: 0.7, result: '', tokens: 0, latency: 0 });
const variablesJson = ref('{}');
const loadingA = ref(false);
const loadingB = ref(false);

const executePanel = async (panel: 'A' | 'B') => {
  const p = panel === 'A' ? panelA : panelB;
  const loading = panel === 'A' ? loadingA : loadingB;
  loading.value = true;

  try {
    let vars = {};
    try { vars = JSON.parse(variablesJson.value); } catch {}

    const start = Date.now();
    const res = await axios.post('/data/v1.0/admin/llm/prompt/message/sync', {
      promptIds: [{ promptId: 'playground', prompt: p.message, temperature: p.temperature }],
      variables: vars,
      modelId: p.modelId || undefined,
      language: 'GENERAL',
      purpose: 'playground',
    });

    p.latency = Date.now() - start;
    if (res.data?.data) {
      p.result = res.data.data.result || res.data.data.message || '';
      p.tokens = (res.data.data.promptTokens || 0) + (res.data.data.completionTokens || 0);
    }
  } catch (e: any) {
    p.result = 'Error: ' + (e.response?.data?.message || e.message);
  } finally {
    loading.value = false;
  }
};

const executeBoth = () => {
  executePanel('A');
  executePanel('B');
};
</script>

<style scoped>
.playground-page { max-width: 1400px; }
.card { background: var(--p-surface-card); border: 1px solid var(--p-surface-border); border-radius: 8px; overflow: hidden; }
.card-header { padding: 1rem 1.25rem; border-bottom: 1px solid var(--p-surface-border); background: var(--p-surface-50); }
.card-content { padding: 1.25rem; }
.playground-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; }
.panel { padding: 1rem; border: 1px solid var(--p-surface-border); border-radius: 8px; }
.result-box { padding: 0.75rem; background: var(--p-surface-50); border-radius: 6px; border: 1px solid var(--p-surface-border); }
.result-pre { white-space: pre-wrap; word-break: break-word; font-size: 0.85rem; max-height: 300px; overflow: auto; margin: 0; }
.field { display: flex; flex-direction: column; gap: 0.25rem; }
.font-mono { font-family: monospace; }
.d-flex { display: flex; }
.justify-content-between { justify-content: space-between; }
.gap-2 { gap: 0.5rem; }
.m-0 { margin: 0; }
.mb-1 { margin-bottom: 0.25rem; }
.mb-2 { margin-bottom: 0.5rem; }
.mb-4 { margin-bottom: 1.5rem; }
.mt-2 { margin-top: 0.5rem; }
.mt-3 { margin-top: 1rem; }
.p-4 { padding: 1.25rem; }
.w-full { width: 100%; }
.text-sm { font-size: 0.875rem; }
.text-muted { color: var(--p-text-muted-color); }
.font-semibold { font-weight: 600; }
@media (max-width: 768px) { .playground-grid { grid-template-columns: 1fr; } }
</style>
