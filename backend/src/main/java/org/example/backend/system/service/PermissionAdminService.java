package org.example.backend.system.service;
import org.example.backend.common.BizException;
import org.example.backend.system.dto.PermissionSaveDTO;
import org.example.backend.system.entity.SysPermission;
import org.example.backend.system.entity.SysRole;
import org.example.backend.system.mapper.SysPermissionMapper;
import org.example.backend.system.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 管理员侧：权限项（菜单/按钮/接口）维护 + 角色授权 */
@Service
public class PermissionAdminService {

    private final SysPermissionMapper permissionMapper;
    private final SysRoleMapper roleMapper;
    private final RolePermissionService rolePermissionService;

    public PermissionAdminService(SysPermissionMapper permissionMapper,
                                  SysRoleMapper roleMapper,
                                  RolePermissionService rolePermissionService) {
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionService = rolePermissionService;
    }

    /** 全量权限项组装成树（parent_id=0 为顶级） */
    public List<SysPermission> tree() {
        List<SysPermission> all = permissionMapper.selectAll();
        Map<Long, List<SysPermission>> byParent = all.stream()
                .collect(Collectors.groupingBy(p -> p.getParentId() == null ? 0L : p.getParentId()));
        all.forEach(p -> p.setChildren(byParent.getOrDefault(p.getPermissionId(), new ArrayList<>())));
        return byParent.getOrDefault(0L, new ArrayList<>());
    }

    /** 角色列表，附带每个角色已授权的权限 ID 集合 */
    public List<Map<String, Object>> roles() {
        List<SysRole> roles = roleMapper.selectAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysRole role : roles) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("roleId", role.getRoleId());
            item.put("roleName", role.getRoleName());
            item.put("roleCode", role.getRoleCode());
            item.put("dataScope", role.getDataScope());
            item.put("permIds", roleMapper.selectPermIdsByRoleId(role.getRoleId()));
            result.add(item);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(PermissionSaveDTO dto) {
        SysPermission p = validate(dto, null);
        return permissionMapper.insert(p);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(PermissionSaveDTO dto) {
        if (dto.getPermissionId() == null) throw new BizException(4001, "权限 ID 不能为空");
        permissionMapper.selectById(dto.getPermissionId())
                .orElseThrow(() -> new BizException(4004, "权限项不存在"));
        SysPermission p = validate(dto, dto.getPermissionId());
        p.setPermissionId(dto.getPermissionId());
        permissionMapper.update(p);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long permissionId) {
        SysPermission p = permissionMapper.selectById(permissionId)
                .orElseThrow(() -> new BizException(4004, "权限项不存在"));
        if (permissionMapper.countChild(permissionId) > 0) {
            throw new BizException(4003, "该权限下还有子权限，请先移除子项");
        }
        if (permissionMapper.countRoleRef(permissionId) > 0) {
            throw new BizException(4003, "该权限已被角色引用，请先在角色授权中移除");
        }
        if (permissionMapper.countUserRef(permissionId) > 0) {
            throw new BizException(4003, "该权限已分配给个别用户，请先移除");
        }
        permissionMapper.deleteRoleRef(permissionId);
        permissionMapper.deleteUserRef(permissionId);
        permissionMapper.deleteById(permissionId);
    }

    /** 覆盖式给角色授权；复用 RolePermissionService 的缓存清理与审计日志 */
    public void assign(Long roleId, List<Long> permissionIds) {
        roleMapper.selectById(roleId)
                .orElseThrow(() -> new BizException(4004, "角色不存在"));
        rolePermissionService.assignPermissions(roleId, permissionIds);
    }

    private SysPermission validate(PermissionSaveDTO dto, Long excludeId) {
        String name = trim(dto.getPermName());
        String code = trim(dto.getPermCode());
        Integer type = dto.getPermType();
        if (name.isEmpty() || name.length() > 64) {
            throw new BizException(4002, "权限名称不能为空且不超过 64 字");
        }
        if (type == null || type < 1 || type > 3) {
            throw new BizException(4002, "权限类型必须为 1 菜单 / 2 按钮 / 3 接口");
        }
        if (code.isEmpty() || code.length() > 100) {
            throw new BizException(4002, "权限标识不能为空且不超过 100 字");
        }
        if (permissionMapper.existsByCode(code, excludeId)) {
            throw new BizException(4001, "权限标识已存在：" + code);
        }
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId > 0 && permissionMapper.selectById(parentId).isEmpty()) {
            throw new BizException(4002, "父权限不存在");
        }
        if (type == 1 && (dto.getPermPath() == null || dto.getPermPath().isBlank())) {
            throw new BizException(4002, "菜单类型必须填写前端路由路径");
        }

        SysPermission p = new SysPermission();
        p.setParentId(parentId);
        p.setPermName(name);
        p.setPermType(type);
        p.setPermCode(code);
        p.setPermPath(dto.getPermPath());
        p.setIcon(dto.getIcon());
        p.setSort(dto.getSort() == null ? 0 : dto.getSort());
        return p;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
