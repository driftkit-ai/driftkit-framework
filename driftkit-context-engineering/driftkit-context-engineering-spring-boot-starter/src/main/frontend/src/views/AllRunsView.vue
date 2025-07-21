<template>
  <div class="all-runs-view" :class="{ 'is-tab-view': isTabView }">
    <div class="header-section" v-if="!isTabView">
      <h1>Evaluation Runs</h1>
      <div class="refresh-controls">
        <button @click="loadAllRuns" class="refresh-button" :disabled="loading">
          <i class="fas fa-sync-alt" :class="{ 'fa-spin': loading }"></i> Refresh
        </button>
        <div class="auto-refresh-toggle">
          <label class="auto-refresh-label" style="cursor: pointer;">
            <input type="checkbox" v-model="autoRefreshEnabled">
            <span>Auto-refresh (30s)</span>
          </label>
          <div v-if="lastRefreshTime" class="last-refresh">
            Last updated: {{ formatLastRefreshTime(lastRefreshTime) }}
          </div>
        </div>
      </div>
    </div>
    
    <div class="filters-panel">
      <div class="filter-group">
        <label for="testSetFilter">Filter by Test Set:</label>
        <select id="testSetFilter" v-model="testSetFilter" @change="applyFilters" class="form-select">
          <option value="">All Test Sets</option>
          <option v-for="testSet in uniqueTestSets" :key="testSet.id" :value="testSet.id">
            {{ testSet.name }}
          </option>
        </select>
      </div>
      
      <div class="filter-group">
        <label for="statusFilter">Filter by Status:</label>
        <select id="statusFilter" v-model="statusFilter" @change="applyFilters" class="form-select">
          <option value="">All Statuses</option>
          <option value="QUEUED">Queued</option>
          <option value="RUNNING">Running</option>
          <option value="COMPLETED">Completed</option>
          <option value="FAILED">Failed</option>
        </select>
      </div>
    </div>
    
    <div v-if="loading" class="loading">
      <div class="skeleton-container">
        <div class="skeleton-header"></div>
        <div class="skeleton-row" v-for="i in 5" :key="i"></div>
      </div>
    </div>
    
    <div v-else-if="filteredRuns.length === 0" class="no-runs">
      <div class="empty-state">
        <i class="fas fa-search"></i>
        <h3>No evaluation runs found</h3>
        <p v-if="testSetFilter || statusFilter">No runs match the selected filters. Try adjusting your filters or <button @click="clearFilters" class="clear-filters-btn">clear all filters</button>.</p>
        <p v-else>There are no evaluation runs available. Start by running an evaluation from a test set.</p>
      </div>
    </div>
    
    <div v-else class="runs-table-wrapper">
      <table class="runs-table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Test Set</th>
            <th>Status</th>
            <th>Started</th>
            <th>Completed</th>
            <th>Results</th>
            <th width="140">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="run in filteredRuns" :key="run.id" :class="{ 'failed': run.status === 'FAILED', 'running': run.status === 'RUNNING', 'completed': run.status === 'COMPLETED' }">
            <td class="run-name">{{ run.name }}</td>
            <td>{{ getTestSetName(run.testSetId) }}</td>
            <td>
              <span :class="'status-badge status-' + run.status.toLowerCase()">
                <i class="fas" :class="{
                  'fa-hourglass-half': run.status === 'QUEUED',
                  'fa-cog fa-spin': run.status === 'RUNNING',
                  'fa-check-circle': run.status === 'COMPLETED',
                  'fa-exclamation-circle': run.status === 'FAILED'
                }"></i>
                {{ run.status }}
              </span>
            </td>
            <td>{{ formatDate(run.startedAt) }}</td>
            <td>{{ run.completedAt ? formatDate(run.completedAt) : '-' }}</td>
            <td>
              <div v-if="run.statusCounts" class="status-counts">
                <span class="status-count passed" v-if="run.statusCounts.PASSED">{{ run.statusCounts.PASSED }} <span class="count-label">passed</span></span>
                <span class="status-count failed" v-if="run.statusCounts.FAILED">{{ run.statusCounts.FAILED }} <span class="count-label">failed</span></span>
                <span class="status-count error" v-if="run.statusCounts.ERROR">{{ run.statusCounts.ERROR }} <span class="count-label">errors</span></span>
              </div>
              <span v-else class="no-results">No results yet</span>
            </td>
            <td class="actions-cell">
              <div class="action-buttons">
                <button @click="viewResults(run)" class="action-button view-button" title="View Results">
                  <i class="fas fa-eye"></i> View
                </button>
                <button v-if="run.status === 'COMPLETED' || run.status === 'FAILED'" 
                        @click="rerunEvaluation(run.testSetId)" 
                        class="action-button rerun-button" title="Run Again">
                  <i class="fas fa-redo"></i> Re-run
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, onMounted, computed, onBeforeUnmount, watch } from 'vue';
import axios from 'axios';
import { useRouter, useRoute } from 'vue-router';

