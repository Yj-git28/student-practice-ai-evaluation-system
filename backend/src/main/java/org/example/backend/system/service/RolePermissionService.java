package org.example.backend.system.service;

import org.example.backend.common.BizException;
import org.example.backend.system.entity.SysRole;
import org.example.backend.system.log.SysLog;
import org.example.backend.system.mapper.SysRoleMapper;
import org.example.backend.system.mapper.SysRolePermissionMapper;
import org.example.backend.system.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class RolePermissionService {

    private final SysRolePermissionMapper rpMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PermissionService permissionService;

    public RolePermissionService(SysRolePermissionMapper rpMapper,
                                  SysUserRoleMapper userRoleMapper,
                                  SysRoleMapper roleMapper,
                                  PermissionService permissionService) {
        this.rpMapper = rpMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.permissionService = permissionService;
    }

    @SysLog(type = "GRANT_PERM", content = "修改角色权限")
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        if (roleId == null) throw new BizException(4001, "角色不能为空");
        SysRole role = roleMapper.selectById(roleId)
                .orElseThrow(() -> new BizException(4004, "角色不存在"));
        // 内置角色（ADMIN/TEACHER/STUDENT）不允许被清空权限，避免误操作锁死系统
        boolean builtin = role.getBuiltin() != null && role.getBuiltin() == 1;
        if (builtin && (permissionIds == null || permissionIds.isEmpty())) {
            throw new BizException(4003, "内置角色不可清空全部权限，请保留必要权限");
        }
        rpMapper.deleteByRoleId(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            rpMapper.batchInsert(roleId, List.copyOf(new LinkedHashSet<>(permissionIds)));
        }
        afterCommit(() -> userRoleMapper.selectUserIdsByRoleId(roleId).forEach(permissionService::evict));
    }

    @SysLog(type = "CHANGE_ROLE", content = "修改用户角色")
    @Transactional(rollbackFor = Exception.class)
    public void changeUserRole(Long userId, Long roleId) {
        if (roleId == null) throw new BizException(4001, "角色不能为空");
        userRoleMapper.replaceUserRoles(userId, List.of(roleId));
        afterCommit(() -> permissionService.evict(userId));
    }

    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { task.run(); }
            });
        } else {
            task.run();
        }
    }
}
