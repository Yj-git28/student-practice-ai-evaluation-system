package org.example.backend.training;

import org.example.backend.common.R;
import org.example.backend.security.LoginUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/training")
public class TrainingController {

    private final TrainingService trainingService;

    public TrainingController(TrainingService trainingService) {
        this.trainingService = trainingService;
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
        Long teacherId = "TEACHER_SELF".equals(user.getDataScope()) ? user.getUserId() : null;
        return R.ok(trainingService.stat(teacherId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{trainingId}")
    public R<Void> delete(@PathVariable Long trainingId) {
        trainingService.delete(trainingId);
        return R.ok(null);
    }
}
