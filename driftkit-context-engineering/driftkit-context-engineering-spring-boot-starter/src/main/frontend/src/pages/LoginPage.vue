<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h2>DriftKit</h2>
        <p>Context Engineering Platform</p>
      </div>
      <form @submit.prevent="login" class="login-form">
        <div class="field">
          <label for="username">Username</label>
          <InputText id="username" v-model="username" placeholder="Enter username" class="w-full" />
        </div>
        <div class="field">
          <label for="password">Password</label>
          <Password id="password" v-model="password" placeholder="Enter password" :feedback="false" toggleMask class="w-full" inputClass="w-full" />
        </div>
        <Message v-if="loginError" severity="error" :closable="false">{{ loginError }}</Message>
        <Button type="submit" label="Sign In" icon="pi pi-sign-in" class="w-full" />
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useAuth } from '@/composables/useAuth';
import InputText from 'primevue/inputtext';
import Password from 'primevue/password';
import Button from 'primevue/button';
import Message from 'primevue/message';

const { username, password, loginError, login } = useAuth();
</script>

<style scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--p-surface-ground);
}

.login-card {
  width: 100%;
  max-width: 400px;
  padding: 2.5rem;
  background: var(--p-surface-card);
  border-radius: 12px;
  border: 1px solid var(--p-surface-border);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.login-header {
  text-align: center;
  margin-bottom: 2rem;
}

.login-header h2 {
  margin: 0;
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--p-text-color);
}

.login-header p {
  margin: 0.25rem 0 0;
  color: var(--p-text-muted-color);
  font-size: 0.9rem;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.field label {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--p-text-color);
}

.w-full {
  width: 100%;
}
</style>
