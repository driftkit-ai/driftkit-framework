<template>
  <div class="container mt-4">
    <h2>Checklists</h2>
    
    <!-- Search Form -->
    <div class="card mb-4">
      <div class="card-body">
        <h5 class="card-title">Search Checklists</h5>
        <div class="row">
          <div class="col-md-4">
            <div class="mb-3">
              <label for="promptId" class="form-label">Prompt ID</label>
              <select class="form-select" id="promptId" v-model="searchForm.promptId">
                <option value="">Any prompt</option>
                <option v-for="promptId in promptIds" :key="promptId" :value="promptId">{{ promptId }}</option>
              </select>
            </div>
          </div>
          <div class="col-md-4">
            <div class="mb-3">
              <label for="query" class="form-label">Query</label>
              <input type="text" class="form-control" id="query" v-model="searchForm.query" placeholder="Search by query...">
            </div>
          </div>
          <div class="col-md-4">
            <div class="mb-3">
              <label for="description" class="form-label">Description</label>
              <input type="text" class="form-control" id="description" v-model="searchForm.description" placeholder="Search in description...">
            </div>
          </div>
        </div>
        <div class="mb-3 form-check">
          <input type="checkbox" class="form-check-input" id="includeSimilar" v-model="searchForm.includeSimilar">
          <label class="form-check-label" for="includeSimilar">Include similar items</label>
        </div>
        <div class="d-flex">
          <button type="button" class="btn btn-primary" @click="searchChecklists">Search</button>
          <button type="button" class="btn btn-secondary ms-2" @click="resetSearch">Reset</button>
        </div>
      </div>
    </div>
    
    <!-- Results -->
    <div class="card">
      <div class="card-body">
        <h5 class="card-title">Results <span v-if="checklist.length" class="text-muted">({{ checklist.length }} items)</span></h5>
        
        <div v-if="loading" class="text-center my-4">
          <div class="spinner-border" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
        
        <div v-else-if="checklist.length === 0" class="alert alert-info">
          No checklist items found. Try adjusting your search criteria.
        </div>
        
        <div v-else class="table-responsive">
          <table class="table table-striped table-hover">
            <thead>
              <tr>
                <th>Description</th>
                <th>Severity</th>
                <th>Query</th>
                <th>Prompt ID</th>
                <th>Created</th>
                <th>Use Count</th>
                <th>Similar To</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in checklist" :key="item.id">
                <td>{{ item.description }}</td>
                <td>
                  <span :class="getSeverityClass(item.severity)">
                    {{ item.severity }}
                  </span>
                </td>
                <td>{{ truncateText(item.query, 50) }}</td>
                <td>{{ item.promptId }}</td>
                <td>{{ formatDate(item.createdAt) }}</td>
                <td>{{ item.useCount }}</td>
                <td>
                  <span v-if="item.similarToId" class="badge bg-info">
                    {{ truncateText(item.similarToId, 8) }}
                  </span>
                  <span v-else>-</span>
                </td>
                <td class="text-end">
                  <button class="btn btn-sm btn-outline-primary me-1" @click="editItem(item)">
                    <i class="bi bi-pencil"></i>
                  </button>
                  <button class="btn btn-sm btn-outline-danger" @click="confirmDelete(item)">
                    <i class="bi bi-trash"></i>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
    
    <!-- Edit Modal -->
    <div v-if="showEditModal" class="modal" style="display: block; background-color: rgba(0,0,0,0.5);">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Edit Checklist Item</h5>
            <button type="button" class="btn-close" @click="closeEditModal"></button>
          </div>
          <div class="modal-body">
            <form>
              <div class="mb-3">
                <label for="edit-description" class="form-label">Description</label>
                <textarea class="form-control" id="edit-description" rows="3" v-model="editingItem.description"></textarea>
              </div>
              <div class="mb-3">
                <label for="edit-severity" class="form-label">Severity</label>
                <select class="form-select" id="edit-severity" v-model="editingItem.severity">
                  <option value="CRITICAL">CRITICAL</option>
                  <option value="HIGH">HIGH</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="LOW">LOW</option>
                </select>
              </div>
              <div class="mb-3">
                <label for="edit-promptId" class="form-label">Prompt ID</label>
                <input type="text" class="form-control" id="edit-promptId" v-model="editingItem.promptId">
              </div>
              <div class="mb-3">
                <label for="edit-query" class="form-label">Query</label>
                <textarea class="form-control" id="edit-query" rows="3" v-model="editingItem.query"></textarea>
              </div>
              <div class="mb-3">
                <label for="edit-useCount" class="form-label">Use Count</label>
                <input type="number" class="form-control" id="edit-useCount" v-model="editingItem.useCount" min="0">
              </div>
            </form>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeEditModal">Cancel</button>
            <button type="button" class="btn btn-primary" @click="saveItem">Save Changes</button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Delete Confirmation Modal -->
    <div v-if="showDeleteModal" class="modal" style="display: block; background-color: rgba(0,0,0,0.5);">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Confirm Delete</h5>
            <button type="button" class="btn-close" @click="closeDeleteModal"></button>
          </div>
          <div class="modal-body">
            <p>Are you sure you want to delete this checklist item?</p>
            <p><strong>Description:</strong> {{ deleteItem?.description }}</p>
            <p><strong>Severity:</strong> {{ deleteItem?.severity }}</p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeDeleteModal">Cancel</button>
            <button type="button" class="btn btn-danger" @click="deleteChecklistItem">Delete</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, onMounted } from 'vue';
import axios from 'axios';

