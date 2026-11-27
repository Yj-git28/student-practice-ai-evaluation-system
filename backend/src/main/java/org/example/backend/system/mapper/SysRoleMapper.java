package org.example.backend.system.mapper;
import org.example.backend.system.entity.SysRole;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SysRoleMapper {

    private final JdbcClient jdbc;

    public SysRoleMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT_COLS = """
            SELECT role_id, role_name, role_code, data_scope, status, builtin, description
            FROM sys_role
            """;

    public List<SysRole> selectAll() {
        return jdbc.sql(SELECT_COLS + " ORDER BY role_id ASC")
                .query(SysRole.class)
                .list();
    }

    public Optional<SysRole> selectById(Long roleId) {
        return jdbc.sql(SELECT_COLS + " WHERE role_id = :id")
                .param("id", roleId)
                .query(SysRole.class)
                .optional();
    }

    public Optional<SysRole> selectByCode(String roleCode) {
        return jdbc.sql(SELECT_COLS + " WHERE role_code = :code")
                .param("code", roleCode)
                .query(SysRole.class)
                .optional();
    }

    public boolean existsByCode(String roleCode, Long excludeId) {
        Long exclude = excludeId == null ? 0L : excludeId;
        Integer c = jdbc.sql("""
                SELECT COUNT(1) FROM sys_role
                WHERE role_code = :code AND role_id <> :id
                """)
                .param("code", roleCode).param("id", exclude)
                .query(Integer.class).single();
        return c != null && c > 0;
    }

    public Long insert(SysRole role) {
        jdbc.sql("""
                INSERT INTO sys_role(role_name, role_code, data_scope, status, builtin, description)
                VALUES (:name, :code, :scope, 1, 0, :desc)
                """)
                .param("name", role.getRoleName())
                .param("code", role.getRoleCode())
                .param("scope", role.getDataScope() == null ? 3 : role.getDataScope())
                .param("desc", role.getDescription())
                .update();
        Long id = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        return id == null ? 0L : id;
    }

    public void update(SysRole role) {
        jdbc.sql("""
                UPDATE sys_role
                SET role_name = :name, data_scope = :scope, description = :desc, status = :status
                WHERE role_id = :id
                """)
                .param("name", role.getRoleName())
                .param("scope", role.getDataScope())
                .param("desc", role.getDescription())
                .param("status", role.getStatus() == null ? 1 : role.getStatus())
                .param("id", role.getRoleId())
                .update();
    }

    public void deleteById(Long roleId) {
        jdbc.sql("DELETE FROM sys_role WHERE role_id = :id")
                .param("id", roleId).update();
    }

    /** 某角色已授权的权限 ID 集合 */
    public List<Long> selectPermIdsByRoleId(Long roleId) {
        return jdbc.sql("""
                SELECT permission_id FROM sys_role_permission WHERE role_id = :rid
                """)
                .param("rid", roleId)
                .query(Long.class)
                .list();
    }
}
