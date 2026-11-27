package org.example.backend.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * 自定义权限注解 {@link RequiresPerm} 的执行切面。
 *
 * <p>此前 {@code @RequiresPerm} 仅被 {@link PermissionCoverageChecker} 当作“已登记权限”的标记，
 * 没有任何组件真正执行校验，属于未生效的死注解。本切面在方法进入前按注解声明的权限码校验当前登录用户：
 * <ul>
 *   <li>{@link RequiresPerm.Logical#AND}：必须拥有全部权限码；</li>
 *   <li>{@link RequiresPerm.Logical#OR}：拥有任一权限码即可。</li>
 * </ul>
 * 校验不通过抛出 {@link AccessDeniedException}，由 SecurityConfig 的 accessDeniedHandler 映射为 403，
 * 与 {@code @PreAuthorize} 走同一条拒绝链路。方法级注解优先于类级注解。
 *
 * <p>本类仅属于 RBAC 鉴权层，不改动任何业务 Service / Controller / 实体。
 */
@Aspect
@Component
public class RequiresPermAspect {

    @Pointcut("@annotation(org.example.backend.security.RequiresPerm) "
            + "|| @within(org.example.backend.security.RequiresPerm)")
    public void permPointcut() {
    }

    @Around("permPointcut()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        RequiresPerm annotation = resolveAnnotation(pjp);
        if (annotation != null) {
            check(annotation);
        }
        return pjp.proceed();
    }

    /** 方法上的注解优先；方法未标注时回退到目标类上的类级注解。 */
    private RequiresPerm resolveAnnotation(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        RequiresPerm onMethod = method.getAnnotation(RequiresPerm.class);
        if (onMethod != null) {
            return onMethod;
        }
        return pjp.getTarget().getClass().getAnnotation(RequiresPerm.class);
    }

    private void check(RequiresPerm annotation) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            throw new AccessDeniedException("没有访问权限");
        }

        Set<String> permissions = loginUser.getPermissions();
        String[] required = annotation.value();
        if (required == null || required.length == 0) {
            return;
        }

        boolean pass = annotation.logical() == RequiresPerm.Logical.OR
                ? java.util.Arrays.stream(required).anyMatch(permissions::contains)
                : java.util.Arrays.stream(required).allMatch(permissions::contains);

        if (!pass) {
            throw new AccessDeniedException("没有访问权限");
        }
    }
}
