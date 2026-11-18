package org.example.backend.evaluation;

import org.example.backend.common.R;
import org.example.backend.security.LoginUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    /** 班级实训数据统计：总人数、已提交、完成率、平均分、四项指标 */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping("/stat")
    public R<Map<String, Object>> stat(@AuthenticationPrincipal LoginUser user) {
        return R.ok(service.stat(user));
    }
}
