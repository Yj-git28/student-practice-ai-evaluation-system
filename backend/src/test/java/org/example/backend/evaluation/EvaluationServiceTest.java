package org.example.backend.evaluation;

import org.example.backend.evaluation.mapper.EvaluationMapper;
import org.example.backend.evaluation.service.EvaluationCacheService;
import org.example.backend.evaluation.service.EvaluationService;

import org.example.backend.security.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock EvaluationMapper mapper;
    @Mock EvaluationCacheService cache;

    private EvaluationService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationService(mapper, cache);
    }

    @Test
    @DisplayName("教师看板命中缓存：直接返回，不再查库")
    void statHitReturnsCachedValue() {
        LoginUser teacher = loginUser(7L, "TEACHER");
        Map<String, Object> cached = new LinkedHashMap<>();
        cached.put("totalStudents", 3);
        cached.put("avgScore", 81.2);

        when(cache.statKey(7L)).thenReturn("eval:stat:teacher:7");
        doReturn(cached).when(cache).get("eval:stat:teacher:7", Map.class);

        assertThat(service.stat(teacher)).isSameAs(cached);
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("未命中：查库计算后按 60 秒 TTL 回写，ADMIN 用全量键")
    void statMissComputesAndCaches() {
        LoginUser admin = loginUser(1L, "ADMIN");
        when(cache.statKey(null)).thenReturn("eval:stat:admin");
        doReturn(null).when(cache).get("eval:stat:admin", Map.class);

        when(mapper.countStudents(null)).thenReturn(10);
        when(mapper.countSubmitted(null)).thenReturn(6);
        when(mapper.avgScore(null)).thenReturn(Optional.of(78.44));

        Map<String, Object> result = service.stat(admin);

        assertThat(result.get("totalStudents")).isEqualTo(10);
        assertThat(result.get("submitted")).isEqualTo(6);
        assertThat(result.get("completionRate")).isEqualTo(60.0);
        assertThat(result.get("avgScore")).isEqualTo(78.4);
        verify(cache).put(eq("eval:stat:admin"), any(), eq(EvaluationCacheService.STAT_TTL));
    }

    @Test
    @DisplayName("AI 初评缓存键带上实训、学生与文件摘要，同一份文件可复用")
    void aiKeyContainsTrainingUserAndDigest() {
        EvaluationCacheService c = new EvaluationCacheService(mock(StringRedisTemplate.class), mock(ObjectMapper.class));
        assertThat(c.aiKey(12L, 34L, "abc123")).isEqualTo("ai:score:12:34:abc123");
        assertThat(c.statKey(null)).isEqualTo("eval:stat:admin");
        assertThat(c.statKey(9L)).isEqualTo("eval:stat:teacher:9");
    }

    private LoginUser loginUser(Long userId, String roleCode) {
        LoginUser u = new LoginUser();
        u.setUserId(userId);
        u.setUsername("u" + userId);
        u.setRoleCode(roleCode);
        return u;
    }
}
