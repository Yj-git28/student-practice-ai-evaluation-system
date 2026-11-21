package org.example.backend.mq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Redis Stream 队列的单元测试：投递、消费组幂等创建、死信 */
@ExtendWith(MockitoExtension.class)
class RedisStreamQueueTest {

    @Mock
    StringRedisTemplate redis;

    @Mock
    StreamOperations<String, Object, Object> ops;

    private RedisStreamQueue queue;

    @BeforeEach
    void setUp() throws Exception {
        queue = new RedisStreamQueue(redis);
        setField(queue, "streamKey", "mq:ai:score");
        setField(queue, "group", "cg:ai");
        lenient().when(redis.opsForStream()).thenReturn(ops);
    }

    @Test
    @DisplayName("publish 把字段原样写进 mq:ai:score，并返回消息 ID")
    void publishWritesPayloadAndReturnsRecordId() {
        when(ops.add(any(MapRecord.class))).thenReturn(RecordId.of("1795254800000-0"));

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("resultId", "6");
        payload.put("trainingId", "1");
        payload.put("userId", "15");
        String id = queue.publish(payload);

        ArgumentCaptor<MapRecord<String, Object, Object>> captor = ArgumentCaptor.forClass(MapRecord.class);
        verify(ops).add(captor.capture());
        MapRecord<String, Object, Object> record = captor.getValue();
        assertThat(record.getStream()).isEqualTo("mq:ai:score");
        assertThat(record.getValue()).containsEntry("resultId", "6")
                .containsEntry("trainingId", "1")
                .containsEntry("userId", "15");
        assertThat(id).isEqualTo("1795254800000-0");
    }

    @Test
    @DisplayName("消费组已存在（BUSYGROUP）时 ensureGroup 视为成功，不抛异常")
    void ensureGroupIsIdempotent() {
        when(redis.hasKey("mq:ai:score")).thenReturn(true);
        when(ops.createGroup(any(), any(ReadOffset.class), any()))
                .thenThrow(new RuntimeException(
                        "ERR BUSYGROUP Consumer Group name already exists"));

        assertThat(queue.ensureGroup()).isTrue();
    }

    @Test
    @DisplayName("BUSYGROUP 被 Spring 包在 cause 里时同样视为组已存在")
    void ensureGroupIsIdempotentWhenBusyGroupWrapped() {
        when(redis.hasKey("mq:ai:score")).thenReturn(true);
        RuntimeException core = new RuntimeException(
                "ERR BUSYGROUP Consumer Group name already exists");
        when(ops.createGroup(any(), any(ReadOffset.class), any()))
                .thenThrow(new RuntimeException("Error in execution", core));

        assertThat(queue.ensureGroup()).isTrue();
    }

    @Test
    @DisplayName("队列 key 还不存在时 ensureGroup 静默返回 false，不去建组报错")
    void ensureGroupWaitsForStream() {
        when(redis.hasKey("mq:ai:score")).thenReturn(false);

        assertThat(queue.ensureGroup()).isFalse();
        verify(ops, never()).createGroup(any(), any(ReadOffset.class), any());
    }

    @Test
    @DisplayName("其他 Redis 错误不能被当成「组已存在」吞掉")
    void ensureGroupPropagatesRealErrors() {
        when(redis.hasKey("mq:ai:score")).thenReturn(true);
        when(ops.createGroup(any(), any(ReadOffset.class), any()))
                .thenThrow(new RuntimeException("NOAUTH Authentication required"));

        assertThatThrownBy(() -> queue.ensureGroup())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("NOAUTH");
    }

    @Test
    @DisplayName("死信投递落到 mq:ai:score:dead，并携带失败原因")
    void deadLetterGoesToDeadKey() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("resultId", "6");

        queue.deadLetter(payload, "文件不存在");

        ArgumentCaptor<MapRecord<String, Object, Object>> captor = ArgumentCaptor.forClass(MapRecord.class);
        verify(ops).add(captor.capture());
        MapRecord<String, Object, Object> record = captor.getValue();
        assertThat(record.getStream()).isEqualTo("mq:ai:score:dead");
        assertThat(record.getValue()).containsEntry("failedReason", "文件不存在");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
