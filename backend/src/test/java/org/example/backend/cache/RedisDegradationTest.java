package org.example.backend.cache;

import org.example.backend.auth.LoginAttemptService;
import org.example.backend.common.BizException;
import org.example.backend.evaluation.EvaluationCacheService;
import org.example.backend.security.LoginUser;
import org.example.backend.security.TokenBlacklistService;
import org.example.backend.system.service.PermissionCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Redis 故障降级策略的单元测试：用一个“所有方法都抛连接异常”的模板模拟 Redis 不可用。
 *
 * 策略分两类：
 *   读缓存（权限、AI 评分、统计看板）：读失败按未命中 → 调用方回落查库/重算，变慢但不报错；
 *   安全状态（黑名单、登录锁定）：fail-closed → 查不到就拒绝，绝不因为 Redis 挂了而放行。
 */
class RedisDegradationTest {

    /** 任何方法调用都抛 RedisConnectionFailureException，等价于 Redis 服务不可用 */
    private static StringRedisTemplate deadRedis() {
        return mock(StringRedisTemplate.class, invocation -> {
            throw new RedisConnectionFailureException("connection refused (simulated)");
        });
    }

    private static final JsonMapper JSON = new JsonMapper();

    @Test
    @DisplayName("统计缓存：Redis 挂了读取按未命中，返回 null 由调用方查库")
    void statCacheGetDegradesToMiss() {
        EvaluationCacheService cache = new EvaluationCacheService(deadRedis(), JSON);
        assertThat(cache.get("eval:stat:admin", Map.class)).isNull();
    }

    @Test
    @DisplayName("统计缓存：写入与清理失败只记日志，不打断提交/复核主流程")
    void statCacheWriteSwallowsFailure() {
        EvaluationCacheService cache = new EvaluationCacheService(deadRedis(), JSON);
        assertThatCode(() -> {
            cache.put("eval:stat:admin", Map.of("totalStudents", 122), Duration.ofSeconds(60));
            cache.evictStats();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("权限缓存：读失败返回 null / 版本号 0，PermissionService 会回落查库重建")
    void permissionCacheGetDegradesToMiss() {
        PermissionCacheService cache = new PermissionCacheService(deadRedis(), JSON);
        assertThat(cache.get(4L)).isNull();
        assertThat(cache.version(4L)).isZero();
    }

    @Test
    @DisplayName("权限缓存：写入与失效失败不打断登录与鉴权")
    void permissionCacheWriteSwallowsFailure() {
        PermissionCacheService cache = new PermissionCacheService(deadRedis(), JSON);
        LoginUser user = new LoginUser();
        assertThatCode(() -> {
            cache.put(4L, user);
            cache.invalidate(4L);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("登录锁定：Redis 挂了 fail-closed，拒绝登录而不是放行")
    void loginLockFailsClosed() {
        LoginAttemptService service = new LoginAttemptService(deadRedis());
        assertThatThrownBy(() -> service.assertNotLocked("admin"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("登录服务暂不可用");
        assertThat(service.isLockedNow("admin")).isTrue();
    }

    @Test
    @DisplayName("失败计数：Redis 挂了按第 1 次返回，绝不谎报“已锁定”")
    void failureCountNeverClaimsLocked() {
        LoginAttemptService service = new LoginAttemptService(deadRedis());
        assertThat(service.onFailure("admin")).isEqualTo(1);
        assertThatCode(() -> service.onSuccess("admin")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("token 黑名单：查不到结果按已拉黑处理，拒绝该请求")
    void blacklistFailsClosed() {
        TokenBlacklistService blacklist = new TokenBlacklistService(deadRedis());
        assertThat(blacklist.isRevoked("jti-abc")).isTrue();
        assertThat(blacklist.isRevoked(null)).isFalse();
    }

    @Test
    @DisplayName("token 黑名单：写不进去等于退出登录没生效，必须抛错告知调用方")
    void blacklistRevokeFailsLoudly() {
        TokenBlacklistService blacklist = new TokenBlacklistService(deadRedis());
        Date expiresAt = new Date(System.currentTimeMillis() + 3_600_000L);
        assertThatThrownBy(() -> blacklist.revoke("jti-abc", expiresAt))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("退出登录失败");
    }
}
