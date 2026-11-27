package org.example.backend.system.service;
import org.example.backend.security.DataScope;
import org.example.backend.security.LoginUser;
import org.example.backend.system.entity.SysPermission;
import org.example.backend.system.entity.SysRole;
import org.example.backend.system.entity.SysUser;
import org.example.backend.system.mapper.SysPermissionMapper;
import org.example.backend.system.mapper.SysUserMapper;
import org.example.backend.system.mapper.SysUserRoleMapper;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysPermissionMapper permissionMapper;
    private final PermissionCacheService cache;

    public PermissionService(SysUserMapper userMapper,
                             SysUserRoleMapper userRoleMapper,
                             SysPermissionMapper permissionMapper,
                             PermissionCacheService cache) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionMapper = permissionMapper;
        this.cache = cache;
    }

    /** 组装 LoginUser：每次先校验账号状态，角色和权限仍可复用缓存 */
    public LoginUser buildLoginUser(Long userId) {
        Optional<SysUser> found = userMapper.selectById(userId);
        if (found.isEmpty()) {
            cache.invalidate(userId);
            throw new UsernameNotFoundException("用户不存在、已禁用或已删除: " + userId);
        }

        LoginUser cached = cache.get(userId);
        if (cached != null) return cached;

        SysUser user = found.get();

        // 多角色：取全部启用角色，权限取并集
        List<SysRole> roles = userRoleMapper.selectRolesByUserId(userId);
        if (roles.isEmpty()) {
            cache.invalidate(userId);
            throw new UsernameNotFoundException("用户未绑定角色: " + userId);
        }

        Set<String> roleCodes = roles.stream()
                .map(SysRole::getRoleCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 角色权限 ∪ 个人附加权限
        Set<String> codes = new LinkedHashSet<>();
        List<SysPermission> rolePerms = permissionMapper.selectByUserId(userId);
        List<SysPermission> personalPerms = permissionMapper.selectPersonalByUserId(userId);
        rolePerms.forEach(p -> addCode(codes, p));
        personalPerms.forEach(p -> addCode(codes, p));

        // 数据范围取最宽一档：1 全部 > 2 本部门 > 3 本人
        int dataScope = roles.stream()
                .mapToInt(r -> r.getDataScope() == null ? DataScope.SELF : r.getDataScope())
                .min()
                .orElse(DataScope.SELF);

        // 主角色取第一个，回填单值字段兼容 JWT / 旧展示
        SysRole primary = roles.get(0);

        LoginUser lu = new LoginUser();
        lu.setUserId(user.getUserId());
        lu.setUsername(user.getUsername());
        lu.setRealName(user.getRealName());
        lu.setEmail(user.getEmail());
        lu.setClassId(user.getClassId());
        lu.setPassword(user.getPassword());
        lu.setRoleId(primary.getRoleId());
        lu.setRoleCode(primary.getRoleCode());
        lu.setRoleName(primary.getRoleName());
        lu.setRoles(roleCodes);
        lu.setPermissions(codes);
        lu.setDataScope(DataScope.normalize(dataScope));

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
