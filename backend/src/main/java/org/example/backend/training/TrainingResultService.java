package org.example.backend.training;

import org.example.backend.common.BizException;
import org.example.backend.config.DataScopeResolver;
import org.example.backend.evaluation.EvaluationCacheService;
import org.example.backend.mq.RedisStreamQueue;
import org.example.backend.security.LoginUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TrainingResultService {

    private static final Set<String> ALLOWED_SUFFIX =
            Set.of("pdf", "doc", "docx", "jpg", "jpeg", "png");
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Logger log = LoggerFactory.getLogger(TrainingResultService.class);

    private final TrainingResultMapper mapper;
    private final DataScopeResolver dataScopeResolver;
    private final EvaluationCacheService cache;
    private final RedisStreamQueue queue;

    /** 上传根目录，可用 app.upload.dir 覆盖；默认项目目录下 uploads */
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /** true=提交后投递消息、后台异步评分；false=退回同步算分（答辩可现场切换两种链路） */
    @Value("${app.mq.enabled:true}")
    private boolean mqEnabled;

    public TrainingResultService(TrainingResultMapper mapper, DataScopeResolver dataScopeResolver,
                                 EvaluationCacheService cache, RedisStreamQueue queue) {
        this.mapper = mapper;
        this.dataScopeResolver = dataScopeResolver;
        this.cache = cache;
        this.queue = queue;
    }

    public Map<String, Object> page(LoginUser user, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 10;

        // 数据范围由 sys_role.data_scope 决定：1 全部 → 不过滤；2/3 → 按本人
        Long selfId = dataScopeResolver.forcedUserId(user);
        Long teacherId = "TEACHER".equals(user.getRoleCode()) ? selfId : null;
        Long studentId = "STUDENT".equals(user.getRoleCode()) ? selfId : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", mapper.count(teacherId, studentId));
        result.put("page", page);
        result.put("size", size);
        result.put("list", mapper.page(teacherId, studentId, page, size));
        return result;
    }

    /** 学生提交成果：落盘 + 入库 + 模拟 AI 初评 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(Long trainingId, MultipartFile file, LoginUser user) {
        Integer status = trainingId == null ? null : mapper.trainingStatus(trainingId).orElse(null);
        if (status == null || status != 1) {
            throw new BizException(4001, "实训不存在或未发布");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(4001, "请选择要提交的文件");
        }

        String fileName = originalName(file.getOriginalFilename());
        String suffix = suffixOf(fileName);
        if (!ALLOWED_SUFFIX.contains(suffix)) {
            throw new BizException(4002, "仅支持 pdf/doc/docx/jpg/jpeg/png 格式");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(4002, "文件大小不能超过 20MB");
        }

        String month = YearMonth.now().format(MONTH);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + suffix;
        // 必须用绝对路径：MultipartFile.transferTo 对相对路径会按 Servlet 容器临时目录解析
        Path dir = Paths.get(uploadDir, month).toAbsolutePath().normalize();
        Path target = dir.resolve(storedName);
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BizException(500, "文件保存失败：" + e.getMessage());
        }
        // 相对路径入库，避免换机器后绝对路径失效
        String filePath = "uploads/" + month + "/" + storedName;

        mapper.insert(trainingId, user.getUserId(), fileName, filePath, suffix);
        Long resultId = mapper.lastInsertId();

        // 提交数变了，看板缓存立刻作废；异步模式下分数稍后由消费者回写
        cache.evictStats();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultId", resultId);

        if (mqEnabled) {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("resultId", String.valueOf(resultId));
            payload.put("trainingId", String.valueOf(trainingId));
            payload.put("userId", String.valueOf(user.getUserId()));
            payload.put("username", user.getUsername());
            payload.put("fileName", fileName);
            payload.put("filePath", filePath);
            // 事务提交后才投递：避免消费者读到还没 commit 的记录
            publishAfterCommit(payload, trainingId, user, fileName, target);
            result.put("aiStatus", "QUEUED");
            result.put("aiScore", null);
            result.put("aiComment", null);
            return result;
        }

        // 同步链路（降级模式）：行为与改造前一致
        Map<String, Object> ai = aiResult(trainingId, user.getUserId(), user.getUsername(), fileName, target);
        int aiScore = ((Number) ai.get("aiScore")).intValue();
        String aiComment = String.valueOf(ai.get("aiComment"));
        mapper.updateAi(resultId, aiScore, aiComment);
        result.put("aiStatus", "DONE");
        result.put("aiScore", aiScore);
        result.put("aiComment", aiComment);
        return result;
    }

    /** 提交事务提交后再投递消息；投递失败退回同步算分，保证记录不会停在"没分数"的状态 */
    private void publishAfterCommit(Map<String, String> payload, Long trainingId, LoginUser user,
                                    String fileName, Path storedFile) {
        Runnable action = () -> {
            try {
                String recordId = queue.publish(payload);
                log.info("AI 初评消息已投递 resultId={} recordId={}", payload.get("resultId"), recordId);
            } catch (Exception e) {
                log.warn("投递 AI 初评消息失败，降级为同步算分 resultId={} : {}",
                        payload.get("resultId"), e.toString());
                applyAiScore(Long.valueOf(payload.get("resultId")), trainingId, user.getUserId(),
                        user.getUsername(), fileName, storedFile.toAbsolutePath().toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** 消费者调用：算分（命中 ai:score 缓存则复用）并回写结果表 */
    public void applyAiScore(Long resultId, Long trainingId, Long userId, String username,
                             String fileName, String filePath) {
        if (resultId == null || trainingId == null || userId == null) {
            throw new BizException(4002, "消息缺少必要字段");
        }
        Path storedFile = Paths.get(filePath == null ? "" : filePath).toAbsolutePath().normalize();
        Map<String, Object> ai = aiResult(trainingId, userId, username, fileName, storedFile);
        int score = ((Number) ai.get("aiScore")).intValue();
        mapper.updateAi(resultId, score, String.valueOf(ai.get("aiComment")));
        cache.evictStats();          // 平均分口径变了，看板作废
    }

    /** 查询某条提交的 AI 初评状态：学生只能看自己的，教师看自己所带实训，管理员看全部 */
    public Map<String, Object> ai(Long resultId, LoginUser user) {
        List<Map<String, Object>> rows = mapper.selectAiRow(resultId);
        if (rows.isEmpty()) {
            throw new BizException(4031, "提交记录不存在或无权查看");
        }
        Map<String, Object> row = rows.get(0);
        Long studentId = toLong(row.get("studentId"));
        Long teacherId = toLong(row.get("teacherId"));
        String role = user.getRoleCode();
        boolean allowed = "ADMIN".equals(role)
                || ("TEACHER".equals(role) && teacherId != null && teacherId.equals(user.getUserId()))
                || ("STUDENT".equals(role) && studentId != null && studentId.equals(user.getUserId()));
        if (!allowed) {
            throw new BizException(4031, "提交记录不存在或无权查看");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultId", row.get("resultId"));
        out.put("aiScore", row.get("aiScore"));
        out.put("aiComment", row.get("aiComment"));
        out.put("aiStatus", row.get("aiScore") == null ? "PENDING" : "DONE");
        return out;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 教师人工复核：仅该实训所属教师或管理员 */
    public void review(Long resultId, ResultReviewDTO dto, LoginUser user) {
        Integer finalScore = dto == null ? null : dto.getFinalScore();
        if (finalScore == null || finalScore < 0 || finalScore > 100) {
            throw new BizException(4002, "最终分数需在 0-100 之间");
        }
        Long teacherId = mapper.selectTrainingTeacher(resultId)
                .orElseThrow(() -> new BizException(4031, "提交记录不存在或无权操作"));
        if (!"ADMIN".equals(user.getRoleCode()) && !teacherId.equals(user.getUserId())) {
            throw new BizException(4031, "提交记录不存在或无权操作");
        }
        mapper.updateReview(resultId, finalScore, dto.getTeacherComment(), user.getUserId());
        cache.evictStats();          // 平均分口径含 final_score，复核后看板作废
    }

    public List<Map<String, Object>> my(Long trainingId, LoginUser user) {
        return mapper.my(user.getUserId(), trainingId);
    }

    /**
     * AI 初评（当前是本地模拟打分，接真实模型时只换这一处）。
     * 缓存键带上文件内容摘要：同一份文件重复提交直接复用上次结果，不重复调用模型。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> aiResult(Long trainingId, Long userId, String username, String fileName, Path storedFile) {
        String key = cache.aiKey(trainingId, userId, sha256(storedFile));
        Map<String, Object> cached = (Map<String, Object>) cache.get(key, Map.class);
        if (cached != null) {
            return cached;
        }

        int score = 70 + Math.floorMod((fileName + username).hashCode(), 16);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("aiScore", score);
        value.put("aiComment", aiComment(score));

        cache.put(key, value, EvaluationCacheService.AI_TTL);
        return value;
    }

    /** 文件内容 SHA-256，用于判断是不是"同一份文件" */
    private String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            // 读不了文件时退回文件名+大小，缓存仍可用，只是判重精度下降
            try {
                return "fallback-" + file.getFileName() + "-" + Files.size(file);
            } catch (IOException ignored) {
                return "fallback";
            }
        }
    }

    private String originalName(String name) {
        String text = name == null ? "" : name.replace('\\', '/');
        text = text.substring(text.lastIndexOf('/') + 1).trim();
        return text.isEmpty() ? "未命名文件" : text;
    }

    private String suffixOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 按分数段拼接固定评语模板 */
    private String aiComment(int score) {
        String base;
        if (score >= 85) {
            base = "代码结构清晰，功能实现完整，文档规范，实训步骤记录详实。";
        } else if (score >= 80) {
            base = "代码质量良好，主要功能已实现，文档较为完整，个别步骤描述可再细化。";
        } else if (score >= 75) {
            base = "实训步骤基本完整，文档格式存在少量问题，部分功能描述不够详细。";
        } else {
            base = "已完成基本实训要求，代码与文档仍有明显欠缺，建议补充功能说明与测试记录。";
        }
        return base + "（AI初评仅供参考，最终成绩以教师复核为准）";
    }
}
