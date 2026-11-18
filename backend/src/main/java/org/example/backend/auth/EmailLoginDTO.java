package org.example.backend.auth;

/**
 * 邮箱验证码登录入参。
 * 发送验证码接口只用 email；登录接口用 email + code。
 */
public class EmailLoginDTO {
    private String email;
    private String code;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
