<template>
  <div class="test-sets-page">
    <!-- Header -->
    <div class="card mb-3">
      <div class="card-content d-flex justify-content-between align-items-center">
        <h5 class="m-0 font-semibold">Prompt Test Sets</h5>
        <div class="d-flex gap-2">
          <Button v-if="selectedTestSets.length > 0" label="Move to Folder" icon="pi pi-folder" size="small" severity="secondary" outlined @click="openMoveToFolderModal" />
          <Button label="New Folder" icon="pi pi-folder-plus" size="small" severity="secondary" outlined @click="createNewFolder" />
          <Button label="New Test Set" icon="pi pi-plus" size="small" @click="createNewTestSet()" />
        </div>
      </div>
    </div>

    <ProgressSpinner v-if="loading" />

    <div v-else-if="testSets.length === 0 && folders.length === 0">
      <Message severity="info" :closable="false">No test sets found. Create your first test set.</Message>
    </div>

    <div v-else>
      <!-- Folders -->
      <div v-for="folder in folders" :key="folder.id" class="card mb-3">
        <div class="card-content">
          <div class="d-flex justify-content-between align-items-center">
            <div class="d-flex align-items-center gap-2 cursor-pointer" @click="toggleFolder(folder.id)">
              <i :class="expandedFolders.includes(folder.id) ? 'pi pi-folder-open' : 'pi pi-folder'" />
              <span class="font-semibold">{{ folder.name }}</span>
              <Tag :value="String(getTestSetsInFolder(folder.id).length)" severity="secondary" />
            </div>
            <div class="d-flex gap-1">
              <Button label="Run All" icon="pi pi-play" size="small" severity="success" text @click="runAllTestsInFolder(folder.id)" />
              <Button icon="pi pi-plus" size="small" severity="info" text @click="createNewTestSet(folder.id)" />
              <Button icon="pi pi-trash" size="small" severity="danger" text @click="deleteFolder(folder.id)" />
            </div>
          </div>

          <div v-if="expandedFolders.includes(folder.id)" class="mt-3">
            <DataTable :value="getTestSetsInFolder(folder.id)" size="small" stripedRows dataKey="id">
              <Column field="name" header="Name" sortable />
              <Column field="description" header="Description" />
              <Column header="Created" style="width:150px">
                <template #body="{data}">{{ formatDateTime(data.createdAt) }}</template>
              </Column>
              <Column header="Actions" style="width:140px">
                <template #body="{data}">
                  <Button :label="expandedTestSets.includes(data.id) ? 'Hide' : 'View'" size="small" severity="secondary" text @click="toggleTestSetDetails(data.id)" />
                  <Button icon="pi pi-trash" size="small" severity="danger" text @click="deleteTestSet(data.id)" />
                </template>
              </Column>
            </DataTable>
          </div>
        </div>
      </div>

      <!-- Unfiled test sets -->
      <div v-if="getTestSetsInFolder(null).length > 0" class="card mb-3">
        <div class="card-content">
          <h6 class="mb-2">Unfiled Test Sets</h6>
          <DataTable :value="getTestSetsInFolder(null)" size="small" stripedRows dataKey="id">
            <Column field="name" header="Name" sortable />
            <Column field="description" header="Description" />
            <Column header="Created" style="width:150px">
              <template #body="{data}">{{ formatDateTime(data.createdAt) }}</template>
            </Column>
            <Column header="Actions" style="width:140px">
              <template #body="{data}">
                <Button :label="expandedTestSets.includes(data.id) ? 'Hide' : 'View'" size="small" severity="secondary" text @click="toggleTestSetDetails(data.id)" />
                <Button icon="pi pi-trash" size="small" severity="danger" text @click="deleteTestSet(data.id)" />
              </template>
            </Column>
          </DataTable>
        </div>
      </div>

      <!-- Expanded Test Set Detail -->
      <div v-for="tsId in expandedTestSets" :key="'detail-'+tsId" class="card mb-3">
        <div class="card-content">
          <div class="d-flex justify-content-between align-items-center mb-3">
            <h5 class="m-0">{{ testSets.find(ts => ts.id === tsId)?.name }}</h5>
            <div class="d-flex gap-1">
              <Button label="Evaluations" icon="pi pi-cog" size="small" severity="info" outlined @click="openEvaluationsModal(testSets.find(ts => ts.id === tsId))" />
              <Button label="Runs" icon="pi pi-play" size="small" severity="success" outlined @click="openRunsModal(testSets.find(ts => ts.id === tsId))" />
              <Button label="Close" icon="pi pi-times" size="small" severity="secondary" text @click="closeTestSetDetails(tsId)" />
            </div>
          </div>

          <ProgressSpinner v-if="testSetItemsLoading && loadingTestSetId === tsId" style="width:24px;height:24px" />
          <div v-else-if="loadedTestSetId === tsId">
            <Message v-if="testSetItems.length === 0" severity="info" :closable="false">No items. Add items from the Traces tab.</Message>
            <DataTable v-else :value="testSetItems" size="small" stripedRows>
              <Column header="Message" style="max-width:300px">
                <template #body="{data}"><span class="truncate-cell">{{ data.message }}</span></template>
              </Column>
              <Column header="Result" style="max-width:300px">
                <template #body="{data}">
                  <Tag v-if="data.isImageTask" value="Image" severity="info" class="me-1" />
                  <span class="truncate-cell">{{ data.result }}</span>
                </template>
              </Column>
              <Column header="Model" style="width:120px">
                <template #body="{data}">{{ data.model }}<br/><small class="text-muted">T: {{ data.temperature || 'N/A' }}</small></template>
              </Column>
              <Column header="" style="width:120px">
                <template #body="{data}">
                  <Button :label="expandedItems.includes(data.id) ? 'Hide' : 'View'" size="small" severity="secondary" text @click="toggleItemDetails(data.id)" />
                  <Button icon="pi pi-trash" size="small" severity="danger" text @click="deleteTestSetItem(data.id, tsId)" />
                </template>
              </Column>
            </DataTable>

            <div v-for="item in testSetItems.filter(i => expandedItems.includes(i.id))" :key="'item-'+item.id" class="expansion-panel mt-2">
              <div class="three-col">
                <div><h6 class="text-sm">Message</h6><pre class="code-block" v-html="highlightVariables(item.message)"></pre></div>
                <div><h6 class="text-sm">Result</h6><pre class="code-block">{{ isJSON(item.result) ? JSON.stringify(JSON.parse(item.result), null, 2) : item.result }}</pre></div>
                <div v-if="item.variables && Object.keys(item.variables).length > 0"><h6 class="text-sm">Variables</h6><pre class="code-block">{{ JSON.stringify(item.variables, null, 2) }}</pre></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import Button from 'primevue/button';
