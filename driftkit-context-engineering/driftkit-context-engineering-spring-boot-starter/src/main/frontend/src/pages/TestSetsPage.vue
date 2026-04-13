<template>
  <div class="test-sets-page">
    <div class="page-header">
      <h4 class="page-subtitle">Prompt Test Sets</h4>
      <p class="page-description">Manage test datasets, configure evaluations, run tests against prompts.</p>
    </div>
    <div class="card">
      <TestSetsTab />
    </div>
  </div>
</template>

<script setup lang="ts">
import TestSetsTab from '@/components/prompts/TestSetsTab.vue';
</script>

<style scoped>
.test-sets-page { max-width: 1400px; }
.page-header { margin-bottom: 1rem; }
.page-subtitle { margin: 0 0 0.25rem; font-weight: 600; color: var(--p-text-color); }
.page-description { margin: 0; font-size: 0.875rem; color: var(--p-text-muted-color); }
.card { background: var(--p-surface-card); border: 1px solid var(--p-surface-border); border-radius: 8px; padding: 1.25rem; }
</style>
