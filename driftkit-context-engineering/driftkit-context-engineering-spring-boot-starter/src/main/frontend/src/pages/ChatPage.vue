<template>
  <div class="chat-page">
    <div class="chat-layout">
      <!-- Chat List Sidebar -->
      <aside class="chat-sidebar" :class="{ collapsed: !showChatsList }">
        <div class="sidebar-header">
          <h5 v-if="showChatsList" class="m-0">Chats</h5>
          <div class="d-flex gap-1">
            <Button icon="pi pi-plus" size="small" @click="createChat" v-if="showChatsList" />
            <Button :icon="showChatsList ? 'pi pi-angle-left' : 'pi pi-angle-right'" size="small" severity="secondary" text @click="showChatsList = !showChatsList" />
          </div>
        </div>
        <div v-if="showChatsList" class="chat-list">
          <div
            v-for="chat in chats" :key="chat.chatId"
            class="chat-item" :class="{ active: chat.chatId === selectedChatId }"
            @click="selectChat(chat.chatId)"
          >
            {{ chat.name }}
          </div>
        </div>
      </aside>

      <!-- Main Chat Area -->
      <div class="chat-main">
        <!-- Messages -->
        <div ref="chatMessages" class="message-area">
          <div v-for="msg in messages" :key="msg.messageId" class="message-row" :class="msg.type === 'USER' ? 'user' : 'ai'">
            <ProgressSpinner v-if="msg.loading" style="width: 24px; height: 24px" />
            <template v-else>
              <div class="message-bubble" :class="msg.type === 'USER' ? 'bubble-user' : 'bubble-ai'">
                <Button icon="pi pi-copy" class="copy-btn" size="small" severity="secondary" text @click="copyToClipboard(msg.message)" />

                <!-- Image message -->
                <div v-if="msg.messageType === 'IMAGE' && msg.imageTaskId">
                  <img v-for="(_, idx) in (imageTasks[msg.imageTaskId]?.images || [1])" :key="idx"
                       :src="'/data/v1.0/admin/llm/image/' + msg.imageTaskId + '/resource/' + idx"
                       class="chat-image" />
                </div>

                <!-- Text message -->
                <div v-else>
                  <!-- Context toggle -->
                  <div v-if="msg.context" class="collapsible-section">
                    <div class="section-header" @click="msg.showContext = !msg.showContext">
                      <span>Context</span>
                      <i :class="msg.showContext ? 'pi pi-chevron-up' : 'pi pi-chevron-down'" />
                    </div>
                    <pre v-if="msg.showContext" class="section-content">{{ typeof msg.context === 'string' ? msg.context : JSON.stringify(msg.context, null, 2) }}</pre>
                  </div>

                  <!-- Token probabilities toggle -->
                  <div v-if="msg.tokenLogprobs && chatForm.logprobs" class="collapsible-section">
                    <div class="section-header" @click="msg.showLogprobs = !msg.showLogprobs">
                      <span>Token probabilities</span>
                      <i :class="msg.showLogprobs ? 'pi pi-chevron-up' : 'pi pi-chevron-down'" />
                    </div>
                    <div v-if="msg.showLogprobs" class="section-content" v-html="displayTokenProbabilities(msg)"></div>
                  </div>

                  <!-- Prompt result object (prompt + testResult) -->
                  <div v-if="isPromptResultObject(msg.message)" class="prompt-result">
                    <div v-if="parsePromptResult(msg.message).prompt" class="collapsible-section">
                      <div class="section-header">
                        <span>Prompt</span>
                        <Button icon="pi pi-copy" size="small" severity="secondary" text @click="copyToClipboard(parsePromptResult(msg.message).prompt)" />
                      </div>
                      <div class="section-content" v-html="formatMessage(String(parsePromptResult(msg.message).prompt))"></div>
                    </div>
                    <div v-if="parsePromptResult(msg.message).testResult" class="collapsible-section mt-2">
                      <div class="section-header">
                        <span>Test Result</span>
                        <Button icon="pi pi-copy" size="small" severity="secondary" text @click="copyToClipboard(JSON.stringify(parsePromptResult(msg.message).testResult))" />
                      </div>
                      <div class="section-content" v-html="formatMessage(JSON.stringify(parsePromptResult(msg.message).testResult, null, 2))"></div>
                    </div>
                  </div>

                  <!-- Thoughts toggle -->
                  <div v-else-if="msg.message && msg.message.includes('<thoughts')" class="collapsible-section">
                    <div class="section-header" @click="msg.showThoughts = !msg.showThoughts">
                      <span>Model thoughts</span>
                      <i :class="msg.showThoughts ? 'pi pi-chevron-up' : 'pi pi-chevron-down'" />
                    </div>
                    <div v-if="msg.showThoughts" class="section-content" v-html="formatMarkdown(msg.message)"></div>
                    <div v-else v-html="formatMessage(msg.message.replace(/<thoughts[\s\S]*?<\/thoughts>/g, ''))"></div>
                  </div>

                  <!-- Regular message -->
                  <div v-else v-html="formatMessage(msg.message)"></div>
                </div>
              </div>
              <div class="message-meta">
                {{ formatTime(msg.createdTime) }}
                <span v-if="msg.responseTime && msg.type === 'AI'">
                  ({{ ((msg.responseTime - (msg.requestInitTime || msg.createdTime)) / 1000).toFixed(2) }}s)
                </span>
              </div>
            </template>
          </div>
        </div>

        <!-- Input Form -->
        <div class="input-area">
          <Textarea v-model="chatMessage" rows="3" class="w-full" placeholder="Type a message..." @keydown.ctrl.enter="sendChatMessage" />
          <div class="input-options">
            <Select v-model="chatForm.workflow" :options="workflowOptions" optionLabel="label" optionValue="value" placeholder="Workflow" class="option-field" />
            <Select v-model="chatForm.language" :options="languageOptions" placeholder="Language" class="option-field" />
            <InputText v-model="chatForm.systemMessage" placeholder="System message" class="option-field" />
            <InputText v-model="chatForm.purpose" placeholder="Purpose" class="option-field" />
            <div class="d-flex align-items-center gap-1">
              <Checkbox v-model="chatForm.logprobs" :binary="true" inputId="logprobs" />
              <label for="logprobs" class="text-sm">Logprobs</label>
            </div>
          </div>
          <Button label="Send" icon="pi pi-send" @click="sendChatMessage" class="mt-2" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue';
