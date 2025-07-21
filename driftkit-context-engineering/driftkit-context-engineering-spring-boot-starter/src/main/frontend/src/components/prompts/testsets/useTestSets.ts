import { ref, Ref, onMounted, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { TestSet, TestSetItem, NewTestSet } from './types';
import * as api from './api';

export default function useTestSets(testSets: Ref<TestSet[]>, fetchTestSets: () => Promise<void>) {
    const router = useRouter();
    const route = useRoute();
    // State
    const selectedTestSet = ref<TestSet | null>(null);
    const testSetItems = ref<TestSetItem[]>([]);
    const testSetItemsLoading = ref(false);
    const expandedTestSets = ref<string[]>([]);
    const expandedItems = ref<string[]>([]);
    const expandedTestSetItems = ref<TestSetItem[]>([]);
    const loadingTestSetId = ref<string | null>(null);
    const loadedTestSetId = ref<string | null>(null);
    const selectedTestSets = ref<string[]>([]);
    const newTestSet = ref<NewTestSet>({
        name: '',
        description: '',
        folderId: ''
    });
    const showCreateTestSetModal = ref(false);

    // Check URL parameters on mount
    onMounted(() => {
        // Check for testSetId parameter
        if (route.query.testSetId) {
            const testSetId = route.query.testSetId as string;
            const testSet = testSets.value.find(ts => ts.id === testSetId);
            
            if (testSet) {
                selectedTestSet.value = testSet;
                toggleTestSetDetails(testSetId);
            }
        }
    });
    
    // Methods
    const toggleTestSetSelection = (id: string) => {
        if (selectedTestSets.value.includes(id)) {
            selectedTestSets.value = selectedTestSets.value.filter(testSetId => testSetId !== id);
        } else {
            selectedTestSets.value.push(id);
        }
    };

    const toggleTestSetDetails = (id: string) => {
        if (expandedTestSets.value.includes(id)) {
            // If already expanded, collapse it
            closeTestSetDetails(id);
        } else {
            // If not expanded, expand it and load its items
            expandedTestSets.value.push(id);

            // Set the selected test set (for modal operations)
            const testSet = testSets.value.find(ts => ts.id === id);
            if (testSet) {
                selectedTestSet.value = testSet;
                fetchTestSetItems(id);
                
                // Update URL to include testSetId
                router.replace({
                    path: route.path,
                    query: {
                        ...route.query,
                        testSetId: id
                    }
                }).catch(err => {
                    if (err.name !== 'NavigationDuplicated') {
                        throw err;
                    }
                });
            }
        }
    };

    const closeTestSetDetails = (id: string) => {
        expandedTestSets.value = expandedTestSets.value.filter(testSetId => testSetId !== id);
        if (loadedTestSetId.value === id) {
            // Clear items when closing to save memory
            testSetItems.value = [];
            loadedTestSetId.value = null;
        }
        
        // Remove testSetId from URL if it matches the closed test set
        if (route.query.testSetId === id) {
            const newQuery = { ...route.query };
            delete newQuery.testSetId;
            
            router.replace({
                path: route.path,
                query: newQuery
            }).catch(err => {
                if (err.name !== 'NavigationDuplicated') {
                    throw err;
                }
            });
        }
    };

    const fetchTestSetItems = async (testSetId: string) => {
        loadingTestSetId.value = testSetId;
        testSetItemsLoading.value = true;
        
        try {
            const data = await api.fetchTestSetItems(testSetId);
            
            // Debug: log what we're getting from the API
            console.log('Test set items received:', data);
            
            // Check if we have image tasks and log them specifically
            const imageTasks = data.filter(item => item.isImageTask);
            if (imageTasks.length > 0) {
                console.log('Image tasks found:', imageTasks);
                
                // Log each image task with detailed info
                imageTasks.forEach((item, index) => {
                    console.log(`Image task ${index+1}:`, {
                        id: item.id,
                        isImageTask: item.isImageTask,
                        originalImageTaskId: item.originalImageTaskId,
                        originalMessageTaskId: item.originalMessageTaskId,
                        originalTraceId: item.originalTraceId,
                        message: item.message ? item.message.substring(0, 50) + '...' : 'No message'
                    });
                });
            } else {
                console.log('No image tasks found in this test set');
            }
            
            testSetItems.value = data;
            loadedTestSetId.value = testSetId;
        } catch (error) {
            console.error('Error fetching test set items:', error);
        } finally {
            testSetItemsLoading.value = false;
            loadingTestSetId.value = null;
        }
    };

    const toggleItemDetails = (id: string) => {
        if (expandedItems.value.includes(id)) {
            expandedItems.value = expandedItems.value.filter(itemId => itemId !== id);
            expandedTestSetItems.value = expandedTestSetItems.value.filter(item => item.id !== id);
        } else {
            expandedItems.value.push(id);
            const item = testSetItems.value.find(item => item.id === id);
            if (item) {
                expandedTestSetItems.value.push(item);
            }
        }
    };

    const createNewTestSet = (folderId: string = '') => {
        newTestSet.value = {
            name: '',
            description: '',
            folderId: folderId
        };
        showCreateTestSetModal.value = true;
        document.body.classList.add('modal-open');
    };

    const closeCreateTestSetModal = () => {
        showCreateTestSetModal.value = false;
        document.body.classList.remove('modal-open');
    };

    const submitNewTestSet = async () => {
        if (!newTestSet.value.name) {
            alert('Please enter a name for the test set');
            return;
        }

        try {
            await api.createTestSet(newTestSet.value);
            closeCreateTestSetModal();
            await fetchTestSets();
        } catch (error) {
            console.error('Error creating test set:', error);
            alert('Error creating test set');
        }
    };

    const deleteTestSet = async (id: string) => {
        if (!confirm('Are you sure you want to delete this test set? This action cannot be undone.')) {
            return;
        }

        try {
            await api.deleteTestSet(id);
            if (selectedTestSet.value && selectedTestSet.value.id === id) {
                selectedTestSet.value = null;
            }
            await fetchTestSets();
        } catch (error) {
            console.error('Error deleting test set:', error);
            alert('Error deleting test set');
        }
    };

    const deleteTestSetItem = async (itemId: string, testSetId: string) => {
        if (!confirm('Are you sure you want to remove this item from the test set?')) {
            return;
        }

        if (!testSetId) return;

        try {
            await api.deleteTestSetItem(itemId, testSetId);
            await fetchTestSetItems(testSetId);
        } catch (error) {
            console.error('Error deleting test set item:', error);
            alert('Error removing item from test set');
        }
    };

    return {
        // State
        selectedTestSet,
        testSetItems,
        testSetItemsLoading,
        expandedTestSets,
        expandedItems,
        expandedTestSetItems,
        loadingTestSetId,
        loadedTestSetId,
        selectedTestSets,
        newTestSet,
        showCreateTestSetModal,

        // Methods
        toggleTestSetSelection,
        toggleTestSetDetails,
        closeTestSetDetails,
        fetchTestSetItems,
        toggleItemDetails,
        createNewTestSet,
        closeCreateTestSetModal,
        submitNewTestSet,
        deleteTestSet,
        deleteTestSetItem
    };
}