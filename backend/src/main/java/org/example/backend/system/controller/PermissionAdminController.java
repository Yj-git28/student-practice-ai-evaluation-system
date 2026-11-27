package org.example.backend.system.controller;
import org.example.backend.common.R;
import org.example.backend.system.dto.PermissionSaveDTO;
import org.example.backend.system.entity.SysPermission;
import org.example.backend.system.log.SysLog;
import org.example.backend.system.service.PermissionAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 管理员：权限项维护（菜单/按钮/接口）与角色授权 */
@RestController
@RequestMapping("/api/system/permission")
public class PermissionAdminController {

    private final PermissionAdminService permissionAdminService;

    public PermissionAdminController(PermissionAdminService permissionAdminService) {
        this.permissionAdminService = permissionAdminService;
    }

    /** 全量权限项树 */
    @PreAuthorize("hasAuthority('permission:manage')")
    @GetMapping("/tree")
    public R<List<SysPermission>> tree() {
        return R.ok(permissionAdminService.tree());
    }

    /** 角色列表 + 每个角色已授权的权限 ID */
    @PreAuthorize("hasAuthority('permission:manage')")
    @GetMapping("/roles")
    public R<List<Map<String, Object>>> roles() {
        return R.ok(permissionAdminService.roles());
    }

    /** 新增权限项 */
    @SysLog(type = "PERM_CREATE", content = "新增权限项")
    @PreAuthorize("hasAuthority('permission:create')")
    @PostMapping
    public R<Long> create(@RequestBody PermissionSaveDTO dto) {
        return R.ok(permissionAdminService.create(dto));
    }

    /** 编辑权限项 */
    @SysLog(type = "PERM_UPDATE", content = "编辑权限项")
    @PreAuthorize("hasAuthority('permission:edit')")
    @PutMapping("/{id}")
    public R<String> update(@PathVariable Long id, @RequestBody PermissionSaveDTO dto) {
        dto.setPermissionId(id);
        permissionAdminService.update(dto);
        return R.ok("ok");
    }

    /** 删除权限项（有子项 / 被角色或个人引用时拒绝） */
    @SysLog(type = "PERM_DELETE", content = "删除权限项")
    @PreAuthorize("hasAuthority('permission:remove')")
    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable Long id) {
        permissionAdminService.delete(id);
        return R.ok("ok");
    }

    /** 覆盖式给角色分配权限 */
    @SysLog(type = "GRANT_PERM", content = "角色权限分配")
    @PreAuthorize("hasAuthority('permission:grant')")
    @PutMapping("/roles/{roleId}")
    public R<String> assign(@PathVariable Long roleId, @RequestBody Map<String, List<Long>> body) {
        permissionAdminService.assign(roleId, body.get("permissionIds"));
        return R.ok("ok");
    }
}
