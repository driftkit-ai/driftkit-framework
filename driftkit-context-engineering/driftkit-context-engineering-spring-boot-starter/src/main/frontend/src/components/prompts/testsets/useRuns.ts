import { ref, computed, Ref } from 'vue';
import { Run, RunResult, TestSet, NewRun, TestSetItem, Evaluation } from './types';
import * as api from './api';
import { getEvaluationName, getTestItemIdentifier } from './utils';

export default function useRuns(
    selectedTestSet: Ref<TestSet | null>,
    testSetItems: Ref<TestSetItem[]>,
    evaluations: Ref<Evaluation[]>
) {
    // Runs state
    const showRunsModal = ref(false);
    const runsLoading = ref(false);
    const runs = ref<Run[]>([]);
    const showCreateRunPanel = ref(false);
    
    // Run form state
    const promptOptionType = ref('original');
    const overrideModelSettings = ref(false);
    const executionMethodType = ref('modelId'); // By default, use modelId instead of workflow
    const newRun = ref<NewRun>({
        name: '',
        description: '',
        alternativePromptId: '',
        alternativePromptTemplate: '',
        modelId: '',
        workflow: '',
        temperature: null
    });

    // Run results state
    const showRunResultsModal = ref(false);
    const runResultsLoading = ref(false);
    const selectedRun = ref<Run | null>(null);
    const runResults = ref<RunResult[]>([]);

    // Methods
    const openRunsModal = (testSet: TestSet | null = null) => {
        if (testSet) {
            selectedTestSet.value = testSet;
        }

        showRunsModal.value = true;
        fetchRuns();
    };

    const closeRunsModal = () => {
        showRunsModal.value = false;
        showCreateRunPanel.value = false;
    };

    const fetchRuns = async () => {
        if (!selectedTestSet.value) return;

        runsLoading.value = true;
        try {
            runs.value = await api.fetchRuns(selectedTestSet.value.id);
        } catch (error) {
            console.error('Error fetching runs:', error);
        } finally {
            runsLoading.value = false;
        }
    };

    const resetRunForm = () => {
        newRun.value = {
            name: '',
            description: '',
            alternativePromptId: '',
            alternativePromptTemplate: '',
            modelId: '',
            workflow: '',
            temperature: null
        };
        promptOptionType.value = 'original';
        overrideModelSettings.value = false;
        executionMethodType.value = 'modelId';
    };

    const cancelCreateRun = () => {
        showCreateRunPanel.value = false;
        resetRunForm();
    };

    const canCreateRun = computed(() => {
        if (!newRun.value.name) {
            return false;
        }

        // Validate prompt options
        if (promptOptionType.value === 'existing' && !newRun.value.alternativePromptId) {
            return false;
        }

        if (promptOptionType.value === 'custom' && !newRun.value.alternativePromptTemplate) {
            return false;
        }

        // Validate execution method options
        if (overrideModelSettings.value) {
            if (executionMethodType.value === 'modelId' && !newRun.value.modelId) {
                return false;
            }

            if (executionMethodType.value === 'workflow' && !newRun.value.workflow) {
                return false;
            }

            // Ensure they haven't entered both (although UI should prevent this)
            if (newRun.value.modelId && newRun.value.workflow) {
                return false;
            }
        }

        return true;
    });

    const createRun = async () => {
        if (!selectedTestSet.value) return;

        // Clear unused fields based on selected options
        if (promptOptionType.value === 'original') {
            newRun.value.alternativePromptId = '';
            newRun.value.alternativePromptTemplate = '';
        } else if (promptOptionType.value === 'existing') {
            newRun.value.alternativePromptTemplate = '';
        } else if (promptOptionType.value === 'custom') {
            newRun.value.alternativePromptId = '';
        }

        // Handle execution method selection
        if (!overrideModelSettings.value) {
            // Clear both modelId and workflow if not overriding
            newRun.value.modelId = '';
            newRun.value.workflow = '';
            newRun.value.temperature = null;
        } else {
            // Clear the unused execution method
            if (executionMethodType.value === 'modelId') {
                newRun.value.workflow = '';
            } else if (executionMethodType.value === 'workflow') {
                newRun.value.modelId = '';
            }
        }

        try {
            const response = await api.createRun(selectedTestSet.value.id, newRun.value);
            if (response.success) {
                await fetchRuns();
                showCreateRunPanel.value = false;
                resetRunForm();
            } else {
                alert('Failed to create run: ' + response.message);
            }
        } catch (error: any) {
            console.error('Error creating run:', error);
            if (error.response?.data?.message) {
                alert('Failed to create run: ' + error.response.data.message);
            } else {
                alert('Failed to create run. Check the console for details.');
            }
        }
    };

    const quickRun = async () => {
        if (!selectedTestSet.value) return;

        try {
            await api.quickRun(selectedTestSet.value.id);
            setTimeout(() => fetchRuns(), 1000); // Refresh after a short delay
            alert('Evaluation run started successfully');
        } catch (error) {
            console.error('Error creating quick run:', error);
            alert('Failed to start evaluation run');
        }
    };

    const startRun = async (runId: string) => {
        try {
            await api.startRun(runId);
            setTimeout(() => fetchRuns(), 1000); // Refresh after a short delay
        } catch (error) {
            console.error('Error starting run:', error);
            alert('Failed to start run');
        }
    };

    const deleteRun = async (runId: string) => {
        if (!confirm('Are you sure you want to delete this run? All results will be lost.')) {
            return;
        }

        try {
            await api.deleteRun(runId);
            await fetchRuns();
        } catch (error) {
            console.error('Error deleting run:', error);
            alert('Failed to delete run');
        }
    };

    const viewRunResults = (runId: string) => {
        const run = runs.value.find(r => r.id === runId);
        if (run) {
            selectedRun.value = run;
            showRunResultsModal.value = true;
            fetchRunResults(runId);
        }
    };

    const closeRunResultsModal = () => {
        showRunResultsModal.value = false;
        selectedRun.value = null;
        runResults.value = [];
    };

    const fetchRunResults = async (runId: string) => {
        runResultsLoading.value = true;
        try {
            runResults.value = await api.fetchRunResults(runId);
        } catch (error) {
            console.error('Error fetching run results:', error);
        } finally {
            runResultsLoading.value = false;
        }
    };

    const viewResultDetails = (result: RunResult) => {
        alert(JSON.stringify(result.details, null, 2));
        // In a real implementation, you might show a more user-friendly details view
    };

    return {
        // State
        showRunsModal,
        runsLoading,
        runs,
        showCreateRunPanel,
        promptOptionType,
        overrideModelSettings,
        executionMethodType,
        newRun,
        showRunResultsModal,
        runResultsLoading,
        selectedRun,
        runResults,
        canCreateRun,

        // Methods
        openRunsModal,
        closeRunsModal,
        fetchRuns,
        resetRunForm,
        cancelCreateRun,
        createRun,
        quickRun,
        startRun,
        deleteRun,
        viewRunResults,
        closeRunResultsModal,
        fetchRunResults,
        viewResultDetails,

        // Helper methods that use dependencies
        getEvaluationName: (evaluationId: string) => getEvaluationName(evaluationId, evaluations.value),
        getTestItemIdentifier: (testSetItemId: string) => getTestItemIdentifier(testSetItemId, testSetItems.value)
    };
}