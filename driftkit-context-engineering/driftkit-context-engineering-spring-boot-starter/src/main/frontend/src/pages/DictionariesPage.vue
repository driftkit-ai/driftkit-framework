<template>
  <div class="dictionaries-page">
    <div class="page-header"><h4 class="page-subtitle">Dictionaries</h4><p class="page-description">Manage prompt variable dictionaries for dynamic content injection.</p></div>
    <div class="card"><DictionariesView /></div>
  </div>
</template>
<script setup lang="ts">
import DictionariesView from '@/views/DictionariesView.vue';
</script>
<style scoped>
.dictionaries-page { max-width: 1400px; }
.page-header { margin-bottom: 1rem; }
.page-subtitle { margin: 0 0 0.25rem; font-weight: 600; color: var(--p-text-color); }
.page-description { margin: 0; font-size: 0.875rem; color: var(--p-text-muted-color); }
.card { background: var(--p-surface-card); border: 1px solid var(--p-surface-border); border-radius: 8px; padding: 1.25rem; }
</style>
