<template>
  <div class="admin-layout">
    <!-- Sidebar -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <h3 v-if="!sidebarCollapsed" class="sidebar-title">DriftKit</h3>
        <button class="sidebar-toggle" @click="sidebarCollapsed = !sidebarCollapsed">
          <i :class="sidebarCollapsed ? 'pi pi-angle-right' : 'pi pi-angle-left'"></i>
        </button>
      </div>

      <nav class="sidebar-nav">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="sidebar-item"
          :class="{ active: isActive(item.path) }"
        >
          <i :class="item.icon"></i>
          <span v-if="!sidebarCollapsed" class="sidebar-label">{{ item.label }}</span>
        </router-link>
      </nav>
    </aside>

    <!-- Main Content -->
    <div class="main-content" :class="{ expanded: sidebarCollapsed }">
      <!-- Header -->
      <header class="top-header">
        <div class="header-left">
          <h4 class="page-title">{{ currentPageTitle }}</h4>
        </div>
        <div class="header-right">
          <Button label="Process Input" icon="pi pi-file-import" severity="info" size="small" @click="showParseModal = true" />
          <Button label="Index Document" icon="pi pi-database" severity="success" size="small" @click="showIndexModal = true" />
          <Button label="Logout" icon="pi pi-sign-out" severity="secondary" size="small" @click="logout" />
        </div>
      </header>

      <!-- Page Content -->
      <main class="page-content">
        <router-view />
      </main>
    </div>

    <!-- Parse Input Modal -->
    <Dialog v-model:visible="showParseModal" header="Parse Input" :modal="true" :style="{ width: '700px' }">
      <div class="mb-3">
        <SelectButton v-model="parseMode" :options="['file', 'youtube']" />
      </div>
      <div v-if="parseMode === 'file'" class="mb-3">
        <label class="field-label">File</label>
        <input type="file" ref="parseFileInput" class="form-control" />
        <div class="mt-2 d-flex align-items-center gap-2">
          <Checkbox v-model="parseMetadata" :binary="true" inputId="parseMetadata" />
          <label for="parseMetadata">Include metadata</label>
        </div>
      </div>
      <div v-else class="mb-3">
        <label class="field-label">Video ID</label>
        <InputText v-model="youtubeVideoId" class="w-full mb-2" />
        <label class="field-label">Language</label>
        <InputText v-model="youtubeInputLang" placeholder="e.g. es" class="w-full" />
        <div class="mt-2 d-flex align-items-center gap-2">
          <Checkbox v-model="parseMetadata" :binary="true" inputId="parseMetadataYt" />
          <label for="parseMetadataYt">Include metadata</label>
        </div>
      </div>
      <div v-if="parseResponse" class="mt-3">
        <h6>Parse Result:</h6>
        <pre class="result-pre">{{ JSON.stringify(parseResponse, null, 2) }}</pre>
      </div>
      <template #footer>
        <Button label="Close" severity="secondary" @click="showParseModal = false; parseResponse = null" />
        <Button label="Process" icon="pi pi-cog" @click="processInput" />
        <Button v-if="parseResponse" label="Copy Output" icon="pi pi-copy" severity="info" @click="copyToClipboard(JSON.stringify(parseResponse, null, 2))" />
      </template>
    </Dialog>

    <!-- Index Document Modal -->
    <Dialog v-model:visible="showIndexModal" header="Index Document" :modal="true" :style="{ width: '700px' }">
      <div class="mb-3">
        <SelectButton v-model="indexMode" :options="['file', 'text', 'youtube']" />
      </div>
      <div v-if="indexMode === 'file'" class="mb-3">
        <label class="field-label">File</label>
        <input type="file" ref="indexFileInput" class="form-control" />
      </div>
      <div v-else-if="indexMode === 'text'" class="mb-3">
        <label class="field-label">Text</label>
        <Textarea v-model="indexTextInput" rows="5" class="w-full" />
      </div>
      <div v-else class="mb-3">
        <label class="field-label">Video ID</label>
        <InputText v-model="indexYoutubeVideoId" class="w-full mb-2" />
        <label class="field-label">Primary Language</label>
        <InputText v-model="indexYoutubePrimaryLang" placeholder="e.g. en" class="w-full mb-2" />
        <label class="field-label">Extra Languages</label>
        <InputText v-model="indexYoutubeExtraLangs" placeholder="e.g. es,fr" class="w-full" />
      </div>
      <div class="mb-3">
        <label class="field-label">Index</label>
        <Select v-model="selectedIndexId" :options="indexesList" optionLabel="indexName" optionValue="id" placeholder="Select an index" class="w-full" />
      </div>
      <div v-if="indexResponse" class="mt-3">
        <h6>Index Response:</h6>
        <pre class="result-pre">{{ JSON.stringify(indexResponse, null, 2) }}</pre>
      </div>
      <div v-if="indexTaskStatus && indexTaskId" class="mt-3">
        <h6>Task Status ({{ indexTaskId }})</h6>
        <p>Status: {{ indexTaskStatus.status }}</p>
        <p>Total chunks: {{ indexTaskStatus.metadata?.totalChunks }}</p>
        <p>Processed: {{ indexTaskStatus.metadata?.totalChunksProcessed }}</p>
      </div>
      <template #footer>
        <Button label="Close" severity="secondary" @click="closeIndexModal" />
        <Button label="Index" icon="pi pi-database" @click="submitIndex" />
        <Button v-if="indexResponse" label="Copy Output" icon="pi pi-copy" severity="info" @click="copyToClipboard(JSON.stringify(indexResponse, null, 2))" />
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useAuth } from '@/composables/useAuth';
import axios from 'axios';
import Button from 'primevue/button';
import Dialog from 'primevue/dialog';
import InputText from 'primevue/inputtext';
import Textarea from 'primevue/textarea';
import Select from 'primevue/select';
import SelectButton from 'primevue/selectbutton';
import Checkbox from 'primevue/checkbox';

