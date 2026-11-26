package org.example.backend.chat.service;

import java.util.Map;

/** 群聊消息广播：由 WebSocket 处理器实现，Service 落库后回调推送给在线成员 */
public interface ChatBroadcaster {

    /** 向某实训群的所有在线会话推送一条消息 */
    void broadcast(Long trainingId, Map<String, Object> payload);
}
