import { createRouter, createWebHistory, RouteLocationNormalizedLoaded } from 'vue-router';
import ChatView from '../views/ChatView.vue';
import PromptsView from '../views/PromptsView.vue';
import IndexesView from '../views/IndexesView.vue';
import DictionariesView from '../views/DictionariesView.vue';
import ChecklistsView from '../views/ChecklistsView.vue';
import AllRunsView from '../views/AllRunsView.vue';
import RunResultsView from '../views/RunResultsView.vue';

const routes = [
  { path: '/', redirect: '/chat' },
  { path: '/chat', name: 'Chat', component: ChatView },
  { path: '/prompts', name: 'Prompts', component: PromptsView },
  { path: '/indexes', name: 'Indexes', component: IndexesView },
  { path: '/dictionaries', name: 'Dictionaries', component: DictionariesView },
  { path: '/checklists', name: 'Checklists', component: ChecklistsView },
  { 
    path: '/evaluation-runs', 
    name: 'Evaluation Runs', 
    redirect: { path: '/prompts', query: { tab: 'evalruns' } } 
  },
  { 
    path: '/evaluation-runs/results', 
    name: 'Run Results', 
    component: RunResultsView,
    props: (route: RouteLocationNormalizedLoaded) => ({ runId: route.query.runId })
  },
  {
    path: '/prompt-engineering/evaluation-runs/results',
    redirect: '/prompts?tab=runResults'
  },
];

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes,
});

export default router;