import axios from 'axios';
import { marked } from 'marked';
import Button from 'primevue/button';
import Textarea from 'primevue/textarea';
import InputText from 'primevue/inputtext';
import Select from 'primevue/select';
import Checkbox from 'primevue/checkbox';
import ProgressSpinner from 'primevue/progressspinner';

interface Chat { chatId: string; name: string; }
interface Message {
  messageId: string; message: string; type: string; messageType: string;
  imageTaskId?: string; createdTime: number; responseTime: number;
  loading?: boolean; requestInitTime?: number;
  tokenLogprobs?: any; context?: any;
  showContext?: boolean; showLogprobs?: boolean; showThoughts?: boolean;
}

const showChatsList = ref(true);
const chats = ref<Chat[]>([]);
const selectedChatId = ref('system');
const messages = ref<Message[]>([]);
const chatMessage = ref('');
const chatForm = ref({ workflow: '', language: '', systemMessage: '', purpose: '', logprobs: false, topLogprobs: '2' });
const languages = ref<string[]>([]);
const workflows = ref<any[]>([]);
const imageTasks = ref<Record<string, any>>({});
const chatMessages = ref<HTMLElement | null>(null);

const workflowOptions = computed(() => [
  { label: 'None', value: '' },
  ...workflows.value.map((w: any) => ({ label: w.name, value: w.id })),
]);
const languageOptions = computed(() => ['', ...languages.value]);

// --- API ---
const fetchChats = () => {
  axios.get('/data/v1.0/admin/llm/chats').then(res => {
    chats.value = res.data.data;
    if (!selectedChatId.value && chats.value.length > 0) {
      selectedChatId.value = chats.value[0].chatId;
      fetchMessages(selectedChatId.value);
    }
  }).catch(err => console.error('Error fetching chats:', err));
};

const fetchMessages = (chatId: string) => {
  axios.get(`/data/v1.0/admin/llm/chat/${chatId}`, { params: { skip: 0, limit: 50 } })
    .then(res => {
      messages.value = res.data.data;
      messages.value.forEach(m => {
        if (m.messageType === 'IMAGE' && m.imageTaskId && !imageTasks.value[m.imageTaskId]) {
          axios.get(`/data/v1.0/admin/llm/image/${m.imageTaskId}`).then(r => { imageTasks.value[m.imageTaskId!] = r.data.data; });
        }
      });
      scrollToBottom();
    }).catch(err => console.error('Error fetching messages:', err));
};

