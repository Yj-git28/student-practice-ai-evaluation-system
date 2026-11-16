package org.example.backend.training;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TrainingMapper {

    private final JdbcClient jdbc;

    public TrainingMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 该实训是否存在且属于这个教师（防越权） */
    public boolean existsOwnedBy(Long trainingId, Long teacherId) {
        Integer cnt = jdbc.sql("""
                SELECT COUNT(1) FROM training
                WHERE training_id = :tid AND teacher_id = :tid2
                """)
                .param("tid", trainingId).param("tid2", teacherId)
                .query(Integer.class).single();
        return cnt != null && cnt > 0;
    }

    /** 发布：状态置 1。若实库没有 status 列，改成你表里真实的发布标志列 */
    public int publish(Long trainingId) {
        return jdbc.sql("UPDATE training SET status = 1 WHERE training_id = :tid")
                .param("tid", trainingId).update();
    }

    public int delete(Long trainingId) {
        return jdbc.sql("DELETE FROM training WHERE training_id = :tid")
                .param("tid", trainingId).update();
    }

    /** 教师口径统计；teacherId 为 null 时统计全量（ADMIN） */
    public java.util.Map<String, Object> stat(Long teacherId) {
        String sql = teacherId == null
                ? "SELECT COUNT(1) AS total, SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS published FROM training"
                : "SELECT COUNT(1) AS total, SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS published FROM training WHERE teacher_id = :tid";
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        if (teacherId != null) spec = spec.param("tid", teacherId);
        return spec.query().singleRow();
    }
}
