package org.example.backend.system.mapper;
import org.example.backend.system.entity.SysPermission;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    /** 管理端：全量权限项，按 sort、permission_id 排序 */
    public List<SysPermission> selectAll() {
        return jdbc.sql("""
                SELECT permission_id, parent_id, perm_name, perm_type,
                       perm_code, perm_path, icon, sort
                FROM sys_permission
                ORDER BY perm_type ASC, sort ASC, permission_id ASC
                """)
                .query(SysPermission.class)
                .list();
    }

    public Optional<SysPermission> selectById(Long permissionId) {
        return jdbc.sql("""
                SELECT permission_id, parent_id, perm_name, perm_type,
                       perm_code, perm_path, icon, sort
                FROM sys_permission
                WHERE permission_id = :id
                """)
                .param("id", permissionId)
                .query(SysPermission.class)
                .optional();
    }

    /** 权限码是否已存在；编辑时排除自身 */
    public boolean existsByCode(String permCode, Long excludeId) {
        Long exclude = excludeId == null ? 0L : excludeId;
        Integer cnt = jdbc.sql("""
                SELECT COUNT(1) FROM sys_permission
                WHERE perm_code = :code AND permission_id <> :id
                """)
                .param("code", permCode).param("id", exclude)
                .query(Integer.class).single();
        return cnt != null && cnt > 0;
    }

    public Long insert(SysPermission p) {
        jdbc.sql("""
                INSERT INTO sys_permission(parent_id, perm_name, perm_type, perm_code, perm_path, icon, sort)
                VALUES (:parentId, :name, :type, :code, :path, :icon, :sort)
                """)
                .param("parentId", p.getParentId() == null ? 0L : p.getParentId())
                .param("name", p.getPermName())
                .param("type", p.getPermType())
                .param("code", p.getPermCode())
                .param("path", p.getPermPath() == null || p.getPermPath().isBlank() ? null : p.getPermPath())
                .param("icon", p.getIcon() == null || p.getIcon().isBlank() ? null : p.getIcon())
                .param("sort", p.getSort() == null ? 0 : p.getSort())
                .update();
        Long id = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        return id == null ? 0L : id;
    }

    public void update(SysPermission p) {
        jdbc.sql("""
                UPDATE sys_permission
                SET parent_id = :parentId, perm_name = :name, perm_type = :type,
                    perm_code = :code, perm_path = :path, icon = :icon, sort = :sort
                WHERE permission_id = :id
                """)
                .param("parentId", p.getParentId() == null ? 0L : p.getParentId())
                .param("name", p.getPermName())
                .param("type", p.getPermType())
                .param("code", p.getPermCode())
                .param("path", p.getPermPath() == null || p.getPermPath().isBlank() ? null : p.getPermPath())
                .param("icon", p.getIcon() == null || p.getIcon().isBlank() ? null : p.getIcon())
                .param("sort", p.getSort() == null ? 0 : p.getSort())
                .param("id", p.getPermissionId())
                .update();
    }

    public void deleteById(Long permissionId) {
        jdbc.sql("DELETE FROM sys_permission WHERE permission_id = :id")
                .param("id", permissionId).update();
    }

    /** 删除前清理角色关联（FK RESTRICT，必须先删子表） */
    public void deleteRoleRef(Long permissionId) {
        jdbc.sql("DELETE FROM sys_role_permission WHERE permission_id = :id")
                .param("id", permissionId).update();
    }

    /** 删除前清理个人附加权限关联 */
    public void deleteUserRef(Long permissionId) {
        jdbc.sql("DELETE FROM sys_user_permission WHERE permission_id = :id")
                .param("id", permissionId).update();
    }

    /** 子权限数量，删除菜单前阻止其仍挂有子项 */
    public int countChild(Long permissionId) {
        Integer c = jdbc.sql("SELECT COUNT(1) FROM sys_permission WHERE parent_id = :id")
                .param("id", permissionId).query(Integer.class).single();
        return c == null ? 0 : c;
    }

    /** 被角色引用数量 */
    public int countRoleRef(Long permissionId) {
        Integer c = jdbc.sql("SELECT COUNT(1) FROM sys_role_permission WHERE permission_id = :id")
                .param("id", permissionId).query(Integer.class).single();
        return c == null ? 0 : c;
    }

    /** 被个人附加权限引用数量 */
    public int countUserRef(Long permissionId) {
        Integer c = jdbc.sql("SELECT COUNT(1) FROM sys_user_permission WHERE permission_id = :id")
                .param("id", permissionId).query(Integer.class).single();
        return c == null ? 0 : c;
    }
}