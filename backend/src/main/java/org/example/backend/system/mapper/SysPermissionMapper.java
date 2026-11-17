package org.example.backend.system.mapper;

import org.example.backend.system.entity.SysPermission;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SysPermissionMapper {

    private final JdbcClient jdbc;

    public SysPermissionMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 按 userId 查全部权限（菜单+按钮+接口），去重，按 sort 排序 */
    public List<SysPermission> selectByUserId(Long userId) {
        return jdbc.sql("""
                SELECT DISTINCT p.permission_id, p.parent_id, p.perm_name, p.perm_type,
                                p.perm_code, p.perm_path, p.icon, p.sort
                FROM sys_user u
                INNER JOIN sys_role_permission rp ON rp.role_id = u.role_id
                INNER JOIN sys_permission p ON p.permission_id = rp.permission_id
                WHERE u.user_id = :userId
                ORDER BY p.sort ASC, p.permission_id ASC
                """)
                .param("userId", userId)
                .query(SysPermission.class)
                .list();
    }

    /** 个人附加权限（sys_user_permission），与角色权限取并集 */
    public List<SysPermission> selectPersonalByUserId(Long userId) {
        return jdbc.sql("""
                SELECT DISTINCT p.permission_id, p.parent_id, p.perm_name, p.perm_type,
                                p.perm_code, p.perm_path, p.icon, p.sort
                FROM sys_user_permission up
                INNER JOIN sys_permission p ON p.permission_id = up.permission_id
                WHERE up.user_id = :userId
                ORDER BY p.sort ASC, p.permission_id ASC
                """)
                .param("userId", userId)
                .query(SysPermission.class)
                .list();
    }

    /** 只取菜单（perm_type = 1），用于前端生成路由和侧边栏 */
    public List<SysPermission> selectMenusByUserId(Long userId) {
        return jdbc.sql("""
                SELECT DISTINCT p.permission_id, p.parent_id, p.perm_name, p.perm_type,
                                p.perm_code, p.perm_path, p.icon, p.sort
                FROM sys_user u
                INNER JOIN sys_role_permission rp ON rp.role_id = u.role_id
                INNER JOIN sys_permission p ON p.permission_id = rp.permission_id
                WHERE u.user_id = :userId AND p.perm_type = 1
                ORDER BY p.sort ASC, p.permission_id ASC
                """)
                .param("userId", userId)
                .query(SysPermission.class)
                .list();
    }

    /** 校验某角色是否有某权限码（自测/后台授权界面用） */
    public boolean existsByRoleAndCode(Long roleId, String permCode) {
        Integer cnt = jdbc.sql("""
                SELECT COUNT(1)
                FROM sys_role_permission rp
                INNER JOIN sys_permission p ON p.permission_id = rp.permission_id
                WHERE rp.role_id = :roleId AND p.perm_code = :permCode
                """)
                .param("roleId", roleId).param("permCode", permCode)
                .query(Integer.class).single();
        return cnt != null && cnt > 0;
    }
}