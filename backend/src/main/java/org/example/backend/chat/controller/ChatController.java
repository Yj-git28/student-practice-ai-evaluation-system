package org.example.backend.chat.controller;

import org.example.backend.chat.service.ChatService;
import org.example.backend.common.R;
import org.example.backend.security.LoginUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@PreAuthorize("hasAuthority('chat:list')")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 当前用户可参与的实训群列表 */
    @GetMapping("/rooms")
    public R<List<Map<String, Object>>> rooms(@AuthenticationPrincipal LoginUser user) {
        return R.ok(chatService.rooms(user));
    }

    /** 群历史消息；lastId 大于 0 时增量拉取 */
    @GetMapping("/messages")
    public R<List<Map<String, Object>>> messages(@AuthenticationPrincipal LoginUser user,
                                                 @RequestParam Long trainingId,
                                                 @RequestParam(required = false) Long lastId) {
        return R.ok(chatService.history(user, trainingId, lastId));
    }

    /** 发消息（HTTP 兜底通道；实时推送由 WebSocket 广播） */
    @PostMapping("/messages")
    public R<Map<String, Object>> send(@AuthenticationPrincipal LoginUser user,
                                       @RequestBody Map<String, Object> body) {
        Long trainingId = body.get("trainingId") == null ? null : Long.valueOf(String.valueOf(body.get("trainingId")));
        String content = body.get("content") == null ? null : String.valueOf(body.get("content"));
        return R.ok(chatService.send(user, trainingId, content));
    }
}
