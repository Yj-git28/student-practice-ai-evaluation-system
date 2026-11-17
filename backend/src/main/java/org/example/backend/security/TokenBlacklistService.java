package org.example.backend.security;

import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * token 黑名单：只存 jti，不存整段 token。
 * 单机内存实现；多实例部署时把这里换成 Redis（key = jti，TTL = token 剩余有效期）。
 */
@Service
public class TokenBlacklistService {

    /** jti -> token 过期时间（毫秒） */
    private final Map<String, Long> revoked = new ConcurrentHashMap<>();

    /** 退出登录时把当前 token 的 jti 拉黑，有效期到 token 自然过期为止 */
    public void revoke(String jti, Date expiresAt) {
        if (jti == null || jti.isBlank()) return;
        long exp = expiresAt == null ? System.currentTimeMillis() : expiresAt.getTime();
        revoked.put(jti, exp);
        purgeExpired();
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) return false;
        Long exp = revoked.get(jti);
        if (exp == null) return false;
        if (exp <= System.currentTimeMillis()) {
            revoked.remove(jti);      // 已自然过期，不需要再记
            return false;
        }
        return true;
    }

    /** 避免长期运行后 Map 无限增长 */
    private void purgeExpired() {
        long now = System.currentTimeMillis();
        revoked.entrySet().removeIf(e -> e.getValue() <= now);
    }

    /** 仅测试用：清空黑名单 */
    public void clear() {
        revoked.clear();
    }
}