const createChat = () => {
  const name = prompt('Enter chat name:');
  if (!name) return;
  axios.post('/data/v1.0/admin/llm/chat', { name }).then(() => fetchChats());
};

const selectChat = (chatId: string) => {
  selectedChatId.value = chatId;
  messages.value = [];
  fetchMessages(chatId);
};

const sendChatMessage = () => {
  if (!chatMessage.value.trim()) return;
  const payload = {
    chatId: selectedChatId.value,
    message: chatMessage.value,
    workflow: chatForm.value.workflow || null,
    language: chatForm.value.language || null,
    systemMessage: chatForm.value.systemMessage || null,
    purpose: chatForm.value.purpose || null,
    variables: {},
    logprobs: chatForm.value.logprobs,
    topLogprobs: chatForm.value.logprobs ? parseInt(chatForm.value.topLogprobs) : null,
  };

  const userMsg: Message = {
    messageId: crypto.randomUUID(), message: chatMessage.value,
    type: 'USER', messageType: 'TEXT', createdTime: Date.now(), responseTime: 0,
  };
  messages.value.push(userMsg);
  chatMessage.value = '';

  axios.post('/data/v1.0/admin/llm/message', payload).then(res => {
    const aiMsg: Message = {
      messageId: res.data.data.messageId, message: '', type: 'AI', messageType: '',
      createdTime: Date.now(), responseTime: 0, loading: true, requestInitTime: Date.now(),
    };
    messages.value.push(aiMsg);
    pollMessage(aiMsg);
  }).catch(() => alert('Error sending message'));
};

const pollMessage = (msg: Message) => {
  const interval = setInterval(() => {
    axios.get(`/data/v1.0/admin/llm/message/${msg.messageId}`).then(res => {
      const task = res.data.data;
      if (!task) return;
      const hasResult = task.result?.trim();
      const hasImage = task.imageTaskId;
      if (hasResult || hasImage) {
        const idx = messages.value.findIndex(m => m.messageId === msg.messageId);
        if (idx !== -1) {
          messages.value.splice(idx, 1, {
            ...messages.value[idx], loading: false, message: task.result || '',
            messageType: hasImage ? 'IMAGE' : 'TEXT', imageTaskId: task.imageTaskId || '',
            createdTime: task.createdTime, responseTime: task.responseTime,
            tokenLogprobs: task.tokenLogprobs, context: task.context,
            showContext: false, showLogprobs: false, showThoughts: false,
          });
        }
        clearInterval(interval);
        scrollToBottom();
      }
    });
  }, 1000);
};

// --- Formatting ---
const formatTime = (ts: number) => ts ? new Date(ts).toLocaleString() : '';

const formatMarkdown = (str: string): string => {
  try { return marked.parse(str, { async: false }) as string; } catch { return str; }
};

const isJSON = (text: string): boolean => {
  if (!text) return false;
  const t = text.trim();
  if ((t.startsWith('{') && t.endsWith('}')) || (t.startsWith('[') && t.endsWith(']'))) {
    try { JSON.parse(t); return true; } catch { return false; }
  }
  return false;
};

const isPromptResultObject = (text: string | any): boolean => {
  if (!text) return false;
  if (typeof text === 'object') return text.prompt !== undefined || text.testResult !== undefined;
  if (typeof text === 'string') {
    try {
      const obj = JSON.parse(text.trim());
      return obj && (obj.prompt !== undefined || obj.testResult !== undefined);
    } catch { return false; }
  }
  return false;
};

const parsePromptResult = (text: string | any): any => {
  if (typeof text === 'object') return text;
  if (typeof text === 'string') {
    try { return JSON.parse(text.trim()); } catch { return { prompt: '', testResult: '' }; }
  }
  return { prompt: '', testResult: '' };
};

const formatMessage = (text: string): string => {
  if (!text) return '';
  if (isJSON(text)) {
    try {
      const parsed = JSON.parse(text);
      return '<pre class="json-pre">' + JSON.stringify(parsed, null, 2) + '</pre>';
    } catch { /* fall through */ }
  }
  return formatMarkdown(text);
};

const displayTokenProbabilities = (msg: Message): string => {
  if (!msg.tokenLogprobs?.content) return formatMarkdown(msg.message);
  let html = '<div class="token-probs">';
  msg.tokenLogprobs.content.forEach((token: any) => {
    const prob = Math.exp(token.logprob) * 100;
    const bg = prob > 90 ? 'rgba(144,238,144,0.5)' : prob > 70 ? 'rgba(144,238,144,0.3)' :
               prob > 50 ? 'rgba(255,255,224,0.5)' : prob > 30 ? 'rgba(255,165,0,0.3)' : 'rgba(255,99,71,0.3)';
    const escaped = token.token.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
    html += `<span class="token" style="background:${bg}" title="${prob.toFixed(1)}%">${escaped}</span>`;
  });
  return html + '</div>';
};

