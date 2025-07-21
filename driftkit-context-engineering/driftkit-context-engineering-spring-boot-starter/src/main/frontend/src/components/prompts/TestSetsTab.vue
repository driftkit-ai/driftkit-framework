<template>
  <div>
    <div class="card mb-4">
      <div class="card-header">
        <div class="d-flex justify-content-between align-items-center">
          <h5 class="mb-0">Prompt Test Sets</h5>
          <div>
            <button v-if="selectedTestSets.length > 0" 
                   class="btn btn-sm btn-outline-primary me-2" 
                   @click="openMoveToFolderModal">
              Move to Folder
            </button>
            <button class="btn btn-sm btn-outline-success me-2" @click="createNewFolder">
              Create New Folder
            </button>
            <button class="btn btn-sm btn-primary" @click="createNewTestSet()">
              Create New Test Set
            </button>
          </div>
        </div>
      </div>
      
      <div class="card-body">
        <div v-if="loading" class="text-center py-4">
          <div class="spinner-border" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
        <div v-else>
          <div v-if="testSets.length === 0 && folders.length === 0" class="alert alert-info my-3">
            No test sets found. Create your first test set to start testing prompts.
          </div>
          
          <!-- Folders Section -->
          <div v-if="folders.length > 0" class="mb-4">
            <h6 class="mb-3">Folders</h6>
            <div class="list-group">
              <div v-for="folder in folders" :key="folder.id" class="list-group-item">
                <div class="d-flex justify-content-between align-items-center">
                  <div>
                    <button class="btn btn-sm btn-link text-decoration-none" @click="toggleFolder(folder.id)">
                      <i :class="['fas', expandedFolders.includes(folder.id) ? 'fa-folder-open' : 'fa-folder']"></i>
                      {{ folder.name }}
                      <span class="badge bg-secondary ms-2">{{ getTestSetsInFolder(folder.id).length }}</span>
                    </button>
                  </div>
                  <div class="btn-group">
                    <button class="btn btn-sm btn-outline-success" @click="runAllTestsInFolder(folder.id)">
                      <i class="fas fa-play"></i> Run All Tests
                    </button>
                    <button class="btn btn-sm btn-outline-primary" @click="createNewTestSet(folder.id)">
                      Add Test Set
                    </button>
                    <button class="btn btn-sm btn-outline-danger" @click="deleteFolder(folder.id)">
                      Delete
                    </button>
                  </div>
                </div>
                
                <!-- Test sets in folder -->
                <div v-if="expandedFolders.includes(folder.id)" class="mt-3 ps-4 border-start">
                  <table class="table table-hover">
                    <thead>
                      <tr>
                        <th style="width: 40px;">
                          <!-- Select all checkbox -->
                          <div class="form-check">
                            <input type="checkbox" class="form-check-input" 
                                  :checked="getTestSetsInFolder(folder.id).every(ts => selectedTestSets.includes(ts.id))"
                                  @click="getTestSetsInFolder(folder.id).forEach(ts => {
                                    if (!selectedTestSets.includes(ts.id)) {
                                      toggleTestSetSelection(ts.id);
                                    }
                                  })">
                          </div>
                        </th>
                        <th>Name</th>
                        <th>Description</th>
                        <th>Created</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      <template v-for="testSet in getTestSetsInFolder(folder.id)" :key="testSet.id">
                        <tr>
                          <td>
                            <div class="form-check">
                              <input type="checkbox" class="form-check-input" 
                                    :checked="selectedTestSets.includes(testSet.id)"
                                    @click="toggleTestSetSelection(testSet.id)">
                            </div>
                          </td>
                          <td>{{ testSet.name }}</td>
                          <td>{{ testSet.description }}</td>
                          <td>{{ formatDateTime(testSet.createdAt) }}</td>
                          <td>
                            <div class="btn-group">
                              <button class="btn btn-sm btn-outline-primary" @click="toggleTestSetDetails(testSet.id)">
                                {{ expandedTestSets.includes(testSet.id) ? 'Hide' : 'View' }}
                              </button>
                              <button class="btn btn-sm btn-outline-danger" @click="deleteTestSet(testSet.id)">
                                Delete
                              </button>
                            </div>
                          </td>
                        </tr>
                        <tr v-if="expandedTestSets.includes(testSet.id)">
                          <td colspan="5" class="p-0">
                            <div class="test-set-details border-top border-bottom bg-light p-3">
                              <!-- Test Set Details Content -->
                              <div class="d-flex justify-content-between align-items-center mb-3">
                                <h5 class="mb-0">Test Set: {{ testSet.name }}</h5>
                                <div>
                                  <button class="btn btn-sm btn-outline-primary me-2" @click="openEvaluationsModal(testSet)">
                                    Manage Evaluations
                                  </button>
                                  <button class="btn btn-sm btn-outline-primary me-2" @click="openRunsModal(testSet)">
                                    View Runs
                                  </button>
                                  <button class="btn btn-sm btn-outline-secondary" @click="closeTestSetDetails(testSet.id)">
                                    Close
                                  </button>
                                </div>
                              </div>
                              <p class="text-muted mb-3">{{ testSet.description }}</p>
                              
                              <div v-if="testSetItemsLoading && loadingTestSetId === testSet.id" class="text-center py-4">
                                <div class="spinner-border" role="status">
                                  <span class="visually-hidden">Loading...</span>
                                </div>
                              </div>
                              <div v-else-if="loadedTestSetId === testSet.id">
                                <div v-if="testSetItems.length === 0" class="alert alert-info my-3">
                                  No items in this test set. Add items from the Traces tab.
                                </div>
                                
                                <!-- Test set items table -->
                                <table v-show="testSetItems.length > 0" class="table table-hover">
                                  <thead>
                                    <tr>
                                      <th>Message</th>
                                      <th>Result</th>
                                      <th>Model / Temperature</th>
                                      <th>Workflow Type</th>
                                      <th>Actions</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    <template v-for="item in testSetItems" :key="item.id">
                                      <tr>
                                        <td style="max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                                          {{ item.message }}
                                        </td>
                                        <td style="max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                                          <span v-if="item.isImageTask" class="badge bg-info me-1" 
                                               data-bs-toggle="tooltip" 
                                               :data-bs-title="`Image Task ID: ${item.originalImageTaskId || 'N/A'}, Trace ID: ${item.originalTraceId || 'N/A'}, Message Task ID: ${item.originalMessageTaskId || 'N/A'}`">
                                            <i class="bi bi-image">📷</i> Image [{{ item.originalImageTaskId ? item.originalImageTaskId.substring(0,6) : 'No ID' }}]
                                          </span>
                                          {{ item.result }}
                                        </td>
                                        <td>
                                          {{ item.model }}<br>
                                          <small class="text-muted">Temp: {{ item.temperature || 'N/A' }}</small>
                                        </td>
                                        <td>{{ item.workflowType || 'N/A' }}</td>
                                        <td>
                                          <div class="btn-group">
                                            <button class="btn btn-sm btn-outline-primary" @click="toggleItemDetails(item.id)">
                                              {{ expandedItems.includes(item.id) ? 'Hide' : 'View' }}
                                            </button>
                                            <button class="btn btn-sm btn-outline-danger" @click="deleteTestSetItem(item.id, testSet.id)">
                                              Remove
                                            </button>
                                          </div>
                                        </td>
                                      </tr>
                                      <tr v-if="expandedItems.includes(item.id)">
                                        <td colspan="5" class="p-3 bg-light">
                                          <div class="row">
                                            <div class="col-md-6">
                                              <h6>Message</h6>
                                              <pre class="border p-2 bg-white" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;" v-html="highlightVariables(item.message)"></pre>
                                            </div>
                                            <div class="col-md-6">
                                              <h6>Result</h6>
                                              <pre v-if="isJSON(item.result)" class="border p-2 bg-white" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;" v-html="formatJSON(JSON.parse(item.result))"></pre>
                                              <pre v-else class="border p-2 bg-white" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;">{{ item.result }}</pre>
                                            </div>
                                          </div>
                                          <div v-if="item.variables && Object.keys(item.variables).length > 0" class="row mt-3">
                                            <div class="col-md-12">
                                              <h6>Variables</h6>
                                              <pre class="border p-2 bg-white" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;" v-html="formatJSON(item.variables)"></pre>
                                            </div>
                                          </div>
                                        </td>
                                      </tr>
                                    </template>
                                  </tbody>
                                </table>
                              </div>
                            </div>
                          </td>
                        </tr>
                      </template>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
          
          <!-- Ungrouped Test Sets Section -->
          <div class="mt-4">
            <h6 class="mb-3">Ungrouped Test Sets</h6>
            <table v-if="getTestSetsInFolder(null).length > 0" class="table table-hover">
              <thead>
                <tr>
                  <th style="width: 40px;">
                    <!-- Select all checkbox -->
                    <div class="form-check">
                      <input type="checkbox" class="form-check-input" 
                            :checked="getTestSetsInFolder(null).every(ts => selectedTestSets.includes(ts.id))"
                            @click="getTestSetsInFolder(null).forEach(ts => {
                              if (!selectedTestSets.includes(ts.id)) {
                                toggleTestSetSelection(ts.id);
                              }
                            })">
                    </div>
                  </th>
                  <th>Name</th>
                  <th>Description</th>
                  <th>Created</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <template v-for="testSet in getTestSetsInFolder(null)" :key="testSet.id">
                  <tr>
                    <td>
                      <div class="form-check">
                        <input type="checkbox" class="form-check-input" 
                              :checked="selectedTestSets.includes(testSet.id)"
                              @click="toggleTestSetSelection(testSet.id)">
                      </div>
                    </td>
                    <td>{{ testSet.name }}</td>
                    <td>{{ testSet.description }}</td>
                    <td>{{ formatDateTime(testSet.createdAt) }}</td>
                    <td>
                      <div class="btn-group">
                        <button class="btn btn-sm btn-outline-primary" @click="toggleTestSetDetails(testSet.id)">
                          {{ expandedTestSets.includes(testSet.id) ? 'Hide' : 'View' }}
                        </button>
                        <button class="btn btn-sm btn-outline-danger" @click="deleteTestSet(testSet.id)">
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="expandedTestSets.includes(testSet.id)">
                    <td colspan="5" class="p-0">
                      <div class="test-set-details border-top border-bottom bg-light p-3">
                        <!-- Test Set Details Content -->
                        <div class="d-flex justify-content-between align-items-center mb-3">
                          <h5 class="mb-0">Test Set: {{ testSet.name }}</h5>
                          <div>
                            <button class="btn btn-sm btn-outline-primary me-2" @click="openEvaluationsModal(testSet)">
                              Manage Evaluations
                            </button>
                            <button class="btn btn-sm btn-outline-primary me-2" @click="openRunsModal(testSet)">
                              View Runs
                            </button>
                            <button class="btn btn-sm btn-outline-secondary" @click="closeTestSetDetails(testSet.id)">
                              Close
                            </button>
                          </div>
                        </div>
                        <p class="text-muted mb-3">{{ testSet.description }}</p>
                        
                        <div v-if="testSetItemsLoading && loadingTestSetId === testSet.id" class="text-center py-4">
                          <div class="spinner-border" role="status">
                            <span class="visually-hidden">Loading...</span>
                          </div>
                        </div>
                        <div v-else-if="loadedTestSetId === testSet.id">
                          <div v-if="testSetItems.length === 0" class="alert alert-info my-3">
                            No items in this test set. Add items from the Traces tab.
                          </div>
                          
                          <!-- Test set items table -->
                          <table v-show="testSetItems.length > 0" class="table table-hover">
                            <thead>
                              <tr>
                                <th>Message</th>
                                <th>Result</th>
                                <th>Model / Temperature</th>
                                <th>Workflow Type</th>
                                <th>Actions</th>
                              </tr>
                            </thead>
                            <tbody>
                              <template v-for="item in testSetItems" :key="item.id">
                                <tr>
                                  <td style="max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                                    {{ item.message }}
                                  </td>
                                  <td style="max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                                    <span v-if="item.isImageTask" class="badge bg-info me-1" 
                                       data-bs-toggle="tooltip" 
                                       :data-bs-title="`Image Task ID: ${item.originalImageTaskId || 'N/A'}, Trace ID: ${item.originalTraceId || 'N/A'}, Message Task ID: ${item.originalMessageTaskId || 'N/A'}`">
                                      <i class="bi bi-image">📷</i> Image [{{ item.originalImageTaskId ? item.originalImageTaskId.substring(0,6) : 'No ID' }}]
                                    </span>
                                    {{ item.result }}
                                  </td>
                                  <td>
                                    {{ item.model }}<br>
                                    <small class="text-muted">Temp: {{ item.temperature || 'N/A' }}</small>
                                  </td>
                                  <td>{{ item.workflowType || 'N/A' }}</td>
                                  <td>
                                    <div class="btn-group">
                                      <button class="btn btn-sm btn-outline-primary" @click="toggleItemDetails(item.id)">
                                        {{ expandedItems.includes(item.id) ? 'Hide' : 'View' }}
                                      </button>
                                      <button class="btn btn-sm btn-outline-danger" @click="deleteTestSetItem(item.id, testSet.id)">
                                        Remove
                                      </button>
                                    </div>
                                  </td>
                                </tr>
                                <tr v-if="expandedItems.includes(item.id)">
                                  <td colspan="5" class="p-3 bg-light">
                                    <div class="row">
                                      <div class="col-md-6">
                                        <h6>Message</h6>
                                        <pre class="border p-2 bg-white" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;" v-html="highlightVariables(item.message)"></pre>
                                      </div>
                                      <div class="col-md-6">
                                        <h6>Result</h6>
                                        
                                        <!-- Image display - Show image if originalImageTaskId exists -->
                                        <div v-if="item.originalImageTaskId" class="text-center mb-3">
                                          <img :src="getImageUrl(item.originalImageTaskId, 0)" 
                                               class="img-fluid border rounded" 
                                               style="max-height: 300px; object-fit: contain; margin: 10px 0;"
                                               alt="Generated image" 
                                               @error="$event.target.nextElementSibling.style.display='block'" />
                                          <div style="display:none;" class="alert alert-warning mt-2 small">
                                            <p>Failed to load image.</p>
                                            <small>Image ID: {{ item.originalImageTaskId }}</small>
                                          </div>
                                          <div class="mt-2">
                                            <small class="text-muted">Image ID: {{ item.originalImageTaskId }}</small>
                                          </div>
                                        </div>
                                        <!-- Regular result display for non-image items -->
                                        <pre v-if="!item.originalImageTaskId && isJSON(item.result)" class="border p-2 bg-white" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;" v-html="formatJSON(JSON.parse(item.result))"></pre>
                                        <pre v-else-if="!item.originalImageTaskId" class="border p-2 bg-white" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;">{{ item.result }}</pre>
                                      </div>
                                    </div>
                                    <div v-if="item.variables && Object.keys(item.variables).length > 0" class="row mt-3">
                                      <div class="col-md-12">
                                        <h6>Variables</h6>
                                        <pre class="border p-2 bg-white" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;" v-html="formatJSON(item.variables)"></pre>
                                      </div>
                                    </div>
                                  </td>
                                </tr>
                              </template>
                            </tbody>
                          </table>
                        </div>
                      </div>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
            <div v-else class="alert alert-info">
              No ungrouped test sets found.
            </div>
          </div>
        </div>
      </div>
    </div>
    
    
    <!-- Create Test Set Modal -->
    <div v-if="showCreateTestSetModal" class="modal-backdrop fade show"></div>
    <div v-if="showCreateTestSetModal" class="modal d-block" id="createTestSetModal" role="dialog">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Create New Test Set</h5>
            <button type="button" class="btn-close" @click="closeCreateTestSetModal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div class="mb-3">
              <label for="testSetName" class="form-label">Name</label>
              <input type="text" class="form-control" id="testSetName" v-model="newTestSet.name" placeholder="Test Set Name">
            </div>
            <div class="mb-3">
              <label for="testSetDescription" class="form-label">Description</label>
              <textarea class="form-control" id="testSetDescription" v-model="newTestSet.description" placeholder="Description"></textarea>
            </div>
            <div class="mb-3">
              <label for="testSetFolder" class="form-label">Folder (optional)</label>
              <select class="form-select" id="testSetFolder" v-model="newTestSet.folderId">
                <option value="">No Folder</option>
                <option v-for="folder in folders" :key="folder.id" :value="folder.id">
                  {{ folder.name }}
                </option>
              </select>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeCreateTestSetModal">Cancel</button>
            <button type="button" class="btn btn-primary" @click="submitNewTestSet">Create</button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Create Folder Modal -->
    <div v-if="showCreateFolderModal" class="modal-backdrop fade show"></div>
    <div v-if="showCreateFolderModal" class="modal d-block" id="createFolderModal" role="dialog">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Create New Folder</h5>
            <button type="button" class="btn-close" @click="closeCreateFolderModal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div class="mb-3">
              <label for="folderName" class="form-label">Name</label>
              <input type="text" class="form-control" id="folderName" v-model="newFolder.name" placeholder="Folder Name">
            </div>
            <div class="mb-3">
              <label for="folderDescription" class="form-label">Description (optional)</label>
              <textarea class="form-control" id="folderDescription" v-model="newFolder.description" placeholder="Description"></textarea>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeCreateFolderModal">Cancel</button>
            <button type="button" class="btn btn-primary" @click="submitNewFolder">Create</button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Move to Folder Modal -->
    <div v-if="showMoveToFolderModal" class="modal-backdrop fade show"></div>
    <div v-if="showMoveToFolderModal" class="modal d-block" id="moveToFolderModal" role="dialog">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Move Test Sets to Folder</h5>
            <button type="button" class="btn-close" @click="closeMoveToFolderModal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div class="alert alert-info">
              <p class="mb-0">Moving {{ selectedTestSets.length }} test set(s) to a folder</p>
            </div>
            
            <div class="mb-3">
              <label for="targetFolder" class="form-label">Target Folder</label>
              <select class="form-select" id="targetFolder" v-model="targetFolderId">
                <option value="">No Folder (Root)</option>
                <option v-for="folder in folders" :key="folder.id" :value="folder.id">
                  {{ folder.name }}
                </option>
              </select>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeMoveToFolderModal">Cancel</button>
            <button type="button" class="btn btn-primary" @click="moveTestSetsToFolder">Move</button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Evaluations Modal -->
    <div v-if="showEvaluationsModal" class="modal-backdrop fade show"></div>
    <div v-if="showEvaluationsModal" class="modal d-block" role="dialog">
      <div class="modal-dialog modal-lg" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Manage Evaluations</h5>
            <button type="button" class="btn-close" @click="closeEvaluationsModal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div v-if="evaluationsLoading" class="text-center py-4">
              <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
            </div>
            <div v-else>
              <!-- Evaluations Tab Navigation -->
              <ul class="nav nav-tabs mb-3">
                <li class="nav-item">
                  <a class="nav-link" :class="{ active: evaluationsTab === 'testset' }" @click.prevent="evaluationsTab = 'testset'" href="#">
                    Test Set Evaluations
                  </a>
                </li>
                <li class="nav-item">
                  <a class="nav-link" :class="{ active: evaluationsTab === 'global' }" @click.prevent="evaluationsTab = 'global'" href="#">
                    Global Evaluations
                  </a>
                </li>
              </ul>
              
              <!-- Test Set Evaluations Tab -->
              <div v-if="evaluationsTab === 'testset'">
                <div class="d-flex justify-content-between mb-3">
                  <h6>Test Set Evaluations</h6>
                  <div>
                    <button class="btn btn-sm btn-primary" @click="showCreateEvaluationPanel = true; isCreatingGlobal = false">
                      Create New
                    </button>
                  </div>
                </div>
              </div>
              
              <!-- Global Evaluations Tab -->
              <div v-if="evaluationsTab === 'global'">
                <div class="d-flex justify-content-between mb-3">
                  <h6>Global Evaluations</h6>
                  <button class="btn btn-sm btn-primary" @click="showCreateEvaluationPanel = true; isCreatingGlobal = true">
                    Create Global Evaluation
                  </button>
                </div>
              </div>
              
              <div v-if="showCreateEvaluationPanel" class="card mb-4">
                <div class="card-header">
                  <h6 class="m-0">{{ isEditMode ? 'Edit Evaluation' : 'Create New Evaluation' }}</h6>
                </div>
                <div class="card-body">
                  <div class="mb-3">
                    <label for="evaluationName" class="form-label">Name</label>
                    <input type="text" class="form-control" id="evaluationName" v-model="newEvaluation.name" placeholder="Evaluation Name">
                  </div>
                  
                  <div class="mb-3">
                    <label for="evaluationType" class="form-label">Evaluation Type</label>
                    <select class="form-select" id="evaluationType" v-model="newEvaluation.type">
                      <option value="">-- Select Type --</option>
                      <option value="JSON_SCHEMA">JSON Schema Validation</option>
                      <option value="CONTAINS_KEYWORDS">Contains Keywords</option>
                      <option value="EXACT_MATCH">Exact Match</option>
                      <option value="LLM_EVALUATION">LLM-based Evaluation</option>
                      <option value="WORD_COUNT">Word Count</option>
                      <option value="ARRAY_LENGTH">Array Length</option>
                      <option value="FIELD_VALUE_CHECK">Field Value Check</option>
                      <option value="REGEX_MATCH">Regex Match</option>
                      <option value="MANUAL_EVALUATION">Manual Evaluation</option>
                    </select>
                  </div>
                  
                  <div class="mb-3">
                    <label for="evaluationDescription" class="form-label">Description</label>
                    <textarea class="form-control" id="evaluationDescription" v-model="newEvaluation.description" placeholder="Description"></textarea>
                  </div>
                  
                  <!-- Dynamic configuration based on type -->
                  <div v-if="newEvaluation.type" class="mb-3">
                    <h6>Configuration</h6>
                    
                    <!-- JSON Schema Config -->
                    <div v-if="newEvaluation.type === 'JSON_SCHEMA'" class="mb-3">
                      <div class="form-check mb-3">
                        <input class="form-check-input" type="checkbox" v-model="jsonSchemaConfig.validateJsonOnly" id="validateJsonOnly">
                        <label class="form-check-label" for="validateJsonOnly">
                          Validate JSON format only (ignore schema)
                        </label>
                      </div>
                      <div v-if="!jsonSchemaConfig.validateJsonOnly" class="mb-3">
                        <label for="jsonSchema" class="form-label">JSON Schema</label>
                        <textarea class="form-control font-monospace" rows="10" id="jsonSchema" v-model="jsonSchemaConfig.jsonSchema" placeholder='{ "type": "object", "properties": { "name": { "type": "string" } }, "required": ["name"] }'></textarea>
                      </div>
                    </div>
                    
                    <!-- Contains Keywords Config -->
                    <div v-if="newEvaluation.type === 'CONTAINS_KEYWORDS'" class="mb-3">
                      <div class="mb-3">
                        <label for="keywordsInput" class="form-label">Keywords (one per line)</label>
                        <textarea class="form-control" rows="5" id="keywordsInput" v-model="keywordsInput" placeholder="Enter keywords, one per line"></textarea>
                      </div>
                      <div class="mb-3">
                        <label for="matchType" class="form-label">Match Type</label>
                        <select class="form-select" id="matchType" v-model="keywordsConfig.matchType">
                          <option value="ALL">All keywords must be present</option>
                          <option value="ANY">Any keyword must be present</option>
                          <option value="EXACTLY">Exactly these keywords</option>
                          <option value="NONE">None of these keywords</option>
                          <option value="MAJORITY">Majority of keywords</option>
                        </select>
                      </div>
                      <div class="form-check mb-3">
                        <input class="form-check-input" type="checkbox" v-model="keywordsConfig.caseSensitive" id="keywordsCaseSensitive">
                        <label class="form-check-label" for="keywordsCaseSensitive">
                          Case sensitive
                        </label>
                      </div>
                    </div>
                    
                    <!-- LLM Evaluation Config -->
                    <div v-if="newEvaluation.type === 'LLM_EVALUATION'" class="mb-3">
                      <div class="mb-3">
                        <label for="evaluationPrompt" class="form-label">Evaluation Prompt</label>
                        <textarea class="form-control" rows="8" id="evaluationPrompt" v-model="llmConfig.evaluationPrompt" :placeholder="DEFAULT_LLM_CONFIG.evaluationPrompt"></textarea>
                      </div>
                      <div class="mb-3">
                        <label for="modelId" class="form-label">Model ID (optional)</label>
                        <input type="text" class="form-control" id="modelId" v-model="llmConfig.modelId" placeholder="Leave empty to use default model">
                      </div>
                      <div class="mb-3">
                        <label for="temperature" class="form-label">Temperature (optional)</label>
                        <input type="number" min="0" max="1" step="0.1" class="form-control" id="temperature" v-model="llmConfig.temperature" placeholder="0.0 - 1.0">
                      </div>
                      <div class="form-check mb-3">
                        <input class="form-check-input" type="checkbox" v-model="llmConfig.generateFeedback" id="generateFeedback">
                        <label class="form-check-label" for="generateFeedback">
                          Generate detailed feedback
                        </label>
                      </div>
                    </div>
                    
                    <!-- Manual Evaluation Config -->
                    <div v-if="newEvaluation.type === 'MANUAL_EVALUATION'" class="mb-3">
                      <label for="reviewInstructions" class="form-label">Instructions for Reviewers</label>
                      <textarea 
                        class="form-control" 
                        id="reviewInstructions" 
                        v-model="manualEvalConfig.reviewInstructions" 
                        :placeholder="DEFAULT_MANUAL_EVAL_CONFIG.reviewInstructions"
                        rows="3"
                      ></textarea>
                      <small class="form-text text-muted">
                        Provide instructions for human reviewers who will manually evaluate the results.
                      </small>
                    </div>
                    
                    <!-- Show different config forms for other evaluation types -->
                    <div v-if="['EXACT_MATCH', 'WORD_COUNT', 'ARRAY_LENGTH', 'FIELD_VALUE_CHECK', 'REGEX_MATCH'].includes(newEvaluation.type)" class="alert alert-info">
                      Additional configuration options will be displayed based on the selected type.
                    </div>
                  </div>
                  
                  <div class="form-check mb-3">
                    <input class="form-check-input" type="checkbox" v-model="newEvaluation.config.negateResult" id="negateResult">
                    <label class="form-check-label" for="negateResult">
                      Negate result (invert pass/fail)
                    </label>
                  </div>
                  
                  <div class="d-flex justify-content-end">
                    <button class="btn btn-secondary me-2" @click="cancelCreateEvaluation">Cancel</button>
                    <button class="btn btn-primary" @click="createEvaluation" :disabled="!canCreateEvaluation">
                      {{ isEditMode ? 'Update' : 'Create' }}
                    </button>
                  </div>
                </div>
              </div>
              
              <!-- Test Set Evaluations Table -->
              <table v-if="evaluationsTab === 'testset' && evaluations.length > 0" class="table table-hover">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Type</th>
                    <th>Description</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="evaluation in evaluations" :key="evaluation.id">
                    <td>{{ evaluation.name }}</td>
                    <td>{{ formatEvaluationType(evaluation.type) }}</td>
                    <td>{{ evaluation.description }}</td>
                    <td>
                      <div class="btn-group">
                        <button class="btn btn-sm btn-outline-secondary" @click="editEvaluation(evaluation)">
                          Edit
                        </button>
                        <button class="btn btn-sm btn-outline-primary" @click="copyEvaluation(evaluation)">
                          Copy
                        </button>
                        <button class="btn btn-sm btn-outline-danger" @click="deleteEvaluation(evaluation.id)">
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div v-if="evaluationsTab === 'testset' && evaluations.length === 0" class="alert alert-info">
                No test set evaluations defined yet. Create an evaluation or add one from the global evaluations.
              </div>
              
              <!-- Global Evaluations Table -->
              <table v-if="evaluationsTab === 'global' && globalEvaluations.length > 0" class="table table-hover">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Type</th>
                    <th>Description</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="evaluation in globalEvaluations" :key="evaluation.id">
                    <td>{{ evaluation.name }}</td>
                    <td>{{ formatEvaluationType(evaluation.type) }}</td>
                    <td>{{ evaluation.description }}</td>
                    <td>
                      <div class="btn-group">
                        <button class="btn btn-sm btn-outline-secondary" @click="editEvaluation(evaluation)">
                          Edit
                        </button>
                        <button class="btn btn-sm btn-outline-primary" @click="addEvaluationToTestSet(evaluation)">
                          Add to Test Set
                        </button>
                        <button class="btn btn-sm btn-outline-danger" @click="deleteEvaluation(evaluation.id)">
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div v-if="evaluationsTab === 'global' && globalEvaluations.length === 0" class="alert alert-info">
                No global evaluations defined yet. Create a global evaluation to use across multiple test sets.
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeEvaluationsModal">Close</button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Runs Modal -->
    <div v-if="showRunsModal" class="modal-backdrop fade show"></div>
    <div v-if="showRunsModal" class="modal d-block" role="dialog">
      <div class="modal-dialog modal-lg" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Evaluation Runs</h5>
            <button type="button" class="btn-close" @click="closeRunsModal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div v-if="runsLoading" class="text-center py-4">
              <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
            </div>
            <div v-else>
              <div class="d-flex justify-content-between mb-3">
                <h6>Test Runs</h6>
                <button class="btn btn-sm btn-primary" @click="showCreateRunPanel = true">
                  Create New Run
                </button>
              </div>
              
              <div v-if="showCreateRunPanel" class="card mb-4">
                <div class="card-body">
                  <div class="mb-3">
                    <label for="runName" class="form-label">Name</label>
                    <input type="text" class="form-control" id="runName" v-model="newRun.name" placeholder="Run Name">
                  </div>
                  
                  <div class="mb-3">
                    <label for="runDescription" class="form-label">Description</label>
                    <textarea class="form-control" id="runDescription" v-model="newRun.description" placeholder="Description"></textarea>
                  </div>
                  
                  <div class="mb-3">
                    <label class="form-label">Prompt Options</label>
                    <div class="form-check mb-2">
                      <input class="form-check-input" type="radio" v-model="promptOptionType" id="useOriginalPrompt" value="original">
                      <label class="form-check-label" for="useOriginalPrompt">
                        Use original prompt (from test items)
                      </label>
                    </div>
                    <div class="form-check mb-2">
                      <input class="form-check-input" type="radio" v-model="promptOptionType" id="useExistingPrompt" value="existing">
                      <label class="form-check-label" for="useExistingPrompt">
                        Use existing prompt
                      </label>
                    </div>
                    <div v-if="promptOptionType === 'existing'" class="mb-3 ps-4">
                      <label for="promptId" class="form-label">Prompt ID</label>
                      <input type="text" class="form-control" id="promptId" v-model="newRun.alternativePromptId" placeholder="Enter prompt ID">
                    </div>
                    <div class="form-check mb-2">
                      <input class="form-check-input" type="radio" v-model="promptOptionType" id="useCustomPrompt" value="custom">
                      <label class="form-check-label" for="useCustomPrompt">
                        Use custom prompt template
                      </label>
                    </div>
                    <div v-if="promptOptionType === 'custom'" class="mb-3 ps-4">
                      <label for="customPromptTemplate" class="form-label">Custom Prompt Template</label>
                      <textarea class="form-control" rows="5" id="customPromptTemplate" v-model="newRun.alternativePromptTemplate" placeholder="Enter prompt template with variables like {{varName}}"></textarea>
                    </div>
                  </div>
                  
                  <div class="mb-3">
                    <label class="form-label">Execution Method</label>
                    <div class="form-check mb-2">
                      <input class="form-check-input" type="checkbox" v-model="overrideModelSettings" id="overrideModelSettings">
                      <label class="form-check-label" for="overrideModelSettings">
                        Override execution settings
                      </label>
                    </div>
                    <div v-if="overrideModelSettings" class="ps-4">
                      <div class="form-check mb-2">
                        <input class="form-check-input" type="radio" v-model="executionMethodType" id="useModelId" value="modelId">
                        <label class="form-check-label" for="useModelId">
                          Use specific Model ID
                        </label>
                      </div>
                      <div v-if="executionMethodType === 'modelId'" class="mb-3 ps-4">
                        <label for="runModelId" class="form-label">Model ID</label>
                        <input type="text" class="form-control" id="runModelId" v-model="newRun.modelId" placeholder="Enter model ID (e.g. gpt-4-turbo)">
                      </div>
                      
                      <div class="form-check mb-2">
                        <input class="form-check-input" type="radio" v-model="executionMethodType" id="useWorkflow" value="workflow">
                        <label class="form-check-label" for="useWorkflow">
                          Use Workflow
                        </label>
                      </div>
                      <div v-if="executionMethodType === 'workflow'" class="mb-3 ps-4">
                        <label for="runWorkflow" class="form-label">Workflow ID</label>
                        <input type="text" class="form-control" id="runWorkflow" v-model="newRun.workflow" placeholder="Enter workflow ID (e.g. reasoning)">
                      </div>
                      
                      <div class="mb-3">
                        <label for="runTemperature" class="form-label">Temperature (optional)</label>
                        <input type="number" min="0" max="1" step="0.1" class="form-control" id="runTemperature" v-model="newRun.temperature" placeholder="0.0 - 1.0">
                      </div>
                    </div>
                  </div>
                  
                  <div class="d-flex justify-content-end">
                    <button class="btn btn-secondary me-2" @click="cancelCreateRun">Cancel</button>
                    <button class="btn btn-primary" @click="createRun" :disabled="!canCreateRun">Create</button>
                  </div>
                </div>
              </div>
              
              <table v-if="runs.length > 0" class="table table-hover">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Status</th>
                    <th>Date</th>
                    <th>Results</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="run in runs" :key="run.id">
                    <td>{{ run.name }}</td>
                    <td>
                      <span :class="getRunStatusClass(run.status)">{{ run.status }}</span>
                    </td>
                    <td>{{ formatDateTime(run.startedAt) }}</td>
                    <td>
                      <div v-if="run.statusCounts" class="d-flex gap-2">
                        <span v-if="run.statusCounts.PASSED" class="badge bg-success me-1">
                          {{ run.statusCounts.PASSED }} Passed
                        </span>
                        <span v-if="run.statusCounts.FAILED" class="badge bg-danger me-1">
                          {{ run.statusCounts.FAILED }} Failed
                        </span>
                        <span v-if="run.statusCounts.ERROR" class="badge bg-warning me-1">
                          {{ run.statusCounts.ERROR }} Error
                        </span>
                      </div>
                      <span v-else>-</span>
                    </td>
                    <td>
                      <div class="btn-group">
                        <button v-if="['QUEUED', 'CREATED'].includes(run.status)" class="btn btn-sm btn-outline-primary" @click="startRun(run.id)">
                          Start
                        </button>
                        <button v-if="run.status === 'COMPLETED'" class="btn btn-sm btn-outline-success" @click="viewRunResults(run.id)">
                          View Results
                        </button>
                        <button class="btn btn-sm btn-outline-danger" @click="deleteRun(run.id)">
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div v-else class="alert alert-info">
                No evaluation runs yet. Create your first run to test your evaluations.
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-primary" @click="quickRun">
              <i class="fas fa-play"></i> Quick Run
            </button>
            <router-link to="/prompts?tab=evalruns" class="btn btn-info">
              <i class="fas fa-list"></i> View All Runs
            </router-link>
            <button type="button" class="btn btn-secondary" @click="closeRunsModal">Close</button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Copy Evaluation Modal -->
    <div v-if="showCopyEvaluationModal" class="modal-backdrop fade show"></div>
    <div v-if="showCopyEvaluationModal" class="modal d-block" role="dialog">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Copy Evaluation to Another Test Set</h5>
            <button type="button" class="btn-close" @click="closeCopyEvaluationModal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div v-if="copyEvaluationLoading" class="text-center py-4">
              <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
            </div>
            <div v-else>
              <div class="alert alert-info">
                <p class="mb-0">You are copying the evaluation: <strong>{{ selectedEvaluation?.name }}</strong></p>
              </div>
              
              <div class="mb-3">
                <label for="targetTestSetSelect" class="form-label">Select Target Test Set</label>
                <select id="targetTestSetSelect" v-model="targetTestSetId" class="form-select">
                  <option value="">-- Select Test Set --</option>
                  <option v-for="ts in allTestSets" :key="ts.id" :value="ts.id" :disabled="ts.id === selectedTestSet.id">
                    {{ ts.name }} {{ ts.id === selectedTestSet.id ? '(current)' : '' }}
                  </option>
                </select>
              </div>
              
              <div v-if="targetTestSetId" class="alert alert-success">
                <p class="mb-0">The evaluation will be copied to: <strong>{{ getTestSetName(targetTestSetId) }}</strong></p>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeCopyEvaluationModal">Cancel</button>
            <button type="button" class="btn btn-primary" @click="executeCopyEvaluation" 
                   :disabled="!targetTestSetId || copyEvaluationLoading">
              Copy Evaluation
            </button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Folder Execution Modal -->
    <div v-if="showFolderExecutionModal" class="modal-backdrop fade show"></div>
    <div v-if="showFolderExecutionModal" class="modal d-block" role="dialog">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Execute All Tests in Folder</h5>
            <button type="button" class="btn-close" @click="closeFolderExecutionModal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div v-if="folderExecutionLoading" class="text-center py-4">
              <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
            </div>
            <div v-else>
              <div class="alert alert-info">
                <p class="mb-0">You are about to run all tests in this folder ({{ getTestSetsInFolder(selectedFolderId).length }} test sets)</p>
              </div>
              
              <div class="mb-3">
                <label class="form-label">Execution Method (Optional)</label>
                <div class="form-check mb-2">
                  <input class="form-check-input" type="radio" v-model="folderExecutionConfig.useModel" id="useModelId" :value="true">
                  <label class="form-check-label" for="useModelId">
                    Use specific Model ID
                  </label>
                </div>
                <div v-if="folderExecutionConfig.useModel" class="mb-3 ps-4">
                  <input type="text" class="form-control" v-model="folderExecutionConfig.modelId" placeholder="Enter model ID (e.g. gpt-4-turbo)">
                </div>
                
                <div class="form-check mb-2">
                  <input class="form-check-input" type="radio" v-model="folderExecutionConfig.useModel" id="useWorkflow" :value="false">
                  <label class="form-check-label" for="useWorkflow">
                    Use Workflow
                  </label>
                </div>
                <div v-if="!folderExecutionConfig.useModel" class="mb-3 ps-4">
                  <input type="text" class="form-control" v-model="folderExecutionConfig.workflow" placeholder="Enter workflow ID (e.g. reasoning)">
                </div>
                
                <div class="mt-4">
                  <label class="form-label">Image Test Options</label>
                  <div class="form-check">
                    <input class="form-check-input" type="checkbox" v-model="folderExecutionConfig.regenerateImages" id="regenerateImages">
                    <label class="form-check-label" for="regenerateImages">
                      Regenerate images (instead of comparing existing ones)
                    </label>
                    <small class="form-text text-muted d-block">
                      When enabled, image tests will generate new images instead of using existing ones.
                      This will consume API credits but allows testing the current state of image generation.
                    </small>
                  </div>
                </div>
                
                <div class="alert alert-warning mt-3">
                  <p class="mb-0">
                    <strong>Note:</strong> If left empty, each test set will use its original item settings.
                  </p>
                </div>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeFolderExecutionModal">Cancel</button>
            <button type="button" class="btn btn-primary" @click="executeTestsInFolder" 
                   :disabled="folderExecutionLoading">
              Execute Tests
            </button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Run Results Modal -->
    <div v-if="showRunResultsModal" class="modal-backdrop fade show"></div>
    <div v-if="showRunResultsModal" class="modal d-block" role="dialog">
      <div class="modal-dialog modal-xl" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Run Results: {{ selectedRun && selectedRun.name }}</h5>
            <button type="button" class="btn-close" @click="closeRunResultsModal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div v-if="runResultsLoading" class="text-center py-4">
              <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
            </div>
            <div v-else>
              <div class="mb-3 d-flex justify-content-between">
                <div>
                  <span v-if="selectedRun" class="me-2">
                    <strong>Status:</strong> 
                    <span :class="getRunStatusClass(selectedRun.status)">{{ selectedRun.status }}</span>
                  </span>
                  <span v-if="selectedRun && selectedRun.completedAt">
                    <strong>Completed:</strong> {{ formatDateTime(selectedRun.completedAt) }}
                  </span>
                </div>
                <div v-if="selectedRun && selectedRun.statusCounts" class="d-flex gap-2">
                  <span v-if="selectedRun.statusCounts.PASSED" class="badge bg-success me-1">
                    {{ selectedRun.statusCounts.PASSED }} Passed
                  </span>
                  <span v-if="selectedRun.statusCounts.FAILED" class="badge bg-danger me-1">
                    {{ selectedRun.statusCounts.FAILED }} Failed
                  </span>
                  <span v-if="selectedRun.statusCounts.ERROR" class="badge bg-warning me-1">
                    {{ selectedRun.statusCounts.ERROR }} Error
                  </span>
                </div>
              </div>
              
              <div v-if="runResults.length > 0">
                <table class="table table-striped">
                  <thead>
                    <tr>
                      <th>Evaluation</th>
                      <th>Test Item</th>
                      <th>Status</th>
                      <th>Message</th>
                      <th>Details</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="result in runResults" :key="result.id">
                      <td>
                        {{ getEvaluationName(result.evaluationId) }}
                      </td>
                      <td>
                        {{ getTestItemIdentifier(result.testSetItemId) }}
                      </td>
                      <td>
                        <span :class="getResultStatusClass(result.status)">{{ result.status }}</span>
                      </td>
                      <td>{{ result.message }}</td>
                      <td>
                        <button v-if="result.details" class="btn btn-sm btn-outline-info" @click="viewResultDetails(result)">
                          View Details
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div v-else class="alert alert-info">
                No results available for this run.
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeRunResultsModal">Close</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./TestSetsTab.ts"></script>

<style scoped>
.test-set-details {
  transition: all 0.3s ease;
  border-left: 4px solid #0d6efd;
  box-shadow: 0 0 10px rgba(0,0,0,0.1) inset;
}

.btn-link {
  cursor: pointer;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
}

.btn-link:hover {
  background-color: rgba(13, 110, 253, 0.1);
}

.border-start {
  border-left: 2px solid #dee2e6 !important;
}

.list-group-item {
  border-radius: 0.375rem;
  margin-bottom: 0.5rem;
  border: 1px solid #dee2e6;
}

.form-check-input {
  cursor: pointer;
}

.badge {
  font-weight: 400;
}

.modal-open {
  overflow: hidden;
}

.modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 1040;
  width: 100vw;
  height: 100vh;
  background-color: #000;
  opacity: 0.5;
}

.modal {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 1050;
  width: 100%;
  height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  outline: 0;
}
</style>