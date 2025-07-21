<template>
  <div class="mt-4">
    <!-- Tabs Navigation -->
    <ul class="nav nav-tabs mb-4">
      <li class="nav-item">
        <a class="nav-link" :class="{ active: activeTab === 'dashboard' }" @click.prevent="activeTab = 'dashboard'" href="#">Dashboard</a>
      </li>
      <li class="nav-item">
        <a class="nav-link" :class="{ active: activeTab === 'prompts' }" @click.prevent="activeTab = 'prompts'" href="#">Prompts</a>
      </li>
      <li class="nav-item">
        <a class="nav-link" :class="{ active: activeTab === 'traces' }" @click.prevent="activeTab = 'traces'" href="#">Traces</a>
      </li>
      <li class="nav-item">
        <a class="nav-link" :class="{ active: activeTab === 'testsets' }" @click.prevent="activeTab = 'testsets'" href="#">Test Sets</a>
      </li>
      <li class="nav-item">
        <a class="nav-link" :class="{ active: activeTab === 'evalruns' }" @click.prevent="activeTab = 'evalruns'" href="#">Evaluation Runs</a>
      </li>
    </ul>

    <!-- Dashboard Tab -->
    <DashboardTab v-if="activeTab === 'dashboard'" />

    <!-- Prompts Tab -->
    <PromptsTab 
      v-if="activeTab === 'prompts'" 
      v-model:selectedPromptId="selectedPromptId" 
      @prompt-selected="handlePromptSelected"
    />

    <!-- Traces Tab -->
    <TracesTab v-if="activeTab === 'traces'" />
    
    <!-- Test Sets Tab -->
    <TestSetsTab v-if="activeTab === 'testsets'" />
    
    <!-- Evaluation Runs Tab -->
    <AllRunsTab v-if="activeTab === 'evalruns'" />
    
    <!-- Run Results Tab (hidden from nav) -->
    <EvaluationRunResultsTab v-if="activeTab === 'runResults'" />
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, watch, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import DashboardTab from '@/components/prompts/DashboardTab.vue';
import PromptsTab from '@/components/prompts/PromptsTab.vue';
import TracesTab from '@/components/prompts/TracesTab.vue';
import TestSetsTab from '@/components/prompts/TestSetsTab.vue';
import AllRunsTab from '@/components/prompts/AllRunsTab.vue';
import EvaluationRunResultsTab from '@/components/prompts/EvaluationRunResultsTab.vue';

export default defineComponent({
  name: 'PromptsView',
  components: {
    DashboardTab,
    PromptsTab,
    TracesTab,
    TestSetsTab,
    AllRunsTab,
    EvaluationRunResultsTab
  },
  setup() {
    const router = useRouter();
    const route = useRoute();
    const activeTab = ref('dashboard');
    const selectedPromptId = ref('');
    const validTabs = ['dashboard', 'prompts', 'traces', 'testsets', 'evalruns', 'runResults'];
    
    // Initial setup - check URL for tab parameter
    onMounted(() => {
      // Check for runId in URL and set special tab
      if (route.query.runId) {
        activeTab.value = 'runResults';
      } else {
        const tabParam = route.query.tab as string;
        if (tabParam && validTabs.includes(tabParam)) {
          activeTab.value = tabParam;
        }
      }
      
      // Check for other parameters
      if (route.query.promptId) {
        selectedPromptId.value = route.query.promptId as string;
      }
    });
    
    // Update URL when tab changes
    watch(activeTab, (newTab) => {
      // Update URL without page reload
      router.push({
        path: '/prompts',
        query: { 
          ...route.query, // preserve other query params
          tab: newTab 
        }
      }).catch(err => {
        if (err.name !== 'NavigationDuplicated') {
          throw err;
        }
      });
    });

    // Watch for route changes to update activeTab
    watch(
      () => route.query,
      (query) => {
        const tabParam = query.tab as string;
        if (tabParam && validTabs.includes(tabParam) && activeTab.value !== tabParam) {
          activeTab.value = tabParam;
        }
      },
      { deep: true }
    );
    
    const handlePromptSelected = (id: string) => {
      selectedPromptId.value = id;
      // Update URL with the selected prompt ID
      router.push({
        path: '/prompts',
        query: { 
          ...route.query,
          tab: 'prompts',
          promptId: id 
        }
      }).catch(err => {
        if (err.name !== 'NavigationDuplicated') {
          throw err;
        }
      });
    };

    return {
      activeTab,
      selectedPromptId,
      handlePromptSelected
    };
  }
});
</script>

<style scoped>
/* Any view-specific styles */
</style>