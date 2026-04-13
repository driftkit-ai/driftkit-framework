<template>
  <div class="eval-runs-page">
    <!-- Reuse existing AllRunsView - will be rebuilt with PrimeVue components in a future pass -->
    <AllRunsView />
  </div>
</template>

<script setup lang="ts">
import AllRunsView from '@/views/AllRunsView.vue';
</script>

<style scoped>
.eval-runs-page {
  max-width: 1400px;
}
</style>
