package org.example.backend.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.example.backend.system.mapper.SysUserMapper;
import org.example.backend.system.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock JwtService jwtService;
    @Mock PermissionService permissionService;
    @Mock TokenBlacklistService blacklist;
    @Mock SysUserMapper userMapper;
    @Mock Claims claims;
    @Mock FilterChain chain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthFilter(jwtService, permissionService, blacklist, userMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("账号失效时不建立认证上下文，旧 token 交由安全入口返回 401")
    void disabledUserDoesNotEstablishAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.parse("valid-token")).thenReturn(claims);
        when(claims.getId()).thenReturn("jti-1");
        when(claims.getSubject()).thenReturn("1");
        when(userMapper.selectCurrentJti(1L)).thenReturn(java.util.Optional.of("jti-1"));
        when(permissionService.buildLoginUser(1L))
                .thenThrow(new UsernameNotFoundException("用户已禁用"));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
