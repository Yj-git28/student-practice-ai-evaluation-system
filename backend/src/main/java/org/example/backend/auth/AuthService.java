package org.example.backend.auth;

import org.example.backend.common.BizException;
import org.example.backend.security.JwtService;
import org.example.backend.security.LoginUser;
import org.example.backend.security.TokenBlacklistService;
import org.example.backend.system.entity.SysPermission;
import org.example.backend.system.entity.SysUser;
import org.example.backend.system.log.SysLogService;
import org.example.backend.system.mapper.SysUserMapper;
import org.example.backend.system.service.PermissionService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final PermissionService permissionService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService blacklist;
    private final SysLogService sysLogService;

    public AuthService(SysUserMapper userMapper, PermissionService permissionService,
                       JwtService jwtService, PasswordEncoder passwordEncoder,
                       LoginAttemptService loginAttemptService, TokenBlacklistService blacklist,
                       SysLogService sysLogService) {
        this.userMapper = userMapper;
        this.permissionService = permissionService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.blacklist = blacklist;
        this.sysLogService = sysLogService;
    }

    public LoginVO login(LoginDTO dto) {
        String username = dto == null ? null : dto.getUsername();
        if (username == null || username.isBlank()) {
            throw new BizException(4001, "用户名不能为空");
        }

        // 1. 锁定期内直接拒绝，不查库
        loginAttemptService.assertNotLocked(username);

        Optional<SysUser> found = userMapper.selectByUsername(username);
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
            throw new BizException(4001, "用户名或密码错误，还可尝试 "
                    + (LoginAttemptService.MAX_ATTEMPTS - failCount) + " 次");
        }

        // 2. 登录成功，清空失败计数
        loginAttemptService.onSuccess(username);

        // 登录时就构建一次，顺便校验角色是否配置完整
        LoginUser lu = permissionService.buildLoginUser(found.get().getUserId());
        String token = jwtService.createToken(lu.getUserId(), lu.getUsername(), lu.getRoleCode());
        return new LoginVO(token, jwtService.getExpireSeconds());
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
