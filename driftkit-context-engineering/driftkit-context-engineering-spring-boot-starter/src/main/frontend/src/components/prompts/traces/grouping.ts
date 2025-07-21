import { computed } from 'vue';
import { groupByChatId, tracesPage } from './state';
import { TracesGroup } from './types';

export const groupedTraces = computed(() => {
    console.log("Computing groupedTraces, groupByChatId =", groupByChatId.value);

    // Standard grouping (by contextId only)
    if (!groupByChatId.value) {
        console.log("Using standard grouping by contextId");
        const grouped: Record<string, TracesGroup> = {};

        if (!tracesPage.value.content || !Array.isArray(tracesPage.value.content)) {
            console.warn('Traces content is not an array:', tracesPage.value);
            return grouped;
        }

        tracesPage.value.content.forEach(trace => {
            if (!trace || !trace.contextId) {
                console.warn('Invalid trace object:', trace);
                return;
            }

            const groupKey = trace.contextId;

            if (!grouped[groupKey]) {
                grouped[groupKey] = [] as TracesGroup;
            }
            grouped[groupKey].push(trace);
        });

        // Sort by timestamp within each group
        Object.keys(grouped).forEach(groupKey => {
            grouped[groupKey].sort((a, b) => a.timestamp - b.timestamp);
        });

        console.log("Grouped by contextId:", Object.keys(grouped).length, "groups");
        return grouped;
    }
    // Hierarchical grouping (by chatId -> contextId)
    else {
        console.log("Using hierarchical grouping by chatId");
        const grouped: Record<string, TracesGroup> = {};

        if (!tracesPage.value.content || !Array.isArray(tracesPage.value.content)) {
            console.warn('Traces content is not an array:', tracesPage.value);
            return grouped;
        }

        // First, group all traces that have a chatId
        const tracesWithChatId = tracesPage.value.content.filter(trace => trace.chatId);
        const tracesByChatId: Record<string, any[]> = {};

        tracesWithChatId.forEach(trace => {
            if (!tracesByChatId[trace.chatId]) {
                tracesByChatId[trace.chatId] = [];
            }
            tracesByChatId[trace.chatId].push(trace);
        });

        // Process chatId groups - create enhanced array with chat metadata
        Object.entries(tracesByChatId).forEach(([chatId, traces]) => {
            // Create a combined metadata for the chat group
            const contextIds = [...new Set(traces.map(t => t.contextId))];
            const timestamps = traces.map(t => t.timestamp);
            const minTimestamp = Math.min(...timestamps);
            const maxTimestamp = Math.max(...timestamps);

            // Get one representative trace from each contextId
            const representativeTraces = contextIds.map(cid =>
                traces.find(t => t.contextId === cid)
            ).filter(Boolean);

            // Include all traces in the group
            grouped[chatId] = traces as TracesGroup;

            // Add the necessary metadata to the array
            grouped[chatId].contextIds = contextIds;
            grouped[chatId].representativeTraces = representativeTraces;
            grouped[chatId].minTimestamp = minTimestamp;
            grouped[chatId].maxTimestamp = maxTimestamp;
        });

        // Add traces without chatId (group by contextId as usual)
        const tracesWithoutChatId = tracesPage.value.content.filter(trace => !trace.chatId);

        tracesWithoutChatId.forEach(trace => {
            if (!trace.contextId) return;

            if (!grouped[trace.contextId]) {
                grouped[trace.contextId] = [] as TracesGroup;
            }
            grouped[trace.contextId].push(trace);
        });

        // Sort all groups by timestamp
        Object.keys(grouped).forEach(groupKey => {
            grouped[groupKey].sort((a, b) => a.timestamp - b.timestamp);
        });

        console.log("Grouped by chatId:", Object.keys(grouped).length, "groups");
        return grouped;
    }
});