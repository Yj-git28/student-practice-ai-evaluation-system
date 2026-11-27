package org.example.backend.system.controller;

import org.example.backend.common.R;
import org.example.backend.system.dto.RoleSaveDTO;
import org.example.backend.system.log.SysLog;
import org.example.backend.system.service.RoleAdminService;
import org.example.backend.security.PermConstants;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 管理员：角色维护（列表 / 新增 / 编辑 / 删除），内置角色受保护 */
@RestController
@RequestMapping("/api/system/roles")
public class RoleAdminController {

    private final RoleAdminService roleAdminService;

    public RoleAdminController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    /** 角色列表（含已授权权限 ID、绑定用户数、内置标记） */
    @PreAuthorize("hasAuthority('" + PermConstants.PERM_MANAGE + "')")
    @GetMapping
    public R<List<Map<String, Object>>> list() {
        return R.ok(roleAdminService.list());
    }

    @SysLog(type = "ROLE_CREATE", content = "新增角色")
    @PreAuthorize("hasAuthority('" + PermConstants.PERM_CREATE + "')")
    @PostMapping
    public R<Long> create(@RequestBody RoleSaveDTO dto) {
        return R.ok(roleAdminService.create(dto));
    }

    @SysLog(type = "ROLE_UPDATE", content = "编辑角色")
    @PreAuthorize("hasAuthority('" + PermConstants.PERM_EDIT + "')")
    @PutMapping("/{id}")
    public R<String> update(@PathVariable Long id, @RequestBody RoleSaveDTO dto) {
        roleAdminService.update(id, dto);
        return R.ok("ok");
    }

    @SysLog(type = "ROLE_DELETE", content = "删除角色")
    @PreAuthorize("hasAuthority('" + PermConstants.PERM_REMOVE + "')")
    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable Long id) {
        roleAdminService.delete(id);
        return R.ok("ok");
    }
}
