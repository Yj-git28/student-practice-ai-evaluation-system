package org.example.backend.system.service;

import org.example.backend.security.LoginUser;
import org.example.backend.system.entity.SysPermission;
import org.example.backend.system.entity.SysRole;
import org.example.backend.system.entity.SysUser;
import org.example.backend.system.mapper.SysPermissionMapper;
import org.example.backend.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock SysUserMapper userMapper;
    @Mock SysPermissionMapper permissionMapper;
    @Mock PermissionCacheService cache;

    private PermissionService service;

    @BeforeEach
    void setUp() {
        service = new PermissionService(userMapper, permissionMapper, cache);
    }

    @Test
    @DisplayName("权限 = 角色权限 ∪ 个人权限；dataScope 读 sys_role.data_scope，不按 roleCode 推断")
    void mergesRoleAndPersonalPermissionsAndReadsDataScope() {
        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setUsername("admin");
        user.setPassword("$2a$10$xxx");
        user.setRoleId(3L);

        // 故意让 ADMIN 的 data_scope = 2：如果还按 roleCode 硬编码，这里会是 1
        SysRole role = new SysRole();
        role.setRoleId(3L);
        role.setRoleCode("ADMIN");
        role.setRoleName("管理员");
        role.setDataScope(2);

        when(userMapper.selectById(1L)).thenReturn(Optional.of(user));
        when(userMapper.selectRoleByUserId(1L)).thenReturn(Optional.of(role));
        when(permissionMapper.selectByUserId(1L)).thenReturn(List.of(perm("training:list")));
        when(permissionMapper.selectPersonalByUserId(1L)).thenReturn(List.of(perm("config:list")));

        LoginUser lu = service.buildLoginUser(1L);

        assertThat(lu.getPermissions())
                .containsExactlyInAnyOrder("training:list", "config:list");
        assertThat(lu.getDataScope()).isEqualTo(2);
        assertThat(lu.getRoles()).containsExactly("ADMIN");
        verify(cache).put(eq(1L), any(LoginUser.class));
    }

    @Test
    @DisplayName("data_scope 为空或非法时兜底为 3（本人），避免默认放开全量")
    void invalidDataScopeFallsBackToSelf() {
        SysUser user = new SysUser();
        user.setUserId(2L);
        user.setUsername("stu");

        SysRole role = new SysRole();
        role.setRoleCode("STUDENT");
        role.setDataScope(null);

        when(userMapper.selectById(2L)).thenReturn(Optional.of(user));
        when(userMapper.selectRoleByUserId(2L)).thenReturn(Optional.of(role));
        when(permissionMapper.selectByUserId(2L)).thenReturn(List.of());
        when(permissionMapper.selectPersonalByUserId(2L)).thenReturn(List.of());

        assertThat(service.buildLoginUser(2L).getDataScope()).isEqualTo(3);
    }

    @Test
    @DisplayName("权限刷新接口：返回刷新的用户数，并逐个失效缓存")
    void refreshAllInvalidatesEveryUser() {
        when(userMapper.selectAllUserIds()).thenReturn(List.of(1L, 2L, 3L));

        int refreshed = service.refreshAll();

        assertThat(refreshed).isEqualTo(3);
        verify(cache, times(3)).invalidate(anyLong());
    }

    private SysPermission perm(String code) {
        SysPermission p = new SysPermission();
        p.setPermCode(code);
        return p;
    }
}
