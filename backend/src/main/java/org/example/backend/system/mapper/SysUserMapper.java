package org.example.backend.system.mapper;

import org.example.backend.system.entity.SysUser;
import org.example.backend.system.entity.SysRole;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SysUserMapper {

    private final JdbcClient jdbc;

    public SysUserMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 按用户名查用户。注意：表里没有 status/deleted 字段，所以不加任何状态过滤 */
    public Optional<SysUser> selectByUsername(String username) {
        return jdbc.sql("""
                SELECT user_id, username, password, real_name, role_id, class_id
                FROM sys_user
                WHERE username = :username
                """)
                .param("username", username)
                .query(SysUser.class)
                .optional();
    }

    public Optional<SysUser> selectById(Long userId) {
        return jdbc.sql("""
                SELECT user_id, username, password, real_name, role_id, class_id
                FROM sys_user WHERE user_id = :userId
                """)
                .param("userId", userId)
                .query(SysUser.class)
                .optional();
    }

    /** 按 userId 查角色（单角色模型，最多 1 行） */
    public Optional<SysRole> selectRoleByUserId(Long userId) {
        return jdbc.sql("""
                SELECT r.role_id, r.role_name, r.role_code
                FROM sys_user u
                INNER JOIN sys_role r ON r.role_id = u.role_id
                WHERE u.user_id = :userId
                """)
                .param("userId", userId)
                .query(SysRole.class)
                .optional();
    }

    /** 权限变更后按角色反查受影响用户，用于精准清缓存 */
    public java.util.List<Long> selectUserIdsByRoleId(Long roleId) {
        return jdbc.sql("SELECT user_id FROM sys_user WHERE role_id = :roleId")
                .param("roleId", roleId)
                .query(Long.class)
                .list();
    }

    /** 明文密码一次性升级为 BCrypt 用 */
    public java.util.List<SysUser> selectAllForPasswordUpgrade() {
        return jdbc.sql("SELECT user_id, username, password, real_name, role_id, class_id FROM sys_user")
                .query(SysUser.class).list();
    }

    public void updatePassword(Long userId, String encoded) {
        jdbc.sql("UPDATE sys_user SET password = :pwd WHERE user_id = :userId")
                .param("pwd", encoded).param("userId", userId).update();
    }

    /** 管理员给用户改角色：单角色模型下就是一条 UPDATE */
    public void updateRoleId(Long userId, Long roleId) {
        jdbc.sql("UPDATE sys_user SET role_id = :roleId WHERE user_id = :userId")
                .param("roleId", roleId).param("userId", userId).update();
    }
}