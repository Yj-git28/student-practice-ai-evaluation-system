package org.example.backend.chat.ws;

import tools.jackson.databind.ObjectMapper;
import org.example.backend.chat.service.ChatBroadcaster;
import org.example.backend.chat.service.ChatService;
import org.example.backend.common.BizException;
import org.example.backend.security.LoginUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler implements ChatBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final String ATTR_USER = "loginUser";
    private static final String ATTR_ROOM = "trainingId";

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    /** trainingId -> 该群在线会话集合 */
    private final Map<Long, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        LoginUser user = (LoginUser) session.getAttributes().get(ATTR_USER);
        log.info("[WS] 连接建立 userId={} session={}", user == null ? null : user.getUserId(), session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        LoginUser user = (LoginUser) session.getAttributes().get(ATTR_USER);
        if (user == null) {
            sendError(session, "未登录");
            return;
        }
        Map<String, Object> body;
        try {
            body = objectMapper.readValue(message.getPayload(), Map.class);
        } catch (Exception e) {
            sendError(session, "消息格式错误");
            return;
        }

        String type = body.get("type") == null ? "" : String.valueOf(body.get("type"));
        Long trainingId = body.get("trainingId") == null ? null : Long.valueOf(String.valueOf(body.get("trainingId")));

        try {
            if ("join".equals(type)) {
                chatService.canAccess(user, trainingId);
                bindRoom(session, trainingId);
                sendJson(session, Map.of("type", "ack", "trainingId", trainingId));
            } else if ("message".equals(type)) {
                String content = body.get("content") == null ? null : String.valueOf(body.get("content"));
                Map<String, Object> payload = chatService.send(user, trainingId, content);
                // 广播由 ChatService 回调 broadcast 统一推送，这里无需再单独回发
                log.debug("[WS] 消息已发送 trainingId={} sender={}", trainingId, user.getUserId());
            } else {
                sendError(session, "未知消息类型");
            }
        } catch (BizException e) {
            sendError(session, e.getMessage());
        } catch (Exception e) {
            log.error("[WS] 处理消息异常", e);
            sendError(session, "服务端处理失败");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object room = session.getAttributes().get(ATTR_ROOM);
        if (room instanceof Long trainingId) {
            Set<WebSocketSession> set = rooms.get(trainingId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) rooms.remove(trainingId);
            }
        }
        LoginUser user = (LoginUser) session.getAttributes().get(ATTR_USER);
        log.info("[WS] 连接关闭 userId={} session={}", user == null ? null : user.getUserId(), session.getId());
    }

    /** 实现广播接口：向指定群的所有在线会话推送 */
    @Override
    public void broadcast(Long trainingId, Map<String, Object> payload) {
        Set<WebSocketSession> set = rooms.get(trainingId);
        if (set == null || set.isEmpty()) return;
        Map<String, Object> frame = new ConcurrentHashMap<>(payload);
        frame.put("type", "message");
        for (WebSocketSession session : set) {
            if (session.isOpen()) {
                sendJson(session, frame);
            }
        }
    }

    private void bindRoom(WebSocketSession session, Long trainingId) {
        Object old = session.getAttributes().get(ATTR_ROOM);
        if (old instanceof Long oldRoom && !oldRoom.equals(trainingId)) {
            Set<WebSocketSession> oldSet = rooms.get(oldRoom);
            if (oldSet != null) oldSet.remove(session);
        }
        session.getAttributes().put(ATTR_ROOM, trainingId);
        rooms.computeIfAbsent(trainingId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    private void sendJson(WebSocketSession session, Map<String, Object> payload) {
        if (!session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.error("[WS] 推送消息失败 session={}", session.getId(), e);
        }
    }

    private void sendError(WebSocketSession session, String msg) {
        sendJson(session, Map.of("type", "error", "msg", msg));
    }
}
