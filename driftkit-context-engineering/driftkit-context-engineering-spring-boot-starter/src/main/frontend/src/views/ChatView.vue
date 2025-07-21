<template>
  <div class="mt-4">
    <!-- Copy Notification -->
    <div v-if="showCopyNotification" class="copy-notification" @click="showCopyNotification = false">
      <div class="notification-content">
        <span>Текст скопирован</span>
      </div>
    </div>

    <div class="row">
      <!-- Chats List -->
      <div :class="['col-md-3', { 'd-none': !showChatsList }]" style="max-height: 600px; overflow-y: auto;">
        <div class="d-flex justify-content-between align-items-center mb-2">
          <h4>Chats</h4>
          <button class="btn btn-primary btn-sm" @click="createChat">New Chat</button>
          <button class="btn btn-sm btn-secondary" @click="toggleChatsList">
            {{ showChatsList ? 'Hide' : 'Show' }}
          </button>
        </div>
        <ul class="list-group">
          <li v-for="chat in chats" :key="chat.chatId" :class="['list-group-item', { 'active': chat.chatId === selectedChatId }]" @click="selectChat(chat.chatId)" style="cursor: pointer;">
            {{ chat.name }}
          </li>
        </ul>
      </div>
      <!-- Chat Area -->
      <div :class="showChatsList ? 'col-md-9' : 'col-md-12'">
        <div ref="chatMessages" class="chat-messages" style="max-height: 500px; overflow-y: auto;">
          <div v-for="(message) in messages" :key="message.messageId" class="mb-3">
            <div v-if="message.loading" class="text-start">
              <div class="alert alert-info d-inline-block">Loading...</div>
            </div>
            <div v-else>
              <!-- User Message -->
              <div v-if="message.type === 'USER'" class="text-end">
                <div class="alert alert-primary d-inline-block position-relative" align="left">
                  <button class="copy-button" @click="copyMessageContent(message.message)" title="Копировать">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" viewBox="0 0 16 16">
                      <path d="M4 1.5H3a2 2 0 0 0-2 2V14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V3.5a2 2 0 0 0-2-2h-1v1h1a1 1 0 0 1 1 1V14a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3.5a1 1 0 0 1 1-1h1v-1z"/>
                      <path d="M9.5 1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-3a.5.5 0 0 1-.5-.5v-1a.5.5 0 0 1 .5-.5h3zm-3-1A1.5 1.5 0 0 0 5 1.5v1A1.5 1.5 0 0 0 6.5 4h3A1.5 1.5 0 0 0 11 2.5v-1A1.5 1.5 0 0 0 9.5 0h-3z"/>
                    </svg>
                  </button>
                  <p v-html="formatMarkdown(message.message)"></p>
                </div>
                <div class="text-muted">{{ formatTime(message.createdTime) }}</div>
              </div>
              <!-- AI Message -->
              <div v-else-if="message.type === 'AI'" class="text-start">
                <div class="alert alert-secondary d-inline-block position-relative">
                  <button class="copy-button" @click="copyMessageContent(message.message)" title="Копировать">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" viewBox="0 0 16 16">
                      <path d="M4 1.5H3a2 2 0 0 0-2 2V14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V3.5a2 2 0 0 0-2-2h-1v1h1a1 1 0 0 1 1 1V14a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3.5a1 1 0 0 1 1-1h1v-1z"/>
                      <path d="M9.5 1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-3a.5.5 0 0 1-.5-.5v-1a.5.5 0 0 1 .5-.5h3zm-3-1A1.5 1.5 0 0 0 5 1.5v1A1.5 1.5 0 0 0 6.5 4h3A1.5 1.5 0 0 0 11 2.5v-1A1.5 1.5 0 0 0 9.5 0h-3z"/>
                    </svg>
                  </button>
                  <div v-if="message.messageType === 'TEXT'">
                    <!-- Context display -->
                    <div v-if="message.context" class="context-container">
                      <div class="context-header">
                        <span>Context:</span>
                        <button class="toggle-button" @click="toggleContext(message)">
                          {{ message.showContext ? 'Hide' : 'Show' }}
                        </button>
                      </div>
                      <div v-if="message.showContext" class="context-content" v-html="formatJSON(message.context)"></div>
                    </div>
                    
                    <!-- Token probabilities display -->
                    <div v-if="message.tokenLogprobs && chatForm.logprobs" class="logprobs-container">
                      <div class="logprobs-header">
                        <span>Token probabilities:</span>
                        <button class="toggle-button" @click="toggleLogprobs(message)">
                          {{ message.showLogprobs ? 'Hide' : 'Show' }}
                        </button>
                      </div>
                      <div v-if="message.showLogprobs" v-html="displayTokenProbabilities(message)"></div>
                    </div>
                    
                    <!-- Special formatting for messages with prompt and testResult fields -->
                    <div v-if="isPromptResultObject(message.message)" class="prompt-result-container">
                      <div v-if="parsePromptResultObject(message.message).prompt" class="prompt-container">
                        <div class="prompt-header">
                          <span>Prompt:</span>
                          <button class="copy-button-small" @click="copyPromptContent(parsePromptResultObject(message.message).prompt)" title="Copy prompt text">
                            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" fill="currentColor" viewBox="0 0 16 16">
                              <path d="M4 1.5H3a2 2 0 0 0-2 2V14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V3.5a2 2 0 0 0-2-2h-1v1h1a1 1 0 0 1 1 1V14a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3.5a1 1 0 0 1 1-1h1v-1z"/>
                              <path d="M9.5 1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-3a.5.5 0 0 1-.5-.5v-1a.5.5 0 0 1 .5-.5h3zm-3-1A1.5 1.5 0 0 0 5 1.5v1A1.5 1.5 0 0 0 6.5 4h3A1.5 1.5 0 0 0 11 2.5v-1A1.5 1.5 0 0 0 9.5 0h-3z"/>
                            </svg>
                          </button>
                        </div>
                        <div v-html="typeof parsePromptResultObject(message.message).prompt === 'string' ? 
                          formatMarkdown(parsePromptResultObject(message.message).prompt) : 
                          formatJSON(JSON.stringify(parsePromptResultObject(message.message).prompt, null, 2))"></div>
                      </div>
                      <div v-if="parsePromptResultObject(message.message).testResult" class="test-result-container">
                        <div class="test-result-header">
                          <span>Test Result:</span>
                          <button class="copy-button-small" @click="copyTestResultContent(parsePromptResultObject(message.message).testResult)" title="Copy test result">
                            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" fill="currentColor" viewBox="0 0 16 16">
                              <path d="M4 1.5H3a2 2 0 0 0-2 2V14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V3.5a2 2 0 0 0-2-2h-1v1h1a1 1 0 0 1 1 1V14a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3.5a1 1 0 0 1 1-1h1v-1z"/>
                              <path d="M9.5 1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-3a.5.5 0 0 1-.5-.5v-1a.5.5 0 0 1 .5-.5h3zm-3-1A1.5 1.5 0 0 0 5 1.5v1A1.5 1.5 0 0 0 6.5 4h3A1.5 1.5 0 0 0 11 2.5v-1A1.5 1.5 0 0 0 9.5 0h-3z"/>
                            </svg>
                          </button>
                        </div>
                        <div v-html="formatJSON(typeof parsePromptResultObject(message.message).testResult === 'string' ? 
                          parsePromptResultObject(message.message).testResult : 
                          JSON.stringify(parsePromptResultObject(message.message).testResult, null, 2))"></div>
                      </div>
                    </div>
                    
                    <!-- Thoughts display if present in the message -->
                    <div v-else-if="message.message && message.message.includes('<thoughts')" class="thoughts-container">
                      <div class="thoughts-header">
                        <span>Model thoughts:</span>
                        <button class="toggle-button" @click="message.showThoughts = !message.showThoughts">
                          {{ message.showThoughts ? 'Hide' : 'Show' }}
                        </button>
                      </div>
                      <div v-if="message.showThoughts" v-html="formatJSON(formatMarkdown(message.message))"></div>
                      <div v-else>
                        <!-- When thoughts are hidden, check if the remaining message might be JSON -->
                        <div v-html="isJSON(message.message.replace(/<thoughts[\s\S]*?<\/thoughts>/g, '')) 
                          ? formatJSON(message.message.replace(/<thoughts[\s\S]*?<\/thoughts>/g, '')) 
                          : formatMarkdown(message.message.replace(/<thoughts[\s\S]*?<\/thoughts>/g, ''))"></div>
                      </div>
                    </div>
                    <div v-else v-html="isJSON(message.message) ? formatJSON(message.message) : formatMarkdown(message.message)"></div>
                  </div>
                  <div v-else-if="message.messageType === 'IMAGE'">
                    <div v-for="(image, idx) in (imageTasks[message.imageTaskId]?.images || [])" :key="idx">
                      <img :src="'/data/v1.0/admin/llm/image/' + message.imageTaskId + '/resource/' + idx" class="img-fluid" width="200" />
                    </div>
                  </div>
                  <div v-html="formatJSON(formatMarkdown(message.message))" v-else>
                  </div>
                </div>
                <div class="text-muted">
                  {{ formatTime(message.createdTime) }}
                  <span v-if="message.responseTime">
                    ({{ formatSeconds(message.responseTime - (message.requestInitTime || message.createdTime)) }}s)
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <!-- Message Input Form -->
        <div class="mt-4">
          <h5>Send Message</h5>
          <textarea v-model="chatMessage" class="form-control" rows="3"></textarea>
          <div class="row mt-2">
            <div class="col-md-3">
              <label class="form-label">Workflow:</label>
              <select v-model="chatForm.workflow" class="form-control">
                <option value="">None</option>
                <option v-for="workflow in workflows" :key="workflow.id" :value="workflow.id">
                  {{ workflow.name }}
                </option>
              </select>
            </div>
            <div class="col-md-3">
              <label class="form-label">Language:</label>
              <select v-model="chatForm.language" class="form-control">
                <option value="">Select a language</option>
                <option v-for="lang in languages" :key="lang" :value="lang">{{ lang }}</option>
              </select>
            </div>
            <div class="col-md-3">
              <label class="form-label">System Message:</label>
              <input v-model="chatForm.systemMessage" class="form-control" />
            </div>
            <div class="col-md-3">
              <label class="form-label">Purpose:</label>
              <input v-model="chatForm.purpose" class="form-control" placeholder="Optional purpose" />
            </div>
          </div>
          <div class="row mt-2">
            <div class="col-md-3">
              <div class="form-check">
                <input class="form-check-input" type="checkbox" v-model="chatForm.logprobs" id="logprobsCheck">
                <label class="form-check-label" for="logprobsCheck">
                  Show token probabilities
                </label>
              </div>
            </div>
            <div class="col-md-3" v-if="chatForm.logprobs">
              <label class="form-label">Top alternatives:</label>
              <select v-model="chatForm.topLogprobs" class="form-control">
                <option value="1">1</option>
                <option value="2">2</option>
                <option value="3">3</option>
                <option value="5">5</option>
              </select>
            </div>
          </div>
          <button @click="sendChatMessage" class="btn btn-primary mt-3">Send</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, onMounted, nextTick, watch } from 'vue';
