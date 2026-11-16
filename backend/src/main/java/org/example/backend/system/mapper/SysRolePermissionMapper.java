package org.example.backend.system.mapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SysRolePermissionMapper {

    private final JdbcClient jdbc;

    public SysRolePermissionMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 覆盖式授权：先删后插。FK 是 ON DELETE RESTRICT，删父表前必须先删这里 */
    public void deleteByRoleId(Long roleId) {
        jdbc.sql("DELETE FROM sys_role_permission WHERE role_id = :roleId")
                .param("roleId", roleId).update();
    }

    /** 批量插入。uk_role_perm 唯一索引保证重复不会插两行，配合 INSERT IGNORE 更稳 */
    public void batchInsert(Long roleId, List<Long> permissionIds) {
        for (Long pid : permissionIds) {
            jdbc.sql("INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (:rid, :pid)")
                    .param("rid", roleId).param("pid", pid).update();
        }
    }
}