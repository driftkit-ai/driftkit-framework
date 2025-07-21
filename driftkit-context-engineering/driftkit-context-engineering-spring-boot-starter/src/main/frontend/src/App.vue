<template>
  <div id="app" class="container mt-4">
    <div v-if="!authenticated">
      <!-- Login Form -->
      <div class="row justify-content-center">
        <div class="col-md-4">
          <h2 class="mb-4">Login</h2>
          <div class="mb-3">
            <input v-model="username" placeholder="Username" class="form-control" />
          </div>
          <div class="mb-3">
            <input v-model="password" type="password" placeholder="Password" class="form-control" />
          </div>
          <button @click="login" class="btn btn-primary w-100">Login</button>
        </div>
      </div>
    </div>
    <div v-else>
      <!-- Header with Navigation Buttons -->
      <div class="d-flex justify-content-between align-items-center mb-4">
        <h2>Prompt Engineering App</h2>
        <div>
          <button @click="openProcessInputModal" class="btn btn-info me-2">Process Input</button>
          <button @click="openIndexDocumentModal" class="btn btn-success me-2">Index Document</button>
          <button @click="logout" class="btn btn-secondary">Logout</button>
        </div>
      </div>
      <!-- Tab Navigation (only Chat and Dictionaries remain as pages) -->
      <ul class="nav nav-tabs mb-4">
        <li class="nav-item">
          <router-link to="/chat" class="nav-link" active-class="active">Chat</router-link>
        </li>
        <li class="nav-item">
          <router-link to="/prompts" class="nav-link" active-class="active">Prompts</router-link>
        </li>
        <li class="nav-item">
          <router-link to="/indexes" class="nav-link" active-class="active">Indexes</router-link>
        </li>
        <li class="nav-item">
          <router-link to="/dictionaries" class="nav-link" active-class="active">Dictionaries</router-link>
        </li>
        <li class="nav-item">
          <router-link to="/checklists" class="nav-link" active-class="active">Checklists</router-link>
        </li>
      </ul>
      <!-- Routed Page -->
      <router-view />
      <!-- Global Modal: Parse Input -->
      <div v-if="showInputModal" class="modal" style="background-color: rgba(0,0,0,0.5);">
        <div class="modal-dialog modal-lg" style="max-width: 700px; width: 700px;">
          <div class="modal-content">
            <div class="modal-header">
              <h5 class="modal-title">Parse input</h5>
              <button type="button" class="btn-close" @click="closeInputModal"></button>
            </div>
            <div class="modal-body">
              <!-- Tabs for modes -->
              <ul class="nav nav-tabs">
                <li class="nav-item">
                  <a class="nav-link" :class="{ active: selectedMode === 'file' }" href="javascript:void(0)" @click="selectedMode = 'file'">File</a>
                </li>
                <li class="nav-item">
                  <a class="nav-link" :class="{ active: selectedMode === 'youtube' }" href="javascript:void(0)" @click="selectedMode = 'youtube'">YouTube</a>
                </li>
              </ul>
              <div class="mt-3">
                <div v-if="selectedMode === 'file'">
                  <h5>File Input</h5>
                  <input type="file" ref="fileInput" class="form-control" />
                  <div class="mb-3">
                    <label class="form-label">Metadata:</label>
                    <input type="checkbox" v-model="metadata" class="form-check-input" />
                  </div>
                </div>
                <div v-else-if="selectedMode === 'youtube'">
                  <h5>YouTube Input</h5>
                  <div class="mb-3">
                    <label class="form-label">Video ID:</label>
                    <input v-model="youtubeVideoId" class="form-control" />
                  </div>
                  <div class="mb-3">
                    <label class="form-label">Languages:</label>
                    <input v-model="youtubeInputLang" placeholder="e.g. es" class="form-control" />
                  </div>
                  <div class="mb-3">
                    <label class="form-label">Metadata:</label>
                    <input type="checkbox" v-model="metadata" class="form-check-input" />
                  </div>
                </div>
              </div>
              <div class="d-flex align-items-center mt-3">
                <button @click="closeInputModal" type="button" class="btn btn-secondary me-2">Close</button>
                <button @click="processInput" type="button" class="btn btn-primary me-2">Process</button>
                <button v-if="parseResponse" @click="copyOutput" type="button" class="btn btn-outline-primary">Copy Output</button>
              </div>
              <div v-if="parseResponse" class="mt-4">
                <h5>Parse Result:</h5>
                <pre style="white-space: pre-wrap; word-wrap: break-word;">{{ formattedParseResponse }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>
      <!-- Global Modal: Index Document -->
      <div v-if="showIndexModal" class="modal" style="background-color: rgba(0,0,0,0.5);">
        <div class="modal-dialog modal-lg" style="max-width: 700px; width: 700px;">
          <div class="modal-content">
            <div class="modal-header">
              <h5 class="modal-title">Index document</h5>
              <button type="button" class="btn-close" @click="closeIndexModal"></button>
            </div>
            <div class="modal-body">
              <!-- Tabs for modes -->
              <ul class="nav nav-tabs">
                <li class="nav-item">
                  <a class="nav-link" :class="{ active: selectedIndexMode === 'file' }" href="javascript:void(0)" @click="selectedIndexMode = 'file'">File</a>
                </li>
                <li class="nav-item">
                  <a class="nav-link" :class="{ active: selectedIndexMode === 'text' }" href="javascript:void(0)" @click="selectedIndexMode = 'text'">Text</a>
                </li>
                <li class="nav-item">
                  <a class="nav-link" :class="{ active: selectedIndexMode === 'youtube' }" href="javascript:void(0)" @click="selectedIndexMode = 'youtube'">YouTube</a>
                </li>
              </ul>
              <div class="mt-3">
                <div v-if="selectedIndexMode === 'file'">
                  <h5>File Input</h5>
                  <input type="file" ref="indexFileInput" class="form-control" />
                </div>
                <div v-else-if="selectedIndexMode === 'text'">
                  <h5>Text Input</h5>
                  <textarea v-model="indexTextInput" class="form-control" rows="5"></textarea>
                </div>
                <div v-else-if="selectedIndexMode === 'youtube'">
                  <h5>YouTube Input</h5>
                  <div class="mb-3">
                    <label class="form-label">Video ID:</label>
                    <input v-model="indexYoutubeVideoId" class="form-control" />
                  </div>
                  <div class="mb-3">
                    <label class="form-label">Primary Language:</label>
                    <input v-model="indexYoutubePrimaryLang" placeholder="e.g. en" class="form-control" />
                  </div>
                  <div class="mb-3">
                    <label class="form-label">Extra Languages:</label>
                    <input v-model="indexYoutubeExtraLangs" placeholder="e.g. es,fr" class="form-control" />
                  </div>
                </div>
              </div>
              <div class="mt-3">
                <label class="form-label">Index:</label>
                <select v-model="selectedIndexId" class="form-control">
                  <option value="" disabled>Select an index</option>
                  <option v-for="index in indexesList" :key="index.id" :value="index.id">{{ index.indexName }}</option>
                </select>
              </div>
              <div class="d-flex align-items-center mt-3">
                <button @click="closeIndexModal" type="button" class="btn btn-secondary me-2">Close</button>
                <button @click="submitIndex" type="button" class="btn btn-primary me-2">Index</button>
                <button v-if="indexResponse" @click="copyIndexOutput" type="button" class="btn btn-outline-primary">Copy Output</button>
              </div>
              <div v-if="indexResponse" class="mt-4">
                <h5>Index Response:</h5>
                <pre style="white-space: pre-wrap; word-wrap: break-word;">{{ formattedIndexResponse }}</pre>
              </div>
              <div v-if="indexTaskStatus && indexTaskId" class="mt-4">
                <h5>Task Status (Task ID: {{ indexTaskId }})</h5>
                <p>Status: {{ indexTaskStatus.status }}</p>
                <p>Content type: {{ indexTaskStatus.metadata?.contentType }}</p>
                <p>Total chunks: {{ indexTaskStatus.metadata?.totalChunks }}</p>
                <p>Chunks processed: {{ indexTaskStatus.metadata?.totalChunksProcessed }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';

export default defineComponent({
  name: 'App',
  setup() {
    const router = useRouter();
    const username = ref('');
    const password = ref('');
    const authenticated = ref(false);

    // Global state for Parse Input modal
    const showInputModal = ref(false);
    const selectedMode = ref<'file' | 'youtube'>('file');
    const fileInput = ref<HTMLInputElement | null>(null);
    const metadata = ref('');
    const youtubeVideoId = ref('');
    const youtubeInputLang = ref('en');
    const parseResponse = ref<any>(null);

    // Global state for Index Document modal
    const showIndexModal = ref(false);
    const selectedIndexMode = ref<'file' | 'text' | 'youtube'>('file');
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

    const formattedParseResponse = computed(() =>
      parseResponse.value ? JSON.stringify(parseResponse.value, null, 2) : ''
    );
    const formattedIndexResponse = computed(() =>
      indexResponse.value ? JSON.stringify(indexResponse.value, null, 2) : ''
    );

    const login = () => {
      const creds = btoa(`${username.value}:${password.value}`);
      localStorage.setItem('credentials', creds);
      authenticated.value = true;
      router.push('/chat');
      fetchIndexesList();
    };

    const logout = () => {
      localStorage.removeItem('credentials');
      authenticated.value = false;
      router.push('/');
    };

    const closeInputModal = () => {
      showInputModal.value = false;
      parseResponse.value = null;
      if (fileInput.value) {
        fileInput.value.value = '';
      }
    };

    const processInput = () => {
      if (selectedMode.value === 'file') {
        parseFile();
      } else if (selectedMode.value === 'youtube') {
        parseYoutube();
      }
    };

    const parseFile = () => {
      if (!fileInput.value?.files?.length) {
        alert('No file selected');
        return;
      }
      const formData = new FormData();
      formData.append('file', fileInput.value.files[0]);
      formData.append('metadata', metadata.value);
      const creds = localStorage.getItem('credentials');
      axios
        .post('/data/v1.0/admin/parse/file', formData, {
          headers: {
            ...(creds ? { Authorization: 'Basic ' + creds } : {}),
            'Content-Type': 'multipart/form-data',
          },
        })
        .then((res) => {
          const data = res.data;
          delete data.input;
          parseResponse.value = data;
        })
        .catch((err) => {
          console.error('Error parsing file:', err);
          parseResponse.value = { error: 'Error parsing file.' };
        });
    };

    const parseYoutube = () => {
      const data = {
        videoId: youtubeVideoId.value,
        metadata: metadata.value,
        languages: youtubeInputLang.value ? [youtubeInputLang.value] : [],
      };
      const creds = localStorage.getItem('credentials');
      axios
        .post('/data/v1.0/admin/parse/youtube', data, {
          headers: {
            ...(creds ? { Authorization: 'Basic ' + creds } : {}),
            'Content-Type': 'application/json',
          },
        })
        .then((res) => {
          parseResponse.value = res.data;
        })
        .catch((err) => {
          console.error('Error parsing YouTube video:', err);
          parseResponse.value = { error: 'Error parsing YouTube video.' };
        });
    };

    const copyOutput = () => {
      if (!parseResponse.value) return;
      navigator.clipboard
        .writeText(JSON.stringify(parseResponse.value, null, 2))
        .then(() => alert('Output copied to clipboard!'))
        .catch((err) => {
          console.error('Error copying output:', err);
          alert('Failed to copy output.');
        });
    };

    const closeIndexModal = () => {
      showIndexModal.value = false;
      indexResponse.value = null;
      indexTaskId.value = null;
      indexTaskStatus.value = null;
      if (indexFileInput.value) {
        indexFileInput.value.value = '';
      }
      indexTextInput.value = '';
      indexYoutubeVideoId.value = '';
      indexYoutubePrimaryLang.value = 'en';
      indexYoutubeExtraLangs.value = '';
      selectedIndexId.value = '';
    };

    const submitIndex = () => {
      if (!selectedIndexId.value) {
        alert('Please select an index.');
        return;
      }
      const creds = localStorage.getItem('credentials');
      const headers = { ...(creds ? { Authorization: 'Basic ' + creds } : {}) };
      const url = '/data/v1.0/admin/index/submit';
      if (selectedIndexMode.value === 'file') {
        if (!indexFileInput.value?.files?.length) {
          alert('No file selected');
          return;
        }
        const formData = new FormData();
        formData.append('file', indexFileInput.value.files[0]);
        formData.append('index', selectedIndexId.value);
        axios
          .post(url, formData, {
            headers: { ...headers, 'Content-Type': 'multipart/form-data' },
          })
          .then((res) => {
            indexResponse.value = res.data;
            if (res.data && res.data.taskId) {
              indexTaskId.value = res.data.taskId;
            }
          })
          .catch((err) => {
            console.error('Error indexing file:', err);
            indexResponse.value = { error: 'Error indexing file.' };
          });
      } else if (selectedIndexMode.value === 'text' || selectedIndexMode.value === 'youtube') {
        const data: any = { index: selectedIndexId.value };
        if (selectedIndexMode.value === 'text') {
          if (!indexTextInput.value) {
            alert('Please provide text input');
            return;
          }
          data.text = indexTextInput.value;
        } else if (selectedIndexMode.value === 'youtube') {
          if (!indexYoutubeVideoId.value) {
            alert('Please provide a YouTube Video ID.');
            return;
          }
          const extraLangs = indexYoutubeExtraLangs.value ? indexYoutubeExtraLangs.value.split(',') : [];
          data.videoId = indexYoutubeVideoId.value;
          data.primaryLang = indexYoutubePrimaryLang.value;
          data.input = extraLangs;
        }
        axios
          .post(url, data, { headers })
          .then((res) => {
            indexResponse.value = res.data;
            if (res.data && res.data.taskId) {
              indexTaskId.value = res.data.taskId;
            }
          })
          .catch((err) => {
            console.error('Error indexing:', err);
            indexResponse.value = { error: 'Error indexing.' };
          });
      }
    };

    const copyIndexOutput = () => {
      if (!indexResponse.value) return;
      navigator.clipboard
        .writeText(JSON.stringify(indexResponse.value, null, 2))
        .then(() => alert('Output copied to clipboard!'))
        .catch((err) => {
          console.error('Error copying output:', err);
          alert('Failed to copy output.');
        });
    };

    const fetchIndexesList = () => {
      const creds = localStorage.getItem('credentials');
      axios
        .get('/data/v1.0/admin/index/list', {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) },
        })
        .then((res) => {
          indexesList.value = res.data;
        })
        .catch((err) => {
          console.error(err);
          alert('Error fetching indexes.');
        });
    };

    const openProcessInputModal = () => {
      showInputModal.value = true;
    };

    const openIndexDocumentModal = () => {
      showIndexModal.value = true;
    };

    onMounted(() => {
      const creds = localStorage.getItem('credentials');
      if (creds) {
        authenticated.value = true;
        fetchIndexesList();
      }
    });

    return {
      username,
      password,
      authenticated,
      login,
      logout,
      showInputModal,
      selectedMode,
      fileInput,
      metadata,
      youtubeVideoId,
      youtubeInputLang,
      parseResponse,
      formattedParseResponse,
      closeInputModal,
      processInput,
      copyOutput,
      showIndexModal,
      selectedIndexMode,
      indexFileInput,
      indexTextInput,
      indexYoutubeVideoId,
      indexYoutubePrimaryLang,
      indexYoutubeExtraLangs,
      selectedIndexId,
      indexResponse,
      formattedIndexResponse,
      indexTaskId,
      indexTaskStatus,
      indexesList,
      closeIndexModal,
      submitIndex,
      copyIndexOutput,
      openProcessInputModal,
      openIndexDocumentModal
    };
  },
});
</script>

<style>
.modal {
  display: block;
}
.modal .modal-dialog {
  margin-top: 10%;
}
</style>
