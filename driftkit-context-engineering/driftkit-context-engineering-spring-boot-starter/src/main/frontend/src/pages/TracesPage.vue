<template>
  <div class="traces-page">
    <div class="page-header">
      <h4 class="page-subtitle">Model Request Traces</h4>
      <p class="page-description">View and filter LLM call traces, inspect request/response details, add to test sets.</p>
    </div>
    <!-- Existing TracesTab with Bootstrap — wrapped in PrimeVue card -->
    <div class="card">
      <TracesTab />
    </div>
  </div>
</template>

<script setup lang="ts">
import TracesTab from '@/components/prompts/TracesTab.vue';
</script>

<style scoped>
.traces-page { max-width: 1400px; }
.page-header { margin-bottom: 1rem; }
.page-subtitle { margin: 0 0 0.25rem; font-weight: 600; color: var(--p-text-color); }
.page-description { margin: 0; font-size: 0.875rem; color: var(--p-text-muted-color); }
.card { background: var(--p-surface-card); border: 1px solid var(--p-surface-border); border-radius: 8px; padding: 1.25rem; }
</style>
