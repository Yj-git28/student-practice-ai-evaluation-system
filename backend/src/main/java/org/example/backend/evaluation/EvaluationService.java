package org.example.backend.evaluation;

import org.example.backend.security.LoginUser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EvaluationService {

    private final EvaluationMapper mapper;
    private final EvaluationCacheService cache;

    public EvaluationService(EvaluationMapper mapper, EvaluationCacheService cache) {
        this.mapper = mapper;
        this.cache = cache;
    }

    /** 教师只统计自己发布的实训；ADMIN 全量 */
    public Map<String, Object> stat(LoginUser user) {
        Long teacherId = "TEACHER".equals(user.getRoleCode()) ? user.getUserId() : null;

        String key = cache.statKey(teacherId);
        @SuppressWarnings("unchecked")
        Map<String, Object> cached = (Map<String, Object>) cache.get(key, Map.class);
        if (cached != null) {
            return cached;
        }

        int totalStudents = mapper.countStudents(teacherId);
        int submitted = mapper.countSubmitted(teacherId);
        double completionRate = totalStudents == 0 ? 0.0 : submitted * 100.0 / totalStudents;
        double avgScore = round1(mapper.avgScore(teacherId).orElse(0.0));

        List<Map<String, Object>> indicators = new ArrayList<>();
        indicators.add(indicator("代码质量", avgScore - 1.5));
        indicators.add(indicator("文档规范", avgScore + 0.8));
        indicators.add(indicator("功能实现", avgScore + 1.2));
        indicators.add(indicator("步骤完整", avgScore - 0.7));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalStudents", totalStudents);
        result.put("submitted", submitted);
        result.put("completionRate", round1(completionRate));
        result.put("avgScore", avgScore);
        result.put("indicators", indicators);

        cache.put(key, result, EvaluationCacheService.STAT_TTL);
        return result;
    }

    private Map<String, Object> indicator(String name, double score) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("score", round1(score));
        return item;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
