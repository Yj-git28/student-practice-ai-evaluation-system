package org.example.backend.auth;

import org.example.backend.auth.service.LoginAttemptService;

import org.example.backend.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final String USER = "teacher01";
    private static final String FAIL_KEY = "login:fail:" + USER;
    private static final String LOCK_KEY = "login:lock:" + USER;

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(redis);
    }

    @Test
    @DisplayName("连续 5 次失败：第 1 次种下计数 TTL，第 5 次写入锁定 key")
    void locksAfterFiveFailures() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_KEY)).thenReturn(1L, 2L, 3L, 4L, 5L);

        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.onFailure(USER);
        }

        verify(redis).expire(eq(FAIL_KEY), any(Duration.class));
        verify(valueOps).set(eq(LOCK_KEY), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("锁定期内 assertNotLocked 抛 4429，并给出剩余分钟数")
    void lockedUserIsRejected() {
        when(redis.getExpire(LOCK_KEY, TimeUnit.MILLISECONDS))
                .thenReturn(LoginAttemptService.LOCK_MILLIS);

        assertThatThrownBy(() -> service.assertNotLocked(USER))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("锁定");
    }

    @Test
    @DisplayName("锁定 key 已过期（TTL <= 0）时放行")
    void unlocksAfterLockDuration() {
        when(redis.getExpire(LOCK_KEY, TimeUnit.MILLISECONDS)).thenReturn(-2L);

        assertThatCode(() -> service.assertNotLocked(USER)).doesNotThrowAnyException();
        assertThat(service.isLockedNow(USER)).isFalse();
    }

    @Test
    @DisplayName("登录成功后删除失败计数，重新计数")
    void successResetsCounter() {
        service.onSuccess(USER);

        verify(redis).delete(FAIL_KEY);
        verifyNoInteractions(valueOps);
    }
}
