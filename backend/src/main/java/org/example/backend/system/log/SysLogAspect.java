package org.example.backend.system.log;

import org.example.backend.system.dto.ClassSaveDTO;
import org.example.backend.system.dto.UserSaveDTO;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.backend.auth.dto.LoginDTO;
import org.example.backend.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 把 @SysLog 注解的方法统一落库：登录、改角色、改权限 */
@Aspect
@Component
public class SysLogAspect {

    private final SysLogService sysLogService;

    public SysLogAspect(SysLogService sysLogService) {
        this.sysLogService = sysLogService;
    }

    @Around("@annotation(sysLog)")
    public Object around(ProceedingJoinPoint pjp, SysLog sysLog) throws Throwable {
        try {
            Object result = pjp.proceed();
            write(sysLog, pjp, "成功");
            return result;
        } catch (Throwable t) {
            write(sysLog, pjp, "失败: " + t.getMessage());
            throw t;
        }
    }

    private void write(SysLog sysLog, ProceedingJoinPoint pjp, String outcome) {
        Long userId = currentUserId();
        String detail = sysLog.content() + " | " + pjp.getSignature().getName() + " | " + args(pjp);
        sysLogService.record(userId, sysLog.type(), detail + " | " + outcome);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser lu) {
            return lu.getUserId();
        }
        return null;
    }

    /** 只挑安全字段，绝不能把登录密码写进日志 */
    private String args(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        if (args == null || args.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (Object a : args) {
            if (a instanceof LoginDTO dto) {
                sb.append("username=").append(dto.getUsername()).append(' ');
            } else if (a instanceof LoginUser lu) {
                sb.append("userId=").append(lu.getUserId()).append(' ');
            } else if (a instanceof CharSequence || a instanceof Number || a instanceof Boolean) {
                sb.append(a).append(' ');
            } else if (a instanceof org.example.backend.system.dto.ClassSaveDTO dto) {
                sb.append("className=").append(dto.getClassName())
                        .append(" grade=").append(dto.getGrade()).append(' ');
            } else if (a instanceof org.example.backend.system.dto.UserSaveDTO dto) {
                sb.append("username=").append(dto.getUsername())
                        .append(" role=").append(dto.getRoleId()).append(' ');
            } else if (a instanceof java.util.Collection<?> c) {
                sb.append("size=").append(c.size()).append(' ');
                // 批量导入：记录前 10 个账号，便于事后追溯
                String names = c.stream()
                        .filter(org.example.backend.system.dto.UserSaveDTO.class::isInstance)
                        .map(x -> ((org.example.backend.system.dto.UserSaveDTO) x).getUsername())
                        .limit(10)
                        .collect(java.util.stream.Collectors.joining(","));
                if (!names.isEmpty()) {
                    sb.append("users=").append(names).append(' ');
                }
            }
        }
        return sb.toString().trim();
    }
}
