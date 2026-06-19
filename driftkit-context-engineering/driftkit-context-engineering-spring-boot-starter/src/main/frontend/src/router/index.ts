import { createRouter, createWebHistory, type RouteLocationNormalizedLoaded } from 'vue-router';
import AdminLayout from '@/layouts/AdminLayout.vue';
import LoginPage from '@/pages/LoginPage.vue';
import { ADVANCED_PATHS, isFeatureEnabled } from '@/config/features';

// All pages
import DashboardPage from '@/pages/DashboardPage.vue';
import PromptsPage from '@/pages/PromptsPage.vue';
import TracesPage from '@/pages/TracesPage.vue';
import TestSetsPage from '@/pages/TestSetsPage.vue';
import EvaluationRunsPage from '@/pages/EvaluationRunsPage.vue';
import ChatPage from '@/pages/ChatPage.vue';
import PipelinesPage from '@/pages/PipelinesPage.vue';
import PlaygroundPage from '@/pages/PlaygroundPage.vue';
import IndexesPage from '@/pages/IndexesPage.vue';
import DictionariesPage from '@/pages/DictionariesPage.vue';
import ChecklistsPage from '@/pages/ChecklistsPage.vue';

// Legacy view still used directly
import RunResultsView from '@/views/RunResultsView.vue';

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: LoginPage,
  },
  {
    path: '/',
    component: AdminLayout,
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', name: 'Dashboard', component: DashboardPage },
      { path: 'prompts', name: 'Prompts', component: PromptsPage },
      { path: 'traces', name: 'Traces', component: TracesPage },
      { path: 'test-sets', name: 'Test Sets', component: TestSetsPage },
      { path: 'evaluation-runs', name: 'Evaluation Runs', component: EvaluationRunsPage },
      {
        path: 'evaluation-runs/results',
        name: 'Run Results',
        component: RunResultsView,
        props: (route: RouteLocationNormalizedLoaded) => ({ runId: route.query.runId }),
      },
      { path: 'pipelines', name: 'Pipelines', component: PipelinesPage },
      { path: 'playground', name: 'Playground', component: PlaygroundPage },
      { path: 'chat', name: 'Chat', component: ChatPage },
      { path: 'indexes', name: 'Indexes', component: IndexesPage },
      { path: 'dictionaries', name: 'Dictionaries', component: DictionariesPage },
      { path: 'checklists', name: 'Checklists', component: ChecklistsPage },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

// Navigation guard: auth + advanced-feature gate.
router.beforeEach((to) => {
  const credentials = sessionStorage.getItem('credentials');
  if (to.path !== '/login' && !credentials) {
    return '/login';
  }
  if (to.path === '/login' && credentials) {
    return '/dashboard';
  }
  // Disabled advanced feature must not be reachable even by direct URL.
  if (ADVANCED_PATHS.some((p) => to.path.startsWith(p)) && !isFeatureEnabled(to.path)) {
    return '/dashboard';
  }
});

export default router;
