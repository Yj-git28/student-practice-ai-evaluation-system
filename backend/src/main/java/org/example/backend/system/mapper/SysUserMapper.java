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

    /** 按用户名查有效用户：仅启用且未逻辑删除的账号允许登录 */
    public Optional<SysUser> selectByUsername(String username) {
        return jdbc.sql("""
                SELECT user_id, username, password, real_name, role_id, class_id, email
                FROM sys_user
                WHERE username = :username AND status = 1 AND deleted = 0
                """)
                .param("username", username)
                .query(SysUser.class)
                .optional();
    }

    /** 邮箱登录：仅启用且未逻辑删除的账号可登录 */
    public Optional<SysUser> selectByEmail(String email) {
        return jdbc.sql("""
                SELECT user_id, username, password, real_name, role_id, class_id, email
                FROM sys_user
                WHERE email = :email AND status = 1 AND deleted = 0
                """)
                .param("email", email)
                .query(SysUser.class)
                .optional();
    }

    public Optional<SysUser> selectById(Long userId) {
        return jdbc.sql("""
                SELECT user_id, username, password, real_name, role_id, class_id, email
                FROM sys_user
                WHERE user_id = :userId AND status = 1 AND deleted = 0
                """)
                .param("userId", userId)
                .query(SysUser.class)
                .optional();
    }

    /** 单设备登录：记录账号当前有效会话的 jti，旧 token 的 jti 不再匹配即失效 */
    public void updateCurrentJti(Long userId, String jti) {
        jdbc.sql("UPDATE sys_user SET current_jti = :jti WHERE user_id = :userId")
                .param("jti", jti)
                .param("userId", userId)
                .update();
    }

    /** 退出登录：清空当前会话 jti */
    public void clearCurrentJti(Long userId) {
        jdbc.sql("UPDATE sys_user SET current_jti = NULL WHERE user_id = :userId")
                .param("userId", userId)
                .update();
    }

    /** 查账号当前绑定的会话 jti；未登录过（或已退出）为空 */
    public Optional<String> selectCurrentJti(Long userId) {
        return jdbc.sql("SELECT current_jti FROM sys_user WHERE user_id = :userId")
                .param("userId", userId)
                .query(String.class)
                .optional();
    }

    /** 按 userId 查角色（单角色模型，最多 1 行）。数据范围来自 sys_role.data_scope，不再按 roleCode 硬编码 */
    public Optional<SysRole> selectRoleByUserId(Long userId) {
        return jdbc.sql("""
                SELECT r.role_id, r.role_name, r.role_code, r.data_scope
                FROM sys_user u
                INNER JOIN sys_role r ON r.role_id = u.role_id
                WHERE u.user_id = :userId
                """)
                .param("userId", userId)
                .query(SysRole.class)
                .optional();
    }

    /** 管理员用户列表：联表角色名、班级名、年级，支持关键词/角色/年级/班级筛选 */
    public java.util.List<java.util.Map<String, Object>> selectUserList(String keyword, Long roleId,
                                                                         String grade, Long classId) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.user_id AS userId, u.username, u.real_name AS realName, u.email,
                       u.role_id AS roleId, u.status, r.role_name AS roleName,
                       u.class_id AS classId, c.class_name AS className, c.grade
                FROM sys_user u
                LEFT JOIN sys_role r ON r.role_id = u.role_id
                LEFT JOIN class c ON c.class_id = u.class_id
                WHERE u.deleted = 0
                """);
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (u.username LIKE :kw OR u.real_name LIKE :kw OR u.email LIKE :kw)");
            params.put("kw", "%" + keyword.trim() + "%");
        }
        if (roleId != null) {
            sql.append(" AND u.role_id = :roleId");
            params.put("roleId", roleId);
        }
        if (classId != null) {
            sql.append(" AND u.class_id = :classId");
            params.put("classId", classId);
        } else if (grade != null && !grade.isBlank()) {
            sql.append(" AND c.grade = :grade");
            params.put("grade", grade);
        }
        sql.append(" ORDER BY u.user_id");
        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
        for (java.util.Map.Entry<String, Object> e : params.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        return spec.query().listOfRows();
    }

    /** 权限刷新接口用：拿到全量用户，逐个失效权限缓存 */
    public java.util.List<Long> selectAllUserIds() {
        return jdbc.sql("SELECT user_id FROM sys_user")
                .query(Long.class)
                .list();
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

    public boolean existsByEmail(String email) {
        Integer c = jdbc.sql("SELECT COUNT(1) FROM sys_user WHERE email = :e AND deleted = 0")
                .param("e", email).query(Integer.class).single();
        return c != null && c > 0;
    }

    public boolean existsByUsername(String username) {
        Integer c = jdbc.sql("SELECT COUNT(1) FROM sys_user WHERE username = :u AND deleted = 0")
                .param("u", username).query(Integer.class).single();
        return c != null && c > 0;
    }

    /** 注册/新增用户：默认启用、未删除；邮箱、班级允许为空（空串写入 NULL，避免唯一索引冲突） */
    public void insertUser(String username, String encodedPassword, String realName, String email,
                           int roleId, Long classId) {
        jdbc.sql("""
                INSERT INTO sys_user(username, password, real_name, role_id, class_id, email, status, deleted)
                VALUES (:u, :p, :r, :role, NULLIF(:classId, 0), NULLIF(:e, ''), 1, 0)
                """)
                .param("u", username).param("p", encodedPassword)
                .param("r", realName).param("e", email == null ? "" : email)
                .param("role", roleId).param("classId", classId == null ? 0L : classId)
                .update();
    }

    /** 管理员编辑用户：账号不可改，其余可改 */
    public void updateUser(Long userId, String realName, String email, Long roleId, Long classId) {
        jdbc.sql("""
                UPDATE sys_user
                SET real_name = :r, email = NULLIF(:e, ''), role_id = :role, class_id = NULLIF(:classId, 0)
                WHERE user_id = :id AND deleted = 0
                """)
                .param("r", realName).param("e", email == null ? "" : email)
                .param("role", roleId).param("classId", classId == null ? 0L : classId)
                .param("id", userId)
                .update();
    }

    /** 逻辑删除：同时清空会话，旧 token 立即失效 */
    public void logicalDelete(Long userId) {
        jdbc.sql("UPDATE sys_user SET deleted = 1, current_jti = NULL WHERE user_id = :id")
                .param("id", userId).update();
    }

    /** 邮箱唯一性校验（编辑时排除自己） */
    public boolean existsByEmailExcluding(String email, Long excludeUserId) {
        Integer c = jdbc.sql("SELECT COUNT(1) FROM sys_user WHERE email = :e AND deleted = 0 AND user_id <> :id")
                .param("e", email).param("id", excludeUserId)
                .query(Integer.class).single();
        return c != null && c > 0;
    }

    /** 统计有效管理员数量，用于阻止删掉最后一个管理员 */
    public int countActiveAdmins() {
        Integer c = jdbc.sql("SELECT COUNT(1) FROM sys_user WHERE role_id = 3 AND status = 1 AND deleted = 0")
                .query(Integer.class).single();
        return c == null ? 0 : c;
    }

    /** 绑定/换绑邮箱 */
    public void updateEmail(Long userId, String email) {
        jdbc.sql("UPDATE sys_user SET email = :e WHERE user_id = :id")
                .param("e", email).param("id", userId).update();
    }
}