package org.example.backend.training;

import org.example.backend.common.R;
import org.example.backend.security.LoginUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/result")
public class TrainingResultController {

    private final TrainingResultService service;

    public TrainingResultController(TrainingResultService service) {
        this.service = service;
    }

    @PreAuthorize("hasAnyAuthority('training:list','training:submit')")
    @GetMapping("/page")
    public R<Map<String, Object>> page(@AuthenticationPrincipal LoginUser user,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        return R.ok(service.page(user, page, size));
    }
}