const { logout } = useAuth();
const route = useRoute();
const sidebarCollapsed = ref(false);

const menuItems = [
  { path: '/dashboard', label: 'Dashboard', icon: 'pi pi-chart-bar' },
  { path: '/prompts', label: 'Prompts', icon: 'pi pi-file-edit' },
  { path: '/traces', label: 'Traces', icon: 'pi pi-list' },
  { path: '/test-sets', label: 'Test Sets', icon: 'pi pi-check-square' },
  { path: '/evaluation-runs', label: 'Eval Runs', icon: 'pi pi-play' },
  { path: '/chat', label: 'Chat', icon: 'pi pi-comments' },
  { path: '/indexes', label: 'Indexes', icon: 'pi pi-search' },
  { path: '/dictionaries', label: 'Dictionaries', icon: 'pi pi-book' },
  { path: '/checklists', label: 'Checklists', icon: 'pi pi-check-circle' },
];

const isActive = (path: string) => route.path.startsWith(path);

const currentPageTitle = computed(() => {
  const item = menuItems.find((i) => isActive(i.path));
  return item?.label || 'DriftKit';
});

// --- Parse Input Modal ---
const showParseModal = ref(false);
const parseMode = ref<string>('file');
const parseFileInput = ref<HTMLInputElement | null>(null);
const parseMetadata = ref(false);
const youtubeVideoId = ref('');
const youtubeInputLang = ref('en');
const parseResponse = ref<any>(null);