export default defineComponent({
  name: 'AllRunsView',
  
  setup() {
    interface Run {
      id: string;
      status: string;
      startedAt: number;
      completedAt?: number;
      statusCounts?: Record<string, number>;
      testSetId: string;
      name: string;
      description?: string;
    }
    
    interface TestSet {
      id: string;
      name: string;
    }
    
    const router = useRouter();
    const route = useRoute();
    
    // Determine if this component is being used as a tab in the Prompts view
    const isTabView = computed(() => {
      return route.path === '/prompts' || route.path.startsWith('/prompts?');
    });
    const allRuns = ref<Run[]>([]);
    const testSets = ref<TestSet[]>([]);
    const loading = ref(true);
    const testSetFilter = ref('');
    const statusFilter = ref('');
    const refreshInterval = ref<number | null>(null);
    const autoRefreshEnabled = ref(true);
    const lastRunsHash = ref('');
    const lastRefreshTime = ref<number | null>(null);
    
    const filteredRuns = computed(() => {
      return allRuns.value.filter(run => {
        if (testSetFilter.value && run.testSetId !== testSetFilter.value) {
          return false;
        }
        
        if (statusFilter.value && run.status !== statusFilter.value) {
          return false;
        }
        
        return true;
      });
    });
    
    // Create a computed property for unique test sets to avoid duplicates in dropdown
    const uniqueTestSets = computed(() => {
      // Get unique test set IDs from both loaded test sets and runs
      const uniqueIds = new Set<string>();
      const uniqueSets: TestSet[] = [];
      
      // First add all loaded test sets
      testSets.value.forEach(testSet => {
        if (!uniqueIds.has(testSet.id)) {
          uniqueIds.add(testSet.id);
          uniqueSets.push(testSet);
        }
      });
      
      // Sort by name
      uniqueSets.sort((a, b) => a.name.localeCompare(b.name));
      
      return uniqueSets;
    });

    const generateRunsHash = (runs: Run[]): string => {
      const simplifiedRuns = runs.map(run => ({
        id: run.id,
        status: run.status,
        completedAt: run.completedAt,
        statusCounts: run.statusCounts ? JSON.stringify(run.statusCounts) : null
      }));
      return JSON.stringify(simplifiedRuns);
    };

    const loadAllRuns = async () => {
      const wasLoading = loading.value;
      if (wasLoading) {
        loading.value = true;
      }
      
      try {
        // First ensure we have all the test sets loaded for proper display
        if (testSets.value.length === 0) {
          await loadAllTestSets();
        }
        
        const creds = localStorage.getItem('credentials');
        const response = await axios.get('/data/v1.0/admin/test-sets/all-runs', {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });
        
        if (response.data.success) {
          const newRuns = response.data.data as Run[];
          
          // Sort runs by start time, newest first
          newRuns.sort((a: Run, b: Run) => b.startedAt - a.startedAt);
          
          const newHash = generateRunsHash(newRuns);
          
          // Check if anything has changed
          if (newHash !== lastRunsHash.value) {
            console.log("Runs data has changed, updating view");
            allRuns.value = newRuns;
            lastRunsHash.value = newHash;
            
            // Check if we have test set data for all runs and reload if needed
            const missingTestSets = newRuns.some(run => 
              run.testSetId && !testSetNameCache.value[run.testSetId]
            );
            
            if (missingTestSets) {
              console.log("Missing test set data detected, reloading test sets");
              await loadAllTestSets();
            }
          }
          
          // Update last refresh time regardless of whether data changed
          lastRefreshTime.value = Date.now();
        } else {
          console.error('Error loading runs:', response.data.message);
        }
      } catch (error) {
        console.error('Error fetching runs:', error);
      } finally {
        if (wasLoading) {
          loading.value = false;
        }
      }
    };
    
    // Load all test sets in one request
    const loadAllTestSets = async () => {
      try {
        const creds = localStorage.getItem('credentials');
        const response = await axios.get('/data/v1.0/admin/test-sets', {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });
        
        if (response.data) {
          testSets.value = response.data;
          
          // Update cache for all test sets
          testSets.value.forEach(ts => {
            testSetNameCache.value[ts.id] = ts.name;
          });
          
          console.log(`Loaded ${testSets.value.length} test sets`);
        } else {
          console.error('Error loading test sets:', response.data ? response.data.message : 'No data received');
        }
      } catch (error) {
        console.error('Error loading test sets:', error);
      }
    };
    
    // Keep loadTestSets as an alias for loadAllTestSets for backward compatibility
    const loadTestSets = loadAllTestSets;
    
    // Apply filters and update URL
    const applyFilters = () => {
      // Update the URL to reflect filter changes
      const query: Record<string, string> = {};
      
      // Only add to query if non-empty
      if (testSetFilter.value) {
        query.testSetId = testSetFilter.value;
      }
      
      if (statusFilter.value) {
        query.status = statusFilter.value;
      }
      
      // Preserve tab parameter if we're in tab view
      if (isTabView.value && route.query.tab) {
        query.tab = route.query.tab as string;
      }
      
      // Update the URL without reloading the page
      router.replace({ 
        path: route.path,
        query 
      }).catch(err => {
        if (err.name !== 'NavigationDuplicated') {
          throw err;
        }
      });
    };
    
    // Clear all applied filters
    const clearFilters = () => {
      testSetFilter.value = '';
      statusFilter.value = '';
      
      // Update URL to remove filters
      const query: Record<string, string> = {};
      
      // Preserve tab parameter if we're in tab view
      if (isTabView.value && route.query.tab) {
        query.tab = route.query.tab as string;
      }
      
      router.replace({ 
        path: route.path,
        query 
      }).catch(err => {
        if (err.name !== 'NavigationDuplicated') {
          throw err;
        }
      });
    };
    
    // Format date from timestamp
    const formatDate = (timestamp: number) => {
      return new Date(timestamp).toLocaleString();
    };
    
    // Format the last refresh time in a relative way
    const formatLastRefreshTime = (timestamp: number) => {
      // If auto-refresh is disabled, show the exact time instead of relative time
      if (!autoRefreshEnabled.value) {
        return new Date(timestamp).toLocaleTimeString();
      }
      
      // Otherwise show relative time for auto-refresh mode
      const now = Date.now();
      const diffSeconds = Math.floor((now - timestamp) / 1000);
      
      if (diffSeconds < 10) {
        return 'just now';
      } else if (diffSeconds < 60) {
        return `${diffSeconds} seconds ago`;
      } else if (diffSeconds < 3600) {
        const minutes = Math.floor(diffSeconds / 60);
        return `${minutes} minute${minutes > 1 ? 's' : ''} ago`;
      } else {
        return new Date(timestamp).toLocaleTimeString();
      }
    };
    
    // Cache for test set names to avoid redundant lookups
    const testSetNameCache = ref<Record<string, string>>({});
    
    // Get test set name by ID - simplified without making network requests
    const getTestSetName = (testSetId: string) => {
      if (!testSetId) return 'Unknown';
      
      // First check the cache
      if (testSetNameCache.value[testSetId]) {
        return testSetNameCache.value[testSetId];
      }
      
      // Look it up in the array
      const testSet = testSets.value.find(ts => ts.id === testSetId);
      if (testSet) {
        // Found it, cache and return
        testSetNameCache.value[testSetId] = testSet.name;
        return testSet.name;
      }
      
      // Not found, return placeholder
      return 'Unknown';
    };
    
    const viewResults = (run: Run) => {
      router.push({
        path: '/prompts',
        query: { tab: 'runResults', runId: run.id }
      });
    };
    
    const rerunEvaluation = async (testSetId: string) => {
      try {
        const creds = localStorage.getItem('credentials');
        const response = await axios.post(`/data/v1.0/admin/test-sets/${testSetId}/quick-run`, {}, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });
        
        if (response.data.success) {
          allRuns.value.unshift(response.data.data);
          loadAllRuns(); // Refresh the data immediately after starting a new run
        } else {
          alert('Error starting evaluation run: ' + response.data.message);
        }
      } catch (error) {
        console.error('Error starting evaluation run:', error);
        alert('Error starting evaluation run');
      }
    };
    
    const startAutoRefresh = () => {
      if (refreshInterval.value) {
        clearInterval(refreshInterval.value);
      }
      
      refreshInterval.value = window.setInterval(() => {
        if (autoRefreshEnabled.value) {
          loadAllRuns();
        }
      }, 30000);
    };
    
    // Watch for URL query changes
    watch(
      () => route.query,
      (query) => {
        // Only update local state if it's different from URL
        const urlTestSetId = query.testSetId as string;
        if (urlTestSetId !== undefined && urlTestSetId !== testSetFilter.value) {
          testSetFilter.value = urlTestSetId;
        }
        
        const urlStatus = query.status as string;
        if (urlStatus !== undefined && urlStatus !== statusFilter.value) {
          statusFilter.value = urlStatus;
        }
      },
      { deep: true }
    );
    
    // Watch for changes in autoRefreshEnabled
    watch(autoRefreshEnabled, (newValue) => {
      console.log('Auto refresh changed to:', newValue);
      if (newValue) {
        // Start the auto-refresh interval
        startAutoRefresh();
      } else {
        // Stop the auto-refresh interval
        if (refreshInterval.value) {
          clearInterval(refreshInterval.value);
          refreshInterval.value = null;
        }
        
        // Update last refresh time when auto-refresh is turned off
        // This ensures the timestamp is current and won't stay as "just now" forever
        lastRefreshTime.value = Date.now();
      }
    });
    
    onMounted(async () => {
      // Check URL for filters
      if (route.query.testSetId) {
        testSetFilter.value = route.query.testSetId as string;
      }
      if (route.query.status) {
        statusFilter.value = route.query.status as string;
      }
      
      // Load all test sets first
      await loadAllTestSets();
      
      // Then load all runs
      await loadAllRuns();
      lastRunsHash.value = generateRunsHash(allRuns.value);
      
      startAutoRefresh();
    });
    
    onBeforeUnmount(() => {
      if (refreshInterval.value) {
        clearInterval(refreshInterval.value);
      }
    });
    
    return {
      allRuns,
      testSets,
      uniqueTestSets,
      loading,
      testSetFilter,
      statusFilter,
      filteredRuns,
      loadAllRuns,
      loadTestSets,
      loadAllTestSets,
      applyFilters,
      clearFilters,
      formatDate,
      formatLastRefreshTime,
      getTestSetName,
      viewResults,
      rerunEvaluation,
      autoRefreshEnabled,
      lastRefreshTime,
      testSetNameCache,
      isTabView
    };
  }
});
</script>

