package org.example.backend.training;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class TrainingMapper {

    private final JdbcClient jdbc;

    public TrainingMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 该实训是否存在且属于这个教师（防越权）；公共实训（teacher_id IS NULL）永远返回 false */
    public boolean existsOwnedBy(Long trainingId, Long teacherId) {
        Integer cnt = jdbc.sql("""
                SELECT COUNT(1) FROM training
                WHERE training_id = :tid AND teacher_id = :tid2
                """)
                .param("tid", trainingId).param("tid2", teacherId)
                .query(Integer.class).single();
        return cnt != null && cnt > 0;
    }

    public boolean exists(Long trainingId) {
        Integer cnt = jdbc.sql("SELECT COUNT(1) FROM training WHERE training_id = :tid")
                .param("tid", trainingId)
                .query(Integer.class).single();
        return cnt != null && cnt > 0;
    }

    /**
     * 分页查询。
     * - teacherId 非空：只看该教师的实训
     * - studentGrade 非空：只看该年级学生可见的（status=1 且 scope_type 匹配）
     * - 两者都空：全量（管理员视角）
     */
    public List<Map<String, Object>> page(Long teacherId, String studentGrade, String keyword,
                                         Integer status, int page, int size) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        appendCondition(sql, teacherId, studentGrade, keyword, status);
        sql.append(" ORDER BY t.training_id DESC LIMIT :limit OFFSET :offset");

        JdbcClient.StatementSpec spec = bind(jdbc.sql(sql.toString()), teacherId, studentGrade, keyword, status);
        return spec.param("limit", size)
                .param("offset", (long) (page - 1) * size)
                .query()
                .listOfRows();
    }

    public long count(Long teacherId, String studentGrade, String keyword, Integer status) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(1) FROM training t WHERE 1 = 1
                """);
        if (teacherId != null) sql.append(" AND t.teacher_id = :teacherId");
        if (studentGrade != null) {
            sql.append(" AND t.status = 1 ").append(studentScopeClause());
        }
        if (studentGrade == null && status != null) {
            sql.append(" AND t.status = :status");
        }
        if (keyword != null && !keyword.isBlank()) sql.append(" AND t.training_name LIKE :kw");

        JdbcClient.StatementSpec spec = bind(jdbc.sql(sql.toString()), teacherId, studentGrade, keyword, status);
        Integer c = spec.query(Integer.class).single();
        return c == null ? 0 : c;
    }

    /** 详情：非学生查全部，学生查自己年级可见的 */
    public Optional<Map<String, Object>> detail(Long trainingId, String studentGrade) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " AND t.training_id = :tid");
        if (studentGrade != null) {
            sql.append(" ").append(studentScopeClause());
        }
        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString()).param("tid", trainingId);
        if (studentGrade != null) spec = spec.param("grade", studentGrade);

        List<Map<String, Object>> rows = spec.query().listOfRows();
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void insert(TrainingSaveDTO dto, Long teacherId) {
        jdbc.sql("""
                INSERT INTO training(training_name, teacher_id, start_time, end_time, description,
                                     status, scope_type, scope_value)
                VALUES (:name, :teacherId, NULLIF(:startTime, ''), NULLIF(:endTime, ''), :description,
                        0, COALESCE(:scopeType, 1), :scopeValue)
                """)
                .param("name", dto.getTrainingName())
                .param("teacherId", teacherId)
                .param("startTime", dto.getStartTime() == null ? "" : dto.getStartTime())
                .param("endTime", dto.getEndTime() == null ? "" : dto.getEndTime())
                .param("description", dto.getDescription())
                .param("scopeType", dto.getScopeType() == null ? 1 : dto.getScopeType())
                .param("scopeValue", normalizeScopeValue(dto.getScopeType(), dto.getScopeValue()))
                .update();
    }

    /** 与 insert 同一事务内执行，取本次自增主键 */
    public Long lastInsertId() {
        return jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
    }

    public int update(Long trainingId, TrainingSaveDTO dto) {
        return jdbc.sql("""
                UPDATE training
                SET training_name = :name,
                    description = :description,
                    start_time = NULLIF(:startTime, ''),
                    end_time = NULLIF(:endTime, ''),
                    scope_type = COALESCE(:scopeType, scope_type),
                    scope_value = :scopeValue
                WHERE training_id = :tid
                """)
                .param("name", dto.getTrainingName())
                .param("description", dto.getDescription())
                .param("startTime", dto.getStartTime() == null ? "" : dto.getStartTime())
                .param("endTime", dto.getEndTime() == null ? "" : dto.getEndTime())
                .param("scopeType", dto.getScopeType())
                .param("scopeValue", normalizeScopeValue(dto.getScopeType(), dto.getScopeValue()))
                .param("tid", trainingId)
                .update();
    }

    /** 发布：status 置 1（0 未发布 / 1 已发布） */
    public int publish(Long trainingId) {
        return jdbc.sql("UPDATE training SET status = 1 WHERE training_id = :tid")
                .param("tid", trainingId).update();
    }

    public int deleteResults(Long trainingId) {
        return jdbc.sql("DELETE FROM train_result WHERE training_id = :tid")
                .param("tid", trainingId).update();
    }

    public int delete(Long trainingId) {
        return jdbc.sql("DELETE FROM training WHERE training_id = :tid")
                .param("tid", trainingId).update();
    }

    /** 教师口径统计；teacherId 为 null 时统计全量（ADMIN / 数据范围=1） */
    public java.util.Map<String, Object> stat(Long teacherId) {
        String sql = teacherId == null
                ? "SELECT COUNT(1) AS total, COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS published FROM training"
                : "SELECT COUNT(1) AS total, COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS published FROM training WHERE teacher_id = :tid";
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        if (teacherId != null) spec = spec.param("tid", teacherId);
        return spec.query().singleRow();
    }

    /** scope_type 与 scope_value 合法性校验：scope_type=2 必须带有效年级 */
    public java.util.Map<String, Object> getGradeMap() {
        return jdbc.sql("SELECT DISTINCT grade FROM class WHERE grade IS NOT NULL ORDER BY grade")
                .query().listOfRows()
                .stream().collect(java.util.stream.Collectors.toMap(
                        r -> (String) r.get("grade"), r -> r.get("grade")));
    }

    private static final String BASE_SELECT = """
            SELECT t.training_id AS trainingId, t.training_name AS trainingName,
                   t.description, t.start_time AS startTime, t.end_time AS endTime,
                   t.status, t.teacher_id AS teacherId,
                   t.scope_type AS scopeType, t.scope_value AS scopeValue,
                   u.real_name AS teacherName,
                   (SELECT COUNT(1) FROM train_result r WHERE r.training_id = t.training_id) AS resultCount
            FROM training t
            LEFT JOIN sys_user u ON u.user_id = t.teacher_id
            WHERE 1 = 1
            """;

    /**
     * 学生可见性子句：scope_type=1（教师专属）/3（全校公共）直接可见，scope_type=2 需匹配年级。
     * 注意：返回值自带前导空格——调用方可能紧跟在 :tid 之类的占位符后面拼接，
     * 少了空格 Spring 会把占位符解析成 ":tidAND"，报 No value supplied for the SQL parameter。
     */
    private static String studentScopeClause() {
        return " " + """
                AND (
                    t.scope_type = 1
                    OR t.scope_type = 3
                    OR (t.scope_type = 2 AND t.scope_value = :grade)
                )
                """;
    }

    private void appendCondition(StringBuilder sql, Long teacherId, String studentGrade,
                                 String keyword, Integer status) {
        if (teacherId != null) sql.append(" AND t.teacher_id = :teacherId");
        if (studentGrade != null) {
            sql.append(" AND t.status = 1 ").append(studentScopeClause());
        }
        if (studentGrade == null && status != null) {
            sql.append(" AND t.status = :status");
        }
        if (keyword != null && !keyword.isBlank()) sql.append(" AND t.training_name LIKE :kw");
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Long teacherId,
                                           String studentGrade, String keyword, Integer status) {
        if (teacherId != null) spec = spec.param("teacherId", teacherId);
        if (studentGrade != null) spec = spec.param("grade", studentGrade);
        if (studentGrade == null && status != null) spec = spec.param("status", status);
        if (keyword != null && !keyword.isBlank()) spec = spec.param("kw", "%" + keyword.trim() + "%");
        return spec;
    }

    /** scope_type != 2 时强制 scopeValue 为 NULL，避免脏数据 */
    private String normalizeScopeValue(Integer scopeType, String scopeValue) {
        if (scopeType == null || scopeType != 2) return null;
        if (scopeValue == null) return null;
        return scopeValue.trim().isEmpty() ? null : scopeValue.trim();
    }
}
