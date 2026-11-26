package org.example.backend.system.service;
import org.example.backend.security.LoginUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * 用户权限缓存（Redis 实现）。
 *   perm:user:{userId} —— LoginUser 的 JSON，TTL 30 分钟
 *   perm:ver:{userId}  —— 权限版本号，put/invalidate 时自增
 * 原实现用 ConcurrentHashMap，多实例部署时各实例缓存不一致；换成 Redis 后共享同一份。
 *
 * 降级策略：cache.get 读失败按未命中返回 null，调用方 PermissionService 会直接查库重建，
 * 因此 Redis 挂掉时鉴权依旧可用，只是每个请求都要打一次数据库（变慢，但不报错）。
 */
@Service
public class PermissionCacheService {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String USER_PREFIX = "perm:user:";
    private static final String VERSION_PREFIX = "perm:ver:";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private static final Logger log = LoggerFactory.getLogger(PermissionCacheService.class);

    public PermissionCacheService(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public LoginUser get(Long userId) {
        try {
            String raw = redis.opsForValue().get(USER_PREFIX + userId);
            return raw == null ? null : json.readValue(raw, LoginUser.class);
        } catch (RuntimeException e) {
            // 读缓存失败按未命中：调用方回落到查库重建
            log.warn("读取权限缓存失败，按未命中处理 userId={} : {}", userId, e.toString());
            return null;
        }
    }

    public int version(Long userId) {
        try {
            String raw = redis.opsForValue().get(VERSION_PREFIX + userId);
            return raw == null ? 0 : Integer.parseInt(raw);
        } catch (RuntimeException e) {
            log.warn("读取权限版本号失败，按 0 处理 userId={} : {}", userId, e.toString());
            return 0;
        }
    }

    public void put(Long userId, LoginUser user) {
        try {
            redis.opsForValue().increment(VERSION_PREFIX + userId);
            redis.opsForValue().set(USER_PREFIX + userId, json.writeValueAsString(user), TTL);
        } catch (RuntimeException e) {
            // 写缓存失败不影响本次登录/鉴权，下次请求重建即可
            log.warn("写入权限缓存失败，已忽略 userId={} : {}", userId, e.toString());
        }
    }

    /** 只递增版本号并丢弃旧对象 —— 下一次请求会重建 */
    public void invalidate(Long userId) {
        try {
            redis.opsForValue().increment(VERSION_PREFIX + userId);
            redis.delete(USER_PREFIX + userId);
        } catch (RuntimeException e) {
            // 旧对象最长再存活 30 分钟（TTL），权限变更的即时性在此窗口内退化
            log.warn("失效权限缓存失败，等待 TTL 兜底 userId={} : {}", userId, e.toString());
        }
    }
}
