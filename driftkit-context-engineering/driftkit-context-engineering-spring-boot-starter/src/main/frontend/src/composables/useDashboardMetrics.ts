import { ref, computed } from 'vue';
import axios from 'axios';

export function useDashboardMetrics() {
  const metrics = ref<any>({});
  const dashboardLoading = ref(false);
  const timeRange = ref('1day');
  const useCustomDates = ref(false);
  const customStartDate = ref(new Date().toISOString().split('T')[0]);
  const customEndDate = ref(new Date().toISOString().split('T')[0]);
  const expandedPromptPercentiles = ref<string[]>([]);

  const getDaysFromTimeRange = () => {
    switch (timeRange.value) {
      case '7days': return 7;
      case '30days': return 30;
      default: return 1;
    }
  };

  const fetchMetrics = () => {
    dashboardLoading.value = true;

    let startDateStr: string, endDateStr: string;

    if (useCustomDates.value) {
      startDateStr = customStartDate.value;
      endDateStr = customEndDate.value;
    } else {
      const endDate = new Date();
      const startDate = new Date();
      startDate.setDate(startDate.getDate() - getDaysFromTimeRange());
      startDateStr = startDate.toISOString().split('T')[0];
      endDateStr = endDate.toISOString().split('T')[0];
      customStartDate.value = startDateStr;
      customEndDate.value = endDateStr;
    }

    axios
      .get('/data/v1.0/analytics/metrics/daily', {
        params: { startDate: startDateStr, endDate: endDateStr },
      })
      .then((res) => {
        metrics.value = res.data.data;
      })
      .catch((err) => {
        console.error('Error fetching metrics:', err);
      })
      .finally(() => {
        dashboardLoading.value = false;
      });
  };

  const formattedPromptMethods = computed(() => {
    if (!metrics.value?.tasksByPromptMethod) return [];

    const result: any[] = [];
    const tasksByPromptMethod = metrics.value.tasksByPromptMethod || {};
    const tokensByPromptMethod = metrics.value.tokensByPromptMethod || { promptTokens: {}, completionTokens: {} };
    const latencyByPromptMethod = metrics.value.latencyByPromptMethod || {};
    const successByPromptMethod = metrics.value.successByPromptMethod || {};
    const errorsByPromptMethod = metrics.value.errorsByPromptMethod || {};
    const successRateByPromptMethod = metrics.value.successRateByPromptMethod || {};

    for (const method in tasksByPromptMethod) {
      const count = tasksByPromptMethod[method] || 0;
      const promptTokens = tokensByPromptMethod.promptTokens[method] || 0;
      const completionTokens = tokensByPromptMethod.completionTokens[method] || 0;
      const latency = latencyByPromptMethod[method]?.p90 || 0;
      const successCount = successByPromptMethod[method] || 0;
      const errorCount = errorsByPromptMethod[method] || 0;

      let successRate: number;
      if (successRateByPromptMethod[method] !== undefined) {
        successRate = successRateByPromptMethod[method] * 100;
      } else {
        const totalTraces = successCount + errorCount;
        successRate = totalTraces > 0 ? (successCount / totalTraces) * 100 : 100;
      }

      result.push({ method, count, latency, tokens: promptTokens + completionTokens, successCount, errorCount, successRate });
    }

    return result.sort((a, b) => b.count - a.count);
  });

  const formattedModelUsage = computed(() => {
    if (!metrics.value?.tasksByModel) return [];

    const result: any[] = [];
    const tasksByModel = metrics.value.tasksByModel || {};
    const tokensByPromptMethodModel = metrics.value.tokensByPromptMethodModel || { promptTokens: {}, completionTokens: {} };

    for (const model in tasksByModel) {
      const count = tasksByModel[model] || 0;
      let promptTokens = 0;
      let completionTokens = 0;

      for (const key in tokensByPromptMethodModel.promptTokens) {
        if (key.includes(`:${model}`)) {
          promptTokens += tokensByPromptMethodModel.promptTokens[key] || 0;
        }
      }
      for (const key in tokensByPromptMethodModel.completionTokens) {
        if (key.includes(`:${model}`)) {
          completionTokens += tokensByPromptMethodModel.completionTokens[key] || 0;
        }
      }

      result.push({ model, count, promptTokens, completionTokens });
    }

    return result.sort((a, b) => b.count - a.count);
  });

  const calculateSuccessRate = (success: number, total: number) => {
    if (!total) return '0.0';
    return ((success / total) * 100).toFixed(1);
  };

  const calculateErrorRate = (success: number, total: number) => {
    if (!total) return '0.0';
    return (((total - success) / total) * 100).toFixed(1);
  };

  const getPromptLatencyPercentile = (method: string, percentile: string): string => {
    if (!metrics.value?.latencyByPromptMethod?.[method]) return 'N/A';
    const value = metrics.value.latencyByPromptMethod[method][percentile];
    if (value && !isNaN(Number(value))) return Number(value).toLocaleString();
    return value || 'N/A';
  };

  const hasLatencyData = (method: string) => {
    return !!(
      metrics.value?.latencyByPromptMethod?.[method] &&
      Object.keys(metrics.value.latencyByPromptMethod[method]).length > 0
    );
  };

  const togglePromptPercentiles = (method: string) => {
    if (expandedPromptPercentiles.value.includes(method)) {
      expandedPromptPercentiles.value = expandedPromptPercentiles.value.filter((m) => m !== method);
    } else {
      expandedPromptPercentiles.value.push(method);
    }
  };

  const setTimeRange = (range: string) => {
    timeRange.value = range;
    useCustomDates.value = false;
    fetchMetrics();
  };

  const setCustomDate = () => {
    useCustomDates.value = true;
    fetchMetrics();
  };

  const resetCustomDates = () => {
    useCustomDates.value = false;
    fetchMetrics();
  };

  return {
    metrics,
    dashboardLoading,
    timeRange,
    useCustomDates,
    customStartDate,
    customEndDate,
    expandedPromptPercentiles,
    fetchMetrics,
    formattedPromptMethods,
    formattedModelUsage,
    calculateSuccessRate,
    calculateErrorRate,
    getPromptLatencyPercentile,
    hasLatencyData,
    togglePromptPercentiles,
    setTimeRange,
    setCustomDate,
    resetCustomDates,
  };
}
