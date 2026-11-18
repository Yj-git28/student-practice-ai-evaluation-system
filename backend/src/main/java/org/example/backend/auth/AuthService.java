package org.example.backend.auth;

import org.example.backend.common.BizException;
import org.example.backend.security.JwtService;
import org.example.backend.security.LoginUser;
import org.example.backend.security.TokenBlacklistService;
import org.example.backend.system.entity.SysPermission;
import org.example.backend.system.entity.SysUser;
import org.example.backend.system.log.SysLogService;
import org.example.backend.system.mapper.SysUserMapper;
import org.example.backend.system.service.InviteCodeService;
import org.example.backend.system.service.PermissionService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final LoginCodeMapper codeMapper;
    private final EmailSender emailSender;
    private final PermissionService permissionService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService blacklist;
    private final SysLogService sysLogService;
    private final InviteCodeService inviteCodeService;

    public AuthService(SysUserMapper userMapper, LoginCodeMapper codeMapper, EmailSender emailSender,
                       PermissionService permissionService,
                       JwtService jwtService, PasswordEncoder passwordEncoder,
                       LoginAttemptService loginAttemptService, TokenBlacklistService blacklist,
                       SysLogService sysLogService, InviteCodeService inviteCodeService) {
        this.userMapper = userMapper;
        this.codeMapper = codeMapper;
        this.emailSender = emailSender;
        this.permissionService = permissionService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.blacklist = blacklist;
        this.sysLogService = sysLogService;
        this.inviteCodeService = inviteCodeService;
    }

    public LoginVO login(LoginDTO dto) {
        String username = dto == null ? null : dto.getUsername();
        if (username == null || username.isBlank()) {
            throw new BizException(4001, "账号不能为空");
        }

        // 1. 锁定期内直接拒绝，不查库
        loginAttemptService.assertNotLocked(username);

        // 账号字段兼容用户名 / 邮箱：先按用户名查，再按邮箱查
        Optional<SysUser> found = userMapper.selectByUsername(username)
                .or(() -> userMapper.selectByEmail(username));
        boolean passwordOk = found.isPresent()
                && dto.getPassword() != null
                && passwordEncoder.matches(dto.getPassword(), found.get().getPassword());

        if (!passwordOk) {
            int failCount = loginAttemptService.onFailure(username);
            if (failCount >= LoginAttemptService.MAX_ATTEMPTS) {
                sysLogService.record(found.map(SysUser::getUserId).orElse(null), "LOGIN_FAIL",
                        "连续 " + failCount + " 次登录失败，账号 " + username + " 锁定 15 分钟");
                throw new BizException(4429, "连续 " + LoginAttemptService.MAX_ATTEMPTS
                        + " 次登录失败，账号已锁定 15 分钟");
            }
            throw new BizException(4001, "账号或密码错误，还可尝试 "
                    + (LoginAttemptService.MAX_ATTEMPTS - failCount) + " 次");
        }

        // 2. 登录成功，清空失败计数
        loginAttemptService.onSuccess(username);

        // 登录成功后统一走 issueToken：组装 LoginUser + 签发 JWT
        return issueToken(found.get());
    }

    /** 所有登录方式共用：组装 LoginUser + 签发 JWT */
    private LoginVO issueToken(SysUser user) {
        LoginUser lu = permissionService.buildLoginUser(user.getUserId());
        JwtService.IssuedToken issued =
                jwtService.createTokenWithId(lu.getUserId(), lu.getUsername(), lu.getRoleCode());
        // 单设备登录：新会话覆盖当前 jti，其他设备上的旧 token 下次请求即失效
        userMapper.updateCurrentJti(user.getUserId(), issued.jti());
        return new LoginVO(issued.token(), jwtService.getExpireSeconds());
    }

    /** 发送邮箱登录验证码（6 位，5 分钟有效，60 秒限流） */
    public void sendLoginCode(String email) {
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BizException(4002, "邮箱格式不正确");
        }
        if (codeMapper.countRecent(email, 60) > 0) {
            throw new BizException(429, "发送过于频繁，请 60 秒后再试");
        }
        if (userMapper.selectByEmail(email).isEmpty()) {
            throw new BizException(4004, "该邮箱未绑定账号");
        }

        String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        codeMapper.save(email, code, 300);

        // 未配置 SMTP 时走 mock（日志输出），配置后走真实发送
        emailSender.send(email, code);
    }

    /** 邮箱验证码登录：校验并消费验证码，成功后签发 JWT */
    public LoginVO loginByEmail(EmailLoginDTO dto) {
        if (dto == null || dto.getEmail() == null || dto.getCode() == null) {
            throw new BizException(4001, "邮箱和验证码不能为空");
        }
        if (!codeMapper.verifyAndConsume(dto.getEmail(), dto.getCode())) {
            throw new BizException(4001, "验证码错误或已过期");
        }
        SysUser user = userMapper.selectByEmail(dto.getEmail())
                .orElseThrow(() -> new BizException(4004, "该邮箱未绑定账号"));
        // 邮箱登录成功也清空失败计数，避免刚用密码锁过的账号无法走验证码登录
        loginAttemptService.onSuccess(dto.getEmail());
        return issueToken(user);
    }

    /** 发送注册验证码：邮箱必须未被注册 */
    public void sendRegisterCode(String email) {
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BizException(4002, "邮箱格式不正确");
        }
        if (codeMapper.countRecent(email, 60) > 0) {
            throw new BizException(429, "发送过于频繁，请 60 秒后再试");
        }
        if (userMapper.existsByEmail(email)) {
            throw new BizException(4005, "该邮箱已被注册");
        }
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        codeMapper.save(email, code, 300);
        emailSender.send(email, code);
    }

    /** 邮箱注册：校验验证码 → 创建学生账号 → 返回 token */
    public LoginVO register(RegisterDTO dto) {
        if (dto == null || dto.getEmail() == null || dto.getPassword() == null || dto.getCode() == null) {
            throw new BizException(4001, "邮箱、密码、验证码不能为空");
        }
        if (!dto.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BizException(4002, "邮箱格式不正确");
        }
        if (dto.getPassword().length() < 6) {
            throw new BizException(4003, "密码至少 6 位");
        }
        if (userMapper.existsByEmail(dto.getEmail())) {
            throw new BizException(4005, "该邮箱已被注册");
        }
        if (!codeMapper.verifyAndConsume(dto.getEmail(), dto.getCode())) {
            throw new BizException(4001, "验证码错误或已过期");
        }
        String username = (dto.getUsername() == null || dto.getUsername().isBlank())
                ? dto.getEmail() : dto.getUsername();
        if (userMapper.existsByUsername(username)) {
            throw new BizException(4005, "该用户名已被使用");
        }
        // 角色：学生(2)/教师(1)可直接注册；管理员(3)需提供正确邀请码
        int roleId = 2;
        if (dto.getRoleId() != null && dto.getRoleId() == 1) {
            roleId = 1;
        } else if (dto.getRoleId() != null && dto.getRoleId() == 3) {
            // 邀请码由系统生成、存于 sys_config（一次性 + 2 小时有效），管理员可在设置里查看
            String inviteCode = inviteCodeService.current().code();
            if (inviteCode.isBlank()) {
                throw new BizException(4003, "系统未开放管理员注册");
            }
            if (!inviteCode.equals(dto.getInviteCode())) {
                throw new BizException(4003, "管理员邀请码错误");
            }
            roleId = 3;
        }
        String encoded = passwordEncoder.encode(dto.getPassword());
        userMapper.insertUser(username, encoded, username, dto.getEmail(), roleId, null);

        // 邀请码一次性：管理员注册成功后立即作废并换新码
        if (roleId == 3) {
            inviteCodeService.reset();
        }

        SysUser user = userMapper.selectByEmail(dto.getEmail())
                .orElseThrow(() -> new BizException(500, "注册失败，请重试"));
        return issueToken(user);
    }

    /** 已登录用户发送换绑邮箱验证码：新邮箱不能被其他账号占用 */
    public void sendBindCode(Long userId, String newEmail) {
        if (newEmail == null || !newEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BizException(4002, "邮箱格式不正确");
        }
        if (codeMapper.countRecent(newEmail, 60) > 0) {
            throw new BizException(429, "发送过于频繁，请 60 秒后再试");
        }
        SysUser me = userMapper.selectById(userId)
                .orElseThrow(() -> new BizException(4004, "用户不存在"));
        if (newEmail.equalsIgnoreCase(me.getEmail())) {
            throw new BizException(4005, "新邮箱与当前邮箱相同");
        }
        if (userMapper.existsByEmail(newEmail)) {
            throw new BizException(4005, "该邮箱已被其他账号绑定");
        }
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        codeMapper.save(newEmail, code, 300);
        emailSender.send(newEmail, code);
    }

    /** 已登录用户确认换绑邮箱 */
    public void bindEmail(Long userId, String newEmail, String code) {
        if (newEmail == null || code == null) {
            throw new BizException(4001, "邮箱和验证码不能为空");
        }
        if (userMapper.existsByEmail(newEmail)) {
            throw new BizException(4005, "该邮箱已被其他账号绑定");
        }
        if (!codeMapper.verifyAndConsume(newEmail, code)) {
            throw new BizException(4001, "验证码错误或已过期");
        }
        userMapper.updateEmail(userId, newEmail);
    }

    /** 忘记密码：校验邮箱验证码后重置密码 */
    public void resetPassword(String email, String code, String newPassword) {
        if (email == null || code == null || newPassword == null) {
            throw new BizException(4001, "邮箱、验证码、新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new BizException(4003, "密码至少 6 位");
        }
        SysUser user = userMapper.selectByEmail(email)
                .orElseThrow(() -> new BizException(4004, "该邮箱未绑定账号"));
        if (!codeMapper.verifyAndConsume(email, code)) {
            throw new BizException(4001, "验证码错误或已过期");
        }
        userMapper.updatePassword(user.getUserId(), passwordEncoder.encode(newPassword));
        // 重置该账号的登录失败计数
        loginAttemptService.onSuccess(email);
    }

    /**
     * 退出登录：把当前 token 的 jti 拉黑，同一 token 后续请求立即 401。
     * token 解析失败时按“已经失效”处理，仍然返回成功（幂等）。
     */
    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) return;
        String token = authorizationHeader.substring(7);
        try {
            var claims = jwtService.parse(token);
            blacklist.revoke(claims.getId(), claims.getExpiration());
            // 退出当前会话：清空 jti，该账号之后需要重新登录
            userMapper.clearCurrentJti(Long.valueOf(claims.getSubject()));
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) {
            // 签名错/过期/格式错：本来就用不了，不需要再拉黑
        }
    }

    public UserInfoVO getInfo(LoginUser lu) {
        List<SysPermission> menus = permissionService.menusOf(lu.getUserId());

        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(lu.getUserId());
        vo.setUsername(lu.getUsername());
        vo.setRealName(lu.getRealName());
        vo.setEmail(lu.getEmail());
        vo.setRoleId(lu.getRoleId());
        vo.setRoleCode(lu.getRoleCode());
        vo.setRoleName(lu.getRoleName());
        vo.setClassId(lu.getClassId());
        vo.setRoles(lu.getRoles());
        vo.setPermissions(lu.getPermissions());
        vo.setDataScope(lu.getDataScope());
        vo.setMenus(buildTree(menus));
        return vo;
    }

    /** parent_id = 0 为根；孤儿节点（父节点没授权）提升为根，避免菜单消失 */
    private List<SysPermission> buildTree(List<SysPermission> flat) {
        Map<Long, SysPermission> nodeMap = new LinkedHashMap<>();
        flat.forEach(p -> {
            p.setChildren(new ArrayList<>());
            nodeMap.put(p.getPermissionId(), p);
        });

        List<SysPermission> roots = new ArrayList<>();
        for (SysPermission p : flat) {
            Long pid = p.getParentId() == null ? 0L : p.getParentId();
            SysPermission parent = nodeMap.get(pid);
            if (pid == 0L || parent == null) {
                roots.add(p);          // 顶层 或 孤儿 → 都当根
            } else {
                parent.getChildren().add(p);
            }
        }
        return roots;
    }
}
