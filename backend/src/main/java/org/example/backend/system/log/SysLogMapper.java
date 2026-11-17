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
}
