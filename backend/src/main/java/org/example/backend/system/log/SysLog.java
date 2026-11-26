package org.example.backend.system.log;

import java.lang.annotation.*;

/** 标记需要写 sys_log 的业务操作：登录、改角色、改权限 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SysLog {

    /** 操作类型，如 LOGIN / LOGIN_FAIL / CHANGE_ROLE / GRANT_PERM */
    String type();

    /** 操作说明 */
    String content() default "";
}