import Tag from 'primevue/tag';
import Message from 'primevue/message';
import ProgressSpinner from 'primevue/progressspinner';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import { highlightVariables } from '@/utils/formatting';
import { formatDateTime, isJSON, getTestSetsInFolder as getTestSetsInFolderUtil } from '@/components/prompts/testsets/utils';
import * as api from '@/components/prompts/testsets/api';
import useFolders from '@/components/prompts/testsets/useFolders';
import useTestSets from '@/components/prompts/testsets/useTestSets';
import useEvaluations from '@/components/prompts/testsets/useEvaluations';
import useRuns from '@/components/prompts/testsets/useRuns';

const loading = ref(false);
const testSets = ref<any[]>([]);
const folders = ref<any[]>([]);

const fetchTestSets = async () => {
  loading.value = true;
  try {
    const { folders: f, testSets: ts } = await api.fetchTestSets();
    folders.value = f;
    testSets.value = ts;
  } catch (e) { console.error('Error fetching test sets:', e); }
  finally { loading.value = false; }
};

const tsModule = useTestSets(testSets, fetchTestSets);
const foldModule = useFolders(folders, testSets, fetchTestSets, tsModule.selectedTestSets);
const evalModule = useEvaluations(tsModule.selectedTestSet);
const runModule = useRuns(tsModule.selectedTestSet, tsModule.testSetItems, evalModule.evaluations);

const {
  selectedTestSets, expandedTestSets, expandedItems, testSetItems,
  testSetItemsLoading, loadingTestSetId, loadedTestSetId,
  toggleTestSetDetails, closeTestSetDetails, toggleItemDetails,
  deleteTestSet, deleteTestSetItem, createNewTestSet,
} = tsModule;

const { expandedFolders, toggleFolder, createNewFolder, deleteFolder, openMoveToFolderModal, runAllTestsInFolder } = foldModule;
const { openEvaluationsModal } = evalModule;
const { openRunsModal } = runModule;

const getTestSetsInFolder = (folderId: string | null) => getTestSetsInFolderUtil(testSets.value, folderId);

onMounted(fetchTestSets);
</script>

<style scoped>
.test-sets-page { max-width: 1400px; }
.card { background: var(--p-surface-card); border: 1px solid var(--p-surface-border); border-radius: 8px; }
.card-content { padding: 1rem; }
.expansion-panel { padding: 0.75rem; background: var(--p-surface-50); border: 1px solid var(--p-surface-border); border-radius: 6px; }
.three-col { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 0.75rem; }
.code-block { white-space: pre-wrap; word-break: break-word; font-family: monospace; font-size: 0.8rem; background: var(--p-surface-0); border: 1px solid var(--p-surface-border); border-radius: 4px; padding: 0.5rem; max-height: 200px; overflow: auto; }
.truncate-cell { display: inline-block; max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cursor-pointer { cursor: pointer; }
.d-flex { display: flex; } .justify-content-between { justify-content: space-between; } .align-items-center { align-items: center; }
.gap-1 { gap: 0.25rem; } .gap-2 { gap: 0.5rem; }
.m-0 { margin: 0; } .mb-2 { margin-bottom: 0.5rem; } .mb-3 { margin-bottom: 1rem; } .mt-2 { margin-top: 0.5rem; } .mt-3 { margin-top: 1rem; } .me-1 { margin-right: 0.25rem; }
.text-sm { font-size: 0.875rem; } .text-muted { color: var(--p-text-muted-color); } .font-semibold { font-weight: 600; }
@media (max-width: 768px) { .three-col { grid-template-columns: 1fr; } }
</style>
