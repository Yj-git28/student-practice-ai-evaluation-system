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

    /** 发送邮箱登录验证码；未配置 SMTP 时验证码输出到后端日志 */
    @PublicApi
    @PostMapping("/email/send")
    public R<Void> sendEmailCode(@RequestBody EmailLoginDTO dto) {
        authService.sendLoginCode(dto == null ? null : dto.getEmail());
        return R.ok(null);
    }

    /** 邮箱验证码登录 */
    @PublicApi
    @SysLog(type = "LOGIN", content = "邮箱验证码登录")
    @PostMapping("/login/email")
    public R<LoginVO> loginByEmail(@RequestBody EmailLoginDTO dto) {
        return R.ok(authService.loginByEmail(dto));
    }

    /** 发送注册验证码 */
    @PublicApi
    @PostMapping("/register/email/send")
    public R<Void> sendRegisterCode(@RequestBody EmailLoginDTO dto) {
        authService.sendRegisterCode(dto == null ? null : dto.getEmail());
        return R.ok(null);
    }

    /** 邮箱注册：校验验证码并创建学生账号，注册成功直接返回 token */
    @PublicApi
    @SysLog(type = "REGISTER", content = "用户注册")
    @PostMapping("/register")
    public R<LoginVO> register(@RequestBody RegisterDTO dto) {
        return R.ok(authService.register(dto));
    }

    /** 已登录用户发送换绑邮箱验证码 */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/bind-email/send")
    public R<Void> sendBindEmailCode(@AuthenticationPrincipal LoginUser loginUser,
                                     @RequestBody EmailLoginDTO dto) {
        authService.sendBindCode(loginUser.getUserId(), dto == null ? null : dto.getEmail());
        return R.ok(null);
    }

    /** 已登录用户确认换绑邮箱 */
    @PreAuthorize("isAuthenticated()")
    @SysLog(type = "BIND_EMAIL", content = "换绑邮箱")
    @PostMapping("/bind-email")
    public R<Void> bindEmail(@AuthenticationPrincipal LoginUser loginUser,
                             @RequestBody RegisterDTO dto) {
        authService.bindEmail(loginUser.getUserId(),
                dto == null ? null : dto.getEmail(),
                dto == null ? null : dto.getCode());
        return R.ok(null);
    }

    /** 忘记密码-重置密码：验证码由 /api/auth/email/send 发送（要求邮箱已绑定账号） */
    @PublicApi
    @SysLog(type = "RESET_PWD", content = "重置密码")
    @PostMapping("/forgot/reset")
    public R<Void> resetPassword(@RequestBody RegisterDTO dto) {
        authService.resetPassword(
                dto == null ? null : dto.getEmail(),
                dto == null ? null : dto.getCode(),
                dto == null ? null : dto.getPassword());
        return R.ok(null);
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