<style scoped>
.all-runs-view {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
}

.all-runs-view.is-tab-view {
  padding: 0;
  max-width: 100%;
}

.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  border-bottom: 2px solid #f0f0f0;
  padding-bottom: 15px;
}

h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 500;
  color: #333;
}

.filters-panel {
  display: flex;
  margin-bottom: 20px;
  gap: 20px;
  background-color: #f9f9f9;
  padding: 15px;
  border-radius: 6px;
  border: 1px solid #e5e5e5;
}

.filter-group {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.filter-group label {
  margin-bottom: 8px;
  font-weight: 500;
  color: #333;
  font-size: 14px;
}

.form-select {
  padding: 8px 12px;
  border-radius: 4px;
  border: 1px solid #ccc;
  min-width: 200px;
  background-color: white;
  font-size: 14px;
  height: 38px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}

.form-select:focus {
  border-color: #2196F3;
  outline: none;
  box-shadow: 0 0 0 2px rgba(33, 150, 243, 0.25);
}

.refresh-controls {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.refresh-button {
  padding: 8px 15px;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  height: 36px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 5px;
  transition: background-color 0.2s;
}

.refresh-button:hover {
  background-color: #3d9141;
}

.auto-refresh-toggle {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  font-size: 0.85rem;
}

.auto-refresh-label {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.auto-refresh-toggle input {
  margin-right: 6px;
}

.refresh-status {
  font-size: 0.75rem;
  color: #666;
  margin-top: 3px;
  font-style: italic;
}

.last-refresh {
  font-size: 0.75rem;
  color: #666;
  margin-top: 2px;
}

.loading, .no-runs {
  margin: 40px 0;
  text-align: center;
  color: #666;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.loading p, .no-runs p {
  margin-top: 10px;
  font-size: 16px;
}

.skeleton-container {
  width: 100%;
  max-width: 100%;
  border: 1px solid #e5e5e5;
  border-radius: 6px;
  overflow: hidden;
  background-color: white;
}

.skeleton-header {
  height: 48px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-row {
  height: 60px;
  background: linear-gradient(90deg, #f8f8f8 25%, #f0f0f0 50%, #f8f8f8 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-top: 1px solid #e5e5e5;
}

@keyframes shimmer {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}

@keyframes spin {
  to {transform: rotate(360deg);}
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 400px;
  margin: 0 auto;
  padding: 30px;
}

.empty-state i {
  font-size: 48px;
  color: #ccc;
  margin-bottom: 15px;
}

.empty-state h3 {
  font-size: 18px;
  font-weight: 500;
  margin-bottom: 10px;
  color: #555;
}

.empty-state p {
  text-align: center;
  line-height: 1.5;
  color: #777;
}

.clear-filters-btn {
  background: none;
  border: none;
  color: #2196F3;
  padding: 0;
  font-size: inherit;
  text-decoration: underline;
  cursor: pointer;
}

.clear-filters-btn:hover {
  color: #0d8aee;
}

.runs-table-wrapper {
  overflow-x: auto;
  border: 1px solid #e5e5e5;
  border-radius: 6px;
  box-shadow: 0 2px 5px rgba(0,0,0,0.05);
}

.runs-table {
  width: 100%;
  border-collapse: collapse;
  background-color: white;
}

th, td {
  padding: 12px 15px;
  text-align: left;
  border-bottom: 1px solid #e5e5e5;
}

th {
  background-color: #f5f5f5;
  font-weight: 600;
  font-size: 14px;
  color: #444;
  white-space: nowrap;
}

tr:last-child td {
  border-bottom: none;
}

tr:hover {
  background-color: #f8f9fa;
}

tr.failed {
  background-color: rgba(244, 67, 54, 0.05);
}

tr.running {
  background-color: rgba(33, 150, 243, 0.05);
}

tr.completed {
  background-color: rgba(76, 175, 80, 0.05);
}

.run-name {
  font-weight: 500;
}

.status-badge {
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  text-transform: lowercase;
}

.status-badge i {
  font-size: 11px;
}

.status-queued {
  color: #fff3e0;
}

.status-running {
  color: #e3f2fd;
}

.status-completed {
  color: #e8f5e9;
}

.status-failed {
  color: #ffebee;
}

.status-counts {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.status-count {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.status-count.passed {
  color: #4CAF50;
}

.status-count.failed {
  color: #f44336;
}

.status-count.error {
  color: #ff9800;
}

.count-label {
  font-weight: normal;
  opacity: 0.8;
}

.no-results {
  color: #888;
  font-style: italic;
  font-size: 13px;
}

.actions-cell {
  text-align: right;
}

.action-buttons {
  display: flex;
  gap: 5px;
  justify-content: flex-end;
}

.action-button {
  padding: 6px 12px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 5px;
  transition: all 0.2s;
}

.view-button {
  background-color: #2196F3;
  color: white;
}

.view-button:hover {
  background-color: #0d8aee;
}

.rerun-button {
  background-color: #ff9800;
  color: white;
}

.rerun-button:hover {
  background-color: #f08700;
}

/* Responsive adjustments */
@media (max-width: 992px) {
  .filters-panel {
    flex-direction: column;
    gap: 10px;
  }
  
  .header-section {
    flex-direction: column;
    align-items: flex-start;
    gap: 15px;
  }
  
  .refresh-controls {
    align-items: flex-start;
    width: 100%;
  }
}

@media (max-width: 768px) {
  .runs-table {
    font-size: 13px;
  }
  
  th, td {
    padding: 8px 10px;
  }
  
  .action-buttons {
    flex-direction: column;
    gap: 6px;
  }
  
  .action-button {
    width: 100%;
    justify-content: center;
  }
  
  .status-counts {
    flex-direction: column;
    gap: 3px;
  }
}

@media (max-width: 576px) {
  .all-runs-view {
    padding: 10px;
  }
  
  .runs-table-wrapper {
    border-left: none;
    border-right: none;
    border-radius: 0;
    margin-left: -10px;
    margin-right: -10px;
    width: calc(100% + 20px);
  }
}
</style>