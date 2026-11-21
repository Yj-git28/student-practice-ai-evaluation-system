package org.example.backend.training;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class TrainingResultMapper {

    private static final String BASE_SELECT = """
            SELECT r.result_id AS resultId, r.training_id AS trainingId,
                   t.training_name AS trainingName,
                   r.student_id AS studentId,
                   u.real_name AS stuName, u.username AS stuUsername,
                   c.class_name AS className,
                   r.file_name AS fileName, r.file_type AS fileType,
                   r.parse_status AS parseStatus,
                   r.ai_score AS aiScore, r.ai_comment AS aiComment,
                   r.final_score AS finalScore, r.teacher_comment AS teacherComment,
                   r.review_time AS reviewTime, r.submit_time AS submitTime
            FROM train_result r
            INNER JOIN training t ON t.training_id = r.training_id
            LEFT JOIN sys_user u ON u.user_id = r.student_id
            LEFT JOIN class c ON c.class_id = u.class_id
            WHERE 1 = 1
            """;

    private final JdbcClient jdbc;

    public TrainingResultMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> page(Long teacherId, Long studentId, int page, int size) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        if (teacherId != null) sql.append(" AND t.teacher_id = :teacherId");
        if (studentId != null) sql.append(" AND r.student_id = :studentId");
        sql.append(" ORDER BY r.submit_time DESC LIMIT :limit OFFSET :offset");

        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
        if (teacherId != null) spec = spec.param("teacherId", teacherId);
        if (studentId != null) spec = spec.param("studentId", studentId);

        return spec.param("limit", size)
                .param("offset", (long) (page - 1) * size)
                .query()
                .listOfRows();
    }

    public long count(Long teacherId, Long studentId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(1)
                FROM train_result r
                INNER JOIN training t ON t.training_id = r.training_id
                WHERE 1 = 1
                """);
        if (teacherId != null) sql.append(" AND t.teacher_id = :teacherId");
        if (studentId != null) sql.append(" AND r.student_id = :studentId");

        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
        if (teacherId != null) spec = spec.param("teacherId", teacherId);
        if (studentId != null) spec = spec.param("studentId", studentId);

        Integer c = spec.query(Integer.class).single();
        return c == null ? 0 : c;
    }

    /** 当前学生在指定实训下的最近提交，最多 10 条 */
    public List<Map<String, Object>> my(Long studentId, Long trainingId) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        sql.append(" AND r.student_id = :studentId");
        if (trainingId != null) sql.append(" AND r.training_id = :trainingId");
        sql.append(" ORDER BY r.submit_time DESC LIMIT 10");

        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString()).param("studentId", studentId);
        if (trainingId != null) spec = spec.param("trainingId", trainingId);
        return spec.query().listOfRows();
    }

    /** 实训是否存在且已发布（提交前置校验） */
    public Optional<Integer> trainingStatus(Long trainingId) {
        return jdbc.sql("SELECT status FROM training WHERE training_id = :tid")
                .param("tid", trainingId)
                .query(Integer.class)
                .optional();
    }

    public void insert(Long trainingId, Long studentId, String fileName, String filePath, String fileType) {
        jdbc.sql("""
                INSERT INTO train_result(training_id, student_id, file_name, file_path, file_type, parse_status)
                VALUES (:trainingId, :studentId, :fileName, :filePath, :fileType, 1)
                """)
                .param("trainingId", trainingId)
                .param("studentId", studentId)
                .param("fileName", fileName)
                .param("filePath", filePath)
                .param("fileType", fileType)
                .update();
    }

    /** 与 insert 同一事务内执行，取本次自增主键 */
    public Long lastInsertId() {
        return jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
    }

    /** 查询单条提交的 AI 初评字段，供"异步评分进度"接口使用；记录不存在返回空列表 */
    public List<Map<String, Object>> selectAiRow(Long resultId) {
        return jdbc.sql("""
                SELECT r.result_id AS resultId, r.ai_score AS aiScore, r.ai_comment AS aiComment,
                       r.student_id AS studentId, t.teacher_id AS teacherId
                FROM train_result r
                INNER JOIN training t ON t.training_id = r.training_id
                WHERE r.result_id = :rid
                """)
                .param("rid", resultId)
                .query()
                .listOfRows();
    }

    public void updateAi(Long resultId, int aiScore, String aiComment) {
        jdbc.sql("UPDATE train_result SET ai_score = :score, ai_comment = :comment WHERE result_id = :rid")
                .param("score", aiScore)
                .param("comment", aiComment)
                .param("rid", resultId)
                .update();
    }

    /** 该成果所属实训的教师；记录不存在返回空 */
    public Optional<Long> selectTrainingTeacher(Long resultId) {
        return jdbc.sql("""
                SELECT t.teacher_id
                FROM train_result r
                INNER JOIN training t ON t.training_id = r.training_id
                WHERE r.result_id = :rid
                """)
                .param("rid", resultId)
                .query(Long.class)
                .optional();
    }

    public void updateReview(Long resultId, int finalScore, String teacherComment, Long reviewBy) {
        jdbc.sql("""
                UPDATE train_result
                SET final_score = :score, teacher_comment = :comment,
                    review_by = :reviewBy, review_time = NOW()
                WHERE result_id = :rid
                """)
                .param("score", finalScore)
                .param("comment", teacherComment)
                .param("reviewBy", reviewBy)
                .param("rid", resultId)
                .update();
    }
}
