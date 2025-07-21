import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import axios, { InternalAxiosRequestConfig, AxiosHeaders } from 'axios';
import 'bootstrap/dist/css/bootstrap.min.css';
import './assets/css/formatting.css';

axios.defaults.baseURL = process.env.VUE_APP_API_BASE_URL;

axios.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    console.log('Interceptor config:', config);
    if (!config) {
      config = {} as InternalAxiosRequestConfig;
    }
    const credentials = localStorage.getItem('credentials');
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

createApp(App)
  .use(router)
  .mount('#app');
