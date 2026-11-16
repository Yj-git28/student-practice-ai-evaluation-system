package org.example.backend.training;

import org.example.backend.security.LoginUser;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TrainingResultService {

    private final TrainingResultMapper mapper;

    public TrainingResultService(TrainingResultMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> page(LoginUser user, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 10;

        Long teacherId = "TEACHER_SELF".equals(user.getDataScope()) ? user.getUserId() : null;
        Long studentId = "STUDENT_SELF".equals(user.getDataScope()) ? user.getUserId() : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", mapper.count(teacherId, studentId));
        result.put("page", page);
        result.put("size", size);
        result.put("list", mapper.page(teacherId, studentId, page, size));
        return result;
    }
}
