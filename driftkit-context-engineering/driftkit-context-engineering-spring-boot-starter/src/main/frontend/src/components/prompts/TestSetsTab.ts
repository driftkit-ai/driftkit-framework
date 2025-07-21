import { defineComponent, ref, onMounted, nextTick, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { formatJSON, highlightVariables } from '../../utils/formatting';
import { formatDateTime, isJSON, formatEvaluationType, getRunStatusClass, getResultStatusClass, getTestSetsInFolder } from './testsets/utils';
import { getImageUrl, checkImageExists } from './testsets/api';
import useFolders from './testsets/useFolders';
import useTestSets from './testsets/useTestSets';
import useEvaluations from './testsets/useEvaluations';
import useRuns from './testsets/useRuns';
import * as api from './testsets/api';
import { DEFAULT_LLM_CONFIG, DEFAULT_JSON_SCHEMA_CONFIG, DEFAULT_KEYWORDS_CONFIG, DEFAULT_MANUAL_EVAL_CONFIG } from './testsets/constants';

// Bootstrap tooltip import (assuming Bootstrap JS is available)
declare global {
    interface Window {
        bootstrap?: any;
    }
}

export default defineComponent({
    name: 'TestSetsTab',

    setup() {
        // Core state
        const router = useRouter();
        const route = useRoute();
        const loading = ref(false);
        const testSets = ref<any[]>([]);
        const folders = ref<any[]>([]);
        
        // Initialize Bootstrap tooltips
        const initTooltips = () => {
            nextTick(() => {
                const tooltipTriggerList = document.querySelectorAll('[data-bs-toggle="tooltip"]');
                if (tooltipTriggerList.length > 0) {
                    try {
                        // @ts-ignore - Bootstrap may not be fully typed
                        const tooltipList = [...tooltipTriggerList].map(tooltipTriggerEl => new bootstrap.Tooltip(tooltipTriggerEl));
                        console.log(`Initialized ${tooltipList.length} tooltips`);
                    } catch (error) {
                        console.error('Error initializing tooltips:', error);
                    }
                }
            });
        };

        // Fetch test sets and folders
        const fetchTestSets = async () => {
            loading.value = true;
            try {
                const { folders: fetchedFolders, testSets: fetchedTestSets } = await api.fetchTestSets();
                folders.value = fetchedFolders;
                testSets.value = fetchedTestSets;
            } catch (error) {
                console.error('Error fetching test sets and folders:', error);
            } finally {
                loading.value = false;
            }
        };

        // Import composable hooks
        const testSetsModule = useTestSets(testSets, fetchTestSets);
        const foldersModule = useFolders(folders, testSets, fetchTestSets, testSetsModule.selectedTestSets);
        const evaluationsModule = useEvaluations(testSetsModule.selectedTestSet);
        const runsModule = useRuns(
            testSetsModule.selectedTestSet, 
            testSetsModule.testSetItems, 
            evaluationsModule.evaluations
        );

        // Lifecycle hooks
        onMounted(() => {
            fetchTestSets();
            initTooltips();
        });
        
        // Watch for changes to re-initialize tooltips when test set items change
        watch(testSetsModule.testSetItems, () => {
            initTooltips();
        });

        return {
            // Core state
            loading,
            testSets,
            folders,
            fetchTestSets,

            // Utility functions
            formatDateTime,
            isJSON,
            formatJSON,
            highlightVariables,
            getImageUrl,
            formatEvaluationType,
            getRunStatusClass,
            getResultStatusClass,
            
            // Important function used in the template
            getTestSetsInFolder: (folderId: string | null) => getTestSetsInFolder(testSets.value, folderId),

            // Default configs for template use
            DEFAULT_LLM_CONFIG,
            DEFAULT_JSON_SCHEMA_CONFIG,
            DEFAULT_KEYWORDS_CONFIG,
            DEFAULT_MANUAL_EVAL_CONFIG,

            // Re-export all methods and state from modules
            ...testSetsModule,
            ...foldersModule,
            ...evaluationsModule,
            ...runsModule
        };
    }
});