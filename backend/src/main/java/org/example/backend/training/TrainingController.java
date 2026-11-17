package org.example.backend.training;

import org.example.backend.common.R;
import org.example.backend.config.DataScopeResolver;
import org.example.backend.security.LoginUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/training")
public class TrainingController {

    private final TrainingService trainingService;
    private final DataScopeResolver dataScopeResolver;

    public TrainingController(TrainingService trainingService, DataScopeResolver dataScopeResolver) {
        this.trainingService = trainingService;
        this.dataScopeResolver = dataScopeResolver;
    }

    @PreAuthorize("hasAuthority('training:publish')")
    @PostMapping("/{trainingId}/publish")
    public R<Void> publish(@PathVariable Long trainingId,
                           @AuthenticationPrincipal LoginUser user) {
        trainingService.publish(trainingId, user.getUserId());
        return R.ok(null);
    }

    @PreAuthorize("hasAuthority('training:list') and hasAuthority('evaluation:list')")
    @GetMapping("/stat")
    public R<Map<String, Object>> stat(@AuthenticationPrincipal LoginUser user) {
        // 数据范围来自 sys_role.data_scope；教师只统计自己的，ADMIN（全部）不过滤
        Long teacherId = "TEACHER".equals(user.getRoleCode())
                ? dataScopeResolver.forcedUserId(user)
                : null;
        return R.ok(trainingService.stat(teacherId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{trainingId}")
    public R<Void> delete(@PathVariable Long trainingId) {
        trainingService.delete(trainingId);
        return R.ok(null);
    }
}
