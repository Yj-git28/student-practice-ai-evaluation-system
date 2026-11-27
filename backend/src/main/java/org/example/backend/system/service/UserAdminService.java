package org.example.backend.system.service;
import org.example.backend.common.BizException;
import org.example.backend.system.dto.UserSaveDTO;
import org.example.backend.system.entity.SysRole;
import org.example.backend.system.entity.SysUser;
import org.example.backend.system.mapper.ClassMapper;
import org.example.backend.system.mapper.SysRoleMapper;
import org.example.backend.system.mapper.SysUserMapper;
import org.example.backend.system.mapper.SysUserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** 管理员侧的师生账号管理：查询、新增、编辑、重置密码、删除、批量导入 */
@Service
public class UserAdminService {

    private static final String DEFAULT_PASSWORD = "123456";
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,32}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final long DEFAULT_ROLE_ID = 2L; // 学生
    private static final String ADMIN_CODE = "ADMIN";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final ClassMapper classMapper;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;

    public UserAdminService(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                            SysRoleMapper roleMapper, ClassMapper classMapper,
                            PasswordEncoder passwordEncoder, PermissionService permissionService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.classMapper = classMapper;
        this.passwordEncoder = passwordEncoder;
        this.permissionService = permissionService;
    }

    public List<Map<String, Object>> list(String keyword, Long roleId, String grade, Long classId) {
        return userMapper.selectUserList(keyword, roleId, grade, classId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(UserSaveDTO dto) {
        String username = trim(dto.getUsername());
        String realName = trim(dto.getRealName());
        String email = trim(dto.getEmail());
        List<Long> roleIds = resolveRoleIds(dto, null);
        validateProfile(username, realName, email, null);
        if (userMapper.existsByUsername(username)) {
            throw new BizException(4001, "账号已存在：" + username);
        }
        validateClass(dto.getClassId());
        String rawPassword = trim(dto.getPassword());
        if (rawPassword.isEmpty()) {
            rawPassword = DEFAULT_PASSWORD;
        } else if (rawPassword.length() < 6) {
            throw new BizException(4004, "初始密码至少 6 位");
        }
        long primaryRole = roleIds.get(0);
        userMapper.insertUser(username, passwordEncoder.encode(rawPassword), realName, email,
                (int) primaryRole, dto.getClassId());
        SysUser saved = userMapper.selectByUsername(username)
                .orElseThrow(() -> new BizException(500, "新增账号失败"));
        userRoleMapper.replaceUserRoles(saved.getUserId(), roleIds);
        permissionService.evict(saved.getUserId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, UserSaveDTO dto, Long operatorId) {
        SysUser user = userMapper.selectById(userId)
                .orElseThrow(() -> new BizException(4004, "用户不存在或已删除"));
        String realName = trim(dto.getRealName());
        String email = trim(dto.getEmail());
        List<Long> roleIds = resolveRoleIds(dto, user.getRoleId());
        validateProfile(user.getUsername(), realName, email, userId);
        validateClass(dto.getClassId());

        boolean wasAdmin = userMapper.hasAdminRole(userId);
        boolean keepAdmin = containsRoleCode(roleIds, ADMIN_CODE);
        // 不允许把系统中最后一个管理员移出管理员角色
        if (wasAdmin && !keepAdmin && userMapper.countActiveAdmins() <= 1) {
            throw new BizException(4003, "系统至少保留一个管理员，不能修改该账号的角色");
        }
        long primaryRole = roleIds.get(0);
        userMapper.updateUser(userId, realName, email, primaryRole, dto.getClassId());
        userRoleMapper.replaceUserRoles(userId, roleIds);
        permissionService.evict(userId);
    }

    public String resetPassword(Long userId) {
        SysUser user = userMapper.selectById(userId)
                .orElseThrow(() -> new BizException(4004, "用户不存在或已删除"));
        userMapper.updatePassword(user.getUserId(), passwordEncoder.encode(DEFAULT_PASSWORD));
        userMapper.clearCurrentJti(userId);
        permissionService.evict(userId);
        return DEFAULT_PASSWORD;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long operatorId) {
        if (operatorId != null && operatorId.equals(userId)) {
            throw new BizException(4003, "不能删除当前登录的账号");
        }
        SysUser user = userMapper.selectById(userId)
                .orElseThrow(() -> new BizException(4004, "用户不存在或已删除"));
        if (userMapper.hasAdminRole(userId) && userMapper.countActiveAdmins() <= 1) {
            throw new BizException(4003, "系统至少保留一个管理员，不能删除");
        }
        userMapper.logicalDelete(userId);
        permissionService.evict(userId);
    }

    /** 批量导入：逐行处理，返回成功数与失败明细（行号 + 原因） */
    public Map<String, Object> importUsers(List<UserSaveDTO> rows) {
        int success = 0;
        List<Map<String, Object>> failed = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            try {
                create(rows.get(i));
                success++;
            } catch (BizException e) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("row", i + 1);
                item.put("username", rows.get(i).getUsername());
                item.put("reason", e.getMessage());
                failed.add(item);
            } catch (Exception e) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("row", i + 1);
                item.put("username", rows.get(i).getUsername());
                item.put("reason", "导入异常：" + e.getMessage());
                failed.add(item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("success", success);
        result.put("failed", failed);
        return result;
    }

    /** 解析角色列表：优先 roleIds；否则回退单值 roleId；都空时用默认学生角色 */
    private List<Long> resolveRoleIds(UserSaveDTO dto, Long fallbackRoleId) {
        List<Long> roleIds = new ArrayList<>();
        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            roleIds.addAll(dto.getRoleIds());
        } else if (dto.getRoleId() != null) {
            roleIds.add(dto.getRoleId().longValue());
        } else if (fallbackRoleId != null) {
            roleIds.add(fallbackRoleId);
        } else {
            roleIds.add(DEFAULT_ROLE_ID);
        }
        roleIds.removeIf(r -> r == null || r <= 0);
        roleIds = new ArrayList<>(new LinkedHashSet<>(roleIds));
        if (roleIds.isEmpty()) roleIds.add(DEFAULT_ROLE_ID);
        if (userRoleMapper.countInvalidRoles(roleIds) > 0) {
            throw new BizException(4002, "角色不合法或已停用");
        }
        return roleIds;
    }

    private boolean containsRoleCode(List<Long> roleIds, String roleCode) {
        for (Long rid : roleIds) {
            Optional<SysRole> role = roleMapper.selectById(rid);
            if (role.isPresent() && roleCode.equals(role.get().getRoleCode())) {
                return true;
            }
        }
        return false;
    }

    private void validateProfile(String username, String realName, String email, Long excludeUserId) {
        if (username.length() < 3 || username.length() > 32 || !USERNAME.matcher(username).matches()) {
            throw new BizException(4002, "账号需为 3-32 位字母、数字或下划线");
        }
        if (realName.isEmpty() || realName.length() > 32) {
            throw new BizException(4002, "姓名不能为空且不超过 32 字");
        }
        if (!email.isEmpty()) {
            if (!EMAIL.matcher(email).matches()) {
                throw new BizException(4002, "邮箱格式不正确");
            }
            boolean duplicated = excludeUserId == null
                    ? userMapper.existsByEmail(email)
                    : userMapper.existsByEmailExcluding(email, excludeUserId);
            if (duplicated) {
                throw new BizException(4001, "邮箱已被其他账号使用：" + email);
            }
        }
    }

    private void validateClass(Long classId) {
        if (classId != null && classId > 0 && !classMapper.exists(classId)) {
            throw new BizException(4002, "班级不存在");
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
