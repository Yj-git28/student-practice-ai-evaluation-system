package org.example.backend.auth;

import org.example.backend.common.BizException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 登录失败限制：同一账号连续 5 次密码错误，锁定 15 分钟。
 * 内存实现；多实例部署时换 Redis（key = login:fail:{username}，TTL 15 分钟）。
 */
@Service
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    public static final long LOCK_MILLIS = 15 * 60 * 1000L;

    private record Attempt(int count, long lockUntil) {
        boolean locked(long now) { return lockUntil > now; }
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public LoginAttemptService() {
        this(System::currentTimeMillis);
    }

    LoginAttemptService(LongSupplier clock) {
        this.clock = clock;
    }

    /** 登录前先看是否处于锁定期，命中直接拒绝（不查库，避免继续被打） */
    public void assertNotLocked(String username) {
        Attempt a = attempts.get(username);
        if (a == null) return;
        long now = clock.getAsLong();
        if (a.locked(now)) {
            long remainMinutes = Math.max(1, (a.lockUntil() - now + 59_999) / 60_000);
            throw new BizException(4429, "登录失败次数过多，账号已锁定，请 " + remainMinutes + " 分钟后再试");
        }
        if (a.lockUntil() > 0 && !a.locked(now)) {
            attempts.remove(username);   // 锁定期已过，重新计数
        }
    }

    /**
     * 记录一次失败。返回累计失败次数；第 5 次失败时写入 15 分钟锁定。
     */
    public int onFailure(String username) {
        long now = clock.getAsLong();
        Attempt old = attempts.get(username);
        int count = (old == null ? 0 : old.count()) + 1;
        long lockUntil = count >= MAX_ATTEMPTS ? now + LOCK_MILLIS : 0;
        attempts.put(username, new Attempt(count, lockUntil));
        return count;
    }

    public boolean isLockedNow(String username) {
        Attempt a = attempts.get(username);
        return a != null && a.locked(clock.getAsLong());
    }

    public void onSuccess(String username) {
        attempts.remove(username);
    }

    /** 仅测试用 */
    public void clear() {
        attempts.clear();
    }
}
