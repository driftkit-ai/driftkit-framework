import { ref, computed, Ref } from 'vue';
import { Evaluation, TestSet, NewEvaluation, JsonSchemaConfig, KeywordsConfig, LlmConfig } from './types';
import * as api from './api';
import { DEFAULT_LLM_CONFIG, DEFAULT_JSON_SCHEMA_CONFIG, DEFAULT_KEYWORDS_CONFIG, DEFAULT_MANUAL_EVAL_CONFIG, DEFAULT_MANUAL_REVIEW_INSTRUCTIONS } from './constants';

export default function useEvaluations(selectedTestSet: Ref<TestSet | null>) {
    // State
    const showEvaluationsModal = ref(false);
    const evaluationsLoading = ref(false);
    const evaluations = ref<Evaluation[]>([]);
    const globalEvaluations = ref<Evaluation[]>([]);
    const evaluationsTab = ref('testset'); // 'testset' or 'global'
    const showCreateEvaluationPanel = ref(false);
    const isCreatingGlobal = ref(false);
    const showAddEvaluationModal = ref(false);
    
    // Evaluation form
    const newEvaluation = ref<NewEvaluation>({
        name: '',
        description: '',
        type: '',
        config: {
            negateResult: false
        }
    });
    
    // Track if we're in edit mode
    const isEditMode = ref(false);
    const editingEvaluationId = ref('');

    // Configuration objects for different evaluation types
    const jsonSchemaConfig = ref<JsonSchemaConfig>({...DEFAULT_JSON_SCHEMA_CONFIG});

    const keywordsInput = ref('');
    const keywordsConfig = ref<KeywordsConfig>({...DEFAULT_KEYWORDS_CONFIG});
    
    const manualEvalConfig = ref({...DEFAULT_MANUAL_EVAL_CONFIG});

    const llmConfig = ref<LlmConfig>({...DEFAULT_LLM_CONFIG});

    // Evaluation copy functionality
    const showCopyEvaluationModal = ref(false);
    const copyEvaluationLoading = ref(false);
    const selectedEvaluation = ref<Evaluation | null>(null);
    const targetTestSetId = ref('');
    const allTestSets = ref<TestSet[]>([]);

    // Methods
    const openEvaluationsModal = (testSet: TestSet | null = null) => {
        if (testSet) {
            selectedTestSet.value = testSet;
        }

        showEvaluationsModal.value = true;
        evaluationsTab.value = 'testset';
        fetchEvaluations();
        fetchGlobalEvaluations();
    };

    const closeEvaluationsModal = () => {
        showEvaluationsModal.value = false;
        showCreateEvaluationPanel.value = false;
        showAddEvaluationModal.value = false;
        isCreatingGlobal.value = false;
    };

    const fetchEvaluations = async () => {
        if (!selectedTestSet.value) return;

        evaluationsLoading.value = true;
        try {
            evaluations.value = await api.fetchEvaluations(selectedTestSet.value.id);
        } catch (error) {
            console.error('Error fetching evaluations:', error);
        } finally {
            evaluationsLoading.value = false;
        }
    };

    const fetchGlobalEvaluations = async () => {
        evaluationsLoading.value = true;
        try {
            globalEvaluations.value = await api.fetchGlobalEvaluations();
        } catch (error) {
            console.error('Error fetching global evaluations:', error);
        } finally {
            evaluationsLoading.value = false;
        }
    };

    const cancelCreateEvaluation = () => {
        showCreateEvaluationPanel.value = false;
        resetEvaluationForm();
    };

    const resetEvaluationForm = () => {
        newEvaluation.value = {
            name: '',
            description: '',
            type: '',
            config: {
                negateResult: false
            }
        };
        jsonSchemaConfig.value = {...DEFAULT_JSON_SCHEMA_CONFIG};
        keywordsInput.value = '';
        keywordsConfig.value = {...DEFAULT_KEYWORDS_CONFIG};
        llmConfig.value = {...DEFAULT_LLM_CONFIG};
        manualEvalConfig.value = {...DEFAULT_MANUAL_EVAL_CONFIG};
        isCreatingGlobal.value = false;
        isEditMode.value = false;
        editingEvaluationId.value = '';
    };

    const addEvaluationToTestSet = async (evaluation: Evaluation) => {
        if (!selectedTestSet.value) return;

        try {
            await api.addEvaluationToTestSet(selectedTestSet.value.id, evaluation.id);
            await fetchEvaluations();
            alert('Evaluation added to test set successfully');
        } catch (error) {
            console.error('Error adding evaluation to test set:', error);
            alert('Failed to add evaluation to test set');
        }
    };

    const canCreateEvaluation = computed(() => {
        if (!newEvaluation.value.name || !newEvaluation.value.type) {
            return false;
        }

        // Type-specific validation
        switch (newEvaluation.value.type) {
            case 'JSON_SCHEMA':
                return jsonSchemaConfig.value.validateJsonOnly || jsonSchemaConfig.value.jsonSchema;
            case 'CONTAINS_KEYWORDS':
                return keywordsInput.value.trim() !== '';
            case 'LLM_EVALUATION':
                return llmConfig.value.evaluationPrompt.trim() !== '';
            // Additional validation for other types would go here
            default:
                return true;
        }
    });

    const createEvaluation = async () => {
        if (!isEditMode.value && !isCreatingGlobal.value && !selectedTestSet.value) return;

        // Process keywords if necessary
        if (newEvaluation.value.type === 'CONTAINS_KEYWORDS') {
            keywordsConfig.value.keywords = keywordsInput.value
                .split('\n')
                .map(k => k.trim())
                .filter(k => k);
        }

        // Create config based on type
        let config;
        switch (newEvaluation.value.type) {
            case 'JSON_SCHEMA':
                config = {
                    configType: 'JSON_SCHEMA',
                    ...jsonSchemaConfig.value,
                    negateResult: newEvaluation.value.config.negateResult
                };
                break;
            case 'CONTAINS_KEYWORDS':
                config = {
                    configType: 'CONTAINS_KEYWORDS',
                    ...keywordsConfig.value,
                    negateResult: newEvaluation.value.config.negateResult
                };
                break;
            case 'LLM_EVALUATION':
                config = {
                    configType: 'LLM_EVALUATION',
                    ...llmConfig.value,
                    negateResult: newEvaluation.value.config.negateResult
                };
                break;
            case 'EXACT_MATCH':
                config = {
                    configType: 'EXACT_MATCH',
                    negateResult: newEvaluation.value.config.negateResult
                };
                break;
            case 'WORD_COUNT':
                config = {
                    configType: 'WORD_COUNT',
                    negateResult: newEvaluation.value.config.negateResult
                };
                break;
            case 'ARRAY_LENGTH':
                config = {
                    configType: 'ARRAY_LENGTH',
                    negateResult: newEvaluation.value.config.negateResult
                };
                break;
            case 'FIELD_VALUE_CHECK':
                config = {
                    configType: 'FIELD_VALUE_CHECK',
                    negateResult: newEvaluation.value.config.negateResult
                };
                break;
            case 'REGEX_MATCH':
                config = {
                    configType: 'REGEX_MATCH',
                    negateResult: newEvaluation.value.config.negateResult
                };
                break;
            case 'MANUAL_EVALUATION':
                config = {
                    configType: 'MANUAL_EVALUATION',
                    negateResult: newEvaluation.value.config.negateResult,
                    reviewInstructions: manualEvalConfig.value.reviewInstructions
                };
                break;
            default:
                config = {
                    configType: newEvaluation.value.type,
                    negateResult: newEvaluation.value.config.negateResult
                };
        }

        const evaluation = {
            name: newEvaluation.value.name,
            description: newEvaluation.value.description,
            type: newEvaluation.value.type,
            config: config
        };

        try {
            if (isEditMode.value) {
                if (evaluationsTab.value === 'testset' && selectedTestSet.value) {
                    // Update test set-specific evaluation
                    await api.updateTestSetEvaluation(selectedTestSet.value.id, editingEvaluationId.value, evaluation);
                    await fetchEvaluations();
                } else {
                    // Update global evaluation
                    await api.updateEvaluation(editingEvaluationId.value, evaluation);
                    await fetchGlobalEvaluations();
                }
            } else if (isCreatingGlobal.value) {
                // Create a global evaluation
                await api.createGlobalEvaluation(evaluation);
                await fetchGlobalEvaluations();
            } else if (selectedTestSet.value) {
                // Create a test set-specific evaluation
                await api.createTestSetEvaluation(selectedTestSet.value.id, evaluation);
                await fetchEvaluations();
            }
            showCreateEvaluationPanel.value = false;
            resetEvaluationForm();
        } catch (error) {
            console.error('Error saving evaluation:', error);
            alert(`Failed to ${isEditMode.value ? 'update' : 'create'} evaluation`);
        }
    };

    const editEvaluation = (evaluation: Evaluation) => {
        // Set edit mode and store the evaluation ID being edited
        isEditMode.value = true;
        editingEvaluationId.value = evaluation.id;
        
        // Fill the form with evaluation data
        newEvaluation.value = {
            name: evaluation.name,
            description: evaluation.description,
            type: evaluation.type,
            config: {
                negateResult: evaluation.config.negateResult || false
            }
        };
        
        // Set specific configuration based on evaluation type
        switch (evaluation.type) {
            case 'JSON_SCHEMA':
                jsonSchemaConfig.value = {
                    validateJsonOnly: evaluation.config.validateJsonOnly || false,
                    jsonSchema: evaluation.config.jsonSchema || ''
                };
                break;
                
            case 'CONTAINS_KEYWORDS':
                keywordsConfig.value = {
                    keywords: evaluation.config.keywords || [],
                    matchType: evaluation.config.matchType || 'ALL',
                    caseSensitive: evaluation.config.caseSensitive || false
                };
                // Convert keywords array to newline-separated string for the textarea
                keywordsInput.value = (evaluation.config.keywords || []).join('\n');
                break;
                
            case 'LLM_EVALUATION':
                llmConfig.value = {
                    evaluationPrompt: evaluation.config.evaluationPrompt || DEFAULT_LLM_CONFIG.evaluationPrompt,
                    modelId: evaluation.config.modelId || DEFAULT_LLM_CONFIG.modelId,
                    generateFeedback: evaluation.config.generateFeedback !== false,
                    temperature: evaluation.config.temperature || DEFAULT_LLM_CONFIG.temperature
                };
                break;
                
            case 'MANUAL_EVALUATION':
                manualEvalConfig.value = {
                    reviewInstructions: evaluation.config.reviewInstructions || DEFAULT_MANUAL_REVIEW_INSTRUCTIONS
                };
                break;
        }
        
        // Show the create/edit panel
        showCreateEvaluationPanel.value = true;
    };

    const copyEvaluation = (evaluation: Evaluation) => {
        selectedEvaluation.value = evaluation;
        showCopyEvaluationModal.value = true;
        copyEvaluationLoading.value = true;

        // Load all test sets for the target selection
        api.fetchTestSets()
            .then(({ testSets }) => {
                allTestSets.value = testSets;
            })
            .catch(error => {
                console.error('Error fetching all test sets:', error);
            })
            .finally(() => {
                copyEvaluationLoading.value = false;
            });
    };

    const closeCopyEvaluationModal = () => {
        showCopyEvaluationModal.value = false;
        selectedEvaluation.value = null;
        targetTestSetId.value = '';
    };

    const getTestSetName = (id: string) => {
        const ts = allTestSets.value.find(t => t.id === id);
        return ts ? ts.name : id;
    };

    const executeCopyEvaluation = async () => {
        if (!selectedEvaluation.value || !targetTestSetId.value) return;

        copyEvaluationLoading.value = true;
        try {
            await api.copyEvaluation(targetTestSetId.value, selectedEvaluation.value.id);
            alert('Evaluation copied successfully!');
            closeCopyEvaluationModal();
        } catch (error) {
            console.error('Error copying evaluation:', error);
            alert('Failed to copy evaluation');
        } finally {
            copyEvaluationLoading.value = false;
        }
    };

    const deleteEvaluation = async (id: string) => {
        if (!confirm('Are you sure you want to delete this evaluation?')) {
            return;
        }

        try {
            await api.deleteEvaluation(id);
            // Refresh evaluations
            await fetchEvaluations();
            // Also refresh global evaluations if they're displayed
            if (evaluationsTab.value === 'global') {
                await fetchGlobalEvaluations();
            }
        } catch (error) {
            console.error('Error deleting evaluation:', error);
            alert('Failed to delete evaluation');
        }
    };

    return {
        // State
        showEvaluationsModal,
        evaluationsLoading,
        evaluations,
        globalEvaluations,
        evaluationsTab,
        showCreateEvaluationPanel,
        isCreatingGlobal,
        showAddEvaluationModal,
        newEvaluation,
        jsonSchemaConfig,
        keywordsInput,
        keywordsConfig,
        llmConfig,
        manualEvalConfig,
        showCopyEvaluationModal,
        copyEvaluationLoading,
        selectedEvaluation,
        targetTestSetId,
        allTestSets,
        canCreateEvaluation,
        isEditMode,

        // Methods
        openEvaluationsModal,
        closeEvaluationsModal,
        fetchEvaluations,
        fetchGlobalEvaluations,
        cancelCreateEvaluation,
        resetEvaluationForm,
        addEvaluationToTestSet,
        createEvaluation,
        editEvaluation,
        copyEvaluation,
        closeCopyEvaluationModal,
        getTestSetName,
        executeCopyEvaluation,
        deleteEvaluation
    };
}