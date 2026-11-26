package org.example.backend.evaluation.mapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class EvaluationMapper {

    /** 教师实训涉及的学生数（有提交记录的去重）；ADMIN 看全量 */
    private static final String STUDENT_COUNT_ALL = """
            SELECT COUNT(1)
            FROM sys_user u
            INNER JOIN sys_role r ON r.role_id = u.role_id
            WHERE r.role_code = 'STUDENT' AND u.deleted = 0
            """;

    private static final String STUDENT_COUNT_BY_TEACHER = """
            SELECT COUNT(DISTINCT r.student_id)
            FROM train_result r
            INNER JOIN training t ON t.training_id = r.training_id
            WHERE t.teacher_id = :teacherId
            """;

    private final JdbcClient jdbc;

    public EvaluationMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** teacherId=null → 全量；非null → 该教师实训有提交的去重学生数 */
    public int countStudents(Long teacherId) {
        Integer c;
        if (teacherId == null) {
            c = jdbc.sql(STUDENT_COUNT_ALL).query(Integer.class).single();
        } else {
            c = jdbc.sql(STUDENT_COUNT_BY_TEACHER).param("teacherId", teacherId)
                    .query(Integer.class).single();
        }
        return c == null ? 0 : c;
    }

    /** 有提交的学生数（去重）；teacherId 非空时只算该教师的实训 */
    public int countSubmitted(Long teacherId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT r.student_id)
                FROM train_result r
                INNER JOIN training t ON t.training_id = r.training_id
                WHERE 1 = 1
                """);
        if (teacherId != null) sql.append(" AND t.teacher_id = :teacherId");
        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
        if (teacherId != null) spec = spec.param("teacherId", teacherId);
        Integer c = spec.query(Integer.class).single();
        return c == null ? 0 : c;
    }

    /** 平均分：教师复核分优先，否则取 AI 初评分；无数据返回空 */
    public Optional<Double> avgScore(Long teacherId) {
        StringBuilder sql = new StringBuilder("""
                SELECT AVG(COALESCE(r.final_score, r.ai_score))
                FROM train_result r
                INNER JOIN training t ON t.training_id = r.training_id
                WHERE 1 = 1
                """);
        if (teacherId != null) sql.append(" AND t.teacher_id = :teacherId");
        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
        if (teacherId != null) spec = spec.param("teacherId", teacherId);
        return spec.query(Double.class).optional();
    }
}