const copyToClipboard = async (text: string) => {
  try {
    let copy = text;
    if (isJSON(text)) { try { copy = JSON.stringify(JSON.parse(text), null, 2); } catch {} }
    await navigator.clipboard.writeText(copy);
  } catch {}
};

const scrollToBottom = () => {
  nextTick(() => { if (chatMessages.value) chatMessages.value.scrollTop = chatMessages.value.scrollHeight; });
};

onMounted(() => {
  fetchChats();
  axios.get('/data/v1.0/admin/workflows').then(r => { workflows.value = r.data.data || []; });
  axios.get('/data/v1.0/admin/llm/languages').then(r => { languages.value = r.data.data || []; });
});
</script>

<style scoped>
.chat-page { height: calc(100vh - 120px); }

.chat-layout { display: flex; height: 100%; gap: 1rem; }

.chat-sidebar {
  width: 240px; min-width: 240px; background: var(--p-surface-card);
  border: 1px solid var(--p-surface-border); border-radius: 8px;
  display: flex; flex-direction: column; overflow: hidden;
  transition: width 0.2s, min-width 0.2s;
}
.chat-sidebar.collapsed { width: 48px; min-width: 48px; }

.sidebar-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 0.75rem; border-bottom: 1px solid var(--p-surface-border);
}

.chat-list { overflow-y: auto; flex: 1; padding: 0.25rem; }

.chat-item {
  padding: 0.5rem 0.75rem; border-radius: 6px; cursor: pointer;
  font-size: 0.875rem; margin-bottom: 2px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  color: var(--p-text-color);
}
.chat-item:hover { background: var(--p-surface-100); }
.chat-item.active { background: var(--p-primary-color); color: var(--p-primary-contrast-color); }

.chat-main {
  flex: 1; display: flex; flex-direction: column;
  background: var(--p-surface-card); border: 1px solid var(--p-surface-border); border-radius: 8px;
  overflow: hidden;
}

.message-area { flex: 1; overflow-y: auto; padding: 1rem; }

.message-row { margin-bottom: 1rem; }
.message-row.user { text-align: right; }
.message-row.ai { text-align: left; }

.message-bubble {
  display: inline-block; max-width: 80%; padding: 0.75rem 1rem;
  border-radius: 12px; text-align: left; position: relative;
  word-wrap: break-word;
}
.bubble-user { background: var(--p-primary-100); color: var(--p-text-color); }
.bubble-ai { background: var(--p-surface-100); color: var(--p-text-color); }

.copy-btn { position: absolute; top: 4px; right: 4px; opacity: 0.4; }
.copy-btn:hover { opacity: 1; }

.message-meta { font-size: 0.75rem; color: var(--p-text-muted-color); margin-top: 0.25rem; }

.collapsible-section { margin-bottom: 0.5rem; border: 1px solid var(--p-surface-border); border-radius: 6px; overflow: hidden; }
.section-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 0.5rem 0.75rem; background: var(--p-surface-50); cursor: pointer;
  font-size: 0.8rem; font-weight: 500;
}
.section-content { padding: 0.5rem 0.75rem; font-family: monospace; font-size: 0.8rem; max-height: 300px; overflow: auto; white-space: pre-wrap; word-break: break-word; }

.chat-image { max-width: 300px; border-radius: 8px; margin: 0.25rem 0; }

.input-area { padding: 1rem; border-top: 1px solid var(--p-surface-border); }
.input-options { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-top: 0.5rem; }
.option-field { flex: 1; min-width: 140px; }

.token-probs { font-family: monospace; line-height: 1.5; word-wrap: break-word; }
:deep(.token) { display: inline-block; padding: 1px 3px; margin: 1px; border-radius: 3px; cursor: help; }
:deep(.json-pre) { white-space: pre-wrap; word-break: break-word; background: var(--p-surface-50); padding: 0.75rem; border-radius: 6px; font-size: 0.85rem; }

.d-flex { display: flex; }
.align-items-center { align-items: center; }
.gap-1 { gap: 0.25rem; }
.m-0 { margin: 0; }
.mt-2 { margin-top: 0.5rem; }
.w-full { width: 100%; }
.text-sm { font-size: 0.875rem; }
</style>
