package org.example.apimywebsite.configuration;


import org.example.apimywebsite.util.JwtChannelInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocket implements WebSocketMessageBrokerConfigurer {
    @Autowired
    private JwtChannelInterceptor jwtChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Must mirror SecurityConfig's corsConfigurationSource() allowed-origins list exactly:
        // this handshake-level origin check is enforced by Spring's WebSocket layer
        // (OriginHandshakeInterceptor, added implicitly by setAllowedOrigins), a separate
        // mechanism from the SecurityFilterChain/CORS bean above it - /ws/** being permitAll
        // there does not exempt the handshake from this check. Omitting localhost:3000 here
        // (as before) makes every local-dev WebSocket handshake fail with 403 before the STOMP
        // CONNECT frame - and therefore JwtChannelInterceptor - is ever reached.
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:3000", "https://faceboard-frontend.vercel.app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }
}

