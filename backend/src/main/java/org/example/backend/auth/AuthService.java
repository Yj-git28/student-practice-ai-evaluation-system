package org.example.backend.auth;

import org.example.backend.common.BizException;
import org.example.backend.security.JwtService;
import org.example.backend.security.LoginUser;
import org.example.backend.system.entity.SysPermission;
import org.example.backend.system.entity.SysUser;
import org.example.backend.system.mapper.SysUserMapper;
import org.example.backend.system.service.PermissionService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final PermissionService permissionService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(SysUserMapper userMapper, PermissionService permissionService,
                       JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.permissionService = permissionService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginVO login(LoginDTO dto) {
        SysUser user = userMapper.selectByUsername(dto.getUsername())
                .orElseThrow(() -> new BizException(4001, "用户名或密码错误"));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(4001, "用户名或密码错误");
        }

        // 登录时就构建一次，顺便校验角色是否配置完整
        LoginUser lu = permissionService.buildLoginUser(user.getUserId());
        String token = jwtService.createToken(lu.getUserId(), lu.getUsername(), lu.getRoleCode());
        return new LoginVO(token, jwtService.getExpireSeconds());
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