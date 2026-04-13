<template>
  <div class="dashboard-page">
    <!-- Date Range Filter -->
    <div class="card mb-4">
      <div class="card-content p-4">
        <div class="d-flex justify-content-between align-items-center mb-3">
          <h5 class="m-0 font-semibold">Date Range</h5>
          <SelectButton
            v-model="timeRange"
            :options="timeRangeOptions"
            optionLabel="label"
            optionValue="value"
            @change="setTimeRange(timeRange)"
          />
        </div>
        <div class="d-flex align-items-center gap-2">
          <span class="text-muted">From</span>
          <DatePicker v-model="startDate" dateFormat="yy-mm-dd" showIcon @date-select="onStartDateSelect" />
          <span class="text-muted">To</span>
          <DatePicker v-model="endDate" dateFormat="yy-mm-dd" showIcon @date-select="onEndDateSelect" />
          <Button label="Reset" severity="secondary" size="small" @click="resetCustomDates" />
        </div>
      </div>
    </div>

    <!-- Metrics Cards -->
    <div class="metrics-grid mb-4">
      <div class="metric-card">
        <div class="metric-label">Total Tasks</div>
        <div class="metric-value">{{ metrics.totalTasks?.toLocaleString() || 0 }}</div>
        <ProgressSpinner v-if="dashboardLoading" style="width: 20px; height: 20px" class="metric-spinner" />
      </div>
      <div class="metric-card">
        <div class="metric-label">Prompt Tokens</div>
        <div class="metric-value">{{ metrics.totalPromptTokens?.toLocaleString() || 0 }}</div>
        <ProgressSpinner v-if="dashboardLoading" style="width: 20px; height: 20px" class="metric-spinner" />
      </div>
      <div class="metric-card">
        <div class="metric-label">Completion Tokens</div>
        <div class="metric-value">{{ metrics.totalCompletionTokens?.toLocaleString() || 0 }}</div>
        <ProgressSpinner v-if="dashboardLoading" style="width: 20px; height: 20px" class="metric-spinner" />
      </div>
      <div class="metric-card">
        <div class="metric-label">Est. Cost (USD)</div>
        <div class="metric-value">${{ estimatedCost }}</div>
        <ProgressSpinner v-if="dashboardLoading" style="width: 20px; height: 20px" class="metric-spinner" />
      </div>
      <div class="metric-card">
        <div class="metric-label">Median Latency (ms)</div>
        <div class="metric-value">{{ metrics.latencyPercentiles?.p50 || 0 }}</div>
        <ProgressSpinner v-if="dashboardLoading" style="width: 20px; height: 20px" class="metric-spinner" />
      </div>
    </div>

    <!-- Success / Error Statistics -->
    <div class="card mb-4">
      <div class="card-header">
        <h5 class="m-0 font-semibold">Success / Error Statistics</h5>
      </div>
      <div class="card-content p-4">
        <div class="stats-row">
          <div class="stat-block stat-success">
            <div class="stat-title">Success</div>
            <div class="stat-value">{{ metrics.successCount || 0 }}</div>
            <div class="stat-detail" v-if="metrics.successCount || metrics.errorCount">
              {{ metrics.successCount || 0 }} / {{ (metrics.successCount || 0) + (metrics.errorCount || 0) }}
              ({{ calculateSuccessRate(metrics.successCount || 0, (metrics.successCount || 0) + (metrics.errorCount || 0)) }}%)
            </div>
          </div>
          <div class="stat-block" :class="(metrics.errorCount || 0) > 0 ? 'stat-error' : 'stat-neutral'">
            <div class="stat-title">Errors</div>
            <div class="stat-value">{{ metrics.errorCount || 0 }}</div>
            <div class="stat-detail" v-if="metrics.successCount || metrics.errorCount">
              {{ metrics.errorCount || 0 }} / {{ (metrics.successCount || 0) + (metrics.errorCount || 0) }}
              ({{ calculateErrorRate(metrics.successCount || 0, (metrics.successCount || 0) + (metrics.errorCount || 0)) }}%)
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Latency Percentiles -->
    <div class="card mb-4">
      <div class="card-header">
        <h5 class="m-0 font-semibold">Latency Percentiles (ms)</h5>
      </div>
      <div class="card-content p-4">
        <div class="percentiles-row">
          <div class="percentile-item" v-for="p in ['p25', 'p50', 'p75', 'p90']" :key="p">
            <div class="percentile-label">{{ p === 'p50' ? 'p50 (Median)' : p }}</div>
            <div class="percentile-value">{{ metrics.latencyPercentiles?.[p] || 0 }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Two Column: Prompt Methods + Model Usage -->
    <div class="two-col-grid mb-4">
      <!-- Top Prompt Methods -->
      <div class="card">
        <div class="card-header d-flex justify-content-between align-items-center">
          <h5 class="m-0 font-semibold">Top Prompt Methods</h5>
        </div>
        <div class="card-content p-4">
          <ProgressSpinner v-if="dashboardLoading" />
          <DataTable v-else :value="formattedPromptMethods" :rows="20" stripedRows size="small" v-model:expandedRows="expandedRows" dataKey="method">
            <Column field="method" header="Prompt Method" style="max-width: 200px">
              <template #body="{ data }">
                <span class="text-truncate" style="max-width: 200px; display: inline-block;">{{ data.method }}</span>
              </template>
            </Column>
            <Column field="count" header="Count" sortable />
            <Column field="tokens" header="Tokens" sortable>
              <template #body="{ data }">{{ data.tokens.toLocaleString() }}</template>
            </Column>
            <Column header="Success / Total">
              <template #body="{ data }">
                <Tag
                  :severity="data.successRate === 100 ? 'success' : data.successRate >= 70 ? 'warn' : data.errorCount > 0 ? 'danger' : 'secondary'"
                  :value="`${data.successCount} / ${data.successCount + data.errorCount}`"
                />
                <span class="ms-2 text-sm" :class="data.successRate === 100 ? 'text-green-500' : data.successRate >= 70 ? 'text-yellow-500' : 'text-red-500'">
                  ({{ data.successRate.toFixed(1) }}%)
                </span>
              </template>
            </Column>
            <Column header="" style="width: 120px">
              <template #body="{ data }">
                <Button label="Details" size="small" severity="secondary" text @click="togglePromptPercentiles(data.method)" />
              </template>
            </Column>
            <template #expansion="{ data }">
              <div class="p-3">
                <div class="stats-row mb-3">
                  <div class="stat-block stat-success" style="flex: 1">
                    <div class="stat-title text-sm">Success Traces</div>
                    <div class="stat-value text-lg">{{ data.successCount }}</div>
                    <div class="stat-detail">{{ data.successRate.toFixed(1) }}%</div>
                  </div>
                  <div class="stat-block" :class="data.errorCount > 0 ? 'stat-error' : 'stat-neutral'" style="flex: 1">
                    <div class="stat-title text-sm">Error Traces</div>
                    <div class="stat-value text-lg">{{ data.errorCount }}</div>
                    <div class="stat-detail">{{ (100 - data.successRate).toFixed(1) }}%</div>
                  </div>
                </div>
                <Message severity="info" :closable="false" class="mb-3">
                  This prompt was used in <strong>{{ data.count }}</strong> tasks. A single task may generate multiple traces.
                </Message>
                <h6>Latency Percentiles</h6>
                <div v-if="hasLatencyData(data.method)" class="percentiles-row">
                  <div class="percentile-item" v-for="p in ['p25', 'p50', 'p75', 'p90']" :key="p">
                    <div class="percentile-label">{{ p }}</div>
                    <div class="percentile-value">{{ getPromptLatencyPercentile(data.method, p) }} ms</div>
                  </div>
                </div>
                <div v-else class="text-muted">No latency data available</div>
              </div>
            </template>
          </DataTable>
          <div v-if="!dashboardLoading && formattedPromptMethods.length === 0" class="text-center py-4 text-muted">
            No data available
          </div>
        </div>
      </div>

      <!-- Model Usage -->
      <div class="card">
        <div class="card-header">
          <h5 class="m-0 font-semibold">Model Usage</h5>
        </div>
        <div class="card-content p-4">
          <ProgressSpinner v-if="dashboardLoading" />
          <DataTable v-else :value="formattedModelUsage" :rows="20" stripedRows size="small">
            <Column field="model" header="Model" />
            <Column field="count" header="Count" sortable />
            <Column field="promptTokens" header="Prompt Tokens" sortable>
              <template #body="{ data }">{{ data.promptTokens.toLocaleString() }}</template>
            </Column>
            <Column field="completionTokens" header="Completion Tokens" sortable>
              <template #body="{ data }">{{ data.completionTokens.toLocaleString() }}</template>
            </Column>
          </DataTable>
          <div v-if="!dashboardLoading && formattedModelUsage.length === 0" class="text-center py-4 text-muted">
            No data available
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useDashboardMetrics } from '@/composables/useDashboardMetrics';
import SelectButton from 'primevue/selectbutton';
import DatePicker from 'primevue/datepicker';
import Button from 'primevue/button';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Tag from 'primevue/tag';
import ProgressSpinner from 'primevue/progressspinner';
import Message from 'primevue/message';

