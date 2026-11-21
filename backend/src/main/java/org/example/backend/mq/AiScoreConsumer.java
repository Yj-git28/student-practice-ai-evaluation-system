package org.example.backend.mq;

import org.example.backend.training.TrainingResultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * AI 初评消息消费者。
 *
 * 两个循环：
 *   pollOnce      —— 每 2 秒拉一批新消息（最多 5 条），阻塞最多 1.5 秒
 *   reclaimStale  —— 每 30 秒认领"超过 60 秒仍未 ACK"的消息（处理进程崩溃时靠它捞回来）
 *
 * 失败处理：
 *   失败不 ACK，消息留在 PENDING，靠 reclaimStale 重投；
 *   连续失败达到 app.mq.max-attempts（默认 3）次 → 转死信 mq:ai:score:dead 并 ACK，避免无限重试。
 */
@Component
public class AiScoreConsumer {

    private static final Logger log = LoggerFactory.getLogger(AiScoreConsumer.class);
    private static final String ATTEMPT_PREFIX = "mq:attempt:";

    private final RedisStreamQueue queue;
    private final TrainingResultService results;
    private final StringRedisTemplate redis;

    @Value("${app.mq.enabled:true}")
    private boolean enabled;

    @Value("${app.mq.consumer:worker-1}")
    private String consumer;

    @Value("${app.mq.max-attempts:3}")
    private int maxAttempts;

    @Value("${app.mq.claim-idle-ms:60000}")
    private long claimIdleMs;

    private volatile boolean groupReady = false;

    public AiScoreConsumer(RedisStreamQueue queue, TrainingResultService results, StringRedisTemplate redis) {
        this.queue = queue;
        this.results = results;
        this.redis = redis;
    }

    @Scheduled(fixedDelayString = "${app.mq.poll-interval-ms:2000}")
    public void pollOnce() {
        if (!enabled || !ensureGroupOnce()) {
            return;
        }
        try {
            List<MapRecord<String, Object, Object>> records = queue.poll(consumer, 5, Duration.ofMillis(1500));
            for (MapRecord<String, Object, Object> record : records) {
                handle(record);
            }
        } catch (Exception e) {
            // Redis 不可用时这里会抛，属可降级路径：不抛给上层，等下次轮询
            log.warn("消息轮询失败，等待下一轮: {}", e.toString());
        }
    }

    @Scheduled(fixedDelayString = "${app.mq.reclaim-interval-ms:30000}")
    public void reclaimStale() {
        if (!enabled || !ensureGroupOnce()) {
            return;
        }
        try {
            List<MapRecord<String, Object, Object>> stale = queue.claimStale(Duration.ofMillis(claimIdleMs), 20);
            for (MapRecord<String, Object, Object> record : stale) {
                log.warn("认领超时未确认的消息并重试 recordId={}", record.getId().getValue());
                handle(record);
            }
        } catch (Exception e) {
            log.warn("认领超时消息失败，等待下一轮: {}", e.toString());
        }
    }

    private boolean ensureGroupOnce() {
        if (groupReady) {
            return true;
        }
        try {
            groupReady = queue.ensureGroup();   // 队列还没创建时返回 false，下一轮再试
            return groupReady;
        } catch (Exception e) {
            log.warn("创建消费组失败，下一轮重试: {}", e.toString());
            return false;
        }
    }

    private void handle(MapRecord<String, Object, Object> record) {
        String recordId = record.getId().getValue();
        Map<String, String> body = stringify(record.getValue());
        String attemptKey = ATTEMPT_PREFIX + recordId;
        try {
            results.applyAiScore(
                    toLong(body.get("resultId")),
                    toLong(body.get("trainingId")),
                    toLong(body.get("userId")),
                    body.get("username"),
                    body.get("fileName"),
                    body.get("filePath"));
            queue.acknowledge(recordId);
            redis.delete(attemptKey);
            log.info("AI 初评完成 resultId={} recordId={}", body.get("resultId"), recordId);
        } catch (Exception e) {
            long attempts = bumpAttempt(attemptKey);
            if (attempts >= maxAttempts) {
                queue.deadLetter(body, e.getMessage());
                queue.acknowledge(recordId);
                log.error("AI 初评连续 {} 次失败，已转死信队列 resultId={} recordId={} : {}",
                        attempts, body.get("resultId"), recordId, e.toString());
            } else {
                log.warn("AI 初评失败第 {} 次，消息保留在 PENDING 等待重投 resultId={} : {}",
                        attempts, body.get("resultId"), e.toString());
            }
        }
    }

    private long bumpAttempt(String attemptKey) {
        try {
            Long count = redis.opsForValue().increment(attemptKey);
            redis.expire(attemptKey, Duration.ofHours(1));
            return count == null ? 1 : count;
        } catch (Exception ignored) {
            return 1;
        }
    }

    private Long toLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** stream 的 value 是 Object，统一转成字符串再解析 */
    private Map<String, String> stringify(Map<Object, Object> raw) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            out.put(String.valueOf(entry.getKey()), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return out;
    }
}
