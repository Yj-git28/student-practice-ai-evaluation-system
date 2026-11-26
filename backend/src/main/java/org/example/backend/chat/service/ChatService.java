package org.example.backend.chat.service;

import org.example.backend.chat.mapper.ChatMapper;
import org.example.backend.common.BizException;
import org.example.backend.security.LoginUser;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ChatService {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_CONTENT = 2000;

    private final ChatMapper mapper;
    private final JdbcClient jdbc;
    private final ChatBroadcaster broadcaster;

    public ChatService(ChatMapper mapper, JdbcClient jdbc, @Lazy ChatBroadcaster broadcaster) {
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.broadcaster = broadcaster;
    }

    /** 当前用户可参与的实训群 */
    public List<Map<String, Object>> rooms(LoginUser user) {
        return switch (user.getRoleCode() == null ? "" : user.getRoleCode()) {
            case "ADMIN" -> mapper.selectAllRooms();
            case "TEACHER" -> mapper.selectTeacherRooms(user.getUserId());
            default -> mapper.selectStudentRooms(gradeOf(user.getClassId()), user.getUserId());
        };
    }

    /** 历史消息；lastId 大于 0 时增量拉取该 id 之后的消息 */
    public List<Map<String, Object>> history(LoginUser user, Long trainingId, Long lastId) {
        canAccess(user, trainingId);
        List<Map<String, Object>> rows = mapper.selectMessages(trainingId, lastId, 100);
        rows.forEach(row -> {
            Object t = row.get("createTime");
            if (t instanceof LocalDateTime ldt) {
                row.put("createTime", ldt.format(DATETIME));
            }
        });
        if (lastId == null || lastId <= 0) {
            // 首次进入：SQL 按 id 倒序取最新 N 条，这里翻转成正序展示
            List<Map<String, Object>> reversed = new ArrayList<>(rows);
            java.util.Collections.reverse(reversed);
            return reversed;
        }
        return rows;
    }

    /** 发消息：校验群成员身份 → 落库 → 广播给在线成员，返回消息体 */
    public Map<String, Object> send(LoginUser user, Long trainingId, String content) {
        canAccess(user, trainingId);
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BizException(4002, "消息内容不能为空");
        }
        if (text.length() > MAX_CONTENT) {
            throw new BizException(4002, "消息不能超过 " + MAX_CONTENT + " 字");
        }
        String senderName = user.getRealName() != null && !user.getRealName().isBlank()
                ? user.getRealName() : user.getUsername();
        Long messageId = mapper.insertMessage(trainingId, user.getUserId(), senderName, text);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", messageId);
        payload.put("trainingId", trainingId);
        payload.put("senderId", user.getUserId());
        payload.put("senderName", senderName);
        payload.put("content", text);
        payload.put("createTime", LocalDateTime.now().format(DATETIME));

        if (broadcaster != null) {
            broadcaster.broadcast(trainingId, payload);
        }
        return payload;
    }

    /** 判定当前用户是否属于该实训群；不属于抛 4031 */
    public void canAccess(LoginUser user, Long trainingId) {
        if (trainingId == null) throw new BizException(4001, "实训任务不能为空");
        if (!isAllowed(user, trainingId)) {
            throw new BizException(4031, "不在该实训群内，无权查看或发言");
        }
    }

    private boolean isAllowed(LoginUser user, Long trainingId) {
        Optional<Map<String, Object>> rowOpt = mapper.selectTrainingAccess(trainingId);
        if (rowOpt.isEmpty()) return false;
        Map<String, Object> row = rowOpt.get();
        String role = user.getRoleCode() == null ? "" : user.getRoleCode();

        if ("ADMIN".equals(role)) return true;

        if ("TEACHER".equals(role)) {
            Long teacherId = asLong(row.get("teacherId"));
            return teacherId != null && teacherId.equals(user.getUserId());
        }

        if ("STUDENT".equals(role)) {
            if (mapper.hasResult(trainingId, user.getUserId())) return true;
            Integer status = asInt(row.get("status"));
            Integer scopeType = asInt(row.get("scopeType"));
            String scopeValue = row.get("scopeValue") == null ? null : String.valueOf(row.get("scopeValue"));
            String grade = gradeOf(user.getClassId());
            boolean published = status != null && status == 1;
            boolean visibleScope = scopeType != null && (scopeType == 1 || scopeType == 3
                    || (scopeType == 2 && grade != null && grade.equals(scopeValue)));
            return published && visibleScope;
        }
        return false;
    }

    private String gradeOf(Long classId) {
        if (classId == null) return null;
        return jdbc.sql("SELECT grade FROM class WHERE class_id = :cid")
                .param("cid", classId).query(String.class).optional().orElse(null);
    }

    private static Long asLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static Integer asInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(value));
    }
}
