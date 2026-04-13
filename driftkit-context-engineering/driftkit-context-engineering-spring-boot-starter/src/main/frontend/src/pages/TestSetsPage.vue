<template>
  <div class="test-sets-page">
    <!-- Reuse existing TestSetsTab - will be rebuilt with PrimeVue components in a future pass -->
    <TestSetsTab />
  </div>
</template>

<script setup lang="ts">
import TestSetsTab from '@/components/prompts/TestSetsTab.vue';
</script>

<style scoped>
.test-sets-page {
  max-width: 1400px;
}
</style>