import axios from 'axios';
import { marked } from 'marked';

interface Chat {
  chatId: string;
  name: string;
  systemMessage: string;
  language: string;
  memoryLength: number;
  createdTime: number;
  hidden: boolean;
}

interface Message {
  messageId: string;
  message: string;
  type: string;
  messageType: string;
  imageTaskId?: string;
  createdTime: number;
  responseTime: number;
  loading?: boolean;
  requestInitTime?: number;
  logprobs?: any;
  tokenLogprobs?: any;
  context?: string;
  showContext?: boolean;
  showLogprobs?: boolean;
  showThoughts?: boolean;
}

interface ImageTask {
  images: any[];
}

export default defineComponent({
  name: 'ChatView',
  setup() {
    const showChatsList = ref(true);
    const chats = ref<Chat[]>([]);
    const selectedChatId = ref<string>('system');
    const messages = ref<Message[]>([]);
    const chatMessage = ref('');
    const chatForm = ref({ 
      workflow: '', 
      language: '', 
      systemMessage: '', 
      purpose: '',
      logprobs: false,
      topLogprobs: '2'
    });
    const languages = ref<string[]>([]);
    const workflows = ref<any[]>([]);
    const imageTasks = ref<{ [key: string]: ImageTask }>({});
    const chatMessages = ref<HTMLElement | null>(null);

    const fetchChats = () => {
      const creds = localStorage.getItem('credentials');
      axios.get('/data/v1.0/admin/llm/chats', {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
      }).then(res => {
        chats.value = res.data.data;
        if (!selectedChatId.value && chats.value.length > 0) {
          selectedChatId.value = chats.value[0].chatId;
          fetchMessages(selectedChatId.value);
        }
      }).catch(err => {
        console.error('Error fetching chats:', err);
      });
    };

    const fetchMessages = (chatId: string) => {
      const creds = localStorage.getItem('credentials');
      axios.get(`/data/v1.0/admin/llm/chat/${chatId}`, {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) },
        params: { skip: 0, limit: 50 }
      }).then(res => {
        messages.value = res.data.data;
        messages.value.forEach(message => {
          if (message.messageType === 'IMAGE' && message.imageTaskId && !imageTasks.value[message.imageTaskId]) {
            fetchImageTask(message.imageTaskId);
          }
        });
        nextTick(() => {
          if (chatMessages.value) {
            chatMessages.value.scrollTop = chatMessages.value.scrollHeight;
          }
        });
      }).catch(err => {
        console.error('Error fetching messages:', err);
      });
    };

    const fetchImageTask = (imageTaskId: string) => {
      const creds = localStorage.getItem('credentials');
      axios.get(`/data/v1.0/admin/llm/image/${imageTaskId}`, {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
      }).then(res => {
        imageTasks.value[imageTaskId] = res.data.data;
      }).catch(err => {
        console.error('Error fetching image task:', err);
      });
    };

    const createChat = () => {
      const chatName = prompt("Введите название нового чата:");
      if (!chatName) return;
      const creds = localStorage.getItem('credentials');
      axios.post('/data/v1.0/admin/llm/chat', { name: chatName }, {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
      }).then(res => {
        fetchChats();
        res.data;
      }).catch(err => {
        console.error('Error creating chat:', err);
        alert('Ошибка создания чата');
      });
    };

    const sendChatMessage = () => {
      if (!chatMessage.value.trim()) {
        alert('Message cannot be empty');
        return;
      }
      const creds = localStorage.getItem('credentials');
      const payload = {
        chatId: selectedChatId.value,
        message: chatMessage.value,
        workflow: chatForm.value.workflow || null,
        language: chatForm.value.language || null,
        systemMessage: chatForm.value.systemMessage || null,
        purpose: chatForm.value.purpose || null,
        variables: {},
        jsonResponse: false,
        logprobs: chatForm.value.logprobs || false,
        topLogprobs: chatForm.value.logprobs ? parseInt(chatForm.value.topLogprobs) : null,
      };
      const messageId = generateUUID();
      const newMessage: Message = {
        messageId: messageId,
        message: chatMessage.value,
        type: 'USER',
        messageType: 'TEXT',
        createdTime: Date.now(),
        responseTime: 0,
        loading: false,
        requestInitTime: Date.now(),
      };
      messages.value.push(newMessage);
      chatMessage.value = '';
      axios.post('/data/v1.0/admin/llm/message', payload, {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
      }).then(res => {
        const responseData = res.data.data;
        const aiMessageId = responseData.messageId;
        const aiMessage: Message = {
          messageId: aiMessageId,
          message: '',
          type: 'AI',
          messageType: '',
          createdTime: Date.now(),
          responseTime: 0,
          loading: true,
          requestInitTime: Date.now(),
        };
        messages.value.push(aiMessage);
        pollMessage(aiMessage);
      }).catch(err => {
        console.error('Error sending message:', err);
        alert('Error sending message');
      });
    };

    const pollMessage = (message: Message) => {
      const creds = localStorage.getItem('credentials');
      const interval = setInterval(() => {
        axios.get(`/data/v1.0/admin/llm/message/${message.messageId}`, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        }).then(res => {
          const msgTask = res.data.data;
          if (msgTask) {
            const responseMessage = msgTask.result || '';
            const hasTextResult = responseMessage.trim() !== '';
            const hasImageTask = msgTask.imageTaskId !== undefined && msgTask.imageTaskId !== null && msgTask.imageTaskId !== '';
            if (hasTextResult || hasImageTask) {
              const index = messages.value.findIndex(m => m.messageId === message.messageId);
              if (index !== -1) {
                messages.value.splice(index, 1, {
                  ...messages.value[index],
                  loading: false,
                  message: responseMessage,
                  messageType: hasImageTask ? 'IMAGE' : 'TEXT',
                  imageTaskId: msgTask.imageTaskId || '',
                  createdTime: msgTask.createdTime,
                  responseTime: msgTask.responseTime,
                  logprobs: msgTask.logprobs || null,
                  tokenLogprobs: msgTask.tokenLogprobs || null,
                  context: msgTask.context || null,
                  showContext: false,
                  showLogprobs: false,
                  showThoughts: false,
                });
              }
              clearInterval(interval);
              nextTick(() => {
                if (chatMessages.value) {
                  chatMessages.value.scrollTop = chatMessages.value.scrollHeight;
                }
              });
            }
          }
        }).catch(err => {
          console.error('Error polling message:', err);
        });
      }, 1000);
    };

    const toggleChatsList = () => {
      showChatsList.value = !showChatsList.value;
    };

    const selectChat = (chatId: string) => {
      selectedChatId.value = chatId;
      messages.value = [];
      fetchMessages(chatId);
    };

    const formatTime = (timestamp: number): string => {
      if (!timestamp) return '';
      return new Date(timestamp).toLocaleString();
    };

    const formatSeconds = (ms: number): string => {
      return (ms / 1000).toFixed(2);
    };

    const formatMarkdown = (str: string): string => {
      try {
        return marked.parse(str, { async: false });
      } catch {
        return str;
      }
    };
    
    const isJSON = (text: string): boolean => {
      if (!text) return false;
      
      // Check if the text is a valid JSON object or array
      text = text.trim();
      if ((text.startsWith('{') && text.endsWith('}')) || 
          (text.startsWith('[') && text.endsWith(']'))) {
        try {
          JSON.parse(text);
          return true;
        } catch (e) {
          return false;
        }
      }
      return false;
    };
    
    // Check if the message is a prompt result object containing prompt and testResult
    const isPromptResultObject = (text: string | any): boolean => {
      if (!text) return false;
      
      // If it's already an object (not a string), check directly
      if (typeof text === 'object') {
        return text && (text.prompt !== undefined || text.testResult !== undefined);
      }
      
      // Otherwise, try to parse it if it's a string
      if (typeof text === 'string') {
        try {
          const obj = JSON.parse(text.trim());
          return obj && (obj.prompt !== undefined || obj.testResult !== undefined);
        } catch (e) {
          return false;
        }
      }
      
      return false;
    };
    
    // Parse the prompt result object from the message text
    const parsePromptResultObject = (text: string | any): any => {
      // If it's already an object, return it directly
      if (typeof text === 'object') {
        return text;
      }
      
      // Try to parse it as JSON if it's a string
      if (typeof text === 'string') {
        try {
          return JSON.parse(text.trim());
        } catch (e) {
          // If parsing fails, return empty object
          return { prompt: '', testResult: '' };
        }
      }
      
      // Fallback
      return { prompt: '', testResult: '' };
    };
    
    const displayTokenProbabilities = (message: Message): string => {
      if (!message.tokenLogprobs || !message.tokenLogprobs.content) {
        return formatMarkdown(message.message);
      }
      
      let html = '<div class="token-probs">';
      message.tokenLogprobs.content.forEach((token: any) => {
        const prob = Math.exp(token.logprob) * 100;
        let bgColor = getColorForProbability(prob);
        
        let tooltip = '<div class="token-tooltip">';
        tooltip += `<div><b>${escapeHtml(token.token)}</b>: ${prob.toFixed(2)}%</div>`;
        
        if (token.topLogprobs && token.topLogprobs.length > 0) {
          tooltip += '<div class="alternatives">';
          token.topLogprobs.forEach((alt: any) => {
            const altProb = Math.exp(alt.logprob) * 100;
            tooltip += `<div>${escapeHtml(alt.token)}: ${altProb.toFixed(2)}%</div>`;
          });
          tooltip += '</div>';
        }
        tooltip += '</div>';
        
        html += `<span class="token" style="background-color: ${bgColor}">${escapeHtml(token.token)}${tooltip}</span>`;
      });
      html += '</div>';
      return html;
    };
    
    const getColorForProbability = (probability: number): string => {
      // Color scale from red (low probability) to green (high probability)
      if (probability > 90) return 'rgba(144, 238, 144, 0.5)'; // light green
      if (probability > 70) return 'rgba(144, 238, 144, 0.3)';
      if (probability > 50) return 'rgba(255, 255, 224, 0.5)'; // light yellow
      if (probability > 30) return 'rgba(255, 165, 0, 0.3)';   // orange
      return 'rgba(255, 99, 71, 0.3)';  // red
    };
    
    const escapeHtml = (text: string): string => {
      const div = document.createElement('div');
      div.textContent = text;
      return div.innerHTML;
    };

    const generateUUID = (): string => {
      return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
      });
    };
    
    const showCopyNotification = ref(false);
    const copyMessageContent = async (content: string | any) => {
      try {
        // Try to detect and format JSON before copying
        let textToCopy = content;
        
        // Check if content is already an object (non-string)
        if (typeof content === 'object') {
          // Special handling for prompt objects
          if (content && content.prompt) {
            // For prompt field, unescape all JSON string escapes (e.g., \n to actual newlines)
            try {
              textToCopy = typeof content.prompt === 'string' ? 
                unescapeJsonString(content.prompt) : 
                JSON.stringify(content.prompt, null, 2);
            } catch (e) {
              textToCopy = content.prompt;
            }
          } else if (content && content.testResult) {
            // For testResult, format as pretty JSON
            try {
              // Handle if testResult is already parsed or is a string JSON
              const testResultObj = typeof content.testResult === 'string' ?
                JSON.parse(content.testResult) : content.testResult;
              textToCopy = JSON.stringify(testResultObj, null, 2);
            } catch (e) {
              textToCopy = content.testResult;
            }
          } else {
            // Regular object, format it nicely
            textToCopy = JSON.stringify(content, null, 2);
          }
        } else if (typeof content === 'string') {
          // Check if string content is a prompt result object
          try {
            const parsedContent = JSON.parse(content);
            
            // Special handling for prompt objects
            if (parsedContent && parsedContent.prompt) {
              // For prompt field, unescape all JSON string escapes (e.g., \n to actual newlines)
              textToCopy = typeof parsedContent.prompt === 'string' ?
                unescapeJsonString(parsedContent.prompt) : 
                JSON.stringify(parsedContent.prompt, null, 2);
            } else if (parsedContent && parsedContent.testResult) {
              // For testResult, format as pretty JSON
              try {
                const testResultObj = typeof parsedContent.testResult === 'string' ?
                  JSON.parse(parsedContent.testResult) : parsedContent.testResult;
                textToCopy = JSON.stringify(testResultObj, null, 2);
              } catch (e) {
                textToCopy = parsedContent.testResult;
              }
            } else {
              // Regular JSON, format it nicely
              textToCopy = JSON.stringify(parsedContent, null, 2);
            }
          } catch (jsonErr) {
            // If not complete JSON, check for JSON objects/arrays using regex
            const jsonRegex = /^\s*(?:\{[\s\S]*\}|\[[\s\S]*\])\s*$/;
            if (jsonRegex.test(content)) {
              try {
                // Try one more time with trimmed content
                const trimmed = content.trim();
                const parsed = JSON.parse(trimmed);
                textToCopy = JSON.stringify(parsed, null, 2);
              } catch (e) {
                // Not valid JSON, use original content
              }
            }
          }
        }
        
        await navigator.clipboard.writeText(textToCopy);
        showCopyNotification.value = true;
        setTimeout(() => {
          showCopyNotification.value = false;
        }, 2000);
      } catch (err) {
        console.error('Failed to copy text: ', err);
      }
    };
    
    // Helper function to unescape JSON strings with proper handling of special chars
    const unescapeJsonString = (str: string): string => {
      try {
        // Use JSON.parse to properly handle all escape sequences (\n, \t, \", etc.)
        return JSON.parse(`"${str.replace(/"/g, '\\"')}"`);
      } catch (e) {
        // If parsing fails, return the original string
        return str;
      }
    };
    
    // Copy just the prompt content with all escape sequences resolved
    const copyPromptContent = async (content: string | any) => {
      try {
        let unescapedContent = content;
        
        // Handle different content types
        if (typeof content === 'string') {
          unescapedContent = unescapeJsonString(content);
        } else if (typeof content === 'object' && content !== null) {
          // For objects, stringify them nicely
          unescapedContent = JSON.stringify(content, null, 2);
        }
        
        await navigator.clipboard.writeText(unescapedContent);
        showCopyNotification.value = true;
        setTimeout(() => {
          showCopyNotification.value = false;
        }, 2000);
      } catch (err) {
        console.error('Failed to copy prompt text: ', err);
      }
    };
    
    // Copy just the test result content as formatted JSON
    const copyTestResultContent = async (content: string | any) => {
      try {
        // Try to parse the test result as JSON and format it
        let formattedContent = content;
        
        // Handle different content types
        if (typeof content === 'object' && content !== null) {
          // If it's already a parsed object, stringify it nicely
          formattedContent = JSON.stringify(content, null, 2);
        } else if (typeof content === 'string') {
          try {
            // If it's a string representation of JSON, parse and re-stringify
            const parsed = JSON.parse(content);
            formattedContent = JSON.stringify(parsed, null, 2);
          } catch (e) {
            // If not valid JSON, use as is
          }
        }
        
        await navigator.clipboard.writeText(formattedContent);
        showCopyNotification.value = true;
        setTimeout(() => {
          showCopyNotification.value = false;
        }, 2000);
      } catch (err) {
        console.error('Failed to copy test result: ', err);
      }
    };
    
    const formatJSON = (text: string): string => {
      // First, check if the entire text is JSON
      try {
        const parsed = JSON.parse(text);
        return '<pre class="json-content">' + syntaxHighlightJSON(JSON.stringify(parsed, null, 2)) + '</pre>';
      } catch (e) {
        // Not a complete JSON, search for JSON objects within the text
        return findAndFormatJSONObjects(text);
      }
    };
    
    const findAndFormatJSONObjects = (text: string): string => {
      if (!text) return '';
      
      // Check if the entire text is wrapped in markdown code blocks
      const codeBlockRegex = /^```(?:json)?\s*([\s\S]*?)```$/;
      const codeBlockMatch = text.match(codeBlockRegex);
      if (codeBlockMatch) {
        try {
          const jsonContent = codeBlockMatch[1].trim();
          const parsed = JSON.parse(jsonContent);
          return text.replace(codeBlockRegex, '<pre class="json-content">' + syntaxHighlightJSON(JSON.stringify(parsed, null, 2)) + '</pre>');
        } catch (e) {
          // Not valid JSON, continue with regular pattern matching
        }
      }
      
      // Regular pattern to find JSON-like structures: objects {...} and arrays [...]
      const jsonPattern = /(\[[\s\S]*?\]|\{[\s\S]*?\})/g;
      let result = text;
      const matches = [...text.matchAll(jsonPattern)];
      
      // Process matches from longest to shortest to avoid nested JSON issues
      matches.sort((a, b) => b[0].length - a[0].length);
      
      for (const match of matches) {
        try {
          const jsonStr = match[0];
          // Only try to parse it if it looks like valid JSON (starts with [ or { and ends with ] or })
          if ((jsonStr.trim().startsWith('[') && jsonStr.trim().endsWith(']')) || 
              (jsonStr.trim().startsWith('{') && jsonStr.trim().endsWith('}'))) {
            const parsed = JSON.parse(jsonStr);
            const formatted = '<pre class="json-content">' + syntaxHighlightJSON(JSON.stringify(parsed, null, 2)) + '</pre>';
            result = result.replace(jsonStr, formatted);
          }
        } catch (e) {
          // Not valid JSON, continue
        }
      }
      
      return result;
    };
    
    const syntaxHighlightJSON = (json: string): string => {
      json = json.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      return json.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g, function (match) {
        let cls = 'json-number';
        if (/^"/.test(match)) {
          if (/:$/.test(match)) {
            cls = 'json-key';
          } else {
            cls = 'json-string';
          }
        } else if (/true|false/.test(match)) {
          cls = 'json-boolean';
        } else if (/null/.test(match)) {
          cls = 'json-null';
        }
        return '<span class="' + cls + '">' + match + '</span>';
      });
    };
    
    const toggleContext = (message: Message) => {
      message.showContext = !message.showContext;
    };
    
    const toggleLogprobs = (message: Message) => {
      message.showLogprobs = !message.showLogprobs;
    };

    const fetchWorkflows = () => {
      const creds = localStorage.getItem('credentials');
      axios.get('/data/v1.0/admin/workflows', {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
      }).then(res => {
        workflows.value = res.data.data;
      }).catch(err => {
        console.error('Error fetching workflows:', err);
      });
    };

    onMounted(() => {
      fetchChats();
      fetchWorkflows();
      const creds = localStorage.getItem('credentials');
      if (creds) {
        axios.get('/data/v1.0/admin/llm/languages', {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        }).then(res => {
          languages.value = res.data.data;
        }).catch(err => {
          console.error('Error fetching languages:', err);
        });
      }
    });

    watch(selectedChatId, () => {
      // Removed loadPrompt() since it was not defined.
    });

    return {
      showChatsList,
      chats,
      selectedChatId,
      messages,
      chatMessage,
      chatForm,
      languages,
      workflows,
      imageTasks,
      chatMessages,
      sendChatMessage,
      toggleChatsList,
      createChat,
      selectChat,
      formatTime,
      formatSeconds,
      formatMarkdown,
      displayTokenProbabilities,
      copyMessageContent,
      copyPromptContent,
      copyTestResultContent,
      showCopyNotification,
      toggleContext,
      toggleLogprobs,
      formatJSON,
      findAndFormatJSONObjects,
      syntaxHighlightJSON,
      isJSON,
      isPromptResultObject,
      parsePromptResultObject,
      unescapeJsonString,
    };
  },
});
</script>

