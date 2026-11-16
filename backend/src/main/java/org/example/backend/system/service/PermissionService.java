package org.example.backend.system.service;

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

        List<SysPermission> perms = permissionMapper.selectByUserId(userId);
        Set<String> codes = perms.stream()
                .map(SysPermission::getPermCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

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
        lu.setDataScope(resolveScope(role.getRoleCode()));

        cache.put(userId, lu);
        return lu;
    }

    /** 表里没有 data_scope 字段，先用角色码集中映射一处，避免散落 */
    private String resolveScope(String roleCode) {
        return switch (roleCode) {
            case "ADMIN"   -> "ALL";
            case "TEACHER" -> "TEACHER_SELF";
            default        -> "STUDENT_SELF";
        };
    }

    public List<SysPermission> menusOf(Long userId) {
        return permissionMapper.selectMenusByUserId(userId);
    }

    public void evict(Long userId) {
        cache.invalidate(userId);
    }
}