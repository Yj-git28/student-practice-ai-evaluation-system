package org.example.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    private TokenBlacklistService blacklist;

    @BeforeEach
    void setUp() {
        blacklist = new TokenBlacklistService(redis);
    }

    @Test
    @DisplayName("revoke 写入 auth:jti:{jti}，TTL 等于 token 剩余有效期")
    void revokeWritesKeyWithRemainingTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);

        Date expiresAt = new Date(System.currentTimeMillis() + 3600_000L);
        blacklist.revoke("jti-abc", expiresAt);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(eq("auth:jti:jti-abc"), eq("1"), ttl.capture());
        // 剩余有效期应当接近 1 小时（容忍执行耗时）
        assertThat(ttl.getValue().toSeconds()).isBetween(3500L, 3600L);
    }

    @Test
    @DisplayName("已自然过期的 token 不写黑名单")
    void expiredJtiIsNotRejected() {
        blacklist.revoke("jti-old", new Date(System.currentTimeMillis() - 1_000L));

        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("isRevoked 直接查 Redis key 是否存在")
    void revokedJtiIsRejected() {
        when(redis.hasKey("auth:jti:jti-abc")).thenReturn(true);
        when(redis.hasKey("auth:jti:jti-other")).thenReturn(false);

        assertThat(blacklist.isRevoked("jti-abc")).isTrue();
        assertThat(blacklist.isRevoked("jti-other")).isFalse();
        assertThat(blacklist.isRevoked(null)).isFalse();
    }
}
