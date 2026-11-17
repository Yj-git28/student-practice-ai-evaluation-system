package org.example.backend.training;

import org.example.backend.config.DataScopeResolver;
import org.example.backend.security.LoginUser;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TrainingResultService {

    private final TrainingResultMapper mapper;
    private final DataScopeResolver dataScopeResolver;

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
}