interface ChecklistItem {
  id: string;
  description: string;
  severity: string;
  promptId: string;
  query: string;
  workflowType: string;
  createdAt: string;
  useCount: number;
  similarToId: string | null;
  similarityScore: number | null;
}

interface SearchForm {
  promptId: string;
  query: string;
  description: string;
  includeSimilar: boolean;
}

export default defineComponent({
  name: 'ChecklistsView',
  setup() {
    const checklist = ref<ChecklistItem[]>([]);
    const promptIds = ref<string[]>([]);
    const loading = ref(false);
    const searchForm = ref<SearchForm>({
      promptId: '',
      query: '',
      description: '',
      includeSimilar: false,
    });
    
    // Edit modal state
    const showEditModal = ref(false);
    const editingItem = ref<ChecklistItem | null>(null);
    
    // Delete modal state
    const showDeleteModal = ref(false);
    const deleteItem = ref<ChecklistItem | null>(null);
    
    const loadPromptIds = async () => {
      try {
        const creds = localStorage.getItem('credentials');
        const response = await axios.get('/data/v1.0/admin/llm/checklists/prompt-ids', {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });
        
        if (response.data.success) {
          promptIds.value = response.data.data;
        }
      } catch (error) {
        console.error('Error loading prompt IDs:', error);
      }
    };
    
    const searchChecklists = async () => {
      loading.value = true;
      
      try {
        const creds = localStorage.getItem('credentials');
        const response = await axios.get('/data/v1.0/admin/llm/checklists/search', {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) },
          params: {
            promptId: searchForm.value.promptId || null,
            query: searchForm.value.query || null,
            description: searchForm.value.description || null,
            includeSimilar: searchForm.value.includeSimilar,
          }
        });
        
        if (response.data.success) {
          checklist.value = response.data.data;
        }
      } catch (error) {
        console.error('Error searching checklists:', error);
      } finally {
        loading.value = false;
      }
    };
    
    const resetSearch = () => {
      searchForm.value = {
        promptId: '',
        query: '',
        description: '',
        includeSimilar: false,
      };
      checklist.value = [];
    };
    
    // Edit modal methods
    const editItem = (item: ChecklistItem) => {
      // Create a deep copy of the item to edit
      editingItem.value = JSON.parse(JSON.stringify(item));
      showEditModal.value = true;
    };
    
    const closeEditModal = () => {
      showEditModal.value = false;
      editingItem.value = null;
    };
    
    const saveItem = async () => {
      if (!editingItem.value) return;
      
      try {
        const creds = localStorage.getItem('credentials');
        const response = await axios.post(`/data/v1.0/admin/llm/checklists/${editingItem.value.id}`, 
          editingItem.value, 
          {
            headers: { 
              ...(creds ? { Authorization: 'Basic ' + creds } : {}),
              'Content-Type': 'application/json'
            }
          }
        );
        
        if (response.data.success) {
          // Update the item in the list
          const index = checklist.value.findIndex(item => item.id === editingItem.value?.id);
          if (index !== -1) {
            checklist.value[index] = response.data.data;
          }
          
          // Close the modal
          closeEditModal();
          
          // Show success message
          alert('Checklist item updated successfully');
        }
      } catch (error) {
        console.error('Error updating checklist item:', error);
        alert('Error updating checklist item');
      }
    };
    
    // Delete modal methods
    const confirmDelete = (item: ChecklistItem) => {
      deleteItem.value = item;
      showDeleteModal.value = true;
    };
    
    const closeDeleteModal = () => {
      showDeleteModal.value = false;
      deleteItem.value = null;
    };
    
    const deleteChecklistItem = async () => {
      if (!deleteItem.value) return;
      
      try {
        const creds = localStorage.getItem('credentials');
        const response = await axios.delete(`/data/v1.0/admin/llm/checklists/${deleteItem.value.id}`, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });
        
        if (response.data.success) {
          // Remove the item from the list
          checklist.value = checklist.value.filter(item => item.id !== deleteItem.value?.id);
          
          // Close the modal
          closeDeleteModal();
          
          // Show success message
          alert('Checklist item deleted successfully');
        }
      } catch (error) {
        console.error('Error deleting checklist item:', error);
        alert('Error deleting checklist item');
      }
    };
    
    const getSeverityClass = (severity: string): string => {
      switch (severity.toUpperCase()) {
        case 'CRITICAL': return 'badge bg-danger';
        case 'HIGH': return 'badge bg-warning text-dark';
        case 'MEDIUM': return 'badge bg-info text-dark';
        case 'LOW': return 'badge bg-secondary';
        default: return 'badge bg-secondary';
      }
    };
    
    const formatDate = (dateStr: string): string => {
      if (!dateStr) return '';
      try {
        const date = new Date(dateStr);
        return date.toLocaleString();
      } catch (e) {
        return dateStr;
      }
    };
    
    const truncateText = (text: string, maxLength: number): string => {
      if (!text) return '';
      if (text.length <= maxLength) return text;
      return text.substring(0, maxLength) + '...';
    };
    
    onMounted(() => {
      loadPromptIds();
    });
    
    return {
      checklist,
      promptIds,
      loading,
      searchForm,
      searchChecklists,
      resetSearch,
      getSeverityClass,
      formatDate,
      truncateText,
      
      // Edit modal methods
      showEditModal,
      editingItem,
      editItem,
      closeEditModal,
      saveItem,
      
      // Delete modal methods
      showDeleteModal,
      deleteItem,
      confirmDelete,
      closeDeleteModal,
      deleteChecklistItem
    };
  }
});
</script>

<style scoped>
.badge {
  font-size: 0.8rem;
}
</style>