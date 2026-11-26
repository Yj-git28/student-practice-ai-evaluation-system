package org.example.backend.system.service;
import org.example.backend.common.BizException;
import org.example.backend.system.log.SysLog;
import org.example.backend.system.mapper.SysRolePermissionMapper;
import org.example.backend.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class RolePermissionService {

    private final SysRolePermissionMapper rpMapper;
    private final SysUserMapper userMapper;
    private final PermissionService permissionService;

    public RolePermissionService(SysRolePermissionMapper rpMapper, SysUserMapper userMapper,
                                 PermissionService permissionService) {
        this.rpMapper = rpMapper;
        this.userMapper = userMapper;
        this.permissionService = permissionService;
    }

    @SysLog(type = "GRANT_PERM", content = "修改角色权限")
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        if (roleId == null) throw new BizException(4001, "角色不能为空");
        rpMapper.deleteByRoleId(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            rpMapper.batchInsert(roleId, List.copyOf(new LinkedHashSet<>(permissionIds)));
        }
        afterCommit(() -> userMapper.selectUserIdsByRoleId(roleId).forEach(permissionService::evict));
    }

    @SysLog(type = "CHANGE_ROLE", content = "修改用户角色")
    @Transactional(rollbackFor = Exception.class)
    public void changeUserRole(Long userId, Long roleId) {
        if (userId == null || roleId == null) throw new BizException(4001, "参数不能为空");
        userMapper.updateRoleId(userId, roleId);
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
