package org.example.backend.system.service;

import org.example.backend.security.LoginUser;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PermissionCacheService {

    /** userId -> (权限版本号, LoginUser) */
    private record Entry(int version, LoginUser user) {}

    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    public LoginUser get(Long userId) {
        Entry e = cache.get(userId);
        return e == null ? null : e.user();
    }

    public int version(Long userId) {
        Entry e = cache.get(userId);
        return e == null ? 0 : e.version();
    }

    public void put(Long userId, LoginUser user) {
        int v = version(userId) + 1;
        cache.put(userId, new Entry(v, user));
    }

    /** 只递增版本号并丢弃旧对象 —— 下一次请求会重建 */
    public void invalidate(Long userId) {
        Entry e = cache.get(userId);
        if (e == null) {
            cache.put(userId, new Entry(1, null));
        } else {
            cache.put(userId, new Entry(e.version() + 1, null));
        }
    }
}