const {
  metrics,
  dashboardLoading,
  timeRange,
  customStartDate,
  customEndDate,
  formattedPromptMethods,
  formattedModelUsage,
  calculateSuccessRate,
  calculateErrorRate,
  getPromptLatencyPercentile,
  hasLatencyData,
  togglePromptPercentiles,
  expandedPromptPercentiles,
  fetchMetrics,
  setTimeRange,
  setCustomDate,
  resetCustomDates,
} = useDashboardMetrics();

const expandedRows = ref<any>({});

const timeRangeOptions = [
  { label: 'Today', value: '1day' },
  { label: '7 Days', value: '7days' },
  { label: '30 Days', value: '30days' },
];

// Estimated cost from token counts (rough estimate: $2.50/1M input, $10/1M output for GPT-4o)
const estimatedCost = computed(() => {
  const input = metrics.value?.totalPromptTokens || 0;
  const output = metrics.value?.totalCompletionTokens || 0;
  const cost = (input / 1_000_000) * 2.5 + (output / 1_000_000) * 10;
  return cost < 0.01 ? cost.toFixed(4) : cost.toFixed(2);
});

// DatePicker needs Date objects, composable uses ISO strings
const startDate = ref(new Date());
const endDate = ref(new Date());

const formatDateToISO = (date: Date): string => {
  return date.toISOString().split('T')[0];
};

