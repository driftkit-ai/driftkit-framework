<template>
  <div class="run-results-view">
    <div class="header">
      <h1>Run Results: {{ run ? run.name : 'Loading...' }}</h1>
      <div v-if="run" class="run-meta">
        <div style="display: grid; grid-template-columns: auto 1fr; gap: 20px; width: 100%;">
          <div class="status">
            <strong>Status: </strong>
            <span :class="'status-' + run.status.toLowerCase()">{{ run.status }}</span>
          </div>
          <div class="timing" style="width: 100%;">
            <strong>Started:</strong> {{ formatDate(run.startedAt) }}
            <span v-if="run.completedAt">
              | <strong>Completed:</strong> {{ formatDate(run.completedAt) }}
              | <strong>Duration:</strong> {{ formatDuration(run.startedAt, run.completedAt) }}
            </span>
          </div>
        </div>
        <div class="test-set" v-if="testSet" style="margin-top: 5px;">
          <strong>Test Set:</strong> 
          <router-link :to="`/prompt-engineering/prompts?testSetId=${testSet.id}`">{{ testSet.name }}</router-link>
        </div>
      </div>
      
      <div v-if="run && run.alternativePromptTemplate" style="margin-top: 10px; width: 100%;">
        <div class="d-flex" style="align-items: center;">
          <button 
            @click="showAlternativeTemplate = !showAlternativeTemplate" 
            class="btn btn-sm btn-outline-primary"
            style="display: flex; align-items: center; gap: 5px;"
          >
            <i :class="showAlternativeTemplate ? 'fas fa-chevron-down' : 'fas fa-chevron-right'"></i>
            <span>Alternative Prompt Template</span>
          </button>
          <span class="ms-2 text-muted">Click to view prompt used for this test run</span>
        </div>
        <div v-if="showAlternativeTemplate" 
             style="margin-top: 10px; padding: 10px; background-color: #f8f9fa; border-radius: 4px; border: 1px solid #e9ecef; width: 100%;">
          <pre style="margin: 0; white-space: pre-wrap; font-size: 0.9em;" 
               v-html="highlightVariables(run.alternativePromptTemplate)"></pre>
        </div>
      </div>
      
      <!-- Pending Run Warning -->
      <div class="alert alert-warning" v-if="run && run.status === 'PENDING'" style="margin-top: 10px; width: 100%;">
        <i class="fas fa-exclamation-triangle me-2"></i>
        <strong>Manual Review Required:</strong> This test run contains evaluations that need manual review.
        Please review all pending items and mark them as passed or failed.
        <span v-if="resultStats.pending > 0">There are <strong>{{ resultStats.pending }}</strong> items awaiting manual review.</span>
      </div>
      
      <div class="results-summary" v-if="resultStats.total > 0">
        <div class="stat-item">
          <strong>Total:</strong> {{ resultStats.total }}
        </div>
        <div class="stat-item passed" v-if="resultStats.passed > 0">
          <strong>Passed:</strong> {{ resultStats.passed }} ({{ (resultStats.passed / resultStats.total * 100).toFixed(1) }}%)
        </div>
        <div class="stat-item failed" v-if="resultStats.failed > 0">
          <strong>Failed:</strong> {{ resultStats.failed }} ({{ (resultStats.failed / resultStats.total * 100).toFixed(1) }}%)
        </div>
        <div class="stat-item error" v-if="resultStats.error > 0">
          <strong>Error:</strong> {{ resultStats.error }} ({{ (resultStats.error / resultStats.total * 100).toFixed(1) }}%)
        </div>
        <div class="stat-item skipped" v-if="resultStats.skipped > 0">
          <strong>Skipped:</strong> {{ resultStats.skipped }} ({{ (resultStats.skipped / resultStats.total * 100).toFixed(1) }}%)
        </div>
        <div class="stat-item pending" v-if="resultStats.pending > 0">
          <strong>Pending:</strong> {{ resultStats.pending }} ({{ (resultStats.pending / resultStats.total * 100).toFixed(1) }}%)
        </div>
        <div class="stat-item" v-if="resultStats.avgProcessingTime">
          <strong>Avg. Time:</strong> {{ (resultStats.avgProcessingTime).toFixed(1) }} ms
        </div>
      </div>
      
      <div class="actions">
        <button @click="goBack" class="btn back-btn">
          <i class="fas fa-arrow-left"></i> Back to Runs
        </button>
        <button @click="rerunEvaluation" class="btn rerun-btn" v-if="run && testSet">
          <i class="fas fa-redo"></i> Re-run
        </button>
      </div>
    </div>
    
    <div v-if="loading" class="loading">
      Loading results...
    </div>
    
    <div v-else-if="results.length === 0" class="no-results">
      No results found for this run.
    </div>
    
    <div v-else class="results-container">
      <div class="filters">
        <div class="filter-group">
          <label for="statusFilter">Filter by Status:</label>
          <select id="statusFilter" v-model="statusFilter" @change="applyFilters">
            <option value="">All Statuses</option>
            <option value="PASSED">Passed</option>
            <option value="FAILED">Failed</option>
            <option value="ERROR">Error</option>
            <option value="SKIPPED">Skipped</option>
            <option value="PENDING">Pending Review</option>
          </select>
        </div>
        
        <div class="filter-group">
          <label for="evaluationFilter">Filter by Evaluation:</label>
          <select id="evaluationFilter" v-model="evaluationFilter" @change="applyFilters">
            <option value="">All Evaluations</option>
            <option v-for="evaluation in evaluations" :key="evaluation.id" :value="evaluation.id">
              {{ evaluation.name }}
            </option>
          </select>
        </div>
        
        <div class="search-group">
          <label for="searchField">Search:</label>
          <input type="text" id="searchField" v-model="searchQuery" @input="applyFilters" placeholder="Search in results...">
        </div>
      </div>
      
      <div class="results-list">
        <div 
          v-for="result in filteredResults" 
          :key="result.id" 
          class="result-item"
          :class="{'expanded': expandedResults.includes(result.id), [result.status.toLowerCase()]: true}"
          @click="toggleResultDetails(result.id)"
        >
          <div class="result-header">
            <div class="result-status">
              <span :class="'badge status-' + result.status.toLowerCase()">{{ result.status }}</span>
            </div>
            <div class="result-title">
              <strong>{{ getEvaluationName(result.evaluationId) }}</strong>
              <span class="result-message">{{ result.message }}</span>
            </div>
            <div class="result-time" v-if="result.processingTimeMs">
              {{ result.processingTimeMs }}ms
            </div>
            <!-- Manual Evaluation Controls -->
            <div class="manual-review-controls" v-if="result.status === 'PENDING'">
              <button @click.stop="approveManualEvaluation(result)" class="btn approve-btn">
                <i class="fas fa-check"></i> Pass
              </button>
              <button @click.stop="rejectManualEvaluation(result)" class="btn reject-btn">
                <i class="fas fa-times"></i> Fail
              </button>
            </div>
            <div class="result-expand-toggle">
              <i :class="expandedResults.includes(result.id) ? 'fas fa-chevron-up' : 'fas fa-chevron-down'"></i>
            </div>
          </div>
          
          <div class="result-details" v-if="expandedResults.includes(result.id)">
            <div class="details-section">
              <h4>Prompt Used</h4>
              <pre v-html="highlightVariables(result.originalPrompt || getTestItemMessage(result.testSetItemId, result))"></pre>
            </div>
            
            <div class="details-section" v-if="result.promptVariables">
              <h4>Variables</h4>
              <pre v-html="formatJSON(prettyPrintJSON(result.promptVariables))"></pre>
            </div>
            
            <div class="details-section">
              <h4>Results Comparison</h4>
              <div class="results-comparison">
                <div class="expected-result">
                  <h5>Expected Result:</h5>
                  <div v-if="getExpectedImageId(result)" class="image-result-container">
                    <img :src="`/data/v1.0/admin/llm/image/${getExpectedImageId(result)}/resource/0`"
                         alt="Expected image" class="result-image" />
                    <pre>{{ getTestItemResult(result.testSetItemId) }}</pre>
                  </div>
                  <pre v-else-if="isJson(getTestItemResult(result.testSetItemId))" v-html="formatJSON(prettyPrintJSON(getTestItemResult(result.testSetItemId)))"></pre>
                  <pre v-else>{{ getTestItemResult(result.testSetItemId) }}</pre>
                </div>
                <div class="actual-result">
                  <h5>Actual Result:</h5>
                  <div v-if="isImageResult(result)" class="image-result-container">
                    <img :src="`/data/v1.0/admin/llm/image/${result.imageTaskId || result.details?.imageTaskId}/resource/0`"
                         alt="Generated image" class="result-image" 
                         v-if="result.imageTaskId || result.details?.imageTaskId" />
                    <!-- Show image for existing images (using the same image ID as expected) -->
                    <img :src="`/data/v1.0/admin/llm/image/${getExpectedImageId(result)}/resource/0`" 
                         alt="Existing image" class="result-image" 
                         v-else-if="result.modelResult && result.modelResult.includes('Using existing image') && getExpectedImageId(result)" />
                    <pre>{{ result.modelResult || getActualResultFromContext(result) }}</pre>
                  </div>
                  <pre v-else-if="isJson(result.modelResult || getActualResultFromContext(result))" 
                    v-html="formatJSON(prettyPrintJSON(result.modelResult || getActualResultFromContext(result)))"></pre>
                  <pre v-else>{{ result.modelResult || getActualResultFromContext(result) }}</pre>
                </div>
              </div>
            </div>
            
            <div class="details-section" v-if="result.details">
              <h4>Evaluation Details</h4>
              <pre v-html="formatJSON(prettyPrintJSON(result.details))"></pre>
            </div>
            
            <div class="details-section error-details" v-if="result.status === 'ERROR' && result.errorDetails">
              <h4>Error Details</h4>
              <pre>{{ result.errorDetails }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, computed, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import axios from 'axios';
import { formatJSON, prettyPrintJSON, highlightVariables } from '@/utils/formatting';

export default defineComponent({
  name: 'RunResultsView',
  props: {
    runId: {
      type: String,
      default: ''
    }
  },
  
  setup(props) {
    const router = useRouter();
    const route = useRoute();
    const run = ref<any>(null);
    const results = ref<any[]>([]);
    const testSet = ref<any>(null);
    const evaluations = ref<any[]>([]);
    const testSetItems = ref<any[]>([]);
    const loading = ref(true);
    const expandedResults = ref<string[]>([]);
    const showAlternativeTemplate = ref(false);
    
    // Filters
    const statusFilter = ref('');
    const evaluationFilter = ref('');
    const searchQuery = ref('');
    
    // Use runId from props or extract from query params
    const runId = computed(() => props.runId || route.query.runId as string);
    
    // Calculate statistics
    const resultStats = computed(() => {
      const stats: any = {
        total: results.value.length,
        passed: 0,
        failed: 0,
        error: 0,
        skipped: 0,
        pending: 0,
        totalTime: 0,
        avgProcessingTime: 0
      };
      
      results.value.forEach(result => {
        if (result.status === 'PASSED') stats.passed++;
        else if (result.status === 'FAILED') stats.failed++;
        else if (result.status === 'ERROR') stats.error++;
        else if (result.status === 'SKIPPED') stats.skipped++;
        else if (result.status === 'PENDING') stats.pending++;
        
        if (result.processingTimeMs) {
          stats.totalTime += result.processingTimeMs;
        }
      });
      
      if (stats.total > 0 && stats.totalTime > 0) {
        stats.avgProcessingTime = stats.totalTime / stats.total;
      }
      
      return stats;
    });
    
    // Filtered results based on selection
    const filteredResults = computed(() => {
      return results.value.filter(result => {
        // Filter by status
        if (statusFilter.value && result.status !== statusFilter.value) {
          return false;
        }
        
        // Filter by evaluation
        if (evaluationFilter.value && result.evaluationId !== evaluationFilter.value) {
          return false;
        }
        
        // Search in result contents
        if (searchQuery.value) {
          const query = searchQuery.value.toLowerCase();
          const message = (result.message || '').toLowerCase();
          const originalPrompt = (result.originalPrompt || '').toLowerCase();
          const modelResult = (result.modelResult || '').toLowerCase();
          
          if (message.includes(query) || originalPrompt.includes(query) || modelResult.includes(query)) {
            return true;
          }
          
          // Search in variables if they exist
          if (result.promptVariables && JSON.stringify(result.promptVariables).toLowerCase().includes(query)) {
            return true;
          }
          
          // Search in details if they exist
          if (result.details && JSON.stringify(result.details).toLowerCase().includes(query)) {
            return true;
          }
          
          return false;
        }
        
        return true;
      });
    });
    
    // Load run details
    const loadRun = async () => {
      if (!runId.value) return;
      
      try {
        const creds = localStorage.getItem('credentials');
        const response = await axios.get(`/data/v1.0/admin/runs/${runId.value}`, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });
        
        if (response.data.success) {
          run.value = response.data.data;
          
          // Load the associated test set
          loadTestSet(run.value.testSetId);
        } else {
          console.error('Error loading run:', response.data.message);
        }
      } catch (error) {
        console.error('Error fetching run:', error);
      }
    };
    
    // Load test set details
    const loadTestSet = async (testSetId: string) => {
      try {
        const creds = localStorage.getItem('credentials');
        const response = await axios.get(`/data/v1.0/admin/test-sets/${testSetId}`, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });
        
        if (response.data.success) {
          testSet.value = response.data.data;
        } else {
          console.error('Error loading test set:', response.data.message);
        }
      } catch (error) {
        console.error('Error fetching test set:', error);
      }
    };
    
    // Load test set items
    const loadTestSetItems = async (testSetId: string) => {
      try {
        const creds = localStorage.getItem('credentials');
        // Fix: Adjusted response format based on the API
        const response = await axios.get(`/data/v1.0/admin/test-sets/${testSetId}/items`, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });
        
        console.log("Test set items response:", response.data);
        // Check if the data is in response.data or response.data.data
        if (response.data.success && response.data.data) {
          console.log("Loaded test set items from response.data.data:", response.data.data);
          testSetItems.value = response.data.data;
        } else if (Array.isArray(response.data)) {
          // API might return array directly
          console.log("Loaded test set items from direct array:", response.data);
          testSetItems.value = response.data;
        } else {
          console.error('Error loading test set items:', response.data.message);
        }
      } catch (error) {
        console.error('Error fetching test set items:', error);
      }
    };
    
    // Load evaluations
    const loadEvaluations = async (testSetId: string) => {
      try {
        const creds = localStorage.getItem('credentials');
        const response = await axios.get(`/data/v1.0/admin/test-sets/${testSetId}/evaluations`, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });
        
        if (response.data.success) {
          evaluations.value = response.data.data;
        } else {
          console.error('Error loading evaluations:', response.data.message);
        }
      } catch (error) {
        console.error('Error fetching evaluations:', error);
      }
    };
    
    // Load results for a run
    const loadResults = async () => {
      if (!runId.value) return;
      
      loading.value = true;
      
      try {
        const creds = localStorage.getItem('credentials');
        const response = await axios.get(`/data/v1.0/admin/test-sets/runs/${runId.value}/results`, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });
        
        if (response.data.success) {
          results.value = response.data.data || [];
          
          // Sort results: errors first, then failed, then passed
          results.value.sort((a, b) => {
            const statusOrder: Record<string, number> = { ERROR: 0, FAILED: 1, PASSED: 2, SKIPPED: 3 };
            return statusOrder[a.status as keyof typeof statusOrder] - statusOrder[b.status as keyof typeof statusOrder];
          });
          
          // If there are errors or failures, expand the first one
          if (results.value.length > 0) {
            const firstError = results.value.find(r => r.status === 'ERROR');
            const firstFailure = results.value.find(r => r.status === 'FAILED');
            
            if (firstError) {
              expandedResults.value = [firstError.id];
            } else if (firstFailure) {
              expandedResults.value = [firstFailure.id];
            }
          }
        } else {
          console.error('Error loading results:', response.data.message);
        }
      } catch (error) {
        console.error('Error fetching results:', error);
      } finally {
        loading.value = false;
      }
    };
    
    // Format date
    const formatDate = (timestamp: number) => {
      return new Date(timestamp).toLocaleString();
    };
    
    // Format duration
    const formatDuration = (start: number, end: number) => {
      const durationMs = end - start;
      
      if (durationMs < 1000) {
        return `${durationMs}ms`;
      } else if (durationMs < 60000) {
        return `${(durationMs / 1000).toFixed(1)}s`;
      } else {
        const minutes = Math.floor(durationMs / 60000);
        const seconds = ((durationMs % 60000) / 1000).toFixed(1);
        return `${minutes}m ${seconds}s`;
      }
    };
    
    // Apply filters (just here for clarity, actual filtering done in computed property)
    const applyFilters = () => {
      // This function is called on filter changes, but the filtering is done in the computed property
    };
    
    // Toggle result details
    const toggleResultDetails = (resultId: string) => {
      if (expandedResults.value.includes(resultId)) {
        expandedResults.value = expandedResults.value.filter(id => id !== resultId);
      } else {
        expandedResults.value.push(resultId);
      }
    };
    
    // Get evaluation name
    const getEvaluationName = (evaluationId: string) => {
      const evaluation = evaluations.value.find(e => e.id === evaluationId);
      return evaluation ? evaluation.name : 'Unknown Evaluation';
    };
    
    // Get test item message
    const getTestItemMessage = (itemId: string, result?: any) => {
      console.log("Getting message for itemId:", itemId);
      
      // For original message, we don't use the alternative template here
      // Just check the result object or fall back to the test item
      
      // First check the specific result object if provided
      if (result && result.originalPrompt) {
        console.log("Using originalPrompt from result");
        return result.originalPrompt;
      }
      
      // Fallback to the test item's message
      const item = testSetItems.value.find(i => i.id === itemId);
      console.log("Found item:", item);
      
      if (item && item.message) {
        console.log("Using message from item");
        return item.message;
      }
      
      console.log("No message found in any source. Test items count:", testSetItems.value?.length || 0);
      return 'Unknown Item';
    };
    
    // Get test item expected result
    const getTestItemResult = (itemId: string) => {
      const item = testSetItems.value.find(i => i.id === itemId);
      return item ? item.result : 'Unknown Result';
    };
    
    // Get actual result from context if it's not stored directly
    const getActualResultFromContext = (result: any) => {
      return result.modelResult || 'No result available';
    };
    
    // Navigate back
    const goBack = () => {
      router.push('/prompts?tab=evalruns');
    };
    
    // Re-run evaluation
    const rerunEvaluation = async () => {
      if (!run.value || !testSet.value) return;
      
      try {
        const creds = localStorage.getItem('credentials');
        const response = await axios.post(`/data/v1.0/admin/test-sets/${testSet.value.id}/quick-run`, {}, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });
        
        if (response.data.success) {
          router.push('/prompts?tab=evalruns');
        } else {
          alert('Error starting evaluation run: ' + response.data.message);
        }
      } catch (error) {
        console.error('Error starting evaluation run:', error);
        alert('Error starting evaluation run');
      }
    };
    
    // Interface for evaluation result
    interface EvaluationResultType {
      id: string;
      status: string;
      message: string;
      evaluationId: string;
      [key: string]: any; // For other properties
    }
    
    // Handle approve action for manual evaluations
    const approveManualEvaluation = async (result: EvaluationResultType) => {
      await updateManualEvaluation(result, true);
    };
    
    // Handle reject action for manual evaluations  
    const rejectManualEvaluation = async (result: EvaluationResultType) => {
      await updateManualEvaluation(result, false);
    };
    
    // Update a manual evaluation with pass/fail status
    const updateManualEvaluation = async (result: EvaluationResultType, isPassed: boolean) => {
      try {
        const feedback = isPassed 
          ? prompt('Optional feedback for passing this evaluation:')
          : prompt('Please provide a reason for failing this evaluation:');
          
        if (feedback === null) {
          // User cancelled the prompt
          return;
        }
        
        const creds = localStorage.getItem('credentials');
        const response = await axios.post(
          `/data/v1.0/admin/evaluation-results/${result.id}/manual-review`, 
          { passed: isPassed, feedback: feedback },
          { headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) } }
        );
        
        if (response.data.success) {
          // Update the result in our local data
          const index = results.value.findIndex(r => r.id === result.id);
          if (index !== -1) {
            results.value[index] = response.data.data;
          }
        } else {
          alert('Error updating evaluation: ' + response.data.message);
        }
      } catch (error) {
        console.error('Error updating manual evaluation:', error);
        alert('Error updating evaluation');
      }
    };
    
    // Load data when runId changes
    watch(runId, () => {
      if (runId.value) {
        loadRun();
        loadResults();
      }
    }, { immediate: true });
    
    // Load additional data when test set is loaded
    watch(() => run.value?.testSetId, (testSetId) => {
      if (testSetId) {
        console.log("Test set ID updated, loading items and evaluations:", testSetId);
        loadTestSetItems(testSetId);
        loadEvaluations(testSetId);
      }
    });
    
    // Log when results change
    watch(results, (newResults) => {
      console.log("Results updated:", newResults);
    });
    
    // Log when test items change
    watch(testSetItems, (newItems) => {
      console.log("Test set items updated:", newItems);
    });
    
    // Helper method to check if a string is valid JSON
    const isJson = (str: string): boolean => {
      if (!str) return false;
      try {
        JSON.parse(str);
        return true;
      } catch (e) {
        return false;
      }
    };
    
    // Helper method to check if the result is an image result
    const isImageResult = (result: any): boolean => {
      // Check if imageTaskId is directly in the result or in details
      if (result.imageTaskId || (result.details && result.details.imageTaskId)) {
        return true;
      }
      
      // Check if there is an expected image ID for this result (meaning it's an image test)
      if (getExpectedImageId(result)) {
        return true;
      }
      
      // Check if modelResult contains image-related text (as a fallback)
      const modelResult = result.modelResult || getActualResultFromContext(result);
      return typeof modelResult === 'string' && 
             (modelResult.includes("Using existing image") || 
              modelResult.includes("Generated image") || 
              modelResult.includes("image task"));
    };
    
    // Helper method to check if expected result contains image reference
    const isImageExpectedResult = (result: any): boolean => {
      if (!result) return false;
      
      const testItem = testSetItems.value.find(i => i.originalImageTaskId);
      if (testItem && testItem.result === result) {
        return !!testItem.originalImageTaskId;
      }
      
      return false;
    };
    
    // Extract image ID from test result
    const getImageIdFromExpectedResult = (result: any): string | null => {
      if (!result) return null;
      
      // Find the test item that matches this result
      const testItem = testSetItems.value.find(i => i.result === result && i.originalImageTaskId);
      if (testItem && testItem.originalImageTaskId) {
        return testItem.originalImageTaskId;
      }
      
      return null;
    };
    
    // Get the expected image ID for a result
    const getExpectedImageId = (result: any): string | null => {
      if (!result || !result.testSetItemId) return null;
      
      // Find the test item by ID
      const testItem = testSetItems.value.find(i => i.id === result.testSetItemId);
      if (testItem && testItem.originalImageTaskId) {
        return testItem.originalImageTaskId;
      }
      
      return null;
    };
    
    return {
      run,
      results,
      testSet,
      showAlternativeTemplate,
      isJson,
      evaluations,
      testSetItems,
      loading,
      formatJSON,
      prettyPrintJSON,
      highlightVariables,
      expandedResults,
      statusFilter,
      evaluationFilter,
      searchQuery,
      resultStats,
      filteredResults,
      formatDate,
      formatDuration,
      applyFilters,
      toggleResultDetails,
      getEvaluationName,
      getTestItemMessage,
      getTestItemResult,
      getActualResultFromContext,
      goBack,
      rerunEvaluation,
      isImageResult,
      isImageExpectedResult,
      getImageIdFromExpectedResult,
      getExpectedImageId,
      approveManualEvaluation,
      rejectManualEvaluation
    };
  }
});
</script>

