package org.example.backend.system;

import org.example.backend.common.R;
import org.example.backend.system.service.PermissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final PermissionService permissionService;

    public SystemController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /** 仅 ADMIN：手动刷新全部用户的权限缓存，返回刷新的用户数 */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/permission/refresh")
    public R<Integer> refreshPermissions() {
        return R.ok(permissionService.refreshAll());
    }
}
