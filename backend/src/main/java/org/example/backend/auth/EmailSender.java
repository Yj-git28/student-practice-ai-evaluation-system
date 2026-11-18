package org.example.backend.auth;

import jakarta.mail.internet.MimeMessage;
import org.example.backend.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 邮件发送组件。
 *
 * 策略：
 * - spring.mail.host / username / password / app.mail.from 四项全填 → 用 JavaMailSender 真实发送
 * - 缺任意一项 → mock 模式，验证码输出到后端日志（[EMAIL-MOCK]），方便本地开发
 *
 * 真实发送失败时直接抛 BizException，不会静默降级到 mock。
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${spring.mail.password:}")
    private String password;

    @Value("${app.mail.from:}")
    private String from;

    public EmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(String email, String code) {
        if (isRealConfigured()) {
            sendViaSmtp(email, code);
        } else {
            log.info("[EMAIL-MOCK] email={} code={}", email, code);
        }
    }

    private boolean isRealConfigured() {
        return StringUtils.hasText(host)
                && StringUtils.hasText(username)
                && StringUtils.hasText(password)
                && StringUtils.hasText(from);
    }

    private void sendViaSmtp(String email, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("【软件实训AI检查评价系统】登录验证码");
            helper.setText("您的验证码为：<b>" + code + "</b>，5 分钟内有效，请勿泄露。", true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("邮件发送失败 email={}", email, e);
            throw new BizException(500, "邮件发送失败，请稍后重试");
        }
    }
}
