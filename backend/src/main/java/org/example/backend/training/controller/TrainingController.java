package org.example.backend.training.controller;

import org.example.backend.training.dto.TrainingSaveDTO;
import org.example.backend.training.service.TrainingService;
import org.example.backend.common.R;
import org.example.backend.config.DataScopeResolver;
import org.example.backend.security.LoginUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/training")
public class TrainingController {

    private final TrainingService trainingService;
    private final DataScopeResolver dataScopeResolver;
    private final JdbcClient jdbc;

    public TrainingController(TrainingService trainingService, DataScopeResolver dataScopeResolver,
                               JdbcClient jdbc) {
        this.trainingService = trainingService;
        this.dataScopeResolver = dataScopeResolver;
        this.jdbc = jdbc;
    }

    /** 分页：学生只看已发布，教师只看自己，管理员全量 */
    @PreAuthorize("hasAnyAuthority('training:list','training:my-list')")
    @GetMapping("/page")
    public R<Map<String, Object>> page(@AuthenticationPrincipal LoginUser user,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Integer status) {
        return R.ok(trainingService.page(user, page, size, keyword, status));
    }

    /** 单个详情；学生只能看已发布 */
    @PreAuthorize("hasAnyAuthority('training:list','training:my-list')")
    @GetMapping("/{trainingId}")
    public R<Map<String, Object>> detail(@PathVariable Long trainingId,
                                         @AuthenticationPrincipal LoginUser user) {
        return R.ok(trainingService.detail(trainingId, user));
    }

    /** 新增实训：教师创建归属自己；ADMIN 可创建公共实训（年级/全校） */
    @PreAuthorize("hasAuthority('training:add')")
    @PostMapping
    public R<Long> create(@RequestBody TrainingSaveDTO dto,
                          @AuthenticationPrincipal LoginUser user) {
        return R.ok(trainingService.create(dto, user));
    }

    /** 编辑实训：所属教师或管理员 */
    @PreAuthorize("hasAuthority('training:edit')")
    @PutMapping("/{trainingId}")
    public R<Void> update(@PathVariable Long trainingId, @RequestBody TrainingSaveDTO dto,
                          @AuthenticationPrincipal LoginUser user) {
        trainingService.update(trainingId, dto, user);
        return R.ok(null);
    }

    /** 删除实训（级联删除成果）：仅管理员或该实训所属教师 */
    @PreAuthorize("hasAuthority('training:remove')")
    @DeleteMapping("/{trainingId}")
    public R<Void> delete(@PathVariable Long trainingId,
                          @AuthenticationPrincipal LoginUser user) {
        trainingService.delete(trainingId, user);
        return R.ok(null);
    }

    @PreAuthorize("hasAuthority('training:publish')")
    @PostMapping("/{trainingId}/publish")
    public R<Void> publish(@PathVariable Long trainingId,
                           @AuthenticationPrincipal LoginUser user) {
        trainingService.publish(trainingId, user.getUserId(), user.getRoleCode());
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

    /** 可选年级列表（用于管理员发布"年级公共"实训时选择） */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping("/grades")
    public R<List<String>> grades() {
        List<String> grades = jdbc.sql(
                "SELECT DISTINCT grade FROM class WHERE grade IS NOT NULL AND grade != '' ORDER BY grade")
                .query(String.class).list();
        return R.ok(grades);
    }
}
