package org.example.backend.auth.service;
import org.example.backend.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 图形人机验证（发送邮箱验证码前必须先过这一关）。
 *
 * 设计要点：
 *   1. 答案存 Redis，key=captcha:{id}，TTL 默认 120 秒 —— 天然一次性、跨实例共享；
 *   2. 校验时不区分大小写，且无论成败都立即删除 key —— 同一个验证码不能被重试刷；
 *   3. Redis 不可用时 fail-closed：直接拒绝发码，绝不放行人机验证（与安全态策略一致）；
 *   4. 图片用 SVG 生成（无需读写字体文件），前端以 data URI 直接 img 显示，点击即刷新。
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);
    private static final String KEY_PREFIX = "captcha:";
    /** 去掉容易混淆的 0/O/1/I/Z */
    private static final char[] POOL = "ABCDEFGHJKLMNPQRSTUVWXY23456789".toCharArray();
    private static final int LENGTH = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final long ttlSeconds;

    public CaptchaService(StringRedisTemplate redis,
                          @Value("${app.captcha.ttl-seconds:120}") long ttlSeconds) {
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
    }

    /** 生成一个验证码：返回 captchaId 与可直接放进 <img src> 的 data URI */
    public Map<String, String> create() {
        String id = UUID.randomUUID().toString().replace("-", "");
        String answer = randomAnswer();
        try {
            redis.opsForValue().set(KEY_PREFIX + id, answer, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.error("人机验证码写入 Redis 失败，按 fail-closed 拒绝发码: {}", e.toString());
            throw new BizException(500, "人机验证服务暂不可用，请稍后重试");
        }
        Map<String, String> out = new LinkedHashMap<>();
        out.put("captchaId", id);
        out.put("image", toDataUri(answer));
        return out;
    }

    /**
     * 校验并消费验证码。
     * @return true=通过；false=错误/过期/已用过
     * @throws BizException 500 Redis 不可用（fail-closed）
     */
    public boolean verify(String captchaId, String answer) {
        if (captchaId == null || captchaId.isBlank() || answer == null || answer.isBlank()) {
            return false;
        }
        String key = KEY_PREFIX + captchaId;
        String expected;
        try {
            expected = redis.opsForValue().get(key);
        } catch (Exception e) {
            log.error("人机验证码读取失败，按 fail-closed 拒绝: {}", e.toString());
            throw new BizException(500, "人机验证服务暂不可用，请稍后重试");
        }
        // 无论对错立即删掉：一个验证码只能试一次，防止暴力枚举
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("删除已用验证码失败（key 会自然过期）: {}", e.toString());
        }
        return expected != null && expected.equalsIgnoreCase(answer.trim());
    }

    private String randomAnswer() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(POOL[RANDOM.nextInt(POOL.length)]);
        }
        return sb.toString();
    }

    /** 把 4 个字符画成带旋转/噪点/干扰线的 SVG，再转成 data URI */
    private String toDataUri(String answer) {
        int width = 120;
        int height = 44;
        StringBuilder svg = new StringBuilder(1024);
        svg.append("<svg xmlns='http://www.w3.org/2000/svg' width='").append(width)
                .append("' height='").append(height).append("' viewBox='0 0 ")
                .append(width).append(' ').append(height).append("'>");
        svg.append("<rect width='100%' height='100%' fill='#f4f6fb'/>");

        // 干扰线
        for (int i = 0; i < 3; i++) {
            svg.append("<line x1='").append(RANDOM.nextInt(width)).append("' y1='").append(RANDOM.nextInt(height))
                    .append("' x2='").append(RANDOM.nextInt(width)).append("' y2='").append(RANDOM.nextInt(height))
                    .append("' stroke='").append(randomColor(0.55)).append("' stroke-width='1'/>");
        }
        // 噪点
        for (int i = 0; i < 24; i++) {
            svg.append("<circle cx='").append(RANDOM.nextInt(width)).append("' cy='").append(RANDOM.nextInt(height))
                    .append("' r='1' fill='").append(randomColor(0.6)).append("'/>");
        }
        // 字符
        for (int i = 0; i < answer.length(); i++) {
            int x = 14 + i * 25 + RANDOM.nextInt(4);
            int y = 30 + RANDOM.nextInt(6);
            int rotate = RANDOM.nextInt(46) - 23;
            svg.append("<text x='").append(x).append("' y='").append(y)
                    .append("' font-family='Arial,Helvetica,sans-serif' font-size='26' font-weight='bold'")
                    .append(" fill='").append(randomColor(0.35))
                    .append("' transform='rotate(").append(rotate).append(' ').append(x).append(' ').append(y).append(")'>")
                    .append(answer.charAt(i))
                    .append("</text>");
        }
        svg.append("</svg>");
        String base64 = Base64.getEncoder().encodeToString(
                svg.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + base64;
    }

    /** 生成偏深的随机颜色，保证在浅底上看得清 */
    private String randomColor(double brightness) {
        int base = (int) (255 * brightness);
        int r = base + RANDOM.nextInt(60);
        int g = base + RANDOM.nextInt(60);
        int b = base + RANDOM.nextInt(60);
        return String.format("#%02x%02x%02x", Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
    }
}
