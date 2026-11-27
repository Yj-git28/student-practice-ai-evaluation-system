package org.example.backend.training.controller;

import org.example.backend.training.dto.ResultReviewDTO;
import org.example.backend.training.service.TrainingResultService;
import org.example.backend.common.R;
import org.example.backend.security.LoginUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/result")
public class TrainingResultController {

    private final TrainingResultService service;

    public TrainingResultController(TrainingResultService service) {
        this.service = service;
    }

    /** 实训结果分页：仅教师和管理员可查，学生用 /api/result/my 看自己的 */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping("/page")
    public R<Map<String, Object>> page(@AuthenticationPrincipal LoginUser user,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        return R.ok(service.page(user, page, size));
    }

    /** 学生提交实训成果（multipart：trainingId + file），返回 AI 初评结果 */
    @PreAuthorize("hasAuthority('training:submit')")
    @PostMapping("/submit")
    public R<Map<String, Object>> submit(@RequestParam Long trainingId,
                                         @RequestParam("file") MultipartFile file,
                                         @AuthenticationPrincipal LoginUser user) {
        return R.ok(service.submit(trainingId, file, user));
    }

    /** 异步 AI 初评进度：学生看自己的、教师看自己实训下的、管理员看全部 */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{resultId}/ai")
    public R<Map<String, Object>> ai(@PathVariable Long resultId,
                                     @AuthenticationPrincipal LoginUser user) {
        return R.ok(service.ai(resultId, user));
    }

    /** 教师人工复核最终成绩 */
    @PreAuthorize("hasAuthority('evaluation:review')")
    @PostMapping("/{resultId}/review")
    public R<Void> review(@PathVariable Long resultId, @RequestBody ResultReviewDTO dto,
                          @AuthenticationPrincipal LoginUser user) {
        service.review(resultId, dto, user);
        return R.ok(null);
    }

    /** 当前学生在指定实训下的提交记录（最多 10 条） */
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my")
    public R<List<Map<String, Object>>> my(@RequestParam(required = false) Long trainingId,
                                           @AuthenticationPrincipal LoginUser user) {
        return R.ok(service.my(trainingId, user));
    }
}
