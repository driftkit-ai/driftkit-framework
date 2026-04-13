import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import axios, { InternalAxiosRequestConfig, AxiosHeaders } from 'axios';
import PrimeVue from 'primevue/config';
import Aura from '@primeuix/themes/aura';
import 'primeicons/primeicons.css';
// Keep Bootstrap temporarily during incremental migration
import 'bootstrap/dist/css/bootstrap.min.css';
import './assets/css/formatting.css';

axios.defaults.baseURL = import.meta.env.VITE_API_BASE_URL;

axios.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    if (!config) {
      config = {} as InternalAxiosRequestConfig;
    }
    const credentials = sessionStorage.getItem('credentials');
    if (credentials) {
      if (!config.headers) {
        config.headers = new AxiosHeaders();
      }
      config.headers.set('Authorization', `Basic ${credentials}`);
    }
    return config;
  },
  (error) => Promise.reject(error)
);

const app = createApp(App);

app.use(PrimeVue, {
  theme: {
    preset: Aura,
    options: {
      darkModeSelector: '.dark-mode',
    },
  },
});

app.use(router);
app.mount('#app');
