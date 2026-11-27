package org.example.backend.system.mapper;

import org.example.backend.system.entity.SysRole;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 用户-角色多对多关联（sys_user_role）。
 * 一个用户可绑定多个角色；登录时取全部角色，权限取并集。
 */
@Repository
public class SysUserRoleMapper {

    private final JdbcClient jdbc;

    public SysUserRoleMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 用户绑定的角色 ID 列表（去重） */
    public List<Long> selectRoleIdsByUserId(Long userId) {
        return jdbc.sql("""
                SELECT role_id FROM sys_user_role WHERE user_id = :userId
                """)
                .param("userId", userId)
                .query(Long.class)
                .list();
    }

    /** 用户绑定的全部角色（含状态、数据范围），仅启用角色 */
    public List<SysRole> selectRolesByUserId(Long userId) {
        return jdbc.sql("""
                SELECT r.role_id, r.role_name, r.role_code, r.data_scope,
                       r.status, r.builtin, r.description
                FROM sys_user_role ur
                INNER JOIN sys_role r ON r.role_id = ur.role_id
                WHERE ur.user_id = :userId AND r.status = 1
                ORDER BY r.role_id ASC
                """)
                .param("userId", userId)
                .query(SysRole.class)
                .list();
    }

    /** 覆盖式重写用户角色：先删后插 */
    public void replaceUserRoles(Long userId, List<Long> roleIds) {
        jdbc.sql("DELETE FROM sys_user_role WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        if (roleIds == null || roleIds.isEmpty()) return;
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
            if (roleId == null) continue;
            jdbc.sql("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (:uid, :rid)")
                    .param("uid", userId)
                    .param("rid", roleId)
                    .update();
        }
    }

    /** 某角色下绑定的全部用户 ID（用于改角色权限后精准清缓存） */
    public List<Long> selectUserIdsByRoleId(Long roleId) {
        return jdbc.sql("SELECT user_id FROM sys_user_role WHERE role_id = :rid")
                .param("rid", roleId)
                .query(Long.class)
                .list();
    }

    /** 角色下绑定的用户数（删除角色前判断是否被占用） */
    public int countUsersByRoleId(Long roleId) {
        Integer c = jdbc.sql("SELECT COUNT(1) FROM sys_user_role WHERE role_id = :rid")
                .param("rid", roleId)
                .query(Integer.class)
                .single();
        return c == null ? 0 : c;
    }

    /** 删除用户时清理关联（外键 CASCADE 已兜底，这里供业务显式调用） */
    public void deleteByUserId(Long userId) {
        jdbc.sql("DELETE FROM sys_user_role WHERE user_id = :userId")
                .param("userId", userId)
                .update();
    }

    /** 校验传入的角色 ID 是否都存在且启用，返回非法/不存在的角色数（0 表示全部合法） */
    public int countInvalidRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return 0;
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(roleIds));
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append(":r").append(i);
        }
        String sql = "SELECT COUNT(1) FROM sys_role WHERE status = 1 AND role_id IN (" + placeholders + ")";
        var spec = jdbc.sql(sql);
        for (int i = 0; i < ids.size(); i++) spec = spec.param("r" + i, ids.get(i));
        Integer matched = spec.query(Integer.class).single();
        int matchedCount = matched == null ? 0 : matched;
        return ids.size() - matchedCount;
    }
}
