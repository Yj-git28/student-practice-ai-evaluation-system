package org.example.backend.auth;

import org.example.backend.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 登录失败限制：同一账号连续 5 次密码错误，锁定 15 分钟。
 * Redis 实现：
 *   login:fail:{username}  ——失败计数，TTL 15 分钟
 *   login:lock:{username}  ——锁标记，TTL 15 分钟
 * 计数与过期时间都由 Redis 维护，应用重启不会丢；多实例部署时天然共享。
 *
 * 降级策略：锁定状态属于安全状态，fail-closed ——
 *   查询锁定状态失败时拒绝本次登录（返回业务错误，而不是放行）；
 *   失败计数 / 成功清零属于尽力而为，写失败只记日志，不阻断登录流程。
 */
@Service
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    public static final long LOCK_MILLIS = 15 * 60 * 1000L;

    private static final String FAIL_PREFIX = "login:fail:";
    private static final String LOCK_PREFIX = "login:lock:";

    private final StringRedisTemplate redis;
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    public LoginAttemptService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 登录前先看是否处于锁定期，命中直接拒绝（不查库，避免继续被打） */
    public void assertNotLocked(String username) {
        Long ttl;
        try {
            ttl = redis.getExpire(lockKey(username), TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            // fail-closed：查不到锁定状态就不放行，避免 Redis 故障期间限流被绕过
            log.error("查询登录锁定状态失败，拒绝本次登录 username={} : {}", username, e.toString());
            throw new BizException(500, "登录服务暂不可用，请稍后重试");
        }
        if (ttl == null || ttl <= 0) return;
        long remainMinutes = Math.max(1, (ttl + 59_999) / 60_000);
        throw new BizException(4429, "登录失败次数过多，账号已锁定，请 " + remainMinutes + " 分钟后再试");
    }

    /**
     * 记录一次失败。返回累计失败次数；第 5 次失败时写入 15 分钟锁定。
     * Redis 异常时按“第 1 次”返回，绝不谎报已锁定。
     */
    public int onFailure(String username) {
        String failKey = failKey(username);
        try {
            Long count = redis.opsForValue().increment(failKey);
            int attempts = count == null ? 1 : count.intValue();
            if (attempts == 1) {
                // 新窗口：第一次失败时种下过期时间，避免计数 key 永久残留
                redis.expire(failKey, Duration.ofMillis(LOCK_MILLIS));
            }
            if (attempts >= MAX_ATTEMPTS) {
                redis.opsForValue().set(lockKey(username), "1", Duration.ofMillis(LOCK_MILLIS));
            }
            return attempts;
        } catch (RuntimeException e) {
            log.error("记录登录失败次数失败，本次计数丢失 username={} : {}", username, e.toString());
            return 1;
        }
    }

    public boolean isLockedNow(String username) {
        try {
            Long ttl = redis.getExpire(lockKey(username), TimeUnit.MILLISECONDS);
            return ttl != null && ttl > 0;
        } catch (RuntimeException e) {
            // 与 assertNotLocked 保持一致：查不了就当已锁定
            log.error("查询锁定标记失败，按已锁定处理 username={} : {}", username, e.toString());
            return true;
        }
    }

    public void onSuccess(String username) {
        try {
            redis.delete(failKey(username));
        } catch (RuntimeException e) {
            // 清零失败最多让旧计数残留到 15 分钟 TTL 到期，不阻断登录
            log.warn("清除登录失败计数失败，等待 TTL 兜底 username={} : {}", username, e.toString());
        }
    }

    /** 仅测试用：清空本命名空间的计数与锁定 */
    public void clear() {
        var failKeys = redis.keys(FAIL_PREFIX + "*");
        if (failKeys != null && !failKeys.isEmpty()) redis.delete(failKeys);
        var lockKeys = redis.keys(LOCK_PREFIX + "*");
        if (lockKeys != null && !lockKeys.isEmpty()) redis.delete(lockKeys);
    }

    private String failKey(String username) {
        return FAIL_PREFIX + username;
    }

    private String lockKey(String username) {
        return LOCK_PREFIX + username;
    }
}
