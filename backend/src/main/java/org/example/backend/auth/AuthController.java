package org.example.backend.auth;

import org.example.backend.common.R;
import org.example.backend.security.LoginUser;
import org.example.backend.security.PublicApi;
import org.example.backend.system.log.SysLog;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PublicApi
    @SysLog(type = "LOGIN", content = "用户登录")
    @PostMapping("/login")
    public R<LoginVO> login(@RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    /** 退出登录：空 body，返回 R.ok()；旧 token 的 jti 立即进黑名单 */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(authorization);
        return R.ok(null);
    }

    // 注意：必须给个权限注解，否则第 2.11 步的启动扫描会让应用起不来。
    // 这里只要求"登录"，不要求具体权限码。
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/getInfo")
    public R<UserInfoVO> getInfo(@AuthenticationPrincipal LoginUser loginUser) {
        return R.ok(authService.getInfo(loginUser));
    }
}
