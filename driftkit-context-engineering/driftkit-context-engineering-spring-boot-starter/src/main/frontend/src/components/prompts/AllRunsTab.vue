<template>
  <div class="all-runs-tab">
    <!-- Re-use the AllRunsView component -->
    <AllRunsView />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import AllRunsView from '@/views/AllRunsView.vue';

export default defineComponent({
  name: 'AllRunsTab',
  components: {
    AllRunsView
  }
});
</script>

<style scoped>
/* Any tab-specific styles */
.all-runs-tab {
  /* Remove any padding/margin that might cause layout issues */
  padding: 0;
  margin: 0;
}
</style>