<style>
.chat-messages {
  background-color: #f5f5f5;
  padding: 15px;
  border-radius: 5px;
}

.token-probs {
  font-family: 'Courier New', monospace;
  line-height: 1.5;
  word-wrap: break-word;
}

.token {
  position: relative;
  display: inline-block;
  padding: 2px 4px;
  margin: 1px;
  border-radius: 3px;
  cursor: pointer;
}

.token:hover .token-tooltip {
  display: block;
}

.token-tooltip {
  display: none;
  position: absolute;
  top: 100%;
  left: 0;
  z-index: 100;
  background: white;
  border: 1px solid #ccc;
  border-radius: 4px;
  padding: 5px;
  font-size: 12px;
  min-width: 120px;
  box-shadow: 0 4px 8px rgba(0,0,0,0.1);
}

.alternatives {
  margin-top: 5px;
  border-top: 1px solid #eee;
  padding-top: 5px;
}

/* Copy button styles */
.copy-button {
  position: absolute;
  top: 5px;
  right: 5px;
  background-color: rgba(255, 255, 255, 0.8);
  border: 1px solid #ccc;
  border-radius: 4px;
  padding: 3px 6px;
  cursor: pointer;
  z-index: 10;
  opacity: 0.6;
  transition: opacity 0.2s ease;
}

