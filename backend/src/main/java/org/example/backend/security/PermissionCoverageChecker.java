package org.example.backend.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 启动即校验：org.example.backend 下的每个接口都必须显式声明权限，
 * 否则应用直接启动失败（而不是悄悄裸奔）。
 * 认可的三种声明：@PreAuthorize / @RequiresPerm / @PublicApi，方法级或类级均可。
 */
@Component
public class PermissionCoverageChecker implements ApplicationRunner {

    private final RequestMappingHandlerMapping mapping;

    public PermissionCoverageChecker(RequestMappingHandlerMapping mapping) {
        this.mapping = mapping;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
            HandlerMethod hm = e.getValue();
            if (!hm.getBeanType().getPackageName().startsWith("org.example.backend")) continue;
            // Spring 自带的 /error 等基础端点不归本项目管
            if (!declared(hm)) {
                missing.add(e.getKey() + "  ->  " + hm.getShortLogMessage());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "以下接口未登记权限码（@PreAuthorize / @RequiresPerm / @PublicApi），拒绝启动：\n"
                            + String.join("\n", missing));
        }
    }

    private boolean declared(HandlerMethod hm) {
        return has(hm, PreAuthorize.class)
                || has(hm, RequiresPerm.class)
                || has(hm, PublicApi.class);
    }

    private boolean has(HandlerMethod hm, Class<? extends Annotation> type) {
        return hm.hasMethodAnnotation(type) || hm.getBeanType().isAnnotationPresent(type);
    }
}
