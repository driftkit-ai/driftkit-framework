import axios from 'axios';
import { TestRunState } from './types';
import { Ref } from 'vue';
import { Router } from 'vue-router';

export function useTestRunMethods(testRunState: TestRunState, promptForm: Ref<any>, router: Router) {
  // Store for interval ID
  let statusPollingInterval: number | null = null;

  // Open the test runs modal
  const openTestRunsModal = () => {
    if (!promptForm.value.promptId) {
      alert('Please select or enter a prompt ID first');
      return;
    }

    testRunState.showTestRunsModal.value = true;
    testRunState.testRunsLoading.value = true;

    // Reset form
    testRunState.selectedTestSetId.value = '';
    testRunState.selectedTestSet.value = null;
    testRunState.testSetItemCount.value = 0;
    testRunState.newTestRun.value = {
      name: `Run with ${promptForm.value.promptId} - ${new Date().toLocaleString()}`,
      description: `Testing ${promptForm.value.promptId} with test set`,
      alternativePromptId: promptForm.value.promptId,
      // Important: Use the actual prompt message/template here, not just the ID
      alternativePromptTemplate: promptForm.value.message,
      modelId: promptForm.value.modelId || '',
      workflow: promptForm.value.workflow || '',
      temperature: promptForm.value.temperature
    };

    // Prefer workflow if it's set in the prompt form
    if (promptForm.value.workflow) {
      testRunState.executionMethodType.value = 'workflow';
    } else {
      testRunState.executionMethodType.value = 'modelId';
    }

    // Fetch test sets
    fetchTestSets();
  };

  // Close the test runs modal
  const closeTestRunsModal = () => {
    testRunState.showTestRunsModal.value = false;
    // Clear any polling when modal is closed
    if (statusPollingInterval) {
      clearInterval(statusPollingInterval);
      statusPollingInterval = null;
    }
    // Reset current test run
    testRunState.currentTestRun.value = null;
  };

  // Fetch all test sets
  const fetchTestSets = () => {
    const creds = localStorage.getItem('credentials');

    axios.get('/data/v1.0/admin/test-sets', {
      headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
    }).then(response => {
      testRunState.testSets.value = response.data;
    }).catch(error => {
      console.error('Error fetching test sets:', error);
    }).finally(() => {
      testRunState.testRunsLoading.value = false;
    });
  };

  // Load test set evaluations
  const loadTestSetEvaluations = () => {
    if (!testRunState.selectedTestSetId.value) {
      testRunState.selectedTestSet.value = null;
      testRunState.testSetItemCount.value = 0;
      return;
    }

    // Find the test set details
    testRunState.selectedTestSet.value = testRunState.testSets.value.find(ts => ts.id === testRunState.selectedTestSetId.value);

    // Fetch test set items to get the count
    const creds = localStorage.getItem('credentials');

    axios.get(`/data/v1.0/admin/test-sets/${testRunState.selectedTestSetId.value}/items`, {
      headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
    }).then(response => {
      testRunState.testSetItemCount.value = response.data.length || 0;
    }).catch(error => {
      console.error('Error fetching test set items:', error);
      testRunState.testSetItemCount.value = 0;
    });
  };

  // Create a new test run
  const createTestRun = (canCreateTestRun: boolean) => {
    if (!canCreateTestRun) return;

    // Handle execution method selection
    if (testRunState.executionMethodType.value === 'modelId') {
      testRunState.newTestRun.value.workflow = '';
    } else if (testRunState.executionMethodType.value === 'workflow') {
      testRunState.newTestRun.value.modelId = '';
    }

    const creds = localStorage.getItem('credentials');

    axios.post(`/data/v1.0/admin/test-sets/${testRunState.selectedTestSetId.value}/runs`, testRunState.newTestRun.value, {
      headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
    }).then(response => {
      if (response.data.success) {
        // Save the created run to display status
        testRunState.currentTestRun.value = response.data.data;

        // Start the run automatically
        const runId = response.data.data.id;
        axios.post(`/data/v1.0/admin/test-sets/runs/${runId}/start`, {}, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        }).then(() => {
          // Start status polling
          pollTestRunStatus(runId);
        });

        // Keep the modal open to show status
      } else {
        alert('Error creating run: ' + response.data.message);
      }
    }).catch(error => {
      console.error('Error creating run:', error);
      alert('Error creating run: ' + (error.response?.data?.message || error.message));
    });
  };

  // Poll for test run status updates
  const pollTestRunStatus = (runId: string) => {
    // Clear any existing interval
    if (statusPollingInterval) {
      clearInterval(statusPollingInterval);
    }

    // Create function to fetch status
    const fetchRunStatus = () => {
      const creds = localStorage.getItem('credentials');
      console.log("Fetching run status for ID:", runId);
      // First try the endpoint from EvaluationController
      axios.get(`/data/v1.0/admin/runs/${runId}`, {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
      }).catch(() => {
        console.log("First endpoint failed, trying alternative...");
        // If first endpoint fails, try another endpoint format
        return axios.get(`/data/v1.0/admin/test-sets/all-runs`, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });
      }).then(response => {
        if (response.data && response.data.success) {
          console.log("Run status updated, data:", response.data.data);

          // If we got a list of runs (from all-runs endpoint), find our run by ID
          if (Array.isArray(response.data.data)) {
            const foundRun = response.data.data.find((run: { id: string }) => run.id === runId);
            if (foundRun) {
              console.log("Found run in array:", foundRun);
              testRunState.currentTestRun.value = foundRun;
            } else {
              console.log("Run not found in array. Available IDs:", response.data.data.map((r: { id: string }) => r.id));
            }
          } else {
            // Direct response from single run endpoint
            testRunState.currentTestRun.value = response.data.data;
          }

          // If the run is complete, failed, or cancelled, stop polling
          // Note: Keep polling for PENDING status - these have manual evaluations awaiting review
          if (testRunState.currentTestRun.value && 
              (testRunState.currentTestRun.value.status === 'COMPLETED' || 
               testRunState.currentTestRun.value.status === 'FAILED' || 
               testRunState.currentTestRun.value.status === 'CANCELLED')) {
            if (statusPollingInterval) {
              clearInterval(statusPollingInterval);
              statusPollingInterval = null;
            }
          }
        }
      }).catch(error => {
        console.error('Error polling run status:', error);
        // On error, stop polling
        if (statusPollingInterval) {
          clearInterval(statusPollingInterval);
          statusPollingInterval = null;
        }
      });
    };

    // Initial fetch
    fetchRunStatus();

    // Set up interval (every 3 seconds)
    statusPollingInterval = window.setInterval(fetchRunStatus, 3000);
  };

  // Navigate to test results view
  const viewTestResults = (runId: string) => {
    if (runId) {
      router.push({
        path: '/prompt-engineering/evaluation-runs/results',
        query: { runId }
      });
    } else {
      console.error("Cannot view results: missing runId");
    }
  };

  // Clean up interval on unmount
  const cleanupPolling = () => {
    if (statusPollingInterval) {
      clearInterval(statusPollingInterval);
      statusPollingInterval = null;
    }
  };

  return {
    openTestRunsModal,
    closeTestRunsModal,
    fetchTestSets,
    loadTestSetEvaluations,
    createTestRun,
    viewTestResults,
    cleanupPolling
  };
}