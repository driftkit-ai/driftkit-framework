import { createRouter, createWebHistory, type RouteLocationNormalizedLoaded } from 'vue-router';
import AdminLayout from '@/layouts/AdminLayout.vue';
import LoginPage from '@/pages/LoginPage.vue';

// New PrimeVue pages
import DashboardPage from '@/pages/DashboardPage.vue';
import PromptsPage from '@/pages/PromptsPage.vue';
import TracesPage from '@/pages/TracesPage.vue';
import TestSetsPage from '@/pages/TestSetsPage.vue';
import EvaluationRunsPage from '@/pages/EvaluationRunsPage.vue';
import ChatPage from '@/pages/ChatPage.vue';
import PipelinesPage from '@/pages/PipelinesPage.vue';

// Existing views (will be replaced with PrimeVue pages later)
import RunResultsView from '@/views/RunResultsView.vue';
import IndexesView from '@/views/IndexesView.vue';
import DictionariesView from '@/views/DictionariesView.vue';
import ChecklistsView from '@/views/ChecklistsView.vue';

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
      { path: 'chat', name: 'Chat', component: ChatPage },
      { path: 'indexes', name: 'Indexes', component: IndexesView },
      { path: 'dictionaries', name: 'Dictionaries', component: DictionariesView },
      { path: 'checklists', name: 'Checklists', component: ChecklistsView },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

// Navigation guard: redirect to login if not authenticated
router.beforeEach((to) => {
  const credentials = sessionStorage.getItem('credentials');
  if (to.path !== '/login' && !credentials) {
    return '/login';
  }
  if (to.path === '/login' && credentials) {
    return '/dashboard';
  }
});

export default router;