<style scoped>
.run-results-view {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.header {
  margin-bottom: 20px;
}

h1 {
  margin-bottom: 10px;
}

.run-meta {
  width: 100%;
  margin-bottom: 15px;
}

.status-running {
  color: #2196F3;
  font-weight: bold;
}

.status-completed {
  color: #4CAF50;
  font-weight: bold;
}

.status-failed {
  color: #f44336;
  font-weight: bold;
}

.status-queued {
  color: #FF9800;
  font-weight: bold;
}

.results-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 15px;
  padding: 10px;
  background-color: #f5f5f5;
  border-radius: 4px;
}

.stat-item {
  padding: 5px 10px;
  border-radius: 4px;
  background-color: #e0e0e0;
}

.stat-item.passed {
  background-color: #dcedc8;
  color: #33691e;
}

.stat-item.failed {
  background-color: #ffcdd2;
  color: #b71c1c;
}

.stat-item.error {
  background-color: #ffe0b2;
  color: #e65100;
}

.stat-item.skipped {
  background-color: #e0e0e0;
  color: #424242;
}

.actions {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.btn {
  padding: 8px 15px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.back-btn {
  background-color: #607d8b;
  color: white;
}

.rerun-btn {
  background-color: #ff9800;
  color: white;
}

.loading, .no-results {
  margin: 20px 0;
  text-align: center;
  font-style: italic;
  color: #666;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
  margin-bottom: 20px;
  padding: 15px;
  background-color: #f5f5f5;
  border-radius: 4px;
}

.filter-group, .search-group {
  display: flex;
  flex-direction: column;
}

.filter-group label, .search-group label {
  margin-bottom: 5px;
  font-weight: bold;
}

.filter-group select, .search-group input {
  padding: 8px;
  border-radius: 4px;
  border: 1px solid #ccc;
  min-width: 200px;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.result-item {
  border: 1px solid #ddd;
  border-radius: 4px;
  overflow: hidden;
  background-color: white;
  cursor: pointer;
}

.result-item.passed {
  border-left: 5px solid #4CAF50;
}

.result-item.failed {
  border-left: 5px solid #f44336;
}

.result-item.error {
  border-left: 5px solid #ff9800;
}

.result-item.skipped {
  border-left: 5px solid #9e9e9e;
}

.result-item.pending {
  border-left: 5px solid #FF9800;
  animation: pending-pulse 2s infinite;
}

@keyframes pending-pulse {
  0% { box-shadow: 0 0 0 0 rgba(255, 152, 0, 0.4); }
  70% { box-shadow: 0 0 0 7px rgba(255, 152, 0, 0); }
  100% { box-shadow: 0 0 0 0 rgba(255, 152, 0, 0); }
}

.result-header {
  display: flex;
  padding: 10px 15px;
  align-items: center;
  background-color: #f9f9f9;
}

.result-status {
  margin-right: 15px;
}

.badge {
  display: inline-block;
  padding: 5px 10px;
  border-radius: 4px;
  color: white;
  font-weight: bold;
}

.result-title {
  flex-grow: 1;
}

.result-message {
  margin-left: 10px;
  color: #666;
}

.result-time {
  color: #666;
  margin-right: 15px;
}

.manual-review-controls {
  display: flex;
  gap: 5px;
  margin-right: 10px;
}

.approve-btn {
  background-color: #4CAF50;
  color: white;
  font-size: 12px;
  padding: 4px 8px;
}

.reject-btn {
  background-color: #f44336;
  color: white;
  font-size: 12px;
  padding: 4px 8px;
}

.result-details {
  padding: 15px;
  border-top: 1px solid #eee;
}

.details-section {
  margin-bottom: 20px;
}

.details-section h4 {
  margin-bottom: 10px;
  padding-bottom: 5px;
  border-bottom: 1px solid #eee;
}

.item-details {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 15px;
}

pre {
  background-color: #f5f5f5;
  padding: 10px;
  border-radius: 4px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  font-size: 13px;
  line-height: 1.5;
  margin: 0; /* Remove margin to ensure alignment */
  height: 100%; /* Make both pre blocks the same height */
  box-sizing: border-box; /* Include padding in height calculation */
}

.error-details pre {
  background-color: #fff8e1;
  color: #e65100;
}

.results-comparison {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  width: 100%;
}

.expected-result, .actual-result {
  min-width: 0;
}

.image-result-container {
  display: flex;
  flex-direction: column;
}

.result-image {
  max-width: 100%;
  max-height: 300px;
  margin-top: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  object-fit: contain;
  background-color: #f8f8f8;
}

.expected-result h5, .actual-result h5 {
  margin-bottom: 10px;
  padding: 5px;
  border-radius: 4px;
  font-size: 14px;
  font-weight: bold;
}

.expected-result h5 {
  background-color: #e3f2fd;
  color: #0d47a1;
}

.actual-result h5 {
  background-color: #f3e5f5;
  color: #6a1b9a;
}
</style>