package org.example.backend.system.service;
import org.example.backend.common.BizException;
import org.example.backend.system.dto.UserSaveDTO;
import org.example.backend.system.entity.SysUser;
import org.example.backend.system.mapper.ClassMapper;
import org.example.backend.system.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 管理员侧的师生账号管理：查询、新增、编辑、重置密码、删除、批量导入 */
@Service
public class UserAdminService {

    private static final String DEFAULT_PASSWORD = "123456";
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,32}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final SysUserMapper userMapper;
    private final ClassMapper classMapper;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(SysUserMapper userMapper, ClassMapper classMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.classMapper = classMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Map<String, Object>> list(String keyword, Long roleId, String grade, Long classId) {
        return userMapper.selectUserList(keyword, roleId, grade, classId);
    }

    public void create(UserSaveDTO dto) {
        String username = trim(dto.getUsername());
        String realName = trim(dto.getRealName());
        String email = trim(dto.getEmail());
        int roleId = resolveRoleId(dto.getRoleId());
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
        userMapper.insertUser(username, passwordEncoder.encode(rawPassword), realName, email, roleId, dto.getClassId());
    }

    public void update(Long userId, UserSaveDTO dto, Long operatorId) {
        SysUser user = userMapper.selectById(userId)
                .orElseThrow(() -> new BizException(4004, "用户不存在或已删除"));
        String realName = trim(dto.getRealName());
        String email = trim(dto.getEmail());
        int roleId = resolveRoleId(dto.getRoleId() == null ? user.getRoleId().intValue() : dto.getRoleId());
        validateProfile(user.getUsername(), realName, email, userId);
        validateClass(dto.getClassId());
        // 不允许把系统中最后一个管理员改成其他角色
        if (user.getRoleId() != null && user.getRoleId() == 3 && roleId != 3 && userMapper.countActiveAdmins() <= 1) {
            throw new BizException(4003, "系统至少保留一个管理员，不能修改该账号的角色");
        }
        userMapper.updateUser(userId, realName, email, (long) roleId, dto.getClassId());
    }

    public String resetPassword(Long userId) {
        SysUser user = userMapper.selectById(userId)
                .orElseThrow(() -> new BizException(4004, "用户不存在或已删除"));
        userMapper.updatePassword(user.getUserId(), passwordEncoder.encode(DEFAULT_PASSWORD));
        userMapper.clearCurrentJti(userId);
        return DEFAULT_PASSWORD;
    }

    public void delete(Long userId, Long operatorId) {
        if (operatorId != null && operatorId.equals(userId)) {
            throw new BizException(4003, "不能删除当前登录的账号");
        }
        SysUser user = userMapper.selectById(userId)
                .orElseThrow(() -> new BizException(4004, "用户不存在或已删除"));
        if (user.getRoleId() != null && user.getRoleId() == 3 && userMapper.countActiveAdmins() <= 1) {
            throw new BizException(4003, "系统至少保留一个管理员，不能删除");
        }
        userMapper.logicalDelete(userId);
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

    private int resolveRoleId(Integer roleId) {
        if (roleId == null) {
            return 2;
        }
        if (roleId < 1 || roleId > 3) {
            throw new BizException(4002, "角色不合法");
        }
        return roleId;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
