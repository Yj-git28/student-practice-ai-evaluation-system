package org.example.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.backend.system.mapper.SysUserMapper;
import org.example.backend.system.service.PermissionService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final PermissionService permissionService;
    private final TokenBlacklistService blacklist;
    private final SysUserMapper userMapper;

    public JwtAuthFilter(JwtService jwtService, PermissionService permissionService,
                         TokenBlacklistService blacklist, SysUserMapper userMapper) {
        this.jwtService = jwtService;
        this.permissionService = permissionService;
        this.blacklist = blacklist;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);

                // 已退出登录的 token：jti 在黑名单里，直接当未登录处理
                if (blacklist.isRevoked(claims.getId())) {
                    SecurityContextHolder.clearContext();
                    chain.doFilter(request, response);
                    return;
                }

                Long userId = Long.valueOf(claims.getSubject());

                // 单设备登录：token 的 jti 必须是该账号当前绑定的会话。
                // 在另一台设备重新登录后，库里 jti 被覆盖，旧设备的下一次请求即失效。
                // 库里为空（老版本签发的 token 或已退出）时不强制，避免升级后全员被踢。
                String currentJti = userMapper.selectCurrentJti(userId).orElse(null);
                if (currentJti != null && !currentJti.equals(claims.getId())) {
                    SecurityContextHolder.clearContext();
                    chain.doFilter(request, response);
                    return;
                }

                // 每次请求都从缓存/库里重建 LoginUser —— 权限改了立刻生效
                LoginUser loginUser = permissionService.buildLoginUser(userId);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (UsernameNotFoundException e) {
                // 账号已禁用、删除或不存在：旧 token 按未登录处理
                SecurityContextHolder.clearContext();
            } catch (JwtException | IllegalArgumentException e) {
                // 解析失败不抛异常，交给 Security 的 EntryPoint 返回 401
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
