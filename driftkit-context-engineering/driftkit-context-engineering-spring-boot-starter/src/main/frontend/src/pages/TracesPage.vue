<template>
  <div class="traces-page">
    <!-- Reuse existing TracesTab - will be rebuilt with PrimeVue components in a future pass -->
    <TracesTab />
  </div>
</template>

<script setup lang="ts">
import TracesTab from '@/components/prompts/TracesTab.vue';
</script>

<style scoped>
.traces-page {
  max-width: 1400px;
}
</style>
