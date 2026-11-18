package org.example.backend.system.log;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SysLogMapper {

    private final JdbcClient jdbc;

    public SysLogMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 表结构：sys_log(log_id, oper_user_id, oper_content, oper_time) */
    public void insert(Long userId, String content) {
        jdbc.sql("INSERT INTO sys_log (oper_user_id, oper_content) VALUES (:uid, :content)")
                .param("uid", userId)
                .param("content", content)
                .update();
    }

    /** 最近操作日志（含操作人姓名），按时间倒序 */
    public java.util.List<java.util.Map<String, Object>> selectRecent(int limit) {
        return jdbc.sql("""
                SELECT l.log_id AS logId,
                       COALESCE(u.real_name, u.username, '系统') AS operUser,
                       l.oper_content AS content,
                       l.oper_time AS operTime
                FROM sys_log l
                LEFT JOIN sys_user u ON u.user_id = l.oper_user_id
                ORDER BY l.log_id DESC
                LIMIT :limit
                """)
                .param("limit", limit)
                .query().listOfRows();
    }
}
