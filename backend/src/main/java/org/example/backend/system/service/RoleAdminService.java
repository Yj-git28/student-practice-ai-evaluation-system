package org.example.backend.system.service;

import org.example.backend.common.BizException;
import org.example.backend.system.dto.RoleSaveDTO;
import org.example.backend.system.entity.SysRole;
import org.example.backend.system.mapper.SysRoleMapper;
import org.example.backend.system.mapper.SysRolePermissionMapper;
import org.example.backend.system.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 角色维护：列表、新增、编辑、停用/启用、删除；内置角色受保护 */
@Service
public class RoleAdminService {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_]{2,32}$");

    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rpMapper;
    private final PermissionService permissionService;

    public RoleAdminService(SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper,
                            SysRolePermissionMapper rpMapper, PermissionService permissionService) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.rpMapper = rpMapper;
        this.permissionService = permissionService;
    }

    public List<Map<String, Object>> list() {
        List<SysRole> roles = roleMapper.selectAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysRole role : roles) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("roleId", role.getRoleId());
            item.put("roleName", role.getRoleName());
            item.put("roleCode", role.getRoleCode());
            item.put("dataScope", role.getDataScope());
            item.put("status", role.getStatus() == null ? 1 : role.getStatus());
            item.put("builtin", role.getBuiltin() == null ? 0 : role.getBuiltin());
            item.put("description", role.getDescription());
            item.put("permIds", roleMapper.selectPermIdsByRoleId(role.getRoleId()));
            item.put("userCount", userRoleMapper.countUsersByRoleId(role.getRoleId()));
            result.add(item);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(RoleSaveDTO dto) {
        String name = trim(dto.getRoleName());
        String code = trim(dto.getRoleCode()).toUpperCase();
        if (name.isEmpty() || name.length() > 32) throw new BizException(4002, "角色名称不能为空且不超过 32 字");
        if (!CODE.matcher(code).matches()) throw new BizException(4002, "角色标识需为 2-32 位大写字母、数字或下划线");
        if (roleMapper.existsByCode(code, null)) throw new BizException(4001, "角色标识已存在：" + code);
        int scope = dto.getDataScope() == null ? 3 : dto.getDataScope();
        if (scope < 1 || scope > 3) throw new BizException(4002, "数据范围必须为 1 全部 / 2 本部门 / 3 本人");

        SysRole role = new SysRole();
        role.setRoleName(name);
        role.setRoleCode(code);
        role.setDataScope(scope);
        role.setDescription(dto.getDescription());
        role.setStatus(1);
        role.setBuiltin(0);
        return roleMapper.insert(role);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long roleId, RoleSaveDTO dto) {
        SysRole role = roleMapper.selectById(roleId)
                .orElseThrow(() -> new BizException(4004, "角色不存在"));
        String name = trim(dto.getRoleName());
        if (name.isEmpty() || name.length() > 32) throw new BizException(4002, "角色名称不能为空且不超过 32 字");
        int scope = dto.getDataScope() == null ? role.getDataScope() : dto.getDataScope();
        if (scope < 1 || scope > 3) throw new BizException(4002, "数据范围必须为 1 全部 / 2 本部门 / 3 本人");

        // 内置角色：角色码不可修改；仅允许调整名称、数据范围、描述、状态
        if (role.getBuiltin() != null && role.getBuiltin() == 1) {
            String newCode = trim(dto.getRoleCode()).toUpperCase();
            if (!role.getRoleCode().equals(newCode)) {
                throw new BizException(4003, "内置角色的角色标识不可修改");
            }
        } else if (dto.getRoleCode() != null && !role.getRoleCode().equals(trim(dto.getRoleCode()).toUpperCase())) {
            throw new BizException(4003, "角色标识不支持修改，如需变更请新建角色");
        }

        role.setRoleName(name);
        role.setDataScope(scope);
        role.setDescription(dto.getDescription());
        if (dto.getStatus() != null) role.setStatus(dto.getStatus());
        roleMapper.update(role);
        userRoleMapper.selectUserIdsByRoleId(roleId).forEach(permissionService::evict);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long roleId) {
        SysRole role = roleMapper.selectById(roleId)
                .orElseThrow(() -> new BizException(4004, "角色不存在"));
        if (role.getBuiltin() != null && role.getBuiltin() == 1) {
            throw new BizException(4003, "内置角色不可删除");
        }
        int userCount = userRoleMapper.countUsersByRoleId(roleId);
        if (userCount > 0) {
            throw new BizException(4003, "该角色仍绑定 " + userCount + " 个账号，请先迁移账号");
        }
        rpMapper.deleteByRoleId(roleId);
        roleMapper.deleteById(roleId);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
