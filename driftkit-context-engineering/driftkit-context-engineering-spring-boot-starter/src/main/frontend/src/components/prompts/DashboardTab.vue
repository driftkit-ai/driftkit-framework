<template>
  <div class="dashboard-tab">
    <!-- Date Range Filter -->
    <div class="card mb-4">
      <div class="card-body">
        <div class="d-flex justify-content-between align-items-center">
          <h5 class="mb-0">Date Range</h5>
          <div class="btn-group">
            <button class="btn btn-sm" :class="timeRange === '1day' ? 'btn-primary' : 'btn-outline-primary'" @click="timeRange = '1day'; fetchMetrics()">Today</button>
            <button class="btn btn-sm" :class="timeRange === '7days' ? 'btn-primary' : 'btn-outline-primary'" @click="timeRange = '7days'; fetchMetrics()">Last 7 Days</button>
            <button class="btn btn-sm" :class="timeRange === '30days' ? 'btn-primary' : 'btn-outline-primary'" @click="timeRange = '30days'; fetchMetrics()">Last 30 Days</button>
          </div>
        </div>
        <div class="mt-2 d-flex align-items-center">
          <div class="input-group">
            <span class="input-group-text">From</span>
            <input type="date" class="form-control" v-model="customStartDate" @change="useCustomDates = true; fetchMetrics()">
            <span class="input-group-text">To</span>
            <input type="date" class="form-control" v-model="customEndDate" @change="useCustomDates = true; fetchMetrics()">
            <button class="btn btn-outline-secondary" @click="useCustomDates = false; fetchMetrics()">Reset</button>
          </div>
        </div>
      </div>
    </div>
  
    <!-- Metrics Cards -->
    <div class="row mb-4">
      <div class="col-md-3">
        <div class="card">
          <div class="card-body">
            <h5 class="card-title">Total Tasks</h5>
            <div class="d-flex align-items-center">
              <div>
                <h3 class="mb-0">{{ metrics.totalTasks?.toLocaleString() || 0 }}</h3>
              </div>
              <div class="ms-auto">
                <div v-if="dashboardLoading" class="spinner-border spinner-border-sm" role="status">
                  <span class="visually-hidden">Loading...</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div class="col-md-3">
        <div class="card">
          <div class="card-body">
            <h5 class="card-title">Prompt Tokens</h5>
            <div class="d-flex align-items-center">
              <div>
                <h3 class="mb-0">{{ metrics.totalPromptTokens?.toLocaleString() || 0 }}</h3>
              </div>
              <div class="ms-auto">
                <div v-if="dashboardLoading" class="spinner-border spinner-border-sm" role="status">
                  <span class="visually-hidden">Loading...</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div class="col-md-3">
        <div class="card">
          <div class="card-body">
            <h5 class="card-title">Completion Tokens</h5>
            <div class="d-flex align-items-center">
              <div>
                <h3 class="mb-0">{{ metrics.totalCompletionTokens?.toLocaleString() || 0 }}</h3>
              </div>
              <div class="ms-auto">
                <div v-if="dashboardLoading" class="spinner-border spinner-border-sm" role="status">
                  <span class="visually-hidden">Loading...</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div class="col-md-3">
        <div class="card">
          <div class="card-body">
            <h5 class="card-title">Avg Latency (ms)</h5>
            <div class="d-flex align-items-center">
              <div>
                <h3 class="mb-0">{{ metrics.latencyPercentiles?.p50 || 0 }}</h3>
              </div>
              <div class="ms-auto">
                <div v-if="dashboardLoading" class="spinner-border spinner-border-sm" role="status">
                  <span class="visually-hidden">Loading...</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Success/Error Statistics -->
    <div class="row mb-4">
      <div class="col-md-12">
        <div class="card">
          <div class="card-header">
            <h5 class="mb-0">Success/Error Statistics</h5>
          </div>
          <div class="card-body">
            <div class="d-flex">
              <div class="flex-grow-1 text-center p-3 bg-success text-white me-2 rounded">
                <h5>Success</h5>
                <h3>{{ metrics.successCount || 0 }}</h3>
                <div v-if="metrics.successCount || metrics.errorCount">
                  {{ metrics.successCount }} / {{ metrics.successCount + metrics.errorCount }} 
                  ({{ metrics.successRate !== undefined ? (metrics.successRate * 100).toFixed(1) : calculateSuccessRate(metrics.successCount, metrics.successCount + metrics.errorCount) }}%)
                </div>
                <div v-else>0 / 0 (0.0%)</div>
              </div>
              <div class="flex-grow-1 text-center p-3 rounded"
                  :class="metrics.errorCount > 0 ? 'bg-danger text-white' : 'bg-secondary text-white'">
                <h5>Errors</h5>
                <h3>{{ metrics.errorCount || 0 }}</h3>
                <div v-if="metrics.successCount || metrics.errorCount">
                  {{ metrics.errorCount }} / {{ metrics.successCount + metrics.errorCount }} 
                  ({{ calculateErrorRate(metrics.successCount, metrics.successCount + metrics.errorCount) }}%)
                </div>
                <div v-else>0 / 0 (0.0%)</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Latency Percentiles -->
    <div class="row mb-4">
      <div class="col-12">
        <div class="card">
          <div class="card-header">
            <h5 class="mb-0">Latency Percentiles (ms)</h5>
          </div>
          <div class="card-body">
            <div class="d-flex justify-content-between">
              <div class="text-center p-2">
                <h6>p25</h6>
                <h4>{{ metrics.latencyPercentiles?.p25 || 0 }}</h4>
              </div>
              <div class="text-center p-2">
                <h6>p50 (Median)</h6>
                <h4>{{ metrics.latencyPercentiles?.p50 || 0 }}</h4>
              </div>
              <div class="text-center p-2">
                <h6>p75</h6>
                <h4>{{ metrics.latencyPercentiles?.p75 || 0 }}</h4>
              </div>
              <div class="text-center p-2">
                <h6>p90</h6>
                <h4>{{ metrics.latencyPercentiles?.p90 || 0 }}</h4>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="row mb-4">
      <div class="col-md-6">
        <div class="card h-100">
          <div class="card-header d-flex justify-content-between align-items-center">
            <h5 class="mb-0">Top Prompt Methods</h5>
            <div>
              <select class="form-select form-select-sm" v-model="timeRange" @change="fetchMetrics">
                <option value="1day">Last 24 hours</option>
                <option value="7days">Last 7 days</option>
                <option value="30days">Last 30 days</option>
              </select>
            </div>
          </div>
          <div class="card-body">
            <div v-if="dashboardLoading" class="text-center py-4">
              <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
            </div>
            <div v-else>
              <div v-if="formattedPromptMethods.length" class="table-responsive">
                <table class="table table-sm">
                  <thead>
                    <tr>
                      <th>Prompt Method</th>
                      <th>Count</th>
                      <th>Tokens</th>
                      <th>Success / Total Traces</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    <template v-for="(prompt, index) in formattedPromptMethods" :key="index">
                      <tr>
                        <td class="text-truncate" style="max-width: 200px;">{{ prompt.method }}</td>
                        <td>{{ prompt.count }}</td>
                        <td>{{ prompt.tokens.toLocaleString() }}</td>
                        <td>
                          <span 
                            :class="{ 
                              'badge rounded-pill': true, 
                              'bg-success': prompt.successRate === 100,
                              'bg-warning': prompt.successRate < 100 && prompt.successRate >= 70,
                              'bg-danger': prompt.successRate < 70 && prompt.errorCount > 0,
                              'bg-secondary': prompt.successRate < 70 && prompt.errorCount === 0
                            }"
                          >
                            {{ prompt.successCount }} / {{ prompt.successCount + prompt.errorCount }}
                          </span>
                          <span 
                            class="ms-2"
                            :class="{
                              'text-success': prompt.successRate === 100,
                              'text-warning': prompt.successRate < 100 && prompt.successRate >= 70,
                              'text-danger': prompt.successRate < 70 && prompt.errorCount > 0,
                              'text-secondary': prompt.successRate < 70 && prompt.errorCount === 0
                            }"
                          >
                            ({{ prompt.successRate.toFixed(1) }}%)
                          </span>
                        </td>
                        <td>
                          <button class="btn btn-sm btn-outline-primary" 
                                  @click="togglePromptPercentiles(prompt.method)">
                            {{ expandedPromptPercentiles.includes(prompt.method) ? 'Hide Details' : 'Show Details' }}
                          </button>
                        </td>
                      </tr>
                      <!-- Add details row right after each method row if expanded -->
                      <tr v-if="expandedPromptPercentiles.includes(prompt.method)" class="table-light">
                        <td colspan="5">
                          <div class="p-2">
                            <div class="row mb-3">
                              <div class="col-12">
                                <h6>Details for {{ prompt.method }}</h6>
                                
                                <!-- Success/Error mini-stats -->
                                <div class="d-flex mb-3 mt-2">
                                  <div class="flex-grow-1 text-center p-2 bg-success text-white me-2 rounded">
                                    <div class="small">Success Traces</div>
                                    <div class="fw-bold">{{ prompt.successCount }}</div>
                                    <small>{{ prompt.successRate.toFixed(1) }}% of all traces</small>
                                  </div>
                                  <div class="flex-grow-1 text-center p-2 rounded"
                                       :class="prompt.errorCount > 0 ? 'bg-danger text-white' : 'bg-secondary text-white'">
                                    <div class="small">Error Traces</div>
                                    <div class="fw-bold">{{ prompt.errorCount }}</div>
                                    <small>{{ (100 - prompt.successRate).toFixed(1) }}% of all traces</small>
                                  </div>
                                </div>
                                
                                <!-- Usage context -->
                                <div class="alert alert-info mt-3">
                                  <small class="d-block"><strong>Note:</strong> This prompt was used in <strong>{{ prompt.count }}</strong> tasks.</small>
                                  <small class="d-block">Traces represent individual API calls, while tasks are entire user requests.</small>
                                  <small class="d-block">A single task may generate multiple traces if it makes multiple API calls.</small>
                                </div>
                                
                                <!-- Latency percentiles -->
                                <h6 class="mt-3">Latency Percentiles</h6>
                                <div v-if="hasLatencyData(prompt.method)" class="d-flex justify-content-between mt-2">
                                  <div class="text-center p-2">
                                    <div class="small">p25</div>
                                    <div class="fw-bold">{{ getPromptLatencyPercentile(prompt.method, 'p25') }} ms</div>
                                  </div>
                                  <div class="text-center p-2">
                                    <div class="small">p50</div>
                                    <div class="fw-bold">{{ getPromptLatencyPercentile(prompt.method, 'p50') }} ms</div>
                                  </div>
                                  <div class="text-center p-2">
                                    <div class="small">p75</div>
                                    <div class="fw-bold">{{ getPromptLatencyPercentile(prompt.method, 'p75') }} ms</div>
                                  </div>
                                  <div class="text-center p-2">
                                    <div class="small">p90</div>
                                    <div class="fw-bold">{{ getPromptLatencyPercentile(prompt.method, 'p90') }} ms</div>
                                  </div>
                                </div>
                                <div v-else class="text-center p-2 text-muted">
                                  No latency data available for this prompt method
                                </div>
                              </div>
                            </div>
                          </div>
                        </td>
                      </tr>
                    </template>
                  </tbody>
                </table>
              </div>
              <div v-else class="text-center py-4 text-muted">
                No data available
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div class="col-md-6">
        <div class="card h-100">
          <div class="card-header d-flex justify-content-between align-items-center">
            <h5 class="mb-0">Model Usage</h5>
          </div>
          <div class="card-body">
            <div v-if="dashboardLoading" class="text-center py-4">
              <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
            </div>
            <div v-else>
              <div v-if="formattedModelUsage.length" class="table-responsive">
                <table class="table table-sm">
                  <thead>
                    <tr>
                      <th>Model</th>
                      <th>Count</th>
                      <th>Prompt Tokens</th>
                      <th>Completion Tokens</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(model, index) in formattedModelUsage" :key="index">
                      <td>{{ model.model }}</td>
                      <td>{{ model.count }}</td>
                      <td>{{ model.promptTokens.toLocaleString() }}</td>
                      <td>{{ model.completionTokens.toLocaleString() }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div v-else class="text-center py-4 text-muted">
                No data available
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
import axios from 'axios';

export default defineComponent({
  name: 'DashboardTab',
  setup() {
    const metrics = ref<any>({});
    const dashboardLoading = ref(false);
    const timeRange = ref('1day');
    const useCustomDates = ref(false);
    const customStartDate = ref(new Date().toISOString().split('T')[0]);
    const customEndDate = ref(new Date().toISOString().split('T')[0]);
    const expandedPromptPercentiles = ref<string[]>([]);
    
    // Calculate days based on timeRange
    const getDaysFromTimeRange = () => {
      switch (timeRange.value) {
        case '7days': return 7;
        case '30days': return 30;
        default: return 1;
      }
    };
    
    const fetchMetrics = () => {
      dashboardLoading.value = true;
      const creds = localStorage.getItem('credentials');
      
      let startDateStr, endDateStr;
      
      if (useCustomDates.value) {
        // Use custom date selections
        startDateStr = customStartDate.value;
        endDateStr = customEndDate.value;
      } else {
        // Calculate dates based on timeRange
        const endDate = new Date();
        const startDate = new Date();
        startDate.setDate(startDate.getDate() - getDaysFromTimeRange());
        
        // Format dates as ISO strings (YYYY-MM-DD)
        startDateStr = startDate.toISOString().split('T')[0];
        endDateStr = endDate.toISOString().split('T')[0];
        
        // Update the custom date inputs to match the calculated dates
        customStartDate.value = startDateStr;
        customEndDate.value = endDateStr;
      }
      
      axios.get('/data/v1.0/analytics/metrics/daily', {
        params: {
          startDate: startDateStr,
          endDate: endDateStr
        },
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
      }).then(res => {
        metrics.value = res.data.data;
      }).catch(err => {
        console.error('Error fetching metrics:', err);
      }).finally(() => {
        dashboardLoading.value = false;
      });
    };
    
    // Format data for prompt methods table
    const formattedPromptMethods = computed(() => {
      if (!metrics.value?.tasksByPromptMethod) return [];
      
      const result = [];
      const tasksByPromptMethod = metrics.value.tasksByPromptMethod || {};
      const tokensByPromptMethod = metrics.value.tokensByPromptMethod || { promptTokens: {}, completionTokens: {} };
      const latencyByPromptMethod = metrics.value.latencyByPromptMethod || {};
      // Get success/error data
      const successByPromptMethod = metrics.value.successByPromptMethod || {};
      const errorsByPromptMethod = metrics.value.errorsByPromptMethod || {};
      const successRateByPromptMethod = metrics.value.successRateByPromptMethod || {};
      
      // Process each prompt method
      for (const method in tasksByPromptMethod) {
        const count = tasksByPromptMethod[method] || 0;
        const promptTokens = tokensByPromptMethod.promptTokens[method] || 0;
        const completionTokens = tokensByPromptMethod.completionTokens[method] || 0;
        const latency = latencyByPromptMethod[method]?.p90 || 0;
        
        // Get success/error counts
        const successCount = successByPromptMethod[method] || 0;
        const errorCount = errorsByPromptMethod[method] || 0;
        
        // Use the success rate directly from the backend if available, or calculate it
        let successRate;
        if (successRateByPromptMethod[method] !== undefined) {
          successRate = successRateByPromptMethod[method] * 100;
        } else {
          // Fallback calculation if the backend doesn't provide it
          const totalTraces = successCount + errorCount;
          successRate = totalTraces > 0 ? (successCount / totalTraces) * 100 : 100;
        }
        
        result.push({
          method,
          count,
          latency,
          tokens: promptTokens + completionTokens,
          successCount,
          errorCount,
          successRate
        });
      }
      
      // Sort by count (descending)
      return result.sort((a, b) => b.count - a.count);
    });
    
    // No longer needed since we're using v-if directly in the template
    // Left for reference in case we need to revert
    /*
    const promptsWithExpandedDetails = computed(() => {
      if (!formattedPromptMethods.value || formattedPromptMethods.value.length === 0) return [];
      
      return formattedPromptMethods.value.filter(prompt => 
        expandedPromptPercentiles.value.includes(prompt.method)
      );
    });
    */
    
    // Format data for model usage table
    const formattedModelUsage = computed(() => {
      if (!metrics.value?.tasksByModel) return [];
      
      const result = [];
      const tasksByModel = metrics.value.tasksByModel || {};
      const tokensByPromptMethodModel = metrics.value.tokensByPromptMethodModel || { promptTokens: {}, completionTokens: {} };
      
      // Process each model
      for (const model in tasksByModel) {
        const count = tasksByModel[model] || 0;
        
        // Calculate tokens for this model by summing all prompt method + model combinations
        let promptTokens = 0;
        let completionTokens = 0;
        
        // Check each key in tokensByPromptMethodModel to see if it contains this model
        for (const key in tokensByPromptMethodModel.promptTokens) {
          if (key.includes(`:${model}`)) {
            promptTokens += tokensByPromptMethodModel.promptTokens[key] || 0;
          }
        }
        
        for (const key in tokensByPromptMethodModel.completionTokens) {
          if (key.includes(`:${model}`)) {
            completionTokens += tokensByPromptMethodModel.completionTokens[key] || 0;
          }
        }
        
        result.push({
          model,
          count,
          promptTokens,
          completionTokens
        });
      }
      
      // Sort by count (descending)
      return result.sort((a, b) => b.count - a.count);
    });
    
    // Calculate success/error metrics
    const successCount = computed(() => {
      // Initialize with a default of 0
      return metrics.value?.successCount || 0;
    });
    
    const errorCount = computed(() => {
      // Initialize with a default of 0
      return metrics.value?.errorCount || 0;
    });
    
    // Calculate success rate
    const successRate = computed(() => {
      // Use the success rate directly from the backend if available
      if (metrics.value?.successRate !== undefined) {
        return metrics.value.successRate * 100;
      }
      
      // Fallback calculation if the backend doesn't provide it
      const successCount = metrics.value?.successCount || 0;
      const errorCount = metrics.value?.errorCount || 0;
      const totalCount = successCount + errorCount;
      
      if (totalCount === 0) return 0;
      
      return (successCount / totalCount) * 100;
    });
    
    // Helper methods for success/error rate calculations
    const calculateSuccessRate = (successCount: number, totalCount: number) => {
      successCount = successCount || 0;
      totalCount = totalCount || 0;
      if (totalCount === 0) return '0.0';
      return ((successCount / totalCount) * 100).toFixed(1);
    };
    
    const calculateErrorRate = (successCount: number, totalCount: number) => {
      successCount = successCount || 0;
      totalCount = totalCount || 0;
      if (totalCount === 0) return '0.0';
      return (((totalCount - successCount) / totalCount) * 100).toFixed(1);
    };
    
    onMounted(() => {
      fetchMetrics();
    });
    
    // Get latency percentile for a specific prompt method
    const getPromptLatencyPercentile = (method: string, percentile: string) => {
      console.log('Looking for percentile data:', { method, percentile });
      console.log('Available latencyByPromptMethod:', metrics.value?.latencyByPromptMethod);
      
      if (!metrics.value?.latencyByPromptMethod) {
        console.warn('No latencyByPromptMethod data in metrics');
        return 'N/A';
      }
      
      if (!metrics.value.latencyByPromptMethod[method]) {
        console.warn(`No latency data found for method: ${method}`);
        return 'N/A';
      }
      
      const value = metrics.value.latencyByPromptMethod[method][percentile];
      console.log(`Latency for ${method} ${percentile}:`, value);
      
      // Format the value with comma separators if it's a number
      if (value && !isNaN(Number(value))) {
        return Number(value).toLocaleString();
      }
      
      return value || 'N/A';
    };
    
    // Check if latency data exists for a prompt method
    const hasLatencyData = (method: string) => {
      return metrics.value?.latencyByPromptMethod && 
             metrics.value.latencyByPromptMethod[method] &&
             Object.keys(metrics.value.latencyByPromptMethod[method]).length > 0;
    };
    
    // Toggle expanded state for prompt percentiles
    const togglePromptPercentiles = (method: string) => {
      console.log(`Toggling details for method: ${method}`);
      if (expandedPromptPercentiles.value.includes(method)) {
        expandedPromptPercentiles.value = expandedPromptPercentiles.value.filter(m => m !== method);
      } else {
        expandedPromptPercentiles.value.push(method);
        // Log latency data for debugging
        console.log(`Latency data for ${method}:`, 
          metrics.value?.latencyByPromptMethod?.[method] || 'No data available');
      }
    };
    
    return {
      metrics,
      dashboardLoading,
      timeRange,
      useCustomDates,
      customStartDate,
      customEndDate,
      expandedPromptPercentiles,
      fetchMetrics,
      formattedPromptMethods,
      formattedModelUsage,
      successCount,
      errorCount,
      successRate,
      calculateSuccessRate,
      calculateErrorRate,
      getPromptLatencyPercentile,
      togglePromptPercentiles,
      hasLatencyData
    };
  }
});
</script>

<style scoped>
.dashboard-tab {
  margin-top: 15px;
}
</style>