.copy-button:hover {
  opacity: 1;
  background-color: white;
}

/* Copy notification styles */
.copy-notification {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.2);
  z-index: 1000;
  display: flex;
  justify-content: center;
  align-items: center;
}

.notification-content {
  background-color: white;
  border-radius: 8px;
  padding: 15px 30px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
  font-size: 18px;
  font-weight: 500;
  color: #333;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-20px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Styles for context, logprobs, and thoughts */
.context-container, .logprobs-container, .thoughts-container {
  margin-bottom: 12px;
  border: 1px solid #e0e0e0;
  border-radius: 5px;
  background-color: #f8f9fa;
  overflow: hidden;
}

.context-header, .logprobs-header, .thoughts-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background-color: #e9ecef;
  border-bottom: 1px solid #dee2e6;
  font-weight: 500;
}

.context-content {
  margin: 0;
  padding: 10px;
  white-space: pre-wrap;
  font-family: monospace;
  max-height: 300px;
  overflow-y: auto;
  font-size: 0.9rem;
  background-color: #f8f9fa;
}

.toggle-button {
  background-color: transparent;
  border: 1px solid #ced4da;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.2s;
}

.toggle-button:hover {
  background-color: #e2e6ea;
}

/* JSON syntax highlighting */
.json-content {
  font-family: monospace;
  line-height: 1.4;
  padding: 0;
  margin: 0;
  white-space: pre-wrap;
  background-color: #f8f9fa;
}

.json-key {
  color: #0b5394;
  font-weight: bold;
}

.json-string {
  color: #1a8754;
}

.json-number {
  color: #ff5722;
}

.json-boolean {
  color: #7b1fa2;
  font-weight: bold;
}

.json-null {
  color: #607d8b;
  font-style: italic;
}

/* Styles for prompt and test result containers */
.prompt-result-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 12px;
}

.prompt-container, .test-result-container {
  border: 1px solid #e0e0e0;
  border-radius: 5px;
  overflow: hidden;
}

.prompt-header, .test-result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background-color: #e9ecef;
  border-bottom: 1px solid #dee2e6;
  font-weight: 500;
}

.prompt-container > div:last-child,
.test-result-container > div:last-child {
  padding: 10px;
  white-space: pre-wrap;
  max-height: 400px;
  overflow-y: auto;
}

/* Small copy button for prompt and test result containers */
.copy-button-small {
  background-color: transparent;
  border: 1px solid #ced4da;
  border-radius: 3px;
  padding: 2px 5px;
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.2s ease, background-color 0.2s ease;
}

.copy-button-small:hover {
  opacity: 1;
  background-color: #f8f9fa;
}
</style>
