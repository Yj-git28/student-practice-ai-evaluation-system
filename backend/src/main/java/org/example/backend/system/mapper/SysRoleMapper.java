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

    public List<SysRole> selectAll() {
        return jdbc.sql("""
                SELECT role_id, role_name, role_code, data_scope
                FROM sys_role
                ORDER BY role_id ASC
                """)
                .query(SysRole.class)
                .list();
    }

    public Optional<SysRole> selectById(Long roleId) {
        return jdbc.sql("""
                SELECT role_id, role_name, role_code, data_scope
                FROM sys_role
                WHERE role_id = :id
                """)
                .param("id", roleId)
                .query(SysRole.class)
                .optional();
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
