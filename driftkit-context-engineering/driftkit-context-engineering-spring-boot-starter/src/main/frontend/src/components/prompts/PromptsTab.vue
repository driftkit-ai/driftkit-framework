<template>
  <div>
    <!-- Search Input -->
    <div class="mb-4">
      <input type="text" class="form-control" v-model="searchQuery" placeholder="Search by method or prompt message" />
    </div>
    <!-- Prompts Table -->
    <div>
      <h3>
        Prompts
        <template v-if="currentFolder">
          <span class="mx-2">›</span>
          <span>{{ currentFolder }}</span>
          <button class="btn btn-sm btn-outline-secondary ms-2" @click="goToRoot">Back to Root</button>
        </template>
      </h3>
      
      <!-- Bulk Actions -->
      <div class="mb-3" v-if="selectedPrompts.length > 0">
        <button @click="deleteSelectedPrompts" class="btn btn-danger me-2">
          Delete Selected ({{ selectedPrompts.length }})
        </button>
        <button @click="clearSelection" class="btn btn-outline-secondary">
          Clear Selection
        </button>
      </div>
      
      <div v-if="loading" class="my-4 text-center">
        <div class="spinner-border" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
        <div class="mt-2">Loading prompts...</div>
      </div>
      
      <div v-else-if="!prompts.length" class="alert alert-info my-4">
        <p class="mb-0">No prompts found. Create a new prompt using the editor below.</p>
        <div class="mt-2 small text-muted">
          <strong>Debug Info:</strong>
          <ul>
            <li>Loading state: {{ loading }}</li>
            <li>Prompts array type: {{ typeof prompts }}</li>
            <li>Prompts length: {{ prompts.length || 0 }}</li>
            <li>First prompt (if any): {{ prompts.length > 0 ? JSON.stringify(prompts[0]).substring(0, 100) + '...' : 'None' }}</li>
          </ul>
        </div>
      </div>
      
      <div v-else-if="Object.keys(groupedPrompts).length === 0" class="alert alert-info my-4">
        <p class="mb-0">No prompts match the current filter or folder. Try a different search or folder.</p>
        <div class="mt-2 small text-muted">
          <strong>Debug Info:</strong>
          <ul>
            <li>Filtered prompts count: {{ filteredPrompts.length }}</li>
            <li>Current folder: "{{ currentFolder }}"</li>
            <li>Search query: "{{ searchQuery }}"</li>
            <li>First prompt (if any): {{ prompts[0] ? JSON.stringify(prompts[0]).substring(0, 100) + '...' : 'None' }}</li>
          </ul>
        </div>
      </div>
      
      <table v-else class="table table-bordered">
        <thead>
          <tr>
            <th>
              <div class="d-flex align-items-center">
                <input type="checkbox" class="form-check-input me-2" 
                  :checked="isAllSelected" 
                  @change="toggleAllSelection"
                  :disabled="filteredPrompts.length === 0" />
                Select
              </div>
            </th>
            <th>Method</th>
            <th>Language</th>
            <th>State</th>
            <th>Created Time</th>
            <th>Updated Time</th>
            <th>Message Preview</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="(group, method) in groupedPrompts" :key="method">
            <tr 
              :class="{ 'folder-row': group.currentPrompt.state === 'FOLDER', 'prompt-row': group.currentPrompt.state !== 'FOLDER' }" 
              @click="group.currentPrompt.state === 'FOLDER' ? openFolder(method) : selectPrompt(group.currentPrompt.id)">
              <td @click.stop>
                <span v-if="group.currentPrompt.state === 'FOLDER'" class="folder-icon">📁</span>
                <template v-else>
                  <div class="d-flex">
                    <input 
                      class="form-check-input me-2" 
                      type="checkbox"
                      :value="group.currentPrompt.id" 
                      v-model="selectedPrompts"
                      @click.stop
                    />
                    <input 
                      class="form-check-input" 
                      type="radio" 
                      :value="group.currentPrompt.id" 
                      v-model="selectedPromptIdRef"
                      @change="selectPrompt(group.currentPrompt.id)"
                      @click.stop
                    />
                  </div>
                </template>
              </td>
              <td>
                <span v-if="group.currentPrompt.state === 'FOLDER'">
                  <span class="folder-icon me-1">📁</span>{{ method }}
                </span>
                <span v-else>{{ method }}</span>
              </td>
              <td>{{ group.currentPrompt.language }}</td>
              <td>{{ group.currentPrompt.state === 'FOLDER' ? 'Folder' : group.currentPrompt.state }}</td>
              <td>{{ group.currentPrompt.state === 'FOLDER' ? '' : formatTime(group.currentPrompt.createdTime) }}</td>
              <td>{{ group.currentPrompt.state === 'FOLDER' ? '' : formatTime(group.currentPrompt.updatedTime) }}</td>
              <td>{{ group.currentPrompt.state === 'FOLDER' ? '' : (group.currentPrompt.message ? group.currentPrompt.message.substring(0, 100) + '...' : '') }}</td>
              <td>
                <button v-if="group.currentPrompt.state === 'FOLDER'" class="btn btn-primary btn-sm" @click="openFolder(method)">
                  Open Folder
                </button>
                <template v-else>
                  <button class="btn btn-danger btn-sm" @click="deletePrompt(group.currentPrompt)">Delete</button>
                  <button v-if="group.otherPrompts && group.otherPrompts.length > 0" class="btn btn-link btn-sm" @click="toggleMethod(method)">
                    {{ expandedMethods.includes(method) ? 'Hide Versions' : 'Show Versions' }}
                  </button>
                </template>
              </td>
            </tr>
            <template v-if="expandedMethods.includes(method) && group.otherPrompts && group.otherPrompts.length > 0">
              <tr 
                v-for="prompt in group.otherPrompts" 
                :key="prompt.id" 
                class="table-secondary prompt-row"
                @click="selectPrompt(prompt.id)">
                <td @click.stop>
                  <div class="d-flex">
                    <input 
                      class="form-check-input me-2" 
                      type="checkbox"
                      :value="prompt.id" 
                      v-model="selectedPrompts"
                      @click.stop
                    />
                    <input 
                      class="form-check-input" 
                      type="radio" 
                      :value="prompt.id" 
                      v-model="selectedPromptIdRef"
                      @change="selectPrompt(prompt.id)"
                      @click.stop
                    />
                  </div>
                </td>
                <td>{{ method }}</td>
                <td>{{ prompt.language }}</td>
                <td>{{ prompt.state }}</td>
                <td>{{ formatTime(prompt.createdTime) }}</td>
                <td>{{ formatTime(prompt.updatedTime) }}</td>
                <td>{{ prompt.message ? prompt.message.substring(0, 100) + '...' : '' }}</td>
                <td>
                  <button class="btn btn-danger btn-sm" @click="deletePrompt(prompt)">Delete</button>
                </td>
              </tr>
            </template>
          </template>
        </tbody>
      </table>
    </div>
    <!-- Prompt Editor -->
    <div class="mt-4">
      <h3>Prompt Editor</h3>
      <div class="mb-3">
        <label class="form-label">Prompt ID:</label>
        <input v-model="promptForm.promptId" class="form-control" placeholder="Enter prompt identifier" />
        <small class="text-muted">This identifier will be used when saving the prompt.</small>
      </div>
      <div class="mb-3">
        <label class="form-label">System Message:</label>
        <textarea v-model="promptForm.systemMessage" class="form-control" rows="3" placeholder="Enter system message (optional)"></textarea>
      </div>
      
      <div class="mb-3">
        <div class="d-flex justify-content-between">
          <label class="form-label">Prompt Template:</label>
          <div class="form-check form-switch">
            <input class="form-check-input" type="checkbox" id="showPreviewCheck" v-model="showPromptPreview">
            <label class="form-check-label" for="showPreviewCheck">Show preview with highlighted variables</label>
          </div>
        </div>
        
        <!-- Editable textarea -->
        <textarea 
          ref="codeMirrorEditor" 
          v-model="promptForm.message" 
          class="form-control" 
          style="height: 300px; font-family: monospace;"
          :style="{ display: showPromptPreview ? 'none' : 'block' }"
          placeholder="Enter your prompt template here. Use {{variable_name}} for template variables."
        ></textarea>
        
        <!-- Preview with highlighted variables -->
        <div 
          v-if="showPromptPreview" 
          class="form-control prompt-preview"
          style="height: 300px; overflow: auto; white-space: pre-wrap; font-family: monospace; cursor: pointer;"
          @click="showPromptPreview = false"
          v-html="highlightedPrompt"
        ></div>
        
        <small class="text-muted mt-1">Click on the preview to switch back to editing mode.</small>
      </div>
    </div>
    <!-- Variables Section -->
    <div class="mt-4">
      <div class="d-flex justify-content-between align-items-center">
        <h3>Variables</h3>
        <button 
          v-if="promptForm.promptId && promptForm.variables.length > 0" 
          @click="loadRandomTaskVariables" 
          class="btn btn-outline-primary btn-sm"
          :disabled="loadingRandomTask"
        >
          <span v-if="loadingRandomTask" class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span>
          Load Random Example
        </button>
      </div>
      <div v-for="variable in promptForm.variables" :key="variable" class="mb-2">
        <label class="form-label">{{ variable }}:</label>
        <input v-model="promptForm.variableValues[variable]" class="form-control" />
      </div>
    </div>
    <!-- Workflow Selection -->
    <div class="mt-4">
      <h3>Workflow</h3>
      <select v-model="promptForm.workflow" class="form-control">
        <option value="">None</option>
        <option v-for="workflow in workflows" :key="workflow.id" :value="workflow.id">
          {{ workflow.name }} - {{ workflow.description }}
        </option>
      </select>
    </div>
    <div class="mt-4">
      <h3>ModelId</h3>
      <input v-model="promptForm.modelId" class="form-control" />
    </div>
    <!-- Temperature Setting -->
    <div class="mt-4">
      <h3>Temperature</h3>
      <input 
        type="number" 
        v-model.number="promptForm.temperature" 
        class="form-control" 
        min="0" 
        max="2" 
        step="0.01"
        placeholder="Default model temperature"
      />
      <small class="text-muted mt-1 d-block">Controls the randomness of the model's output. Lower values are more deterministic, higher values are more creative. Leave empty to use default.</small>
    </div>
    
    <!-- Purpose Field -->
    <div class="mt-4">
      <h3>Purpose</h3>
      <input 
        v-model="promptForm.purpose" 
        class="form-control" 
        placeholder="Optional request purpose"
      />
      <small class="text-muted mt-1 d-block">Optional field to track the purpose of this request.</small>
    </div>
    <!-- Language Selection -->
    <div class="mt-4">
      <h3>Language</h3>
      <select v-model="promptForm.language" class="form-control">
        <option v-for="lang in ['GENERAL', 'SPANISH', 'ENGLISH']" :key="lang" :value="lang">
          {{ lang.charAt(0) + lang.slice(1).toLowerCase() }}
        </option>
      </select>
    </div>
    <!-- Save Prompt Options -->
    <div class="mt-4">
      <h3>Save Options</h3>
      <div class="form-check">
        <input type="checkbox" v-model="savePrompt" class="form-check-input" id="savePromptCheck" />
        <label class="form-check-label" for="savePromptCheck">Save Prompt</label>
      </div>
      <div class="form-check mt-2">
        <input type="checkbox" v-model="saveForAllLanguages" class="form-check-input" id="saveForAllLanguagesCheck" />
        <label class="form-check-label" for="saveForAllLanguagesCheck">Save for existing languages</label>
        <small class="form-text text-muted d-block">Will save this prompt for all languages that already exist for this method</small>
      </div>
      <div class="form-check mt-2">
        <input type="checkbox" v-model="promptForm.jsonRequest" class="form-check-input" id="jsonRequestCheck" />
        <label class="form-check-label" for="jsonRequestCheck">JSON Request</label>
        <small class="form-text text-muted d-block">Expect JSON input in the request</small>
      </div>
      <div class="form-check mt-2">
        <input type="checkbox" v-model="promptForm.jsonResponse" class="form-check-input" id="jsonResponseCheck" />
        <label class="form-check-label" for="jsonResponseCheck">JSON Response</label>
        <small class="form-text text-muted d-block">Expect JSON output in the response</small>
      </div>
    </div>
    
    <!-- Execute Prompt -->
    <div class="mt-3">
      <button @click="executePrompt" class="btn btn-primary me-2">Execute Prompt</button>
      <button @click="savePromptOnly" class="btn btn-success me-2">Save Prompt</button>
      <button @click="openTestRunsModal" class="btn btn-info">Run With Test Set</button>
    </div>
    
    <!-- Test Runs Modal -->
    <div v-if="showTestRunsModal" class="modal-backdrop fade show"></div>
    <div v-if="showTestRunsModal" class="modal d-block" role="dialog">
      <div class="modal-dialog modal-lg" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Run Prompt with Test Set</h5>
            <button type="button" class="btn-close" @click="closeTestRunsModal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div v-if="testRunsLoading" class="text-center py-4">
              <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
            </div>
            <div v-else>
              <!-- Test Set Selection -->
              <div class="mb-3">
                <label for="testSetSelect" class="form-label">Select Test Set</label>
                <select id="testSetSelect" v-model="selectedTestSetId" class="form-select" @change="loadTestSetEvaluations">
                  <option value="">-- Select Test Set --</option>
                  <option v-for="testSet in testSets" :key="testSet.id" :value="testSet.id">
                    {{ testSet.name }}
                  </option>
                </select>
              </div>
              
              <!-- Display test set details -->
              <div v-if="selectedTestSetId && selectedTestSet" class="mb-3 p-3 bg-light rounded">
                <h6>{{ selectedTestSet.name }}</h6>
                <p class="mb-1 small">{{ selectedTestSet.description }}</p>
                <p class="mb-0 small text-muted">{{ testSetItemCount }} test items</p>
              </div>
              
              <!-- Run Configuration -->
              <div v-if="selectedTestSetId" class="mb-3">
                <h6>Run Configuration</h6>
                <div class="mb-3">
                  <label for="runName" class="form-label">Run Name</label>
                  <input type="text" class="form-control" id="runName" v-model="newTestRun.name" 
                    :placeholder="'Run with ' + promptForm.promptId + ' - ' + new Date().toLocaleString()">
                </div>
                <div class="mb-3">
                  <label for="runDescription" class="form-label">Description</label>
                  <textarea class="form-control" id="runDescription" v-model="newTestRun.description" 
                    placeholder="Description of this run"></textarea>
                </div>
                
                <!-- Execution Method -->
                <div class="mb-3">
                  <label class="form-label">Execution Method</label>
                  <div class="form-check mb-2">
                    <input class="form-check-input" type="radio" v-model="executionMethodType" id="runWithModelId" value="modelId">
                    <label class="form-check-label" for="runWithModelId">
                      Use Model ID
                    </label>
                  </div>
                  <div v-if="executionMethodType === 'modelId'" class="mb-3 ps-4">
                    <label for="testRunModelId" class="form-label">Model ID</label>
                    <input type="text" class="form-control" id="testRunModelId" v-model="newTestRun.modelId" 
                      :placeholder="promptForm.modelId || 'Enter model ID (e.g. gpt-4-turbo)'">
                  </div>
                  
                  <div class="form-check mb-2">
                    <input class="form-check-input" type="radio" v-model="executionMethodType" id="runWithWorkflow" value="workflow">
                    <label class="form-check-label" for="runWithWorkflow">
                      Use Workflow
                    </label>
                  </div>
                  <div v-if="executionMethodType === 'workflow'" class="mb-3 ps-4">
                    <label for="testRunWorkflow" class="form-label">Workflow ID</label>
                    <input type="text" class="form-control" id="testRunWorkflow" v-model="newTestRun.workflow" 
                      :placeholder="promptForm.workflow || 'Enter workflow ID (e.g. reasoning)'">
                  </div>
                  
                  <div class="mb-3">
                    <label for="testRunTemperature" class="form-label">Temperature (optional)</label>
                    <input type="number" step="0.01" min="0" max="2" class="form-control" id="testRunTemperature" 
                      v-model="newTestRun.temperature" :placeholder="promptForm.temperature || '0.7'">
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeTestRunsModal">Cancel</button>
            <button type="button" class="btn btn-primary" @click="createTestRun" 
              :disabled="!canCreateTestRun">Create Run</button>
          </div>
          
          <!-- Current Test Run Status -->
          <div v-if="currentTestRun" class="modal-body border-top mt-3">
            <h5>Current Test Run Status</h5>
            <div class="alert" :class="{
              'alert-info': currentTestRun.status === 'QUEUED' || currentTestRun.status === 'RUNNING',
              'alert-success': currentTestRun.status === 'COMPLETED',
              'alert-warning': currentTestRun.status === 'PENDING',
              'alert-danger': currentTestRun.status === 'FAILED'
            }">
              <div class="d-flex justify-content-between align-items-center">
                <div>
                  <strong>Run:</strong> {{ currentTestRun.name || 'Unnamed Run' }}
                  <div class="mt-1 d-flex align-items-center">
                    <span class="badge me-2" :class="{
                      'bg-secondary': currentTestRun.status === 'QUEUED',
                      'bg-primary': currentTestRun.status === 'RUNNING',
                      'bg-success': currentTestRun.status === 'COMPLETED',
                      'bg-warning': currentTestRun.status === 'PENDING',
                      'bg-danger': currentTestRun.status === 'FAILED'
                    }">
                      <i class="fas" :class="{
                        'fa-hourglass-half': currentTestRun.status === 'QUEUED',
                        'fa-cog fa-spin': currentTestRun.status === 'RUNNING',
                        'fa-check-circle': currentTestRun.status === 'COMPLETED',
                        'fa-tasks': currentTestRun.status === 'PENDING',
                        'fa-exclamation-circle': currentTestRun.status === 'FAILED'
                      }"></i>
                      {{ currentTestRun.status || 'Unknown' }}
                    </span>
                    <span v-if="currentTestRun.statusCounts" class="text-muted small">
                      <span v-if="currentTestRun.statusCounts.PASSED" class="text-success me-2">
                        {{ currentTestRun.statusCounts.PASSED }} passed
                      </span>
                      <span v-if="currentTestRun.statusCounts.FAILED" class="text-danger me-2">
                        {{ currentTestRun.statusCounts.FAILED }} failed
                      </span>
                      <span v-if="currentTestRun.statusCounts.ERROR" class="text-warning me-2">
                        {{ currentTestRun.statusCounts.ERROR }} errors
                      </span>
                      <span v-if="currentTestRun.statusCounts.PENDING" class="text-warning">
                        {{ currentTestRun.statusCounts.PENDING }} pending manual review
                      </span>
                    </span>
                  </div>
                </div>
                <button @click="viewTestResults(currentTestRun.id)" 
                   class="btn btn-sm btn-outline-primary">
                  View Results
                  <i class="fas fa-external-link-alt ms-1"></i>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Loading Indicator -->
    <div v-if="loading" class="mt-3">Loading...</div>
    <!-- Response Display -->
    <div v-if="response || responseData.imageTaskId" class="mt-4">
      <h3>Response</h3>
      <table class="table table-bordered">
        <thead>
          <tr>
            <th>Message ID</th>
            <th>Prompt IDs</th>
            <th>Model ID</th>
            <th>Created Time</th>
            <th>Response Time</th>
            <th>Time Taken (ms)</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>{{ responseData.messageId }}</td>
            <td>
              <ul>
                <li v-for="id in responseData.promptIds" :key="id">{{ id }}</li>
              </ul>
            </td>
            <td>{{ responseData.modelId }}</td>
            <td>{{ formatTime(responseData.createdTime) }}</td>
            <td>{{ formatTime(responseData.responseTime) }}</td>
            <td>{{ responseData.responseTime - responseData.createdTime }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="isCheckerResponse" class="mt-4">
        <div class="mt-4">
          <h4>Correct Message</h4>
          <pre v-if="!isJSON(formattedCorrectMessage)">{{ formattedCorrectMessage }}</pre>
          <div v-else class="json-content border p-2 bg-light rounded" v-html="formattedCorrectMessage"></div>
        </div>
        <div v-if="isResultDifferent" class="mt-4">
          <h4>Fixes</h4>
          <pre v-if="!isJSON(formattedFixes)">{{ formattedFixes }}</pre>
          <div v-else class="json-content border p-2 bg-light rounded" v-html="formattedFixes"></div>
          
          <h4>Result</h4>
          <pre v-if="!isJSON(formattedResult)">{{ formattedResult }}</pre>
          <div v-else class="json-content border p-2 bg-light rounded" v-html="formattedResult"></div>
        </div>
      </div>
      <div v-if="!isCheckerResponse" class="mt-4">
        <h4>Result</h4>
        <!-- Image result -->
        <div v-if="responseData.imageTaskId" class="image-result-container">
          <pre>{{ formattedResult || responseData.message || 'Generated image:' }}</pre>
          <img :src="`/data/v1.0/admin/llm/image/${responseData.imageTaskId}/resource/0`" 
               alt="Generated image" class="result-image" />
        </div>
        <!-- Non-image results -->
        <pre v-else-if="!resultIsFormatted">{{ formattedResult }}</pre>
        <div v-else class="json-content border p-2 bg-light rounded" v-html="formattedResult"></div>
      </div>
      <div v-if="responseData.contextJson" class="mt-4">
        <h3>Context JSON</h3>
        <pre v-if="!contextIsFormatted">{{ formattedContextJson }}</pre>
        <div v-else class="json-content border p-2 bg-light rounded" v-html="formattedContextJson"></div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./PromptsTab.ts"></script>

<style scoped>
.folder-row {
  cursor: pointer;
  background-color: #f8f9fa;
}

.prompt-row {
  cursor: pointer;
}

.folder-row:hover, .prompt-row:hover {
  background-color: #e9ecef;
}

.folder-icon {
  font-size: 1.2rem;
}

pre {
  white-space: pre-wrap;
  word-break: break-word;
  background-color: #f8f9fa;
  padding: 10px;
  border-radius: 4px;
}

.prompt-preview {
  background-color: #f8f9fa;
  border: 1px solid #ced4da;
  transition: background-color 0.2s;
}

.prompt-preview:hover {
  background-color: #e9ecef;
}

/* This will make the preview keep its formatting */
.prompt-preview :deep(.prompt-variable) {
  color: #ff9900;
  font-weight: bold;
  background-color: rgba(255, 153, 0, 0.1);
  padding: 2px;
  border-radius: 3px;
}

/* JSON content formatting */
.json-content {
  font-family: monospace;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-x: auto;
  line-height: 1.5;
  padding: 10px;
}

.image-result-container {
  display: flex;
  flex-direction: column;
}

.result-image {
  max-width: 100%;
  max-height: 500px;
  margin-top: 10px;
  margin-bottom: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  object-fit: contain;
  background-color: #f8f8f8;
}
</style>