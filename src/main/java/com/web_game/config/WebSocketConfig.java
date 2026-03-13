package com.web_game.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Đây là điểm cuối (endpoint) mà JS sẽ kết nối vào
        // setAllowedOriginPatterns("*") cho phép kết nối từ mọi nguồn (để dễ test)
        registry.addEndpoint("/ws-caro").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Server gửi tin nhắn về Client qua tiền tố /topic
        registry.enableSimpleBroker("/topic");
        // Client gửi tin nhắn lên Server qua tiền tố /app
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Interceptor cho kênh inbound để đọc JWT từ header CONNECT
     * và gắn username vào session attributes của WebSocket.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> authHeaders = accessor.getNativeHeader("Authorization");
                    if (authHeaders != null && !authHeaders.isEmpty()) {
                        String header = authHeaders.get(0);
                        if (header != null && header.startsWith("Bearer ")) {
                            String token = header.substring(7);
                            if (jwtUtils.validateJwtToken(token)) {
                                String username = jwtUtils.getUserNameFromJwtToken(token);
                                if (username != null) {
                                    accessor.getSessionAttributes().put("username", username);
                                }
                            }
                        }
                    }
                }

                return message;
            }
        });
    }
}