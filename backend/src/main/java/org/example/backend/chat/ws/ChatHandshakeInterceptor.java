package org.example.backend.chat.ws;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.example.backend.security.JwtService;
import org.example.backend.security.LoginUser;
import org.example.backend.system.service.PermissionService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手鉴权：浏览器握手时在 query 上带 ?token=xxx，
 * 这里解析 JWT 并重建 LoginUser 存入 session attributes；无效则拒绝握手。
 */
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;
    private final PermissionService permissionService;

    public ChatHandshakeInterceptor(JwtService jwtService, PermissionService permissionService) {
        this.jwtService = jwtService;
        this.permissionService = permissionService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String uri = request.getURI().toString();
        String token = extractToken(uri);
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Claims claims = jwtService.parse(token);
            Long userId = Long.valueOf(claims.getSubject());
            LoginUser loginUser = permissionService.buildLoginUser(userId);
            attributes.put("loginUser", loginUser);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String extractToken(String uri) {
        int idx = uri.indexOf('?');
        if (idx < 0) return null;
        String query = uri.substring(idx + 1);
        for (String pair : query.split("&")) {
            if (pair.startsWith("token=")) {
                return pair.substring("token=".length());
            }
        }
        return null;
    }
}