const onStartDateSelect = (event: Date) => {
  customStartDate.value = formatDateToISO(event);
  setCustomDate();
};

const onEndDateSelect = (event: Date) => {
  customEndDate.value = formatDateToISO(event);
  setCustomDate();
};

onMounted(() => {
  fetchMetrics();
});
</script>

<style scoped>
.dashboard-page {
  max-width: 1400px;
}

.card {
  background: var(--p-surface-card);
  border: 1px solid var(--p-surface-border);
  border-radius: 8px;
  overflow: hidden;
}

.card-header {
  padding: 1rem 1.25rem;
  border-bottom: 1px solid var(--p-surface-border);
  background: var(--p-surface-50);
}

.card-content {
  padding: 1.25rem;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 1rem;
}

.metric-card {
  background: var(--p-surface-card);
  border: 1px solid var(--p-surface-border);
  border-radius: 8px;
  padding: 1.25rem;
  position: relative;
}

.metric-label {
  font-size: 0.875rem;
  color: var(--p-text-muted-color);
  margin-bottom: 0.5rem;
}

.metric-value {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--p-text-color);
}

.metric-spinner {
  position: absolute;
  top: 1rem;
  right: 1rem;
}

.stats-row {
  display: flex;
  gap: 1rem;
}

.stat-block {
  flex: 1;
  text-align: center;
  padding: 1rem;
  border-radius: 8px;
  color: white;
}

.stat-success {
  background: var(--p-green-500);
}

.stat-error {
  background: var(--p-red-500);
}

.stat-neutral {
  background: var(--p-surface-500);
}

.stat-title {
  font-size: 0.9rem;
  font-weight: 500;
}

.stat-value {
  font-size: 1.75rem;
  font-weight: 700;
  margin: 0.25rem 0;
}

.stat-detail {
  font-size: 0.8rem;
  opacity: 0.9;
}

.percentiles-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}

.percentile-item {
  text-align: center;
  padding: 0.5rem 1rem;
}

.percentile-label {
  font-size: 0.8rem;
  color: var(--p-text-muted-color);
  margin-bottom: 0.25rem;
}

.percentile-value {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--p-text-color);
}

.two-col-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.d-flex {
  display: flex;
}

.justify-content-between {
  justify-content: space-between;
}

.align-items-center {
  align-items: center;
}

.gap-2 {
  gap: 0.5rem;
}

.mb-3 {
  margin-bottom: 1rem;
}

.mb-4 {
  margin-bottom: 1.5rem;
}

.m-0 {
  margin: 0;
}

.p-3 {
  padding: 1rem;
}

.p-4 {
  padding: 1.25rem;
}

.ms-2 {
  margin-left: 0.5rem;
}

.py-4 {
  padding-top: 1.5rem;
  padding-bottom: 1.5rem;
}

.text-center {
  text-align: center;
}

.text-muted {
  color: var(--p-text-muted-color);
}

.text-sm {
  font-size: 0.875rem;
}

.text-lg {
  font-size: 1.25rem;
}

.font-semibold {
  font-weight: 600;
}

.text-truncate {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .two-col-grid {
    grid-template-columns: 1fr;
  }
}
</style>
