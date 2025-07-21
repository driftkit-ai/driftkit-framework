<template>
  <div class="evaluation-run-results-tab">
    <!-- Re-use the RunResultsView component -->
    <RunResultsView />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import RunResultsView from '@/views/RunResultsView.vue';

export default defineComponent({
  name: 'EvaluationRunResultsTab',
  components: {
    RunResultsView
  }
});
</script>

<style scoped>
.evaluation-run-results-tab {
  padding: 0;
  margin: 0;
}
</style>