package ai.driftkit.workflow.engine.spring.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for real-time workflow updates.
 * Enables STOMP over WebSocket for bidirectional communication.
 */
@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(
    prefix = "driftkit.workflow.websocket",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for topic subscriptions
        config.enableSimpleBroker(
            "/topic/workflow",      // Workflow status updates
            "/topic/chat",          // Chat message updates
            "/topic/async"          // Async task progress updates
        );
        
        // Set application destination prefix for client messages
        config.setApplicationDestinationPrefixes("/app");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register STOMP endpoint with SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        
        // Also register raw WebSocket endpoint (without SockJS)
        registry.addEndpoint("/ws-raw")
                .setAllowedOriginPatterns("*");
    }
}