package org.example.backend.security;
import org.example.backend.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;

/**
 * token 黑名单：只存 jti，不存整段 token。
 * Redis 实现：key = auth:jti:{jti}，TTL = 该 token 的剩余有效期，
 * 到期由 Redis 自动清理，不再需要手工 purge。
 *
 * 降级策略：这是安全状态，不做降级，一律 fail-closed ——
 *   isRevoked 查不到结果时按“已拉黑”处理，宁可把请求拒成 401 也不放行可疑 token；
 *   revoke 写失败时抛业务异常，让调用方明确感知“退出登录未生效”。
 */
@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "auth:jti:";

    private final StringRedisTemplate redis;
    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    public TokenBlacklistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 退出登录时把当前 token 的 jti 拉黑，有效期到 token 自然过期为止 */
    public void revoke(String jti, Date expiresAt) {
        if (jti == null || jti.isBlank()) return;
        long ttlMillis = (expiresAt == null ? 0 : expiresAt.getTime() - System.currentTimeMillis());
        if (ttlMillis <= 0) return;                 // token 本身已过期，无需再记
        try {
            redis.opsForValue().set(key(jti), "1", Duration.ofMillis(ttlMillis));
        } catch (RuntimeException e) {
            // fail-closed：黑名单写不进去就等于退出登录没生效，必须让用户知道
            log.error("写入 token 黑名单失败，退出登录未生效 jti={} : {}", jti, e.toString());
            throw new BizException(500, "退出登录失败：认证服务暂不可用，请稍后重试");
        }
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) return false;
        try {
            return Boolean.TRUE.equals(redis.hasKey(key(jti)));
        } catch (RuntimeException e) {
            // fail-closed：无法确认黑名单状态时按“已拉黑”处理，拒绝该请求
            log.error("查询 token 黑名单失败，按 fail-closed 拒绝 jti={} : {}", jti, e.toString());
            return true;
        }
    }

    /** 仅测试用：清空本命名空间的黑名单 */
    public void clear() {
        var keys = redis.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private String key(String jti) {
        return KEY_PREFIX + jti;
    }
}
