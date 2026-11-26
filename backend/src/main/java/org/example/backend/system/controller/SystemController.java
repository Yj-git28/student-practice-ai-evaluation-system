package org.example.backend.system.controller;

import org.example.backend.system.log.SysLogService;
import org.example.backend.common.R;
import org.example.backend.security.LoginUser;
import org.example.backend.system.dto.ClassSaveDTO;
import org.example.backend.system.dto.SystemConfigDTO;
import org.example.backend.system.dto.UserSaveDTO;
import org.example.backend.system.log.SysLog;
import org.example.backend.system.mapper.ClassMapper;
import org.example.backend.system.mapper.SysUserMapper;
import org.example.backend.system.service.ClassService;
import org.example.backend.system.service.InviteCodeService;
import org.example.backend.system.service.PermissionService;
import org.example.backend.system.service.SystemConfigService;
import org.example.backend.system.service.UserAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final PermissionService permissionService;
    private final SysUserMapper sysUserMapper;
    private final InviteCodeService inviteCodeService;
    private final UserAdminService userAdminService;
    private final ClassMapper classMapper;
    private final ClassService classService;
    private final org.example.backend.system.log.SysLogService sysLogService;
    private final SystemConfigService systemConfigService;

    public SystemController(PermissionService permissionService, SysUserMapper sysUserMapper,
                            InviteCodeService inviteCodeService, UserAdminService userAdminService,
                            ClassMapper classMapper, ClassService classService,
                            org.example.backend.system.log.SysLogService sysLogService,
                            SystemConfigService systemConfigService) {
        this.permissionService = permissionService;
        this.sysUserMapper = sysUserMapper;
        this.inviteCodeService = inviteCodeService;
        this.userAdminService = userAdminService;
        this.classMapper = classMapper;
        this.classService = classService;
        this.sysLogService = sysLogService;
        this.systemConfigService = systemConfigService;
    }

    /** 仅 ADMIN：查看管理员注册邀请码（一次性 + 2 小时有效，过期自动换新） */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin-invite-code")
    public R<Map<String, Object>> adminInviteCode() {
        return R.ok(inviteInfo(inviteCodeService.current()));
    }

    /** 仅 ADMIN：重置邀请码，旧码立即失效，并重新计时 2 小时 */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin-invite-code/reset")
    public R<Map<String, Object>> resetAdminInviteCode() {
        return R.ok(inviteInfo(inviteCodeService.reset()));
    }

    private Map<String, Object> inviteInfo(InviteCodeService.InviteCode ic) {
        long remainingSeconds = Math.max(0, (ic.expireAt() - System.currentTimeMillis()) / 1000);
        return Map.of("inviteCode", ic.code(), "remainingSeconds", remainingSeconds);
    }

    /** 仅 ADMIN：用户列表，支持关键词 / 角色 / 年级 / 班级筛选 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public R<List<Map<String, Object>>> userList(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Long roleId,
                                                 @RequestParam(required = false) String grade,
                                                 @RequestParam(required = false) Long classId) {
        return R.ok(userAdminService.list(keyword, roleId, grade, classId));
    }

    /** 仅 ADMIN：新增账号 */
    @SysLog(type = "USER_CREATE", content = "新增账号")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/users")
    public R<String> createUser(@RequestBody UserSaveDTO dto) {
        userAdminService.create(dto);
        return R.ok("ok");
    }

    /** 仅 ADMIN：编辑账号（账号名不可改） */
    @SysLog(type = "USER_UPDATE", content = "编辑账号")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/users/{id}")
    public R<String> updateUser(@PathVariable Long id, @RequestBody UserSaveDTO dto,
                                @AuthenticationPrincipal LoginUser current) {
        userAdminService.update(id, dto, current == null ? null : current.getUserId());
        return R.ok("ok");
    }

    /** 仅 ADMIN：重置为默认密码，并踢掉该账号现有登录 */
    @SysLog(type = "USER_RESET_PWD", content = "重置密码")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/users/{id}/reset-password")
    public R<Map<String, Object>> resetPassword(@PathVariable Long id) {
        String password = userAdminService.resetPassword(id);
        return R.ok(Map.of("password", password));
    }

    /** 仅 ADMIN：删除账号（逻辑删除，不能删自己、不能删最后一个管理员） */
    @SysLog(type = "USER_DELETE", content = "删除账号")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/users/{id}")
    public R<String> deleteUser(@PathVariable Long id, @AuthenticationPrincipal LoginUser current) {
        userAdminService.delete(id, current == null ? null : current.getUserId());
        return R.ok("ok");
    }

    /** 仅 ADMIN：批量导入账号，返回成功数与失败明细 */
    @SysLog(type = "USER_IMPORT", content = "批量导入账号")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/users/import")
    public R<Map<String, Object>> importUsers(@RequestBody List<UserSaveDTO> rows) {
        return R.ok(userAdminService.importUsers(rows));
    }

    /** 仅 ADMIN：班级列表（含年级），用于筛选与编辑下拉框 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/classes")
    public R<List<Map<String, Object>>> classes() {
        return R.ok(classMapper.selectAll());
    }

    /** 仅 ADMIN：新增班级 */
    @SysLog(type = "CLASS_CREATE", content = "新增班级")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/classes")
    public R<String> createClass(@RequestBody ClassSaveDTO dto) {
        classService.create(dto);
        return R.ok("ok");
    }

    /** 仅 ADMIN：修改班级（名称、年级） */
    @SysLog(type = "CLASS_UPDATE", content = "编辑班级")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/classes/{id}")
    public R<String> updateClass(@PathVariable Long id, @RequestBody ClassSaveDTO dto) {
        classService.update(id, dto);
        return R.ok("ok");
    }

    /** 仅 ADMIN：删除班级（班级下仍有账号时拒绝） */
    @SysLog(type = "CLASS_DELETE", content = "删除班级")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/classes/{id}")
    public R<String> deleteClass(@PathVariable Long id) {
        classService.delete(id);
        return R.ok("ok");
    }

    /** 仅 ADMIN：年级列表 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/grades")
    public R<List<String>> grades() {
        return R.ok(classMapper.selectGrades());
    }

    /** 仅 ADMIN：最近操作日志（新增账号、批量导入、登录等） */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/logs")
    public R<List<Map<String, Object>>> logs(@RequestParam(defaultValue = "100") int limit) {
        return R.ok(sysLogService.recent(limit));
    }

    /** 仅 ADMIN：手动刷新全部用户的权限缓存，返回刷新的用户数 */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/permission/refresh")
    public R<Integer> refreshPermissions() {
        return R.ok(permissionService.refreshAll());
    }

    /** 系统参数配置：模型模式、API Key、单文件大小上限、允许后缀 */
    @PreAuthorize("hasAuthority('config:list')")
    @GetMapping("/config")
    public R<Map<String, Object>> config() {
        return R.ok(systemConfigService.get());
    }

    /** 仅 ADMIN：保存系统参数配置 */
    @SysLog(type = "CONFIG_SAVE", content = "保存系统配置")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/config")
    public R<Void> saveConfig(@RequestBody SystemConfigDTO dto) {
        systemConfigService.save(dto);
        return R.ok(null);
    }
}
