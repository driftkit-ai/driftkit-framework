import { ref, Ref, onMounted, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { Folder, NewFolder, TestSet } from './types';
import * as api from './api';

export default function useFolders(
    folders: Ref<Folder[]>,
    testSets: Ref<TestSet[]>,
    fetchTestSets: () => Promise<void>,
    selectedTestSets: Ref<string[]>
) {
    const router = useRouter();
    const route = useRoute();
    // State
    const expandedFolders = ref<string[]>([]);
    const newFolder = ref<NewFolder>({
        name: '',
        description: ''
    });
    const showCreateFolderModal = ref(false);
    const showMoveToFolderModal = ref(false);
    const targetFolderId = ref<string>('');

    // Check URL parameters on mount
    onMounted(() => {
        // Check if folderId is in URL
        if (route.query.folderId) {
            const folderId = route.query.folderId as string;
            if (!expandedFolders.value.includes(folderId)) {
                expandedFolders.value.push(folderId);
            }
        }
    });
    
    // Methods
    const toggleFolder = (folderId: string) => {
        if (expandedFolders.value.includes(folderId)) {
            // If already expanded, collapse it
            expandedFolders.value = expandedFolders.value.filter(id => id !== folderId);
            
            // Remove from URL if present
            if (route.query.folderId === folderId) {
                const newQuery = { ...route.query };
                delete newQuery.folderId;
                
                router.replace({
                    path: route.path,
                    query: newQuery
                }).catch(err => {
                    if (err.name !== 'NavigationDuplicated') {
                        throw err;
                    }
                });
            }
        } else {
            // If not expanded, expand it
            expandedFolders.value.push(folderId);
            
            // Add to URL
            router.replace({
                path: route.path,
                query: {
                    ...route.query,
                    folderId
                }
            }).catch(err => {
                if (err.name !== 'NavigationDuplicated') {
                    throw err;
                }
            });
        }
    };

    const createNewFolder = () => {
        newFolder.value = {
            name: '',
            description: ''
        };
        showCreateFolderModal.value = true;
        document.body.classList.add('modal-open');
    };

    const closeCreateFolderModal = () => {
        showCreateFolderModal.value = false;
        document.body.classList.remove('modal-open');
    };

    const submitNewFolder = async () => {
        if (!newFolder.value.name) {
            alert('Please enter a name for the folder');
            return;
        }

        try {
            await api.createFolder(newFolder.value);
            closeCreateFolderModal();
            await fetchTestSets(); // Refresh the list with the new folder
        } catch (error) {
            console.error('Error creating folder:', error);
            alert('Error creating folder');
        }
    };

    const deleteFolder = async (id: string) => {
        if (!confirm('Are you sure you want to delete this folder? This will NOT delete the test sets inside.')) {
            return;
        }

        try {
            await api.deleteFolder(id);
            await fetchTestSets(); // Refresh the list
        } catch (error) {
            console.error('Error deleting folder:', error);
            alert('Error deleting folder');
        }
    };

    const openMoveToFolderModal = () => {
        if (selectedTestSets.value.length === 0) {
            alert('Please select at least one test set to move');
            return;
        }

        targetFolderId.value = '';
        showMoveToFolderModal.value = true;
        document.body.classList.add('modal-open');
    };

    const closeMoveToFolderModal = () => {
        showMoveToFolderModal.value = false;
        document.body.classList.remove('modal-open');
    };

    const moveTestSetsToFolder = async () => {
        if (selectedTestSets.value.length === 0) {
            alert('Please select at least one test set to move');
            return;
        }

        try {
            await api.moveTestSetsToFolder(selectedTestSets.value, targetFolderId.value);
            closeMoveToFolderModal();
            selectedTestSets.value = []; // Clear selection
            await fetchTestSets(); // Refresh the list
        } catch (error) {
            console.error('Error moving test sets to folder:', error);
            alert('Error moving test sets to folder');
        }
    };

    // Folder execution state
    const showFolderExecutionModal = ref(false);
    const folderExecutionLoading = ref(false);
    const selectedFolderId = ref<string>('');
    const folderExecutionConfig = ref({
        modelId: '',
        workflow: '',
        useModel: true, // true for modelId, false for workflow
        regenerateImages: true // Whether to regenerate images during test runs
    });

    // Execute all test sets in a folder
    const runAllTestsInFolder = (folderId: string) => {
        if (!folderId) return;

        const folderTests = testSets.value.filter(ts => ts.folderId === folderId);
        if (folderTests.length === 0) {
            alert('No test sets in this folder to run');
            return;
        }

        // Set default values and open modal
        selectedFolderId.value = folderId;
        folderExecutionConfig.value = {
            modelId: '',
            workflow: '',
            useModel: true,
            regenerateImages: true
        };
        showFolderExecutionModal.value = true;
    };

    const closeFolderExecutionModal = () => {
        showFolderExecutionModal.value = false;
        selectedFolderId.value = '';
    };

    const executeTestsInFolder = async () => {
        if (!selectedFolderId.value) return;

        folderExecutionLoading.value = true;
        try {
            const modelId = folderExecutionConfig.value.useModel ? folderExecutionConfig.value.modelId : undefined;
            const workflow = !folderExecutionConfig.value.useModel ? folderExecutionConfig.value.workflow : undefined;
            const response = await api.executeFolderTests(
                selectedFolderId.value,
                modelId,
                workflow,
                folderExecutionConfig.value.regenerateImages
            );

            if (response.success) {
                alert(`Successfully started batch execution of ${response.data.length} test sets`);
                closeFolderExecutionModal();
            } else {
                alert(`Failed to execute tests: ${response.message || 'Unknown error'}`);
            }
        } catch (error: any) {
            console.error('Error executing folder tests:', error);
            let errorMessage = 'Error executing tests in folder';
            if (error.response?.data?.message) {
                errorMessage = error.response.data.message;
            }
            alert(errorMessage);
        } finally {
            folderExecutionLoading.value = false;
        }
    };

    return {
        // State
        expandedFolders,
        newFolder,
        showCreateFolderModal,
        showMoveToFolderModal,
        targetFolderId,
        showFolderExecutionModal,
        folderExecutionLoading,
        selectedFolderId,
        folderExecutionConfig,

        // Methods
        toggleFolder,
        createNewFolder,
        closeCreateFolderModal,
        submitNewFolder,
        deleteFolder,
        openMoveToFolderModal,
        closeMoveToFolderModal,
        moveTestSetsToFolder,
        runAllTestsInFolder,
        closeFolderExecutionModal,
        executeTestsInFolder
    };
}