const processInput = () => {
  if (parseMode.value === 'file') {
    if (!parseFileInput.value?.files?.length) { alert('No file selected'); return; }
    const formData = new FormData();
    formData.append('file', parseFileInput.value.files[0]);
    formData.append('metadata', String(parseMetadata.value));
    axios.post('/data/v1.0/admin/parse/file', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then((res) => { const data = res.data; delete data.input; parseResponse.value = data; })
      .catch(() => { parseResponse.value = { error: 'Error parsing file.' }; });
  } else {
    axios.post('/data/v1.0/admin/parse/youtube', {
      videoId: youtubeVideoId.value,
      metadata: parseMetadata.value,
      languages: youtubeInputLang.value ? [youtubeInputLang.value] : [],
    }).then((res) => { parseResponse.value = res.data; })
      .catch(() => { parseResponse.value = { error: 'Error parsing YouTube video.' }; });
  }
};

// --- Index Document Modal ---
const showIndexModal = ref(false);
const indexMode = ref<string>('file');
const indexFileInput = ref<HTMLInputElement | null>(null);
const indexTextInput = ref('');
const indexYoutubeVideoId = ref('');
const indexYoutubePrimaryLang = ref('en');
const indexYoutubeExtraLangs = ref('');
const selectedIndexId = ref('');
const indexResponse = ref<any>(null);
const indexTaskId = ref<string | null>(null);
const indexTaskStatus = ref<any>(null);
const indexesList = ref<any[]>([]);

const fetchIndexesList = () => {
  axios.get('/data/v1.0/admin/index/list')
    .then((res) => { indexesList.value = res.data; })
    .catch((err) => { console.error('Error fetching indexes:', err); });
};

const closeIndexModal = () => {
  showIndexModal.value = false;
  indexResponse.value = null;
  indexTaskId.value = null;
  indexTaskStatus.value = null;
  indexTextInput.value = '';
  indexYoutubeVideoId.value = '';
  selectedIndexId.value = '';
};

const submitIndex = () => {
  if (!selectedIndexId.value) { alert('Please select an index.'); return; }
  const url = '/data/v1.0/admin/index/submit';

  if (indexMode.value === 'file') {
    if (!indexFileInput.value?.files?.length) { alert('No file selected'); return; }
    const formData = new FormData();
    formData.append('file', indexFileInput.value.files[0]);
    formData.append('index', selectedIndexId.value);
    axios.post(url, formData, { headers: { 'Content-Type': 'multipart/form-data' } })
      .then((res) => { indexResponse.value = res.data; if (res.data?.taskId) indexTaskId.value = res.data.taskId; })
      .catch(() => { indexResponse.value = { error: 'Error indexing file.' }; });
  } else {
    const data: any = { index: selectedIndexId.value };
    if (indexMode.value === 'text') {
      if (!indexTextInput.value) { alert('Please provide text input'); return; }
      data.text = indexTextInput.value;
    } else {
      if (!indexYoutubeVideoId.value) { alert('Please provide a YouTube Video ID.'); return; }
      data.videoId = indexYoutubeVideoId.value;
      data.primaryLang = indexYoutubePrimaryLang.value;
      data.input = indexYoutubeExtraLangs.value ? indexYoutubeExtraLangs.value.split(',') : [];
    }
    axios.post(url, data)
      .then((res) => { indexResponse.value = res.data; if (res.data?.taskId) indexTaskId.value = res.data.taskId; })
      .catch(() => { indexResponse.value = { error: 'Error indexing.' }; });
  }
};

const copyToClipboard = (text: string) => {
  navigator.clipboard.writeText(text).catch(() => alert('Failed to copy.'));
};

onMounted(() => {
  fetchIndexesList();
});
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: var(--p-surface-ground);
}

.sidebar {
  width: 240px;
  background: var(--p-surface-900);
  color: var(--p-surface-0);
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 100;
}

.sidebar.collapsed {
  width: 60px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem;
  border-bottom: 1px solid var(--p-surface-700);
  min-height: 60px;
}

.sidebar-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  white-space: nowrap;
}

.sidebar-toggle {
  background: none;
  border: none;
  color: var(--p-surface-400);
  cursor: pointer;
  padding: 0.25rem;
  border-radius: 4px;
  transition: color 0.2s;
}

.sidebar-toggle:hover {
  color: var(--p-surface-0);
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  padding: 0.5rem;
  gap: 2px;
  overflow-y: auto;
  flex: 1;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.625rem 0.75rem;
  border-radius: 8px;
  color: var(--p-surface-300);
  text-decoration: none;
  font-size: 0.875rem;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.sidebar-item:hover {
  background: var(--p-surface-700);
  color: var(--p-surface-0);
}

.sidebar-item.active {
  background: var(--p-primary-color);
  color: var(--p-primary-contrast-color);
}

.sidebar-item i {
  font-size: 1.1rem;
  width: 20px;
  text-align: center;
  flex-shrink: 0;
}

.sidebar-label {
  overflow: hidden;
  text-overflow: ellipsis;
}

.main-content {
  flex: 1;
  margin-left: 240px;
  transition: margin-left 0.2s ease;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.main-content.expanded {
  margin-left: 60px;
}

.top-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1.5rem;
  background: var(--p-surface-card);
  border-bottom: 1px solid var(--p-surface-border);
  position: sticky;
  top: 0;
  z-index: 50;
}

.header-left {
  display: flex;
  align-items: center;
}

.page-title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--p-text-color);
}

.header-right {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.page-content {
  flex: 1;
  padding: 1.5rem;
}

.field-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--p-text-color);
  display: block;
  margin-bottom: 0.375rem;
}

.result-pre {
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--p-surface-50);
  padding: 0.75rem;
  border-radius: 6px;
  border: 1px solid var(--p-surface-border);
  font-size: 0.85rem;
  max-height: 300px;
  overflow: auto;
}

.d-flex { display: flex; }
.align-items-center { align-items: center; }
.gap-2 { gap: 0.5rem; }
.mb-2 { margin-bottom: 0.5rem; }
.mb-3 { margin-bottom: 1rem; }
.mt-2 { margin-top: 0.5rem; }
.mt-3 { margin-top: 1rem; }
.w-full { width: 100%; }
</style>
