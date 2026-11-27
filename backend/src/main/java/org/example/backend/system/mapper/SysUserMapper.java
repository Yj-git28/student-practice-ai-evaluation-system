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

    /** 主角色（冗余列 sys_user.role_id），用于 JWT 与单值展示；全部角色见 SysUserRoleMapper */
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

    /** 管理员用户列表：联表角色名/班级名/年级，多角色聚合展示，支持关键词/角色/年级/班级筛选 */
    public java.util.List<java.util.Map<String, Object>> selectUserList(String keyword, Long roleId,
                                                                         String grade, Long classId) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.user_id AS userId, u.username, u.real_name AS realName, u.email,
                       u.role_id AS roleId, u.status,
                       GROUP_CONCAT(DISTINCT r.role_name SEPARATOR ',') AS roleName,
                       GROUP_CONCAT(DISTINCT r.role_code SEPARATOR ',') AS roleCode,
                       GROUP_CONCAT(DISTINCT ur.role_id SEPARATOR ',') AS roleIds,
                       u.class_id AS classId, c.class_name AS className, c.grade
                FROM sys_user u
                LEFT JOIN sys_user_role ur ON ur.user_id = u.user_id
                LEFT JOIN sys_role r ON r.role_id = ur.role_id
                LEFT JOIN class c ON c.class_id = u.class_id
                WHERE u.deleted = 0
                """);
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (u.username LIKE :kw OR u.real_name LIKE :kw OR u.email LIKE :kw)");
            params.put("kw", "%" + keyword.trim() + "%");
        }
        if (roleId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM sys_user_role ur2 WHERE ur2.user_id = u.user_id AND ur2.role_id = :roleId)");
            params.put("roleId", roleId);
        }
        if (classId != null) {
            sql.append(" AND u.class_id = :classId");
            params.put("classId", classId);
        } else if (grade != null && !grade.isBlank()) {
            sql.append(" AND c.grade = :grade");
            params.put("grade", grade);
        }
        sql.append(" GROUP BY u.user_id ORDER BY u.user_id");
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

    /** 权限变更后按角色反查受影响用户，用于精准清缓存（走多对多关联表） */
    public java.util.List<Long> selectUserIdsByRoleId(Long roleId) {
        return jdbc.sql("SELECT user_id FROM sys_user_role WHERE role_id = :roleId")
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

    /** 绑定/换绑邮箱 */
    public void updateEmail(Long userId, String email) {
        jdbc.sql("UPDATE sys_user SET email = :e WHERE user_id = :id")
                .param("e", email).param("id", userId).update();
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

    /** 注册/新增用户：默认启用、未删除；主角色写入冗余列，多角色关联由 Service 同步 */
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

    /** 编辑用户基础资料；主角色冗余列同步为传入主角色，关联表由 Service 重写 */
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

    /** 逻辑删除：同时清空会话与角色关联，旧 token 立即失效 */
    public void logicalDelete(Long userId) {
        jdbc.sql("UPDATE sys_user SET deleted = 1, current_jti = NULL WHERE user_id = :id")
                .param("id", userId).update();
        jdbc.sql("DELETE FROM sys_user_role WHERE user_id = :id")
                .param("id", userId).update();
    }

    /** 邮箱唯一性校验（编辑时排除自己） */
    public boolean existsByEmailExcluding(String email, Long excludeUserId) {
        Integer c = jdbc.sql("SELECT COUNT(1) FROM sys_user WHERE email = :e AND deleted = 0 AND user_id <> :id")
                .param("e", email).param("id", excludeUserId)
                .query(Integer.class).single();
        return c != null && c > 0;
    }

    /** 统计有效管理员数量：绑定 ADMIN 角色的启用账号，用于阻止删掉最后一个管理员 */
    public int countActiveAdmins() {
        Integer c = jdbc.sql("""
                SELECT COUNT(DISTINCT u.user_id)
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.user_id
                JOIN sys_role r ON r.role_id = ur.role_id AND r.role_code = 'ADMIN'
                WHERE u.status = 1 AND u.deleted = 0
                """)
                .query(Integer.class).single();
        return c == null ? 0 : c;
    }

    /** 判断用户是否绑定了 ADMIN 角色 */
    public boolean hasAdminRole(Long userId) {
        Integer c = jdbc.sql("""
                SELECT COUNT(1)
                FROM sys_user_role ur
                JOIN sys_role r ON r.role_id = ur.role_id AND r.role_code = 'ADMIN'
                WHERE ur.user_id = :id
                """)
                .param("id", userId).query(Integer.class).single();
        return c != null && c > 0;
    }
}
