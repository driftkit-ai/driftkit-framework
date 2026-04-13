<template>
  <div class="chat-page">
    <!-- Reuse existing ChatView - will be rebuilt with PrimeVue components in a future pass -->
    <ChatView />
  </div>
</template>

<script setup lang="ts">
import ChatView from '@/views/ChatView.vue';
</script>

<style scoped>
.chat-page {
  max-width: 1400px;
}
</style>
