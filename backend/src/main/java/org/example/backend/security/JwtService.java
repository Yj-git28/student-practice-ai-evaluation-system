package org.example.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expireSeconds;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expire-seconds}") long expireSeconds) {
        // HS256 要求 key >= 32 字节，secret 太短会直接抛 WeakKeyException
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    /**
     * 签发 token。写入 jti（JWT ID）：logout 时把这个 jti 拉黑，
     * 同一个 token 后续请求在 JwtAuthFilter 里直接被拒。
     */
    public String createToken(Long userId, String username, String roleCode) {
        return createTokenWithId(userId, username, roleCode).token();
    }

    /** 签发结果：token 原文 + jti（单设备登录需要把 jti 写入库） */
    public record IssuedToken(String token, String jti) {
    }

    /** 签发 token，同时返回 jti，供单设备登录绑定当前会话 */
    public IssuedToken createTokenWithId(Long userId, String username, String roleCode) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireSeconds * 1000);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", roleCode)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti);
    }

    /** 解析失败（签名不对/过期/格式错）会抛 JwtException，调用方统一按 401 处理 */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}
