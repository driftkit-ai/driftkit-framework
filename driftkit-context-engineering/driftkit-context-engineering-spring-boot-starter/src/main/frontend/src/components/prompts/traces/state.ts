import { ref, computed, watch } from 'vue';
import { TracesPage, MessageTaskType, TracesGroup } from './types';

// State for traces functionality
export const tracesLoading = ref(false);
export const traces = ref<any[]>([]);
export const tracesPage = ref<TracesPage>({
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 100
});
export const availablePromptMethods = ref<string[]>([]);
export const traceTimeRange = ref('1day');
export const traceStartTime = ref('');
export const traceEndTime = ref('');
export const selectedPromptMethod = ref('');
export const promptMetrics = ref<any>(null);
export const promptMetricsLoading = ref(false);
export const expandedTraces = ref<string[]>([]);
export const expandedMessageDetails = ref<string[]>([]);
export const expandedWorkflowSteps = ref<string[]>([]);
export const expandedChatContexts = ref<string[]>([]);
export const showWorkflowSteps = ref(true);
export const messageTasksByContextId = ref<Record<string, MessageTaskType>>({});
export const searchMessageId = ref('');
export const excludePurposeFilter = ref('');

// Test set selection state
export const selectedMessageTasks = ref<string[]>([]);
export const selectedTraceSteps = ref<{contextId: string, traceId: string}[]>([]);
export const selectedImageTasks = ref<string[]>([]);
export const availableTestSets = ref<any[]>([]);
export const selectedTestSetId = ref('');
export const newTestSetName = ref('');
export const newTestSetDescription = ref('');
export const addingToTestSet = ref(false);
export const showAddToTestSetModal = ref(false);

// Map to store promptId to method name
export const promptIdToMethodMap = ref<Record<string, string>>({});

// This is reactive and should trigger recomputation of groupedTraces
export const groupByChatId = ref(false);

// Computed properties
export const totalSelectedItems = computed(() =>
    selectedMessageTasks.value.length + selectedTraceSteps.value.length + selectedImageTasks.value.length
);

// When groupByChatId changes, log it to ensure reactivity works
watch(groupByChatId, (newVal) => {
    console.log("groupByChatId changed to:", newVal);
});

export const paginationRange = computed(() => {
    const current = tracesPage.value.number + 1;
    const total = tracesPage.value.totalPages;
    const delta = 2;

    if (total <= 5) {
        return Array.from({ length: total }, (_, i) => i + 1);
    }

    const range = [];

    if (current > 1) {
        range.push(1);
    }

    const from = Math.max(2, current - delta);
    const to = Math.min(total - 1, current + delta);

    if (from > 2) {
        range.push('...');
    }

    for (let i = from; i <= to; i++) {
        range.push(i);
    }

    if (to < total - 1) {
        range.push('...');
    }

    if (current < total) {
        range.push(total);
    }

    return range;
});