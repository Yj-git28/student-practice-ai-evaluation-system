package org.example.backend.chat.mapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ChatMapper {

    private final JdbcClient jdbc;

    public ChatMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static final String ROOM_SELECT = """
            SELECT t.training_id AS trainingId, t.training_name AS trainingName,
                   t.status, t.scope_type AS scopeType, t.scope_value AS scopeValue,
                   t.teacher_id AS teacherId, u.real_name AS teacherName,
                   c.grade AS grade
            FROM training t
            LEFT JOIN sys_user u ON u.user_id = t.teacher_id
            LEFT JOIN class c ON c.class_id = (SELECT class_id FROM sys_user WHERE user_id = t.teacher_id)
            """;

    /** ADMIN：全部实训 */
    public List<Map<String, Object>> selectAllRooms() {
        return jdbc.sql(ROOM_SELECT + " ORDER BY t.training_id DESC")
                .query().listOfRows();
    }

    /** 教师：自己创建的实训 */
    public List<Map<String, Object>> selectTeacherRooms(Long teacherId) {
        return jdbc.sql(ROOM_SELECT + " WHERE t.teacher_id = :tid ORDER BY t.training_id DESC")
                .param("tid", teacherId)
                .query().listOfRows();
    }

    /** 学生：已发布且范围可见（按年级），或本人提交过成果的实训 */
    public List<Map<String, Object>> selectStudentRooms(String grade, Long userId) {
        String sql = ROOM_SELECT + """
                WHERE t.status = 1 AND (
                    t.scope_type = 1 OR t.scope_type = 3
                    OR (t.scope_type = 2 AND t.scope_value = :grade)
                    OR EXISTS (SELECT 1 FROM train_result r WHERE r.training_id = t.training_id AND r.student_id = :uid)
                )
                ORDER BY t.training_id DESC
                """;
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("uid", userId);
        if (grade != null && !grade.isBlank()) {
            spec = spec.param("grade", grade);
        } else {
            spec = spec.param("grade", "__NO_GRADE__");
        }
        return spec.query().listOfRows();
    }

    /** 单个实训的可见性信息，供成员判定 */
    public Optional<Map<String, Object>> selectTrainingAccess(Long trainingId) {
        return jdbc.sql(ROOM_SELECT + " WHERE t.training_id = :tid")
                .param("tid", trainingId)
                .query().listOfRows().stream().findFirst();
    }

    /** 学生是否提交过该实训成果 */
    public boolean hasResult(Long trainingId, Long userId) {
        Integer c = jdbc.sql("""
                SELECT COUNT(1) FROM train_result
                WHERE training_id = :tid AND student_id = :uid
                """)
                .param("tid", trainingId).param("uid", userId)
                .query(Integer.class).single();
        return c != null && c > 0;
    }

    public Long insertMessage(Long trainingId, Long senderId, String senderName, String content) {
        jdbc.sql("""
                INSERT INTO chat_message(training_id, sender_id, sender_name, content, message_type)
                VALUES (:tid, :sid, :sname, :content, 1)
                """)
                .param("tid", trainingId)
                .param("sid", senderId)
                .param("sname", senderName)
                .param("content", content)
                .update();
        Long id = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        return id == null ? 0L : id;
    }

    /** 历史消息；lastId 非空时只取该 id 之后的消息（增量拉取），否则取最新 limit 条倒序再翻正 */
    public List<Map<String, Object>> selectMessages(Long trainingId, Long lastId, int limit) {
        int size = Math.min(Math.max(limit, 1), 200);
        if (lastId != null && lastId > 0) {
            return jdbc.sql("""
                    SELECT message_id AS messageId, training_id AS trainingId,
                           sender_id AS senderId, sender_name AS senderName,
                           content, create_time AS createTime
                    FROM chat_message
                    WHERE training_id = :tid AND message_id > :lastId
                    ORDER BY message_id ASC
                    LIMIT :limit
                    """)
                    .param("tid", trainingId).param("lastId", lastId).param("limit", size)
                    .query().listOfRows();
        }
        // 首次进入：取最新 size 条，倒序查出后在 Service 里翻转成正序
        return jdbc.sql("""
                SELECT message_id AS messageId, training_id AS trainingId,
                       sender_id AS senderId, sender_name AS senderName,
                       content, create_time AS createTime
                FROM chat_message
                WHERE training_id = :tid
                ORDER BY message_id DESC
                LIMIT :limit
                """)
                .param("tid", trainingId).param("limit", size)
                .query().listOfRows();
    }
}
