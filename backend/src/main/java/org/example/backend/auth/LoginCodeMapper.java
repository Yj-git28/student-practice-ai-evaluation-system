package org.example.backend.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 登录验证码存储（target 列存手机号或邮箱）。
 * 演示环境直接落表；上线建议换 Redis，验证码也应存哈希而非明文。
 */
@Repository
public class LoginCodeMapper {

    private final JdbcClient jdbc;

    public LoginCodeMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void save(String target, String code, int expireSeconds) {
        jdbc.sql("""
                INSERT INTO sys_login_code(target, code, expire_at)
                VALUES (:t, :c, DATE_ADD(NOW(), INTERVAL :s SECOND))
                """)
                .param("t", target).param("c", code).param("s", expireSeconds)
                .update();
    }

    /** 校验并消费：仅未使用且在有效期内的验证码可命中 */
    public boolean verifyAndConsume(String target, String code) {
        int n = jdbc.sql("""
                UPDATE sys_login_code SET used = 1
                WHERE target = :t AND code = :c AND used = 0 AND expire_at > NOW()
                ORDER BY id DESC LIMIT 1
                """)
                .param("t", target).param("c", code)
                .update();
        return n > 0;
    }

    /** 指定秒数内是否已发送过，用于 60 秒限流 */
    public int countRecent(String target, int seconds) {
        Integer c = jdbc.sql("""
                SELECT COUNT(1) FROM sys_login_code
                WHERE target = :t AND create_time > DATE_SUB(NOW(), INTERVAL :s SECOND)
                """)
                .param("t", target).param("s", seconds)
                .query(Integer.class).single();
        return c == null ? 0 : c;
    }
}
