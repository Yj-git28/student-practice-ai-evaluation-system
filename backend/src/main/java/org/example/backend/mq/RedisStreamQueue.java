package org.example.backend.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis Stream 消息队列的薄封装。
 *
 * key 结构：
 *   mq:ai:score          —— 待办队列（消息体是一组字符串字段）
 *   mq:ai:score:dead     —— 死信队列，连续失败 maxAttempts 次后转入
 *   mq:attempt:{recordId}—— 单条消息的失败次数，TTL 1 小时
 *   cg:ai                —— 消费组，多实例自动分片；未 ACK 的消息留在 PENDING，可被认领重投
 *
 * 与 List（LPUSH/BRPOP）的区别：有 ACK 与 PENDING，进程崩溃时正在处理的消息不会丢。
 */
@Service
public class RedisStreamQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamQueue.class);
    private static final String DEAD_SUFFIX = ":dead";

    private final StringRedisTemplate redis;

    @Value("${app.mq.stream-key:mq:ai:score}")
    private String streamKey;

    @Value("${app.mq.group:cg:ai}")
    private String group;

    public RedisStreamQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String getStreamKey() {
        return streamKey;
    }

    public String getGroup() {
        return group;
    }

    public String getDeadKey() {
        return streamKey + DEAD_SUFFIX;
    }

    /** 投递一条消息，返回 stream 内的消息 ID（如 1795254800000-0） */
    public String publish(Map<String, String> payload) {
        RecordId id = redis.opsForStream()
                .add(StreamRecords.newRecord().in(streamKey).ofMap(payload));
        return id == null ? null : id.getValue();
    }

    /** 转入死信队列，附带失败原因 */
    public void deadLetter(Map<String, String> payload, String reason) {
        Map<String, String> copy = new LinkedHashMap<>(payload);
        copy.put("failedReason", reason == null ? "unknown" : reason.length() > 500 ? reason.substring(0, 500) : reason);
        redis.opsForStream().add(StreamRecords.newRecord().in(getDeadKey()).ofMap(copy));
    }

    /**
     * 确保消费组存在，返回 true 表示可以开始消费。
     * 用 0-0 作为起点，保证"消息先投、组后建"也不会漏处理；
     * 队列 key 还不存在（还没人投递过）时直接返回 false，等有消息再建组，避免刷报错日志。
     */
    public boolean ensureGroup() {
        if (!Boolean.TRUE.equals(redis.hasKey(streamKey))) {
            return false;
        }
        try {
            redis.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), group);
            log.info("Redis Stream 消费组已创建 stream={} group={}", streamKey, group);
            return true;
        } catch (RuntimeException e) {
            // Spring Data 会把 Redis 错误再包一层 "Error in execution"，BUSYGROUP 常落在 cause 链里
            if (causeChainContains(e, "BUSYGROUP")) {
                return true;    // 组已存在：多实例、重启后都属于正常情况
            }
            throw e;
        }
    }

    /** 沿异常 cause 链找关键字，避免漏判被包装过的 Redis 错误 */
    private static boolean causeChainContains(Throwable e, String keyword) {
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains(keyword)) {
                return true;
            }
            Throwable next = cur.getCause();
            if (next == cur) {
                break;
            }
            cur = next;
        }
        return false;
    }

    /** 拉取新消息（阻塞最多 block 毫秒），未 ACK 的消息不会被重复投递给同一个消费者 */
    public List<MapRecord<String, Object, Object>> poll(String consumer, int count, Duration block) {
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                Consumer.from(group, consumer),
                StreamReadOptions.empty().count(count).block(block),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
        return records == null ? List.of() : records;
    }

    /** 确认消费成功，消息从 PENDING 移除 */
    public void acknowledge(String recordId) {
        redis.opsForStream().acknowledge(streamKey, group, recordId);
    }

    /** 当前未确认（处理中/处理失败）的消息数，用于监控 */
    public long pendingCount() {
        PendingMessagesSummary summary = redis.opsForStream().pending(streamKey, group);
        return summary == null ? 0 : summary.getTotalPendingMessages();
    }

    /**
     * 认领"超时未确认"的消息（等价于 XAUTOCLAIM 的效果）。
     * 场景：消费进程处理到一半崩溃，消息会一直留在 PENDING，靠这里捞回来重算。
     */
    public List<MapRecord<String, Object, Object>> claimStale(Duration minIdle, int limit) {
        PendingMessages pendings = redis.opsForStream()
                .pending(streamKey, group, Range.unbounded(), (long) limit);
        if (pendings == null) {
            return List.of();
        }
        List<MapRecord<String, Object, Object>> out = new ArrayList<>();
        for (PendingMessage pm : pendings) {
            Duration idle = pm.getElapsedTimeSinceLastDelivery();
            if (idle == null || idle.compareTo(minIdle) < 0) {
                continue;   // 还在别人的处理窗口内，不抢
            }
            List<MapRecord<String, Object, Object>> body =
                    redis.opsForStream().range(streamKey, Range.just(pm.getIdAsString()));
            if (body != null && !body.isEmpty()) {
                out.add(body.get(0));
            }
        }
        return out;
    }
}
