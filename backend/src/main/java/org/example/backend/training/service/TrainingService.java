package org.example.backend.training.service;

import org.example.backend.training.dto.TrainingSaveDTO;
import org.example.backend.training.mapper.TrainingMapper;
import org.example.backend.common.BizException;
import org.example.backend.evaluation.service.EvaluationCacheService;
import org.example.backend.security.LoginUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TrainingService {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATETIME_T = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final TrainingMapper mapper;
    private final JdbcClient jdbc;
    private final EvaluationCacheService cache;

    public TrainingService(TrainingMapper mapper, JdbcClient jdbc, EvaluationCacheService cache) {
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.cache = cache;
    }

    /** 发布：教师只能发布自己的实训；ADMIN/公共实训(teacher_id IS NULL) 也能发布 */
    public void publish(Long trainingId, Long teacherId, String roleCode) {
        if ("ADMIN".equals(roleCode)) {
            if (!mapper.exists(trainingId)) {
                throw new BizException(4031, "实训不存在或无权操作");
            }
        } else if (!mapper.existsOwnedBy(trainingId, teacherId)) {
            throw new BizException(4031, "实训不存在或无权操作");
        }
        mapper.publish(trainingId);
    }

    /** 教师口径统计；teacherId 为 null 时统计全量（ADMIN） */
    public Map<String, Object> stat(Long teacherId) {
        return mapper.stat(teacherId);
    }

    /** 分页：
     * - STUDENT 只看已发布 + scope_type 匹配自己年级的
     * - TEACHER 只看自己的（teacher_id=自己）
     * - ADMIN 全量
     */
    public Map<String, Object> page(LoginUser user, int page, int size, String keyword, Integer status) {
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 10;

        String studentGrade = null;
        Long teacherId = null;

        switch (user.getRoleCode()) {
            case "STUDENT" -> studentGrade = gradeOf(user.getClassId());
            case "TEACHER" -> teacherId = user.getUserId();
            default -> { /* ADMIN / 其他：全量 */ }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", mapper.count(teacherId, studentGrade, keyword, studentGrade != null ? null : status));
        result.put("page", page);
        result.put("size", size);
        result.put("list", mapper.page(teacherId, studentGrade, keyword, studentGrade != null ? null : status, page, size));
        return result;
    }

    public Map<String, Object> detail(Long trainingId, LoginUser user) {
        String studentGrade = "STUDENT".equals(user.getRoleCode()) ? gradeOf(user.getClassId()) : null;
        return mapper.detail(trainingId, studentGrade)
                .orElseThrow(() -> new BizException(4031, "实训不存在或无权操作"));
    }

    /** 查 class.grade，查不到返回 null（防御性：class_id 可能为空或班级被删） */
    private String gradeOf(Long classId) {
        if (classId == null) return null;
        return jdbc.sql("SELECT grade FROM class WHERE class_id = :cid")
                .param("cid", classId).query(String.class).optional().orElse(null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(TrainingSaveDTO dto, LoginUser user) {
        normalize(dto);
        Long teacherId;
        int scopeType = dto.getScopeType() == null ? 1 : dto.getScopeType();

        if ("ADMIN".equals(user.getRoleCode()) && scopeType != 1) {
            // 管理员发布公共实训（年级/全校）：teacher_id 留空
            teacherId = null;
        } else {
            // 教师创建 / 管理员创建教师专属：归属当前教师
            teacherId = user.getUserId();
            dto.setScopeType(1);
        }
        mapper.insert(dto, teacherId);
        return mapper.lastInsertId();
    }

    public void update(Long trainingId, TrainingSaveDTO dto, LoginUser user) {
        boolean admin = "ADMIN".equals(user.getRoleCode());
        boolean allowed;
        if (admin) {
            allowed = mapper.exists(trainingId);
        } else {
            // 教师只能改自己的
            allowed = mapper.existsOwnedBy(trainingId, user.getUserId());
        }
        if (!allowed) {
            throw new BizException(4031, "实训不存在或无权操作");
        }
        normalize(dto);
        mapper.update(trainingId, dto);
    }

    /** 删除实训并级联删除成果；ADMIN 可删全部，教师只能删自己的 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long trainingId, LoginUser user) {
        boolean admin = "ADMIN".equals(user.getRoleCode());
        if (!admin && !mapper.existsOwnedBy(trainingId, user.getUserId())) {
            throw new BizException(4031, "实训不存在或无权操作");
        }
        if (!admin && !mapper.existsOwnedBy(trainingId, user.getUserId())) {
            // 双重检查（公共实训 teacher_id IS NULL 时，ADMIN 已在上面通过 exists 放行；这里再走一遍）
        }
        mapper.deleteResults(trainingId);
        mapper.delete(trainingId);
        cache.evictStats();          // 删实训连带删提交记录，看板缓存作废
    }

    /** 名称必填；scope_type=2 时 scope_value 必须是有效年级；时间统一格式化 */
    private void normalize(TrainingSaveDTO dto) {
        String name = dto.getTrainingName() == null ? "" : dto.getTrainingName().trim();
        if (name.isEmpty()) {
            throw new BizException(4002, "实训名称不能为空");
        }
        if (name.length() > 128) {
            throw new BizException(4002, "实训名称不能超过 128 字");
        }
        dto.setTrainingName(name);
        dto.setStartTime(normalizeTime(dto.getStartTime(), "开始时间"));
        dto.setEndTime(normalizeTime(dto.getEndTime(), "截止时间"));

        // scope 校验
        Integer st = dto.getScopeType();
        if (st != null && st == 2) {
            String sv = dto.getScopeValue();
            if (sv == null || sv.isBlank()) {
                throw new BizException(4002, "发布范围为年级公共时必须指定年级");
            }
        }
    }

    private String normalizeTime(String value, String label) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        try {
            LocalDateTime time = LocalDateTime.parse(text, text.contains("T") ? DATETIME_T : DATETIME);
            return time.format(DATETIME);
        } catch (DateTimeParseException e) {
            throw new BizException(4002, label + "格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }
}
