package org.example.backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBlacklistServiceTest {

    private final TokenBlacklistService blacklist = new TokenBlacklistService();

    @Test
    @DisplayName("logout 后的 jti 立即被判定为已拉黑")
    void revokedJtiIsRejected() {
        Date exp = new Date(System.currentTimeMillis() + 3600_000L);

        blacklist.revoke("jti-abc", exp);

        assertThat(blacklist.isRevoked("jti-abc")).isTrue();
        assertThat(blacklist.isRevoked("jti-other")).isFalse();
    }

    @Test
    @DisplayName("已自然过期的 jti 不再拦截")
    void expiredJtiIsNotRejected() {
        blacklist.revoke("jti-old", new Date(System.currentTimeMillis() - 1));

        assertThat(blacklist.isRevoked("jti-old")).isFalse();
    }
}
