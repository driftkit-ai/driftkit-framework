<template>
  <div class="card mb-4">
    <div class="card-header d-flex justify-content-between align-items-center">
      <div class="d-flex align-items-center gap-2">
        <h5 class="m-0 font-semibold">Prompts</h5>
        <template v-if="currentFolder">
          <span class="mx-1 text-muted">/</span>
          <Tag :value="currentFolder" severity="info" />
          <Button label="Back to Root" icon="pi pi-arrow-left" size="small" severity="secondary" text @click="$emit('go-to-root')" />
        </template>
      </div>
      <div v-if="selectedPrompts.length > 0" class="d-flex gap-2">
        <Button :label="`Delete Selected (${selectedPrompts.length})`" icon="pi pi-trash" severity="danger" size="small" @click="$emit('delete-selected')" />
        <Button label="Clear" severity="secondary" size="small" text @click="$emit('clear-selection')" />
      </div>
    </div>
    <div class="card-content p-4">
      <!-- Search -->
      <IconField class="mb-3">
        <InputIcon class="pi pi-search" />
        <InputText :modelValue="searchQuery" @update:modelValue="$emit('update:searchQuery', $event)" placeholder="Search by method or prompt message" class="w-full" />
      </IconField>

      <!-- Loading -->
      <div v-if="loading" class="text-center py-4">
        <ProgressSpinner style="width: 40px; height: 40px" />
        <div class="mt-2 text-muted">Loading prompts...</div>
      </div>

      <!-- Empty state -->
      <Message v-else-if="!filteredPrompts.length" severity="info" :closable="false">
        No prompts found. Create a new prompt using the editor below.
      </Message>

      <!-- Table -->
      <DataTable
        v-else
        :value="tableData"
        :rows="50"
        stripedRows
        size="small"
        dataKey="id"
        :rowClass="rowClass"
        @row-click="onRowClick"
        selectionMode="single"
        :selection="selectedRow"
      >
        <Column header="" style="width: 50px">
          <template #header>
            <Checkbox :modelValue="isAllSelected" :binary="true" @change="$emit('toggle-all')" />
          </template>
          <template #body="{ data }">
            <span v-if="data._isFolder" class="text-xl">📁</span>
            <div v-else class="d-flex gap-1">
              <Checkbox :modelValue="selectedPrompts.includes(data.id)" :binary="true" @change="togglePromptCheck(data.id)" @click.stop />
              <RadioButton :modelValue="selectedPromptIdRef" :value="data.id" @change="$emit('select-prompt', data.id)" @click.stop />
            </div>
          </template>
        </Column>
        <Column field="method" header="Method" sortable>
          <template #body="{ data }">
            <span v-if="data._isFolder"><span class="me-1">📁</span>{{ data.method }}</span>
            <span v-else>{{ data.method }}</span>
          </template>
        </Column>
        <Column field="language" header="Language" style="width: 100px" />
        <Column field="state" header="State" style="width: 100px">
          <template #body="{ data }">
            <Tag v-if="data._isFolder" value="Folder" severity="secondary" />
            <Tag v-else :value="data.state" :severity="data.state === 'CURRENT' ? 'success' : 'secondary'" />
          </template>
        </Column>
        <Column header="Created" style="width: 160px">
          <template #body="{ data }">{{ data._isFolder ? '' : formatTime(data.createdTime) }}</template>
        </Column>
        <Column header="Updated" style="width: 160px">
          <template #body="{ data }">{{ data._isFolder ? '' : formatTime(data.updatedTime) }}</template>
        </Column>
        <Column header="Preview" style="max-width: 250px">
          <template #body="{ data }">
            <span v-if="!data._isFolder" class="text-truncate-cell">{{ data.message?.substring(0, 100) }}</span>
          </template>
        </Column>
        <Column header="Actions" style="width: 160px">
          <template #body="{ data }">
            <Button v-if="data._isFolder" label="Open" icon="pi pi-folder-open" size="small" severity="info" text @click.stop="$emit('open-folder', data.method)" />
            <div v-else class="d-flex gap-1">
              <Button icon="pi pi-trash" severity="danger" size="small" text @click.stop="$emit('delete-prompt', data)" />
              <Button v-if="data._hasVersions" :label="data._expanded ? 'Hide' : 'Versions'" size="small" severity="secondary" text @click.stop="$emit('toggle-method', data.method)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Button from 'primevue/button';
