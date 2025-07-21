<template>
  <div class="mt-4">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h3>Indexes</h3>
      <div>
        <button @click="openCreateIndexModal" class="btn btn-primary me-2">Add Index</button>
        <button @click="openIndexesListModal" class="btn btn-secondary">Indexes List</button>
      </div>
    </div>
    <h4>Index Tasks</h4>
    <table class="table table-bordered">
      <thead>
        <tr>
          <th>Task ID</th>
          <th>Index Name</th>
          <th>Content Type</th>
          <th>Status</th>
          <th>Created Time</th>
          <th>Completed Time</th>
          <th>Indexing Time (ms)</th>
          <th>Error Message</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="indexTask in indexes" :key="indexTask.taskId">
          <td>{{ indexTask.taskId }}</td>
          <td>{{ indexIdToNameMap[indexTask.indexId] || indexTask.indexId }}</td>
          <td>{{ indexTask.parserInput.contentType }}</td>
          <td>{{ indexTask.status }}</td>
          <td>{{ formatTime(indexTask.createdTime) }}</td>
          <td>{{ formatTime(indexTask.completedTime) }}</td>
          <td>{{ indexTask.indexingTime > 0 ? indexTask.indexingTime : '' }}</td>
          <td>{{ indexTask.errorMessage }}</td>
        </tr>
      </tbody>
    </table>

    <!-- Modal for Create/Edit Index -->
    <div v-if="showAddIndexModal" class="modal" style="background-color: rgba(0,0,0,0.5);">
      <div class="modal-dialog" style="max-width: 600px; width: 600px;">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{ isEditIndex ? 'Edit' : 'Create' }} Index</h5>
            <button type="button" class="btn-close" @click="closeCreateIndexModal"></button>
          </div>
          <div class="modal-body">
            <div v-if="isEditIndex" class="mb-3">
              <label class="form-label">ID:</label>
              <input v-model="indexForm.id" class="form-control" readonly />
            </div>
            <div class="mb-3">
              <label class="form-label">Index Name:</label>
              <input v-model="indexForm.indexName" class="form-control" />
            </div>
            <div class="mb-3">
              <label class="form-label">Index description:</label>
              <input v-model="indexForm.description" class="form-control" />
            </div>
            <div class="mt-3">
              <h3>Language</h3>
              <select v-model="indexForm.language" class="form-control">
                <option value="general" disabled>Select a language</option>
                <option v-for="lang in ['general', 'spanish', 'english']" :key="lang" :value="lang">{{ lang }}</option>
              </select>
            </div>
            <div class="d-flex align-items-center mt-3">
              <button @click="closeCreateIndexModal" type="button" class="btn btn-secondary me-2">Close</button>
              <button @click="submitIndexForm" type="button" class="btn btn-primary me-2">
                {{ isEditIndex ? 'Save Changes' : 'Create Index' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Modal for Indexes List -->
    <div v-if="showIndexesListModal" class="modal" style="background-color: rgba(0,0,0,0.5);">
      <div class="modal-dialog modal-lg" style="max-width: 800px; width: 800px;">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Indexes List</h5>
            <button type="button" class="btn-close" @click="closeIndexesListModal"></button>
          </div>
          <div class="modal-body">
            <table class="table table-bordered">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Index Name</th>
                  <th>Lang</th>
                  <th>Created Time</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="index in indexesList" :key="index.id">
                  <td>{{ index.id }}</td>
                  <td>{{ index.indexName }}</td>
                  <td>{{ index.language }}</td>
                  <td>{{ formatTime(index.createdTime) }}</td>
                  <td>
                    <button class="btn btn-sm btn-warning me-2" @click="editIndex(index)">Edit</button>
                    <button class="btn btn-sm btn-danger" @click="deleteIndex(index)">Delete</button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div class="d-flex align-items-center mt-3">
              <button @click="closeIndexesListModal" type="button" class="btn btn-secondary me-2">Close</button>
            </div>
          </div>
        </div>
      </div>
    </div>

  </div>
</template>

<script lang="ts">
import { defineComponent, ref, computed, onMounted } from 'vue';
import axios from 'axios';

interface IndexTask {
  taskId: string;
  indexId: string;
  parserInput: { contentType: string };
  status: string;
  createdTime: number;
  completedTime: number;
  indexingTime: number;
  errorMessage: string;
}

interface Index {
  id: string;
  indexName: string;
  description: string;
  language: string;
  createdTime: number;
}

export default defineComponent({
  name: 'IndexesView',
  setup() {
    const indexes = ref<IndexTask[]>([]);
    const indexesList = ref<Index[]>([]);
    const showAddIndexModal = ref(false);
    const showIndexesListModal = ref(false);
    const isEditIndex = ref(false);
    const indexForm = ref<Index>({ id: '', indexName: '', description: '', language: 'general', createdTime: 0 });
    
    const fetchIndexes = () => {
      const creds = localStorage.getItem('credentials');
      axios.get('/data/v1.0/admin/index/indexed/list', {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
      }).then(res => {
        indexes.value = res.data;
        indexes.value.forEach(task => {
          task.indexingTime = task.completedTime - task.createdTime;
          if (task.parserInput) {
            task.parserInput = { contentType: task.parserInput.contentType };
          }
        });
      }).catch(err => {
        console.error(err);
        alert('Error fetching index tasks.');
      });
    };

    const fetchIndexesList = () => {
      const creds = localStorage.getItem('credentials');
      axios.get('/data/v1.0/admin/index/list', {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
      }).then(res => {
        indexesList.value = res.data;
      }).catch(err => {
        console.error(err);
        alert('Error fetching indexes.');
      });
    };

    const formatTime = (timestamp: number): string => {
      if (!timestamp) return '';
      return new Date(timestamp).toLocaleString();
    };

    const indexIdToNameMap = computed(() => {
      const map: { [key: string]: string } = {};
      indexesList.value.forEach(index => {
        map[index.id] = index.indexName + ' (' + index.language + ')';
      });
      return map;
    });

    const generateUUID = (): string => {
      return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
      });
    };

    const openCreateIndexModal = () => {
      isEditIndex.value = false;
      indexForm.value = { id: '', indexName: '', description: '', language: 'general', createdTime: Date.now() };
      showAddIndexModal.value = true;
    };

    const closeCreateIndexModal = () => {
      showAddIndexModal.value = false;
    };

    const openIndexesListModal = () => {
      showIndexesListModal.value = true;
    };

    const closeIndexesListModal = () => {
      showIndexesListModal.value = false;
    };

    const submitIndexForm = () => {
      if (!indexForm.value.indexName) {
        alert('Index Name is required.');
        return;
      }
      if (!isEditIndex.value) {
        indexForm.value.id = generateUUID();
        indexForm.value.createdTime = Date.now();
      }
      const creds = localStorage.getItem('credentials');
      axios.post('/data/v1.0/admin/index/', indexForm.value, {
        headers: {
          ...(creds ? { Authorization: 'Basic ' + creds } : {}),
          'Content-Type': 'application/json',
        },
      }).then(() => {
        fetchIndexesList();
        showAddIndexModal.value = false;
      }).catch(err => {
        console.error('Error submitting index:', err);
        alert('Error submitting index.');
      });
    };

    const editIndex = (index: Index) => {
      isEditIndex.value = true;
      indexForm.value = { ...index };
      showAddIndexModal.value = true;
      showIndexesListModal.value = false;
    };

    const deleteIndex = (index: Index) => {
      if (confirm(`Are you sure you want to delete index "${index.indexName}"?`)) {
        const creds = localStorage.getItem('credentials');
        axios.delete(`/data/v1.0/admin/index/${index.id}`, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        }).then(() => {
          fetchIndexesList();
        }).catch(err => {
          console.error('Error deleting index:', err);
          alert('Error deleting index.');
        });
      }
    };

    onMounted(() => {
      fetchIndexes();
      fetchIndexesList();
    });

    return {
      indexes,
      indexesList,
      showAddIndexModal,
      showIndexesListModal,
      isEditIndex,
      indexForm,
      formatTime,
      indexIdToNameMap,
      openCreateIndexModal,
      closeCreateIndexModal,
      openIndexesListModal,
      closeIndexesListModal,
      submitIndexForm,
      editIndex,
      deleteIndex,
    };
  },
});
</script>

<style>
</style>
