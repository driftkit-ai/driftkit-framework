<template>
  <div class="card mb-4" v-if="method">
    <div class="card-header d-flex justify-content-between align-items-center">
      <h5 class="m-0 font-semibold">Version History</h5>
      <Button label="Refresh" icon="pi pi-refresh" size="small" severity="secondary" text @click="loadVersions" />
    </div>
    <div class="card-content p-4">
      <ProgressSpinner v-if="loading" style="width: 30px; height: 30px" />
      <div v-else-if="versions.length === 0" class="text-muted">No versions found.</div>
      <div v-else class="version-list">
        <div v-for="v in versions" :key="v.id" class="version-item" :class="{ active: v.state === 'CURRENT' }">
          <div class="d-flex justify-content-between align-items-center">
            <div class="d-flex align-items-center gap-2">
              <Checkbox :binary="true" :modelValue="diffSelection.includes(v.version)" @change="toggleDiffSelection(v.version)" />
              <span class="version-number">v{{ v.version }}</span>
              <Tag :value="v.state" :severity="stateSeverity(v.state)" />
              <span class="text-sm text-muted">{{ v.language }}</span>
            </div>
            <div class="d-flex align-items-center gap-2">
              <span class="text-sm text-muted">{{ formatTime(v.updatedTime) }}</span>
              <Button v-if="v.state === 'REPLACED'" label="Restore" icon="pi pi-history" size="small" severity="secondary" text @click="restore(v.version)" />
            </div>
          </div>
          <div class="text-sm text-muted mt-1 message-preview">{{ v.message?.substring(0, 120) }}{{ v.message?.length > 120 ? '...' : '' }}</div>
        </div>

        <!-- Diff Viewer -->
        <div v-if="diffSelection.length === 2" class="diff-panel mt-2">
          <div class="diff-header">
            <span class="font-semibold">Diff: v{{ diffVersions[0]?.version }} → v{{ diffVersions[1]?.version }}</span>
            <Button icon="pi pi-times" size="small" severity="secondary" text @click="diffSelection = []" />
          </div>
          <div class="diff-grid">
            <div>
              <h6 class="text-sm">v{{ diffVersions[0]?.version }} ({{ diffVersions[0]?.state }})</h6>
              <pre class="diff-block">{{ diffVersions[0]?.message }}</pre>
            </div>
            <div>
              <h6 class="text-sm">v{{ diffVersions[1]?.version }} ({{ diffVersions[1]?.state }})</h6>
              <pre class="diff-block">{{ diffVersions[1]?.message }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import axios from 'axios';
import Button from 'primevue/button';
import Tag from 'primevue/tag';
import Checkbox from 'primevue/checkbox';
import ProgressSpinner from 'primevue/progressspinner';

const props = defineProps<{ method: string; language: string }>();
const emit = defineEmits(['restored']);

const versions = ref<any[]>([]);
const loading = ref(false);
const diffSelection = ref<number[]>([]);

const toggleDiffSelection = (version: number) => {
  const idx = diffSelection.value.indexOf(version);
  if (idx >= 0) { diffSelection.value.splice(idx, 1); }
  else {
    if (diffSelection.value.length >= 2) diffSelection.value.shift();
    diffSelection.value.push(version);
  }
};

const diffVersions = computed(() =>
  diffSelection.value.map(v => versions.value.find(ver => ver.version === v)).filter(Boolean).sort((a, b) => a.version - b.version)
);

const loadVersions = async () => {
  if (!props.method) { versions.value = []; return; }
  loading.value = true;
  try {
    const res = await axios.get(`/data/v1.0/admin/prompt/${encodeURIComponent(props.method)}/versions`, {
      params: { language: props.language || undefined },
    });
    versions.value = res.data.data || [];
  } catch { versions.value = []; }
  finally { loading.value = false; }
};

const restore = async (version: number) => {
  try {
    await axios.post(`/data/v1.0/admin/prompt/${encodeURIComponent(props.method)}/restore`, null, {
      params: { version, language: props.language },
    });
    emit('restored');
    loadVersions();
  } catch (e: any) {
    alert('Error restoring: ' + (e.response?.data?.message || e.message));
  }
};

const stateSeverity = (state: string) => {
  switch (state) {
    case 'CURRENT': return 'success';
    case 'DRAFT': return 'secondary';
    case 'AUTO_TESTING': return 'info';
    case 'MANUAL_TESTING': return 'warn';
    case 'REPLACED': return 'contrast';
    default: return 'secondary';
  }
};

const formatTime = (ts: number) => ts ? new Date(ts).toLocaleString() : '';

watch(() => props.method, loadVersions, { immediate: true });
</script>

<style scoped>
.card { background: var(--p-surface-card); border: 1px solid var(--p-surface-border); border-radius: 8px; overflow: hidden; }
.card-header { padding: 1rem 1.25rem; border-bottom: 1px solid var(--p-surface-border); background: var(--p-surface-50); }
.card-content { padding: 1.25rem; }
.version-list { display: flex; flex-direction: column; gap: 0.5rem; }
.version-item { padding: 0.75rem; border: 1px solid var(--p-surface-border); border-radius: 6px; transition: background 0.15s; }
.version-item.active { border-color: var(--p-green-300); background: var(--p-green-50); }
.version-number { font-weight: 700; font-size: 0.9rem; min-width: 2rem; }
.message-preview { font-family: monospace; font-size: 0.8rem; opacity: 0.7; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.diff-panel { border: 1px solid var(--p-surface-border); border-radius: 6px; overflow: hidden; }
.diff-header { display: flex; justify-content: space-between; align-items: center; padding: 0.5rem 0.75rem; background: var(--p-surface-50); border-bottom: 1px solid var(--p-surface-border); }
.diff-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0; }
.diff-grid > div { padding: 0.5rem; }
.diff-grid > div:first-child { border-right: 1px solid var(--p-surface-border); }
.diff-block { white-space: pre-wrap; word-break: break-word; font-family: monospace; font-size: 0.8rem; background: var(--p-surface-0); padding: 0.5rem; border-radius: 4px; max-height: 300px; overflow: auto; }
.d-flex { display: flex; }
.justify-content-between { justify-content: space-between; }
.align-items-center { align-items: center; }
.gap-2 { gap: 0.5rem; }
.m-0 { margin: 0; }
.mb-4 { margin-bottom: 1.5rem; }
.mt-1 { margin-top: 0.25rem; }
.p-4 { padding: 1.25rem; }
.text-sm { font-size: 0.875rem; }
.text-muted { color: var(--p-text-muted-color); }
.font-semibold { font-weight: 600; }
</style>