import Checkbox from 'primevue/checkbox';
import RadioButton from 'primevue/radiobutton';
import Tag from 'primevue/tag';
import InputText from 'primevue/inputtext';
import IconField from 'primevue/iconfield';
import InputIcon from 'primevue/inputicon';
import ProgressSpinner from 'primevue/progressspinner';
import Message from 'primevue/message';

const props = defineProps<{
  prompts: any[];
  loading: boolean;
  searchQuery: string;
  currentFolder: string;
  groupedPrompts: Record<string, any>;
  filteredPrompts: any[];
  selectedPrompts: string[];
  selectedPromptIdRef: string;
  expandedMethods: string[];
  isAllSelected: boolean;
}>();

const emit = defineEmits([
  'update:searchQuery', 'select-prompt', 'delete-prompt', 'delete-selected',
  'clear-selection', 'toggle-all', 'toggle-method', 'open-folder', 'go-to-root',
  'update:selectedPrompts', 'update:selectedPromptIdRef',
]);

const selectedRow = computed(() => null);

// Flatten grouped prompts into table rows
const tableData = computed(() => {
  const rows: any[] = [];
  for (const [method, group] of Object.entries(props.groupedPrompts)) {
    const g = group as any;
    if (g.currentPrompt.state === 'FOLDER') {
      rows.push({ ...g.currentPrompt, method, _isFolder: true, id: `folder_${method}` });
    } else {
      rows.push({
        ...g.currentPrompt,
        method,
        _isFolder: false,
        _hasVersions: g.otherPrompts?.length > 0,
        _expanded: props.expandedMethods.includes(method),
      });
      // Add version rows if expanded
      if (props.expandedMethods.includes(method) && g.otherPrompts?.length) {
        for (const p of g.otherPrompts) {
          rows.push({ ...p, method, _isFolder: false, _hasVersions: false, _isVersion: true });
        }
      }
    }
  }
  return rows;
});

const rowClass = (data: any) => {
  if (data._isFolder) return 'cursor-pointer folder-row';
  if (data._isVersion) return 'cursor-pointer version-row';
  return 'cursor-pointer';
};

const onRowClick = (event: any) => {
  const data = event.data;
  if (data._isFolder) {
    emit('open-folder', data.method);
  } else {
    emit('select-prompt', data.id);
  }
};

const togglePromptCheck = (id: string) => {
  const current = [...props.selectedPrompts];
  const idx = current.indexOf(id);
  if (idx >= 0) current.splice(idx, 1);
  else current.push(id);
  emit('update:selectedPrompts', current);
};

const formatTime = (timestamp: number) => {
  if (!timestamp) return '';
  return new Date(timestamp).toLocaleString();
};
</script>

<style scoped>
.card { background: var(--p-surface-card); border: 1px solid var(--p-surface-border); border-radius: 8px; overflow: hidden; }
.card-header { padding: 1rem 1.25rem; border-bottom: 1px solid var(--p-surface-border); background: var(--p-surface-50); }
.card-content { padding: 1.25rem; }
.d-flex { display: flex; }
.justify-content-between { justify-content: space-between; }
.align-items-center { align-items: center; }
.gap-1 { gap: 0.25rem; }
.gap-2 { gap: 0.5rem; }
.m-0 { margin: 0; }
.mb-3 { margin-bottom: 1rem; }
.mb-4 { margin-bottom: 1.5rem; }
.me-1 { margin-right: 0.25rem; }
.mx-1 { margin-left: 0.25rem; margin-right: 0.25rem; }
.mt-2 { margin-top: 0.5rem; }
.py-4 { padding-top: 1.5rem; padding-bottom: 1.5rem; }
.p-4 { padding: 1.25rem; }
.w-full { width: 100%; }
.text-center { text-align: center; }
.text-muted { color: var(--p-text-muted-color); }
.text-xl { font-size: 1.25rem; }
.font-semibold { font-weight: 600; }
.cursor-pointer { cursor: pointer; }
.text-truncate-cell { display: inline-block; max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
:deep(.folder-row) { background: var(--p-surface-50); }
:deep(.version-row) { background: var(--p-surface-100); }
</style>
