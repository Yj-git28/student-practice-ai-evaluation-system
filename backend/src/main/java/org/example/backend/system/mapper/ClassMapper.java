package org.example.backend.system.mapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 班级表（表名 class）：支持按年级/班级筛选 */
@Repository
public class ClassMapper {

    private final JdbcClient jdbc;

    public ClassMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> selectAll() {
        return jdbc.sql("""
                SELECT class_id AS classId, class_name AS className, grade
                FROM class
                ORDER BY grade DESC, class_name
                """)
                .query().listOfRows();
    }

    public List<String> selectGrades() {
        return jdbc.sql("""
                SELECT DISTINCT grade FROM class
                WHERE grade IS NOT NULL AND grade <> ''
                ORDER BY grade DESC
                """)
                .query(String.class).list();
    }

    public Optional<Long> selectIdByName(String className) {
        return jdbc.sql("SELECT class_id FROM class WHERE class_name = :n")
                .param("n", className)
                .query(Long.class)
                .optional();
    }

    public boolean exists(Long classId) {
        Integer c = jdbc.sql("SELECT COUNT(1) FROM class WHERE class_id = :id")
                .param("id", classId)
                .query(Integer.class).single();
        return c != null && c > 0;
    }

    /** 班级名是否重复（编辑时排除自己） */
    public boolean existsByName(String className, Long excludeId) {
        Integer c = jdbc.sql("SELECT COUNT(1) FROM class WHERE class_name = :n AND class_id <> :id")
                .param("n", className)
                .param("id", excludeId == null ? 0L : excludeId)
                .query(Integer.class).single();
        return c != null && c > 0;
    }

    public void insert(String className, String grade) {
        jdbc.sql("INSERT INTO class(class_name, grade) VALUES (:n, NULLIF(:g, ''))")
                .param("n", className).param("g", grade == null ? "" : grade)
                .update();
    }

    public void update(Long classId, String className, String grade) {
        jdbc.sql("UPDATE class SET class_name = :n, grade = NULLIF(:g, '') WHERE class_id = :id")
                .param("n", className).param("g", grade == null ? "" : grade)
                .param("id", classId)
                .update();
    }

    public void deleteById(Long classId) {
        jdbc.sql("DELETE FROM class WHERE class_id = :id")
                .param("id", classId).update();
    }

    /** 该班级下的有效账号数，用于阻止删除仍有成员的班级 */
    public int countUsers(Long classId) {
        Integer c = jdbc.sql("SELECT COUNT(1) FROM sys_user WHERE class_id = :id AND deleted = 0")
                .param("id", classId)
                .query(Integer.class).single();
        return c == null ? 0 : c;
    }
}
