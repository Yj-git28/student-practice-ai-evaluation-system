package org.example.backend.training;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class TrainingResultMapper {

    private static final String BASE_SELECT = """
            SELECT r.result_id, r.training_id, r.student_id,
                   r.file_name, r.file_type, r.submit_time
            FROM train_result r
            INNER JOIN training t ON t.training_id = r.training_id
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
}
