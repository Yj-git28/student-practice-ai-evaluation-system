package org.example.backend.training;

import org.example.backend.common.BizException;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TrainingService {

    private final TrainingMapper mapper;

    public TrainingService(TrainingMapper mapper) {
        this.mapper = mapper;
    }

    /** 教师发布自己的实训；先做归属校验，防越权发布别人的实训 */
    public void publish(Long trainingId, Long teacherId) {
        if (!mapper.existsOwnedBy(trainingId, teacherId)) {
            throw new BizException(4031, "实训不存在或无权操作");
        }
        mapper.publish(trainingId);
    }

    /** 教师口径统计；teacherId 为 null 时统计全量（ADMIN） */
    public Map<String, Object> stat(Long teacherId) {
        return mapper.stat(teacherId);
    }

    /** 删除实训（仅 ADMIN，权限由 Controller 上的 @PreAuthorize 控制） */
    public void delete(Long trainingId) {
        mapper.delete(trainingId);
    }
}
