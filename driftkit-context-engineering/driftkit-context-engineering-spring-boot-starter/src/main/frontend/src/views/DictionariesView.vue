<template>
  <div class="mt-4">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h3>Dictionaries</h3>
      <div>
        <button @click="openCreateDictionaryModal" class="btn btn-primary me-2">Create New Dictionary</button>
        <button @click="openDictionariesGroupModal" class="btn btn-secondary">Groups List</button>
      </div>
    </div>
    <table class="table table-bordered">
      <thead>
        <tr>
          <th>ID</th>
          <th>Name</th>
          <th>Group</th>
          <th>Markers</th>
          <th>Samples</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in dictionaries" :key="item.id">
          <td>{{ item.id }}</td>
          <td>{{ item.name }}</td>
          <td>{{ groupIdToNameMap[item.groupId] || item.groupId }}</td>
          <td>{{ item.markers ? item.markers.join(', ') : '' }}</td>
          <td>{{ item.samples ? item.samples.join(', ') : '' }}</td>
          <td>
            <button class="btn btn-sm btn-warning me-2" @click="openEditDictionaryModal(item)">Edit</button>
          </td>
        </tr>
      </tbody>
    </table>

    <!-- Modal for Dictionaries Groups List -->
    <div v-if="showDictionariesGroupModal" class="modal" style="background-color: rgba(0,0,0,0.5);">
      <div class="modal-dialog modal-lg" style="max-width: 800px; width: 800px;">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Dictionary Groups List</h5>
            <button type="button" class="btn-close" @click="closeDictionariesGroupModal"></button>
          </div>
          <div class="modal-body">
            <div class="d-flex justify-content-end mb-3">
              <button @click="openCreateDictionaryGroupModal" class="btn btn-primary">New Group</button>
            </div>
            <table class="table table-bordered">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Name</th>
                  <th>Language</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="group in dictionaryGroups" :key="group.id">
                  <td>{{ group.id }}</td>
                  <td>{{ group.name }}</td>
                  <td>{{ group.language }}</td>
                  <td>
                    <button class="btn btn-sm btn-warning me-2" @click="openEditDictionaryGroupModal(group)">Edit</button>
                    <button class="btn btn-sm btn-danger" @click="deleteDictionaryGroup(group)">Delete</button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div class="d-flex align-items-center mt-3">
              <button @click="closeDictionariesGroupModal" type="button" class="btn btn-secondary me-2">Close</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Modal for Create/Edit Dictionary Group -->
    <div v-if="showAddDictionaryGroupModal" class="modal" style="background-color: rgba(0,0,0,0.5);">
      <div class="modal-dialog" style="max-width: 600px; width: 600px;">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{ isEditDictionaryGroup ? 'Edit' : 'Create' }} Dictionary Group</h5>
            <button type="button" class="btn-close" @click="closeAddDictionaryGroupModal"></button>
          </div>
          <div class="modal-body">
            <div class="mb-3">
              <label class="form-label">ID:</label>
              <input v-model="dictionaryGroupForm.id" class="form-control" :readonly="isEditDictionaryGroup" />
            </div>
            <div class="mb-3">
              <label class="form-label">Name:</label>
              <input v-model="dictionaryGroupForm.name" class="form-control" />
            </div>
            <div class="mt-3">
              <h3>Language</h3>
              <select v-model="dictionaryGroupForm.language" class="form-control">
                <option value="general">Select a language</option>
                <option v-for="lang in ['general', 'spanish', 'english']" :key="lang" :value="lang">{{ lang }}</option>
              </select>
            </div>
            <div class="d-flex align-items-center mt-3">
              <button @click="closeAddDictionaryGroupModal" type="button" class="btn btn-secondary me-2">Close</button>
              <button @click="submitDictionaryGroupForm" type="button" class="btn btn-primary me-2">
                {{ isEditDictionaryGroup ? 'Save Changes' : 'Create Group' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Modal for Create/Edit Dictionary -->
    <div v-if="showDictionaryModal" class="modal" style="background-color: rgba(0,0,0,0.5);">
      <div class="modal-dialog" style="max-width: 600px; width: 600px;">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{ isEditDictionary ? 'Edit' : 'Create' }} Dictionary</h5>
            <button type="button" class="btn-close" @click="closeDictionaryModal"></button>
          </div>
          <div class="modal-body">
            <div class="mb-3">
              <label class="form-label">ID:</label>
              <input v-model="dictionaryForm.id" class="form-control" :readonly="isEditDictionary" />
            </div>
            <div class="mb-3">
              <label class="form-label">Name:</label>
              <input v-model="dictionaryForm.name" class="form-control" />
            </div>
            <div class="mb-3">
              <label class="form-label">Index:</label>
              <input v-model="dictionaryForm.index" class="form-control" />
            </div>
            <div class="mb-3">
              <label class="form-label">Group:</label>
              <select v-model="dictionaryForm.groupId" class="form-control">
                <option value="" disabled>Select a group</option>
                <option v-for="group in dictionaryGroups" :key="group.id" :value="group.id">{{ group.name }}</option>
              </select>
            </div>
            <div class="mb-3">
              <label class="form-label">Markers (comma or newline separated):</label>
              <textarea v-model="dictionaryForm.markers" class="form-control" rows="3"></textarea>
            </div>
            <div class="mb-3">
              <label class="form-label">Examples (comma or newline separated):</label>
              <textarea v-model="dictionaryForm.examples" class="form-control" rows="3"></textarea>
            </div>
            <div class="d-flex align-items-center mt-3">
              <button @click="closeDictionaryModal" type="button" class="btn btn-secondary me-2">Close</button>
              <button @click="submitDictionary" type="button" class="btn btn-primary me-2">
                {{ isEditDictionary ? 'Save Changes' : 'Create Dictionary' }}
              </button>
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

interface DictionaryItem {
  id: string;
  index: number;
  name: string;
  groupId: string;
  markers: string[];
  samples: string[];
}

interface DictionaryGroup {
  id: string;
  name: string;
  language: string;
}

interface DictionaryTextData {
  id: string;
  index: number;
  name: string;
  groupId: string;
  markers: string;
  examples: string;
}

export default defineComponent({
  name: 'DictionariesView',
  setup() {
    const dictionaries = ref<DictionaryItem[]>([]);
    const dictionaryGroups = ref<DictionaryGroup[]>([]);
    const groupIdToNameMap = computed(() => {
      const map: { [key: string]: string } = {};
      dictionaryGroups.value.forEach(group => {
        map[group.id] = group.name;
      });
      return map;
    });

    const showDictionaryModal = ref(false);
    const isEditDictionary = ref(false);
    const dictionaryForm = ref<DictionaryTextData>({ id: '', index: 0, name: '', groupId: '', markers: '', examples: '' });

    const openCreateDictionaryModal = () => {
      isEditDictionary.value = false;
      dictionaryForm.value = { id: '', index: 0, name: '', groupId: '', markers: '', examples: '' };
      showDictionaryModal.value = true;
    };

    const openEditDictionaryModal = (item: DictionaryItem) => {
      isEditDictionary.value = true;
      dictionaryForm.value = {
        id: item.id,
        index: item.index,
        name: item.name,
        groupId: item.groupId,
        markers: item.markers ? item.markers.join(', ') : '',
        examples: item.samples ? item.samples.join(', ') : '',
      };
      showDictionaryModal.value = true;
    };

    const closeDictionaryModal = () => {
      showDictionaryModal.value = false;
    };

    const submitDictionary = () => {
      if (!dictionaryForm.value.id || !dictionaryForm.value.name) {
        alert('ID and Name are required.');
        return;
      }
      const creds = localStorage.getItem('credentials');
      axios.post('/data/v1.0/admin/dictionary/text', dictionaryForm.value, {
        headers: {
          ...(creds ? { Authorization: 'Basic ' + creds } : {}),
          'Content-Type': 'application/json',
        },
      }).then(() => {
        fetchDictionaries();
        showDictionaryModal.value = false;
      }).catch(err => {
        console.error('Error submitting dictionary:', err);
        alert('Error submitting dictionary.');
      });
    };

    const fetchDictionaries = () => {
      const creds = localStorage.getItem('credentials');
      axios.get('/data/v1.0/admin/dictionary/', {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) },
      }).then(res => {
        dictionaries.value = res.data.data;
      }).catch(err => {
        console.error(err);
        alert('Error fetching dictionaries.');
      });
    };

    const fetchDictionaryGroups = () => {
      const creds = localStorage.getItem('credentials');
      axios.get('/data/v1.0/admin/dictionary/group/', {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) },
      }).then(res => {
        dictionaryGroups.value = res.data.data || res.data;
      }).catch(err => {
        console.error(err);
        alert('Error fetching dictionary groups.');
      });
    };

    const showDictionariesGroupModal = ref(false);
    const openDictionariesGroupModal = () => {
      showDictionariesGroupModal.value = true;
    };
    const closeDictionariesGroupModal = () => {
      showDictionariesGroupModal.value = false;
    };

    const showAddDictionaryGroupModal = ref(false);
    const isEditDictionaryGroup = ref(false);
    const dictionaryGroupForm = ref({ id: '', name: '', language: 'general' });

    const openCreateDictionaryGroupModal = () => {
      isEditDictionaryGroup.value = false;
      dictionaryGroupForm.value = { id: '', name: '', language: 'general' };
      showAddDictionaryGroupModal.value = true;
    };

    const openEditDictionaryGroupModal = (group: DictionaryGroup) => {
      isEditDictionaryGroup.value = true;
      dictionaryGroupForm.value = { ...group };
      showAddDictionaryGroupModal.value = true;
      showDictionariesGroupModal.value = false;
    };

    const closeAddDictionaryGroupModal = () => {
      showAddDictionaryGroupModal.value = false;
    };

    const submitDictionaryGroupForm = () => {
      if (!dictionaryGroupForm.value.id || !dictionaryGroupForm.value.name) {
        alert('ID and Name are required.');
        return;
      }
      const creds = localStorage.getItem('credentials');
      axios.post('/data/v1.0/admin/dictionary/group/', dictionaryGroupForm.value, {
        headers: {
          ...(creds ? { Authorization: 'Basic ' + creds } : {}),
          'Content-Type': 'application/json',
        },
      }).then(() => {
        fetchDictionaryGroups();
        showAddDictionaryGroupModal.value = false;
      }).catch(err => {
        console.error('Error submitting dictionary group:', err);
        alert('Error submitting dictionary group.');
      });
    };

    const deleteDictionaryGroup = (group: DictionaryGroup) => {
      if (confirm(`Are you sure you want to delete group "${group.name}"?`)) {
        const creds = localStorage.getItem('credentials');
        axios.delete(`/data/v1.0/admin/dictionary/group/${group.id}`, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) },
        }).then(() => {
          fetchDictionaryGroups();
        }).catch(err => {
          console.error('Error deleting group:', err);
          alert('Error deleting group.');
        });
      }
    };

    onMounted(() => {
      fetchDictionaries();
      fetchDictionaryGroups();
    });

    return {
      dictionaries,
      dictionaryGroups,
      groupIdToNameMap,
      showDictionaryModal,
      isEditDictionary,
      dictionaryForm,
      openCreateDictionaryModal,
      openEditDictionaryModal,
      closeDictionaryModal,
      submitDictionary,
      openDictionariesGroupModal,
      closeDictionariesGroupModal,
      showDictionariesGroupModal,
      showAddDictionaryGroupModal,
      isEditDictionaryGroup,
      dictionaryGroupForm,
      openCreateDictionaryGroupModal,
      openEditDictionaryGroupModal,
      closeAddDictionaryGroupModal,
      submitDictionaryGroupForm,
      deleteDictionaryGroup,
    };
  },
});
</script>

<style>
</style>
