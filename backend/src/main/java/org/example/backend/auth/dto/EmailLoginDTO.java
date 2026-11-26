package org.example.backend.auth.dto;

/**
 * 邮箱验证码相关入参。
 * - 发送验证码接口：email + captchaId + captchaAnswer（必须先过人机验证）
 * - 登录/注册接口：email + code
 */
public class EmailLoginDTO {
    private String email;
    private String code;
    /** 人机验证码标识（GET /api/auth/captcha 返回） */
    private String captchaId;
    /** 用户填写的 4 位图形验证码 */
    private String captchaAnswer;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
    public String getCaptchaAnswer() { return captchaAnswer; }
    public void setCaptchaAnswer(String captchaAnswer) { this.captchaAnswer = captchaAnswer; }
}
