<template>
  <div>
    <div class="card mb-4">
      <div class="card-header">
        <div class="d-flex justify-content-between align-items-center">
          <h5 class="mb-0">Model Request Traces</h5>
          <div class="d-flex gap-2">
            <button v-if="totalSelectedItems > 0" class="btn btn-sm btn-success" @click="openAddToTestSetModal">
              Add {{ totalSelectedItems }} Selected to Test Set
            </button>
            <div class="btn-group">
              <button class="btn btn-sm" :class="traceTimeRange === '1hour' ? 'btn-primary' : 'btn-outline-primary'" @click="setTraceTimeRange('1hour')">1 Hour</button>
              <button class="btn btn-sm" :class="traceTimeRange === '3hours' ? 'btn-primary' : 'btn-outline-primary'" @click="setTraceTimeRange('3hours')">3 Hours</button>
              <button class="btn btn-sm" :class="traceTimeRange === '8hours' ? 'btn-primary' : 'btn-outline-primary'" @click="setTraceTimeRange('8hours')">8 Hours</button>
              <button class="btn btn-sm" :class="traceTimeRange === '1day' ? 'btn-primary' : 'btn-outline-primary'" @click="setTraceTimeRange('1day')">1 Day</button>
              <button class="btn btn-sm" :class="traceTimeRange === '1week' ? 'btn-primary' : 'btn-outline-primary'" @click="setTraceTimeRange('1week')">1 Week</button>
            </div>
          </div>
        </div>
        <div class="mt-2 d-flex align-items-center">
          <div class="input-group">
            <span class="input-group-text">From</span>
            <input type="datetime-local" class="form-control" v-model="traceStartTime" @change="fetchTraces">
            <span class="input-group-text">To</span>
            <input type="datetime-local" class="form-control" v-model="traceEndTime" @change="fetchTraces">
          </div>
        </div>
        <div class="mt-2 d-flex flex-wrap align-items-center gap-2">
          <div class="input-group flex-grow-1">
            <span class="input-group-text">Prompt Method</span>
            <select class="form-select" v-model="selectedPromptMethod" @change="onPromptMethodChange">
              <option value="">All Prompts</option>
              <option v-for="method in availablePromptMethods" :key="method" :value="method">
                {{ method }}
              </option>
            </select>
          </div>
          <div class="input-group flex-grow-1">
            <span class="input-group-text">Message ID</span>
            <input type="text" class="form-control" v-model="searchMessageId" placeholder="Search by message ID">
            <button class="btn btn-outline-primary" @click="searchByMessageId">Search</button>
          </div>
        </div>
        <div class="mt-2 d-flex flex-wrap align-items-center gap-2">
          <div class="input-group flex-grow-1">
            <span class="input-group-text">Exclude by Purpose</span>
            <input type="text" class="form-control" v-model="excludePurposeFilter" placeholder="e.g. qa_test_pipeline,debug" @change="fetchTraces">
            <button class="btn btn-outline-danger" @click="clearExcludePurposeFilter" title="Clear filter">×</button>
          </div>
          <div class="form-check form-switch ms-2">
            <input class="form-check-input" type="checkbox" role="switch" id="groupByChatId" v-model="groupByChatId" @change="regroupTraces">
            <label class="form-check-label" for="groupByChatId">
              Group by Chat ID ({{ groupByChatId ? 'Enabled' : 'Disabled' }})
            </label>
          </div>
        </div>
      </div>

      <!-- Prompt metrics section - Only shown when a prompt method is selected -->
      <div v-if="selectedPromptMethod && promptMetrics" class="card-body border-top">
        <h6 class="mb-3">Metrics for Prompt: <span class="badge bg-info">{{ selectedPromptMethod }}</span></h6>

        <div v-if="promptMetricsLoading" class="text-center py-4">
          <div class="spinner-border" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
        <div v-else>
          <!-- Basic statistics cards -->
          <div class="row mb-4">
            <div class="col-md-3">
              <div class="card bg-light">
                <div class="card-body text-center">
                  <h6 class="card-title">Total Requests</h6>
                  <h2>{{ promptMetrics.totalTraces || 0 }}</h2>
                </div>
              </div>
            </div>
            <div class="col-md-3">
              <div class="card bg-light">
                <div class="card-body text-center">
                  <h6 class="card-title">Success Rate</h6>
                  <h2>{{ promptMetrics.successRate ? (promptMetrics.successRate * 100).toFixed(1) + '%' : '0%' }}</h2>
                </div>
              </div>
            </div>
            <div class="col-md-3">
              <div class="card bg-light">
                <div class="card-body text-center">
                  <h6 class="card-title">Total Tokens</h6>
                  <h2>{{ promptMetrics.totalTokens ? promptMetrics.totalTokens.toLocaleString() : 0 }}</h2>
                </div>
              </div>
            </div>
            <div class="col-md-3">
              <div class="card bg-light">
                <div class="card-body text-center">
                  <h6 class="card-title">Median Latency</h6>
                  <h2>{{ promptMetrics.latencyPercentiles?.p50 || 'N/A' }} ms</h2>
                </div>
              </div>
            </div>
          </div>

          <!-- Detailed statistics -->
          <div class="row mb-4">
            <!-- Latency percentiles -->
            <div class="col-md-6">
              <div class="card">
                <div class="card-header">
                  <h6 class="mb-0">Latency Metrics (ms)</h6>
                </div>
                <div class="card-body">
                  <table class="table table-bordered">
                    <thead>
                    <tr>
                      <th>Percentile</th>
                      <th>Latency (ms)</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr>
                      <td>p25</td>
                      <td>{{ promptMetrics.latencyPercentiles?.p25 || 'N/A' }}</td>
                    </tr>
                    <tr>
                      <td>p50 (Median)</td>
                      <td>{{ promptMetrics.latencyPercentiles?.p50 || 'N/A' }}</td>
                    </tr>
                    <tr>
                      <td>p75</td>
                      <td>{{ promptMetrics.latencyPercentiles?.p75 || 'N/A' }}</td>
                    </tr>
                    <tr>
                      <td>p90</td>
                      <td>{{ promptMetrics.latencyPercentiles?.p90 || 'N/A' }}</td>
                    </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            <!-- Token usage -->
            <div class="col-md-6">
              <div class="card">
                <div class="card-header">
                  <h6 class="mb-0">Token Usage</h6>
                </div>
                <div class="card-body">
                  <table class="table table-sm table-striped">
                    <thead>
                    <tr>
                      <th>Model</th>
                      <th>Prompt Tokens</th>
                      <th>Completion Tokens</th>
                      <th>Total</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="(model, idx) in Object.keys(promptMetrics.tokensByModel?.promptTokens || {})" :key="idx">
                      <td>{{ model }}</td>
                      <td>{{ promptMetrics.tokensByModel?.promptTokens[model] || 0 }}</td>
                      <td>{{ promptMetrics.tokensByModel?.completionTokens[model] || 0 }}</td>
                      <td>{{ (promptMetrics.tokensByModel?.promptTokens[model] || 0) + (promptMetrics.tokensByModel?.completionTokens[model] || 0) }}</td>
                    </tr>
                    <tr class="table-dark">
                      <td><strong>Total</strong></td>
                      <td><strong>{{ promptMetrics.totalPromptTokens || 0 }}</strong></td>
                      <td><strong>{{ promptMetrics.totalCompletionTokens || 0 }}</strong></td>
                      <td><strong>{{ promptMetrics.totalTokens || 0 }}</strong></td>
                    </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>

          <div class="row mb-4">
            <!-- Requests by model -->
            <div class="col-md-6">
              <div class="card">
                <div class="card-header">
                  <h6 class="mb-0">Requests by Model</h6>
                </div>
                <div class="card-body">
                  <table class="table table-sm table-striped">
                    <thead>
                    <tr>
                      <th>Model</th>
                      <th>Count</th>
                      <th>Percentage</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="(count, model) in promptMetrics.tracesByModel" :key="model">
                      <td>{{ model }}</td>
                      <td>{{ count }}</td>
                      <td>{{ ((count / promptMetrics.totalTraces) * 100).toFixed(1) }}%</td>
                    </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            <!-- Latency by model -->
            <div class="col-md-6">
              <div class="card">
                <div class="card-header">
                  <h6 class="mb-0">Latency by Model (ms)</h6>
                </div>
                <div class="card-body">
                  <table class="table table-sm table-striped">
                    <thead>
                    <tr>
                      <th>Model</th>
                      <th>p25</th>
                      <th>p50 (Median)</th>
                      <th>p75</th>
                      <th>p90</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="(percentiles, model) in promptMetrics.latencyByModel" :key="model">
                      <td>{{ model }}</td>
                      <td>{{ percentiles.p25 || 'N/A' }}</td>
                      <td>{{ percentiles.p50 || 'N/A' }}</td>
                      <td>{{ percentiles.p75 || 'N/A' }}</td>
                      <td>{{ percentiles.p90 || 'N/A' }}</td>
                    </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>

          <!-- Success/Error stats -->
          <div class="row mb-4">
            <div class="col-md-12">
              <div class="card">
                <div class="card-header">
                  <h6 class="mb-0">Success/Error Statistics</h6>
                </div>
                <div class="card-body">
                  <div class="d-flex">
                    <div class="flex-grow-1 text-center p-3 bg-success text-white me-2 rounded">
                      <h5>Success</h5>
                      <h3>{{ promptMetrics.successCount || 0 }}</h3>
                      <div>{{ promptMetrics.totalTraces ? ((promptMetrics.successCount / promptMetrics.totalTraces) * 100).toFixed(1) + '%' : '0%' }}</div>
                    </div>
                    <div class="flex-grow-1 text-center p-3 bg-danger text-white rounded">
                      <h5>Errors</h5>
                      <h3>{{ promptMetrics.errorCount || 0 }}</h3>
                      <div>{{ promptMetrics.totalTraces ? ((promptMetrics.errorCount / promptMetrics.totalTraces) * 100).toFixed(1) + '%' : '0%' }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="card-body">
        <div v-if="tracesLoading" class="text-center py-4">
          <div class="spinner-border" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
        <div v-else>
          <!-- Grouped traces by context ID -->
          <div v-if="Object.keys(groupedTraces).length === 0" class="alert alert-info my-3">
            No traces found matching your criteria. Try adjusting your search parameters.
            <div class="small mt-2">
              Current grouping mode: {{ groupByChatId ? 'By Chat ID' : 'By Context ID' }}
            </div>
          </div>
          <div v-for="(contextTraces, groupKey) in groupedTraces" :key="(groupByChatId ? 'chat-' : 'ctx-') + groupKey" class="mb-4">
            <div class="card">
              <!-- Chat group header (when grouping by chatId) -->
              <div v-if="groupByChatId && contextTraces.contextIds"
                   class="card-header bg-info bg-opacity-10"
                   data-test-mode="chat-mode">
                <div class="d-flex justify-content-between align-items-start">
                  <div class="d-flex align-items-center">
                    <input type="checkbox"
                           class="me-2"
                           :checked="isMessageTaskSelected(groupKey)"
                           @change="toggleMessageTaskSelection(messageTasksByContextId[groupKey]?.messageId || '')" />
                    <div>
                      <h6 class="mb-0">
                        <i class="bi bi-chat-dots"></i> Chat ID: {{ groupKey }}
                      </h6>
                      <div class="d-flex align-items-center flex-wrap">
                        <small v-if="messageTasksByContextId[groupKey]" class="text-muted me-2">
                          Prompt Method: <span class="badge bg-secondary">{{ getPromptMethod(messageTasksByContextId[groupKey]) }}</span>
                          <span v-if="messageTasksByContextId[groupKey].imageTaskId" class="badge bg-info ms-1" title="Contains image">
                            <i class="bi bi-image">📷</i>
                          </span>
                        </small>
                        <small class="text-info me-2">
                          <span class="badge bg-info">{{ contextTraces.contextIds.length }} context(s)</span>
                        </small>
                      </div>
                    </div>
                  </div>
                  <div class="d-flex flex-wrap justify-content-end align-items-center">
                    <div class="d-flex align-items-center me-2">
                      <span v-if="getContextPurpose(groupKey, contextTraces)"
                            :class="['badge me-2', getContextPurpose(groupKey, contextTraces) === 'qa_test_pipeline' ? 'bg-warning text-dark' : 'bg-secondary']">
                        {{ getContextPurpose(groupKey, contextTraces) }}
                      </span>
                      <span class="badge bg-info me-2">Time: {{ calculateTotalTime(contextTraces) }} ms</span>
                      <span class="badge bg-info">Tokens: {{ calculateTotalTokens(contextTraces) }}</span>
                    </div>
                    <div>
                      <button class="btn btn-sm btn-outline-info me-2" @click="toggleChatContexts(groupKey)">
                        {{ expandedChatContexts.includes(groupKey) ? 'Hide Contexts' : 'Show Contexts' }}
                      </button>
                      <button class="btn btn-sm btn-outline-secondary" @click="toggleMessageDetails(groupKey)">
                        {{ expandedMessageDetails.includes(groupKey) ? 'Hide Message' : 'Show Message' }}
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Standard context header (or when not grouping by chatId) -->
              <div v-if="!groupByChatId || !contextTraces.contextIds"
                   class="card-header bg-light"
                   data-test-mode="context-mode">
                <div class="d-flex justify-content-between align-items-start">
                  <div class="d-flex align-items-center">
                    <input type="checkbox"
                           class="me-2"
                           :checked="isMessageTaskSelected(groupKey)"
                           @change="toggleMessageTaskSelection(messageTasksByContextId[groupKey]?.messageId || '')" />
                    <div>
                      <h6 class="mb-0">
                        <i class="bi bi-box"></i> Context ID: {{ groupKey }}
                      </h6>
                      <div class="d-flex align-items-center flex-wrap">
                        <small v-if="messageTasksByContextId[groupKey]" class="text-muted me-2">
                          <span class="badge bg-secondary">{{ getPromptMethod(messageTasksByContextId[groupKey]) }}</span>
                          <span v-if="messageTasksByContextId[groupKey].imageTaskId" class="badge bg-info ms-1" title="Contains image">
                            <i class="bi bi-image">📷</i>
                          </span>
                        </small>
                        <small v-if="!groupByChatId && contextTraces[0]?.chatId" class="text-info">
                          <span class="badge bg-info">Chat ID: {{ contextTraces[0].chatId }}</span>
                        </small>
                      </div>
                    </div>
                  </div>
                  <div class="d-flex flex-wrap justify-content-end align-items-center">
                    <div class="d-flex align-items-center me-2">
                      <span v-if="getContextPurpose(groupKey, contextTraces)"
                            :class="['badge me-2', getContextPurpose(groupKey, contextTraces) === 'qa_test_pipeline' ? 'bg-warning text-dark' : 'bg-secondary']">
                        {{ getContextPurpose(groupKey, contextTraces) }}
                      </span>
                      <span class="badge bg-info me-2">Time: {{ calculateTotalTime(contextTraces) }} ms</span>
                      <span class="badge bg-info">Tokens: {{ calculateTotalTokens(contextTraces) }}</span>
                    </div>
                    <div>
                      <button class="btn btn-sm btn-outline-info me-2" @click="toggleContextWorkflowSteps(groupKey)">
                        {{ expandedWorkflowSteps.includes(groupKey) ? 'Hide Details' : 'Show Details' }}
                      </button>
                      <button class="btn btn-sm btn-outline-secondary" @click="toggleMessageDetails(groupKey)">
                        {{ expandedMessageDetails.includes(groupKey) ? 'Hide Message' : 'Show Message' }}
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Message Task details for the context (displayed before contexts) -->
              <div v-if="expandedMessageDetails.includes(groupKey)" class="card-body border-bottom bg-light mb-3">
                <!-- Chat Group Message Details -->
                <div v-if="groupByChatId && contextTraces.contextIds">
                  <h6 class="mb-3 text-primary">Chat Overview ({{ contextTraces.contextIds.length }} contexts)</h6>
                  <div class="row">
                    <div class="col-md-4">
                      <h6>First Context Prompt Template</h6>
                      <pre class="border p-2 bg-white code-block" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;" v-html="highlightVariables(getFirstContextPromptTemplate(contextTraces))"></pre>
                    </div>
                    <div class="col-md-4">
                      <h6>First Context Variables</h6>
                      <pre v-if="getFirstContextVariables(contextTraces)"
                           class="border p-2 bg-white code-block json-highlight"
                           style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;"
                           v-html="getFirstContextVariables(contextTraces)"></pre>
                      <pre v-else class="border p-2 bg-white code-block" style="max-height: 200px; overflow: auto;">No variables</pre>
                    </div>
                    <div class="col-md-4">
                      <h6>Last Context Response (Final Result)</h6>
                      <!-- Show image if the last context has an image -->
                      <div v-if="getLastContextDetails(contextTraces)?.messageTask?.imageTaskId" class="text-center">
                        <img :src="getImageUrl(getLastContextDetails(contextTraces)?.messageTask?.imageTaskId)"
                             class="img-fluid border rounded mb-2"
                             style="max-height: 200px; object-fit: contain;"
                             alt="Generated image" />
                        <div class="mt-2">
                          <div class="form-check mb-2">
                            <input class="form-check-input" type="checkbox"
                                   :id="`image-select-last-${groupKey}`"
                                   :checked="isImageTaskSelected(getLastContextDetails(contextTraces)?.messageTask?.imageTaskId)"
                                   @change="toggleImageTaskSelection(getLastContextDetails(contextTraces)?.messageTask?.imageTaskId)" />
                            <label class="form-check-label" :for="`image-select-last-${groupKey}`">
                              Select this image for test set
                            </label>
                          </div>
                          <small class="text-muted d-block">Image ID: {{ getLastContextDetails(contextTraces)?.messageTask?.imageTaskId }}</small>
                        </div>
                      </div>
                      <!-- Regular text response -->
                      <div v-else>
                        <pre class="border p-2 bg-white code-block"
                             style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;"
                             v-html="getLastContextResponse(contextTraces)"></pre>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Standard Context Message Details -->
                <div v-else-if="messageTasksByContextId[groupKey]" class="row">
                  <div class="col-md-4">
                    <h6>Input Message</h6>
                    <pre class="border p-2 bg-white code-block"
                         style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;"
                         v-html="highlightVariables(messageTasksByContextId[groupKey]?.message || 'N/A')"></pre>
                  </div>
                  <div class="col-md-4">
                    <h6>Variables</h6>
                    <pre v-if="contextTraces[0] && contextTraces[0].variables"
                         class="border p-2 bg-white code-block json-highlight"
                         style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;"
                         v-html="formatJSON(contextTraces[0].variables)"></pre>
                    <pre v-else class="border p-2 bg-white code-block" style="max-height: 200px; overflow: auto; white-space: pre-wrap;">No variables</pre>
                  </div>
                  <div class="col-md-4">
                    <h6>Result</h6>
                    <!-- Show image if the message task has an imageTaskId -->
                    <div v-if="messageTasksByContextId[groupKey]?.imageTaskId" class="text-center">
                      <img :src="getImageUrl(messageTasksByContextId[groupKey].imageTaskId)"
                           class="img-fluid border rounded mb-2"
                           style="max-height: 200px; object-fit: contain;"
                           alt="Generated image" />
                      <div class="mt-2">
                        <div class="form-check mb-2">
                          <input class="form-check-input" type="checkbox"
                                 :id="`image-select-mt-${groupKey}`"
                                 :checked="isImageTaskSelected(messageTasksByContextId[groupKey].imageTaskId)"
                                 @change="toggleImageTaskSelection(messageTasksByContextId[groupKey].imageTaskId)" />
                          <label class="form-check-label" :for="`image-select-mt-${groupKey}`">
                            Select this image for test set
                          </label>
                        </div>
                        <small class="text-muted d-block">Image ID: {{ messageTasksByContextId[groupKey].imageTaskId }}</small>
                      </div>
                    </div>
                    <!-- Regular text result -->
                    <div v-else>
                      <pre v-if="isJSON(messageTasksByContextId[groupKey]?.result)"
                           class="border p-2 bg-white code-block json-highlight"
                           style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;"
                           v-html="formatJSON(JSON.parse(messageTasksByContextId[groupKey].result))"></pre>
                      <pre v-else class="border p-2 bg-white code-block" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;">{{ messageTasksByContextId[groupKey]?.result || 'N/A' }}</pre>
                    </div>
                  </div>
                </div>
                <div v-else class="alert alert-warning">
                  No message task found for this context ID
                </div>
              </div>

              <!-- Nested contexts list for chat groups -->
              <div v-if="groupByChatId && contextTraces.contextIds && expandedChatContexts.includes(groupKey)"
                   class="card-body border-bottom bg-light p-2">
                <div class="small text-muted mb-2">Contexts in this chat:</div>
                <div class="context-list">
                  <div v-for="contextId in contextTraces.contextIds" :key="contextId" class="context-item mb-2">
                    <div class="card">
                      <div class="card-header py-2 px-3 bg-secondary bg-opacity-10">
                        <div class="d-flex justify-content-between align-items-start">
                          <div>
                            <h6 class="mb-0"><i class="bi bi-box"></i> Context ID: <strong>{{ contextId }}</strong></h6>
                            <div class="d-flex align-items-center flex-wrap">
                              <small v-if="messageTasksByContextId[contextId]" class="text-muted me-2">
                                Prompt Method: <span class="badge bg-secondary">{{ getPromptMethod(messageTasksByContextId[contextId]) }}</span>
                              </small>
                            </div>
                          </div>
                          <div class="d-flex flex-wrap justify-content-end align-items-center">
                            <div class="d-flex align-items-center me-2">
                              <span class="badge bg-info me-2">Time: {{ calculateTotalTime(getTracesForContext(contextTraces, contextId)) }} ms</span>
                              <span class="badge bg-info">Tokens: {{ calculateTotalTokens(getTracesForContext(contextTraces, contextId)) }}</span>
                            </div>
                            <button class="btn btn-sm btn-outline-info" @click="toggleContextWorkflowSteps(contextId)">
                              {{ expandedWorkflowSteps.includes(contextId) ? 'Hide Details' : 'Show Details' }}
                            </button>
                          </div>
                        </div>
                      </div>
                      <div class="card-body p-0" v-if="expandedWorkflowSteps.includes(contextId)">
                        <div v-if="getTracesForContext(contextTraces, contextId).length === 0" class="p-3">
                          <div class="alert alert-info mb-0">No traces found for this context.</div>
                        </div>
                        <table v-else class="table table-sm table-hover mb-0">
                          <thead>
                          <tr>
                            <th>Request Type</th>
                            <th>Prompt Method</th>
                            <th>Time (ms)</th>
                            <th>Tokens</th>
                            <th>Status</th>
                            <th></th>
                          </tr>
                          </thead>
                          <tbody>
                          <!-- Use a computed property for interleaved rows -->
                          <tr v-for="row in getInterleavedRows(getTracesForContext(contextTraces, contextId))"
                              :key="row.key"
                              :class="[row.type === 'detail' ? 'trace-details-row' : (row.trace && row.trace.trace && row.trace.trace.hasError ? 'table-danger' : '')]">

                            <!-- Main row content -->
                            <template v-if="row.type === 'main'">
                              <td>
                                <span>{{ row.trace ? row.trace.requestType : 'N/A' }}</span>
                                <span v-if="row.trace && isImageGenerationTrace(row.trace)" class="badge bg-info ms-1" title="Contains image">
                                    <i class="bi bi-image">📷</i>
                                  </span>
                              </td>
                              <td>
                                <div v-if="row.trace && row.trace.workflowInfo && row.trace.workflowInfo.workflowStep && showWorkflowSteps">
                                  <strong>{{ row.trace.workflowInfo.workflowType }}</strong>
                                  <div>{{ row.trace.workflowInfo.workflowStep }}</div>
                                </div>
                                <div v-else-if="row.trace && row.trace.workflowInfo && row.trace.workflowInfo.workflowStep">
                                  <strong>{{ row.trace.workflowInfo.workflowType }}</strong>
                                  <small class="text-muted">(workflow steps hidden)</small>
                                </div>
                                <div v-else-if="row.trace && row.trace.promptId">
                                  {{ promptIdToMethodMap[row.trace.promptId] || row.trace.promptId }}
                                </div>
                                <span v-else>N/A</span>
                              </td>
                              <td>{{ row.trace && row.trace.trace ? row.trace.trace.executionTimeMs : 'N/A' }}</td>
                              <td>{{ row.trace && row.trace.trace ? (row.trace.trace.promptTokens + row.trace.trace.completionTokens) : 0 }}</td>
                              <td>
                                <span v-if="row.trace && row.trace.trace && row.trace.trace.hasError" class="badge bg-danger">Error</span>
                                <span v-else class="badge bg-success">Success</span>
                              </td>
                              <td>
                                <button class="btn btn-sm btn-outline-primary"
                                        @click.stop="row.trace && row.trace.id && toggleTraceDetails(row.trace.id, contextId)">
                                  {{ row.trace && row.trace.id && expandedTraces.includes(row.trace.id) ? 'Hide' : 'Show' }}
                                </button>
                              </td>
                            </template>

                            <!-- Detail row content -->
                            <template v-else>
                              <td colspan="6" class="p-3 bg-light">
                                <div class="row">
                                  <div class="col-md-4">
                                    <h6>Prompt Template</h6>
                                    <pre class="border p-2 bg-white code-block" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;" v-html="highlightVariables((row.trace && row.trace.promptTemplate) || 'N/A')"></pre>
                                  </div>
                                  <div class="col-md-4">
                                    <h6>Variables</h6>
                                    <pre v-if="row.trace && row.trace.variables" class="border p-2 bg-white code-block json-highlight" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;" v-html="formatJSON(row.trace.variables)"></pre>
                                    <pre v-else class="border p-2 bg-white code-block" style="max-height: 200px; overflow: auto;">N/A</pre>
                                  </div>
                                  <div class="col-md-4">
                                    <h6>Response</h6>
                                    <!-- Image response -->
                                    <div v-if="row.trace && isImageGenerationTrace(row.trace)" class="text-center">
                                      <div v-if="row.trace.contextId">
                                        <img :src="getImageUrl(row.trace)"
                                             class="img-fluid border rounded mb-2"
                                             style="max-height: 200px; object-fit: contain;"
                                             alt="Generated image" />
                                        <div class="mt-2">
                                          <div class="form-check mb-2">
                                            <input class="form-check-input" type="checkbox"
                                                   :id="`image-select-row-${row.trace.id}`"
                                                   :checked="isImageTaskSelected(getImageTaskIdFromTrace(row.trace))"
                                                   @change="toggleImageTaskSelection(getImageTaskIdFromTrace(row.trace))" />
                                            <label class="form-check-label" :for="`image-select-row-${row.trace.id}`">
                                              Select this image for test set
                                            </label>
                                          </div>
                                          <small class="text-muted d-block">Image ID: {{ getImageTaskIdFromTrace(row.trace) || row.trace.contextId }}</small>
                                        </div>
                                      </div>
                                      <div v-else class="alert alert-warning">Image ID not available</div>
                                    </div>
                                    <!-- Regular response -->
                                    <div v-else>
                                      <pre v-if="row.trace && row.trace.response && isJSON(row.trace.response)" class="border p-2 bg-white code-block json-highlight" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;" v-html="formatJSON(JSON.parse(row.trace.response))"></pre>
                                      <pre v-else class="border p-2 bg-white code-block" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;">{{ row.trace && (row.trace.response || row.trace.errorMessage) || 'N/A' }}</pre>
                                    </div>
                                  </div>
                                </div>
                              </td>
                            </template>
                          </tr>
                          </tbody>
                        </table>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Standard traces table - only for non-chat groups or when not grouping by chatId -->
              <div v-if="!groupByChatId || !contextTraces.contextIds" class="card-body p-0">
                <!-- Main traces table - show only if context is in expandedWorkflowSteps -->
                <table class="table table-sm table-hover mb-0" v-if="expandedWorkflowSteps.includes(groupKey)">
                  <thead>
                  <tr>
                    <th>Select</th>
                    <th>Request Type</th>
                    <th>Prompt Method</th>
                    <th>Purpose</th>
                    <th>Execution Time (ms)</th>
                    <th>Tokens (P/C)</th>
                    <th>Model</th>
                    <th>Temperature</th>
                    <th>Status</th>
                    <th>Time</th>
                    <th></th>
                  </tr>
                  </thead>
                  <tbody>
                  <template v-for="trace in contextTraces" :key="trace.id">
                    <tr :class="{'table-danger': trace.trace && trace.trace.hasError, 'table-info': isMessageTaskSelected(groupKey)}">
                      <td>
                        <input type="checkbox"
                               :checked="isTraceStepSelected(trace.contextId || groupKey, trace.id)"
                               @change="toggleTraceStepSelection(trace.contextId || groupKey, trace.id)" />
                      </td>
                      <td>
                        <span>{{ trace.requestType }}</span>
                        <span v-if="isImageGenerationTrace(trace)" class="badge bg-info ms-1" title="Contains image">
                            <i class="bi bi-image">📷</i>
                          </span>
                      </td>
                      <td>
                        <div v-if="trace.workflowInfo && trace.workflowInfo.workflowStep && showWorkflowSteps">
                          <strong>{{ trace.workflowInfo.workflowType }}</strong>
                          <div>{{ trace.workflowInfo.workflowStep }}</div>
                        </div>
                        <div v-else-if="trace.workflowInfo && trace.workflowInfo.workflowStep">
                          <strong>{{ trace.workflowInfo.workflowType }}</strong>
                          <small class="text-muted">(workflow steps hidden)</small>
                        </div>
                        <div v-else>
                          {{ promptIdToMethodMap[trace.promptId] || trace.promptId }}
                        </div>
                      </td>
                      <td>
                        <span v-if="trace.purpose === 'qa_test_pipeline'" class="badge bg-warning text-dark">{{ trace.purpose }}</span>
                        <span v-else-if="trace.purpose" class="badge bg-secondary">{{ trace.purpose }}</span>
                        <span v-else>-</span>
                      </td>
                      <td>{{ trace.trace ? trace.trace.executionTimeMs : 'N/A' }}</td>
                      <td>
                          <span
                              v-tooltip="`Prompt: ${trace.trace ? trace.trace.promptTokens : 0}\nCompletion: ${trace.trace ? trace.trace.completionTokens : 0}`"
                          >
                            {{ trace.trace ? (trace.trace.promptTokens + trace.trace.completionTokens) : 0 }}
                          </span>
                      </td>
                      <td>{{ trace.trace ? trace.trace.model : trace.modelId }}</td>
                      <td>{{ trace.trace ? trace.trace.temperature : 'N/A' }}</td>
                      <td>
                        <span v-if="trace.trace && trace.trace.hasError" class="badge bg-danger">Error</span>
                        <span v-else class="badge bg-success">Success</span>
                      </td>
                      <td>{{ formatDateTime(trace.timestamp) }}</td>
                      <td>
                        <button
                            class="btn btn-sm btn-outline-primary"
                            @click="toggleTraceDetails(trace.id)"
                        >
                          {{ expandedTraces.includes(trace.id) ? 'Hide' : 'Show' }}
                        </button>
                      </td>
                    </tr>
                    <tr v-if="expandedTraces.includes(trace.id)" class="trace-details-row">
                      <td colspan="10" class="p-3 bg-light">
                        <div class="row">
                          <div class="col-md-4">
                            <h6>Prompt Template</h6>
                            <pre class="border p-2 bg-white code-block" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;" v-html="highlightVariables(trace.promptTemplate || 'N/A')"></pre>
                            <div v-if="trace.purpose" class="mt-2">
                              <strong>Purpose:</strong> <span class="badge bg-secondary">{{ trace.purpose }}</span>
                            </div>
                          </div>
                          <div class="col-md-4">
                            <h6>Variables</h6>
                            <pre v-if="trace.variables" class="border p-2 bg-white code-block json-highlight" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;" v-html="formatJSON(trace.variables)"></pre>
                            <pre v-else class="border p-2 bg-white code-block" style="max-height: 200px; overflow: auto;">N/A</pre>
                          </div>
                          <div class="col-md-4">
                            <h6>Response</h6>
                            <!-- Image response -->
                            <div v-if="isImageGenerationTrace(trace)" class="text-center">
                              <div v-if="trace.contextId">
                                <img :src="getImageUrl(trace)"
                                     class="img-fluid border rounded mb-2"
                                     style="max-height: 200px; object-fit: contain;"
                                     alt="Generated image" />
                                <div class="mt-2">
                                  <div class="form-check mb-2">
                                    <input class="form-check-input" type="checkbox"
                                           :id="`image-select-${trace.id}`"
                                           :checked="isImageTaskSelected(getImageTaskIdFromTrace(trace))"
                                           @change="toggleImageTaskSelection(getImageTaskIdFromTrace(trace))" />
                                    <label class="form-check-label" :for="`image-select-${trace.id}`">
                                      Select this image for test set
                                    </label>
                                  </div>
                                  <small class="text-muted d-block">Image ID: {{ getImageTaskIdFromTrace(trace) || trace.contextId }}</small>
                                </div>
                              </div>
                              <div v-else class="alert alert-warning">Image ID not available</div>
                            </div>
                            <!-- Regular response -->
                            <div v-else>
                              <pre v-if="isJSON(trace.response)" class="border p-2 bg-white code-block json-highlight" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;" v-html="formatJSON(JSON.parse(trace.response))"></pre>
                              <pre v-else class="border p-2 bg-white code-block" style="max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-word;">{{ trace.response || trace.errorMessage || 'N/A' }}</pre>
                            </div>
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

          <!-- Pagination -->
          <nav v-if="tracesPage.totalPages > 1" aria-label="Traces pagination">
            <ul class="pagination">
              <li class="page-item" :class="{ disabled: tracesPage.number === 0 }">
                <a class="page-link" href="#" @click.prevent="changePage(tracesPage.number - 1)">Previous</a>
              </li>
              <li
                  v-for="i in paginationRange"
                  :key="i"
                  class="page-item"
                  :class="{ active: i === tracesPage.number + 1 }"
              >
                <a class="page-link" href="#" @click.prevent="changePage(i - 1)">{{ i }}</a>
              </li>
              <li class="page-item" :class="{ disabled: tracesPage.number >= tracesPage.totalPages - 1 }">
                <a class="page-link" href="#" @click.prevent="changePage(tracesPage.number + 1)">Next</a>
              </li>
            </ul>
          </nav>
        </div>
      </div>
    </div>

    <!-- Add to Test Set Modal -->
    <div v-if="showAddToTestSetModal" class="modal-backdrop fade show"></div>
    <div v-if="showAddToTestSetModal" class="modal d-block" id="addToTestSetModal" role="dialog">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Add to Test Set</h5>
            <button type="button" class="btn-close" @click="closeAddToTestSetModal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div class="mb-3">
              <label class="form-label">Test Set</label>
              <select class="form-select" v-model="selectedTestSetId">
                <option value="">Create New Test Set</option>
                <option v-for="testSet in availableTestSets" :key="testSet.id" :value="testSet.id">
                  {{ testSet.name }}
                </option>
              </select>
            </div>

            <div v-if="!selectedTestSetId" class="mb-3">
              <label for="newTestSetName" class="form-label">New Test Set Name</label>
              <input type="text" class="form-control" id="newTestSetName" v-model="newTestSetName" placeholder="Test Set Name">
            </div>
            <div v-if="!selectedTestSetId" class="mb-3">
              <label for="newTestSetDescription" class="form-label">Description</label>
              <textarea class="form-control" id="newTestSetDescription" v-model="newTestSetDescription" placeholder="Description"></textarea>
            </div>

            <div class="alert alert-info">
              <p class="mb-0">
                Selected {{ totalSelectedItems }} items to add to test set:<br>
                - {{ selectedMessageTasks.length }} message tasks<br>
                - {{ selectedTraceSteps.length }} trace steps<br>
                - <span :class="{'text-success fw-bold': selectedImageTasks.length > 0}">
                    {{ selectedImageTasks.length }} image tasks
                  </span>
              </p>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeAddToTestSetModal">Cancel</button>
            <button type="button" class="btn btn-primary" @click="handleAddToTestSet" :disabled="addingToTestSet">
              <span v-if="addingToTestSet" class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span>
              Add to Test Set
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./TracesTab.ts"></script>

<style scoped>
.trace-details-row td {
  border-top: none;
  background-color: rgba(0, 0, 0, 0.05);
}

.code-block {
  font-family: monospace;
  word-wrap: break-word;
}

.json-highlight {
  font-family: monospace;
  word-wrap: break-word;
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

.context-list {
  max-height: 500px;
  overflow-y: auto;
}

.context-item .card {
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.context-item .card-header {
  padding: 0.5rem 1rem;
}

/* Layout improvements */
.card-header .d-flex.justify-content-between {
  min-height: 48px;
}

.card-header .badge {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

@media (max-width: 991px) {
  .card-header .d-flex.flex-wrap {
    margin-top: 0.5rem;
  }
}
</style>