package org.example.backend.evaluation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Collection;

/**
 * AI 初评结果与统计看板的 Redis 缓存。
 *
 *   ai:score:{trainingId}:{userId}:{文件sha256}  AI 初评结果，TTL 24h
 *       同一学生重复提交同一份文件不再重复调用模型；换成真实大模型后这里直接省 token。
 *   eval:stat:{admin|teacher:{id}}              统计看板，TTL 60s
 *       看板是 4 条聚合 SQL，60 秒内的重复刷新直接命中缓存；数据变动时主动 evict。
 *
 * 降级策略：本类只做纯缓存，Redis 不可用时读按“未命中”处理、写与清理直接忽略，
 * 调用方会自然回落到重算 / 直查数据库，功能降级为变慢而不是报错。
 */
@Service
public class EvaluationCacheService {

    public static final Duration AI_TTL = Duration.ofHours(24);
    public static final Duration STAT_TTL = Duration.ofSeconds(60);

    private static final String AI_PREFIX = "ai:score:";
    private static final String STAT_PREFIX = "eval:stat:";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private static final Logger log = LoggerFactory.getLogger(EvaluationCacheService.class);

    public EvaluationCacheService(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public String aiKey(Long trainingId, Long userId, String fileSha256) {
        return AI_PREFIX + trainingId + ":" + userId + ":" + fileSha256;
    }

    /** teacherId 为 null 表示管理员的全量口径 */
    public String statKey(Long teacherId) {
        return STAT_PREFIX + (teacherId == null ? "admin" : "teacher:" + teacherId);
    }

    public <T> T get(String key, Class<T> type) {
        try {
            String raw = redis.opsForValue().get(key);
            return raw == null ? null : json.readValue(raw, type);
        } catch (RuntimeException e) {
            // 读缓存失败一律当作未命中：调用方会回落到重算 / 查库
            log.warn("读取缓存失败，按未命中处理 key={} : {}", key, e.toString());
            return null;
        }
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, json.writeValueAsString(value), ttl);
        } catch (RuntimeException e) {
            log.warn("写入缓存失败，已忽略 key={} : {}", key, e.toString());
        }
    }

    /** 提交、复核等会改变统计口径的操作后调用 */
    public void evictStats() {
        try {
            Collection<String> keys = redis.keys(STAT_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (RuntimeException e) {
            // 清理失败时，看板最迟 60 秒后由 TTL 自然过期
            log.warn("清理统计缓存失败，交由 TTL 兜底 : {}", e.toString());
        }
    }
}
