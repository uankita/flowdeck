package com.flowdeck.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket, consumed by {@code @stomp/stompjs} on the frontend.
 *
 * <p>This uses the in-memory simple broker, which is per-instance: two backend
 * replicas would not see each other's messages. Redis is already in the stack
 * for that reason — swap in a relay (or a Redis pub/sub bridge) before scaling
 * past one instance.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final FlowdeckProperties properties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Server → client destinations.
        registry.enableSimpleBroker("/topic", "/queue");
        // Client → server destinations, handled by @MessageMapping methods.
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = properties.cors().allowedOrigins().toArray(String[]::new);

        // Native WebSocket — what @stomp/stompjs uses by default, and what the
        // frontend connects with.
        registry.addEndpoint("/ws").setAllowedOriginPatterns(origins);

        // SockJS fallback on the same path, for clients behind proxies that
        // will not upgrade the connection.
        registry.addEndpoint("/ws").setAllowedOriginPatterns(origins).withSockJS();
    }
}
