package com.sanguosha.config;

import com.sanguosha.websocket.session.UserSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtConfig jwtConfig;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        URI uri = request.getURI();
        String query = uri.getQuery();

        if (query == null || !query.contains("token=")) {
            return false;
        }

        String token = extractToken(query);
        if (token == null || !jwtConfig.validateToken(token)) {
            return false;
        }

        Long userId = jwtConfig.getUserIdFromToken(token);
        String username = jwtConfig.getUsernameFromToken(token);

        attributes.put("userId", userId);
        attributes.put("username", username);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nothing
    }

    private String extractToken(String query) {
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}