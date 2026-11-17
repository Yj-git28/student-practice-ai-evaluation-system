package org.example.backend.auth;

import org.example.backend.common.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

class LoginAttemptServiceTest {

    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final LoginAttemptService service = new LoginAttemptService(clock::get);

    @Test
    @DisplayName("连续 5 次失败即锁定，锁定期内拒绝登录")
    void locksAfterFiveFailures() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.onFailure("teacher01");
        }

        assertThat(service.isLockedNow("teacher01")).isTrue();
        assertThatThrownBy(() -> service.assertNotLocked("teacher01"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("锁定");
    }

    @Test
    @DisplayName("锁定 15 分钟后自动解除，重新可以登录")
    void unlocksAfterLockDuration() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.onFailure("teacher01");
        }
        clock.addAndGet(LoginAttemptService.LOCK_MILLIS + 1);

        assertThatCode(() -> service.assertNotLocked("teacher01")).doesNotThrowAnyException();
        assertThat(service.isLockedNow("teacher01")).isFalse();
    }

    @Test
    @DisplayName("登录成功后清空失败计数")
    void successResetsCounter() {
        service.onFailure("teacher01");
        service.onSuccess("teacher01");
        service.onFailure("teacher01");

        assertThat(service.isLockedNow("teacher01")).isFalse();
        assertThatCode(() -> service.assertNotLocked("teacher01")).doesNotThrowAnyException();
    }
}
