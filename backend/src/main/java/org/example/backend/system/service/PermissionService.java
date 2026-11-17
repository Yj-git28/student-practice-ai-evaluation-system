package org.example.backend.system.service;

import org.example.backend.security.DataScope;
import org.example.backend.security.LoginUser;
import org.example.backend.system.entity.SysPermission;
import org.example.backend.system.entity.SysRole;
import org.example.backend.system.entity.SysUser;
import org.example.backend.system.mapper.SysPermissionMapper;
import org.example.backend.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;
    private final PermissionCacheService cache;

    public PermissionService(SysUserMapper userMapper,
                             SysPermissionMapper permissionMapper,
                             PermissionCacheService cache) {
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
        this.cache = cache;
    }

    /** 组装 LoginUser：这是整个 RBAC 的核心，登录和每次请求都走它 */
    public LoginUser buildLoginUser(Long userId) {
        LoginUser cached = cache.get(userId);
        if (cached != null) return cached;

        SysUser user = userMapper.selectById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + userId));
        SysRole role = userMapper.selectRoleByUserId(userId)
                .orElseThrow(() -> new RuntimeException("用户未绑定角色: " + userId));

        // 角色权限 ∪ 个人附加权限（sys_user_permission）
        Set<String> codes = new LinkedHashSet<>();
        List<SysPermission> rolePerms = permissionMapper.selectByUserId(userId);
        List<SysPermission> personalPerms = permissionMapper.selectPersonalByUserId(userId);
        rolePerms.forEach(p -> addCode(codes, p));
        personalPerms.forEach(p -> addCode(codes, p));

        LoginUser lu = new LoginUser();
        lu.setUserId(user.getUserId());
        lu.setUsername(user.getUsername());
        lu.setRealName(user.getRealName());
        lu.setClassId(user.getClassId());
        lu.setPassword(user.getPassword());
        lu.setRoleId(role.getRoleId());
        lu.setRoleCode(role.getRoleCode());
        lu.setRoleName(role.getRoleName());
        lu.setRoles(Set.of(role.getRoleCode()));
        lu.setPermissions(codes);
        // 直接读 sys_role.data_scope（1 全部 / 2 本部门 / 3 本人），不再按 roleCode 硬编码
        lu.setDataScope(DataScope.normalize(role.getDataScope()));

        cache.put(userId, lu);
        return lu;
    }

    private void addCode(Set<String> codes, SysPermission p) {
        if (p != null && p.getPermCode() != null && !p.getPermCode().isBlank()) {
            codes.add(p.getPermCode());
        }
    }

    public List<SysPermission> menusOf(Long userId) {
        return permissionMapper.selectMenusByUserId(userId);
    }

    public void evict(Long userId) {
        cache.invalidate(userId);
    }

    /**
     * 手动刷新全部用户的权限缓存（POST /api/system/permission/refresh）。
     * 返回本次刷新的用户数。
     */
    public int refreshAll() {
        List<Long> userIds = userMapper.selectAllUserIds();
        userIds.forEach(cache::invalidate);
        return userIds.size();
    }
}
