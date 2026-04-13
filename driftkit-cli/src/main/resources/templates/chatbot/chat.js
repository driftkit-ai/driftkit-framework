class ChatClient {
    constructor() {
        this.chatId = null;
        this.currentWorkflow = 'user-onboarding';
        
        this.initializeEventListeners();
        this.createNewChat();
    }
    
    initializeEventListeners() {
        document.getElementById('chatForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.sendMessage();
        });
        
        // Show review panel if user has reviewer role (demo mode)
        const urlParams = new URLSearchParams(window.location.search);
        if (urlParams.get('reviewer') === 'true') {
            document.getElementById('reviewPanel').style.display = 'block';
        }
    }
    
    async createNewChat() {
        try {
            const response = await fetch('/chat/create', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    workflowId: this.currentWorkflow
                })
            });
            
            if (response.ok) {
                const data = await response.json();
                this.chatId = data.id;
                this.setConnected(true);
                
                // Send initial message to start workflow
                this.startWorkflow();
            } else {
                throw new Error('Failed to create chat');
            }
        } catch (error) {
            console.error('Failed to create chat:', error);
            this.setConnected(false);
            this.showError('Failed to connect to chat service');
        }
    }
    
    setConnected(connected) {
        const statusDot = document.querySelector('.status-dot');
        const statusText = document.querySelector('.status-text');
        
        if (connected) {
            statusDot.classList.add('connected');
            statusText.textContent = 'Connected';
            document.getElementById('messageInput').disabled = false;
            document.getElementById('sendButton').disabled = false;
        } else {
            statusDot.classList.remove('connected');
            statusText.textContent = 'Disconnected';
            document.getElementById('messageInput').disabled = true;
            document.getElementById('sendButton').disabled = true;
        }
    }
    
    async startWorkflow() {
        const response = {
            type: 'AI',
            content: 'Welcome! Let\'s get you onboarded. First, please tell me your name.',
            timestamp: new Date().toISOString()
        };
        this.handleMessage(response);
    }
    
    async sendMessage() {
        const input = document.getElementById('messageInput');
        const message = input.value.trim();
        
        if (!message || !this.chatId) return;
        
        // Display user message
        this.displayMessage(message, 'user');
        input.value = '';
        
        try {
            const response = await fetch('/chat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    id: Date.now().toString(),
                    chatId: this.chatId,
                    type: 'USER',
                    properties: [
                        {
                            name: 'message',
                            value: message,
                            type: 'STRING'
                        }
                    ]
                })
            });
            
            if (response.ok) {
                const data = await response.json();
                this.handleResponse(data);
            } else {
                throw new Error('Failed to send message');
            }
        } catch (error) {
            console.error('Failed to send message:', error);
            this.showError('Failed to send message. Please try again.');
        }
    }
    
    handleResponse(response) {
        // Extract message from properties
        const messageProperty = response.properties?.find(p => p.name === 'message');
        const message = messageProperty?.value || 'Processing...';
        
        this.handleMessage({
            type: 'AI',
            content: message,
            complete: response.completed,
            nextSchema: response.nextSchema
        });
        
        // Handle any tasks (for async operations)
        if (response.tasks && response.tasks.length > 0) {
            response.tasks.forEach(task => {
                if (task.requiresReview) {
                    this.showReviewNotification(task);
                }
            });
        }
    }
    
    handleMessage(message) {
        this.displayMessage(message.content, message.type === 'AI' ? 'bot' : 'user');
        
        if (message.complete) {
            this.displayMessage('Workflow completed! 🎉', 'system');
            document.getElementById('messageInput').disabled = true;
            document.getElementById('sendButton').disabled = true;
        }
    }
    
    displayMessage(content, sender) {
        const messagesContainer = document.getElementById('messages');
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${sender}`;
        
        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        contentDiv.textContent = content;
        
        const timeDiv = document.createElement('div');
        timeDiv.className = 'message-time';
        timeDiv.textContent = new Date().toLocaleTimeString();
        
        messageDiv.appendChild(contentDiv);
        messageDiv.appendChild(timeDiv);
        messagesContainer.appendChild(messageDiv);
        
        // Scroll to bottom
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }
    
    showError(message) {
        this.displayMessage(message, 'system');
    }
    
    showReviewNotification(task) {
        const notification = document.createElement('div');
        notification.className = 'review-notification';
        notification.innerHTML = `
            <h4>Review Required</h4>
            <p>User profile needs review</p>
            <button onclick="chatClient.approveReview('${task.id}', true)">Approve</button>
            <button onclick="chatClient.approveReview('${task.id}', false)">Reject</button>
        `;
        document.getElementById('reviewQueue').appendChild(notification);
    }
    
    async approveReview(taskId, approved) {
        try {
            const response = await fetch(`/chat/task/${taskId}/complete`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    approved: approved,
                    notes: approved ? 'Approved' : 'Needs more information'
                })
            });
            
            if (response.ok) {
                // Remove notification
                const notifications = document.querySelectorAll('.review-notification');
                notifications.forEach(n => n.remove());
                
                this.displayMessage(
                    approved ? 'Review approved ✅' : 'Review rejected ❌', 
                    'system'
                );
            }
        } catch (error) {
            console.error('Failed to submit review:', error);
        }
    }
}

// Initialize when page loads
let chatClient;
document.addEventListener('DOMContentLoaded', () => {
    chatClient = new ChatClient();
});