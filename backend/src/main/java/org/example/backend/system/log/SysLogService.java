package org.example.backend.system.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SysLogService {

    private static final Logger log = LoggerFactory.getLogger(SysLogService.class);
    private static final int MAX_CONTENT = 512;   // oper_content VARCHAR(512)

    private final SysLogMapper mapper;

    public SysLogService(SysLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 审计日志失败不能影响主业务，这里吞掉异常只打 error 日志。
     * REQUIRES_NEW：业务事务回滚时日志仍然保留（审计目的），且日志写失败不会污染业务事务。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(Long userId, String type, String content) {
        String text = type + ": " + content;
        if (text.length() > MAX_CONTENT) text = text.substring(0, MAX_CONTENT);
        try {
            mapper.insert(userId, text);
        } catch (Exception e) {
            log.error("写 sys_log 失败: {}", text, e);
        }
    }
}
