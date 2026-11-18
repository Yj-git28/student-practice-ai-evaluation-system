package org.example.backend.training;

import org.example.backend.common.BizException;
import org.example.backend.config.DataScopeResolver;
import org.example.backend.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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

    private final TrainingResultMapper mapper;
    private final DataScopeResolver dataScopeResolver;

    /** 上传根目录，可用 app.upload.dir 覆盖；默认项目目录下 uploads */
    @org.springframework.beans.factory.annotation.Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public TrainingResultService(TrainingResultMapper mapper, DataScopeResolver dataScopeResolver) {
        this.mapper = mapper;
        this.dataScopeResolver = dataScopeResolver;
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
        Path dir = Paths.get(uploadDir, month);
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(storedName).toFile());
        } catch (IOException e) {
            throw new BizException(500, "文件保存失败：" + e.getMessage());
        }
        // 相对路径入库，避免换机器后绝对路径失效
        String filePath = "uploads/" + month + "/" + storedName;

        mapper.insert(trainingId, user.getUserId(), fileName, filePath, suffix);
        Long resultId = mapper.lastInsertId();

        int aiScore = 70 + Math.floorMod((fileName + user.getUsername()).hashCode(), 16);
        String aiComment = aiComment(aiScore);
        mapper.updateAi(resultId, aiScore, aiComment);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultId", resultId);
        result.put("aiScore", aiScore);
        result.put("aiComment", aiComment);
        return result;
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
    }

    public List<Map<String, Object>> my(Long trainingId, LoginUser user) {
        return mapper.my(user.getUserId(), trainingId);
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
