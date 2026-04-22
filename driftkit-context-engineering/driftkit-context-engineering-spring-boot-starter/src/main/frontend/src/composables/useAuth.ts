import { ref } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';

const authenticated = ref(false);
const username = ref('');
const password = ref('');
const loginError = ref('');

export function useAuth() {
  const router = useRouter();

  const login = async () => {
    loginError.value = '';
    const creds = btoa(`${username.value}:${password.value}`);
    try {
      await axios.get('/data/v1.0/admin/prompt/', {
        headers: { Authorization: `Basic ${creds}` },
      });
      sessionStorage.setItem('credentials', creds);
      authenticated.value = true;
      router.push('/dashboard');
    } catch {
      authenticated.value = false;
      sessionStorage.removeItem('credentials');
      loginError.value = 'Invalid credentials';
    }
  };

  const logout = () => {
    sessionStorage.removeItem('credentials');
    authenticated.value = false;
    username.value = '';
    password.value = '';
    router.push('/login');
  };

  const checkAuth = () => {
    const creds = sessionStorage.getItem('credentials');
    if (creds) {
      authenticated.value = true;
    }
  };

  return {
    authenticated,
    username,
    password,
    loginError,
    login,
    logout,
    checkAuth,
  };
}
