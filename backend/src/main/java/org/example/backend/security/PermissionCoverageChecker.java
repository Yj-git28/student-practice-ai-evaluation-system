package org.example.backend.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

            boolean registered = hm.hasMethodAnnotation(PreAuthorize.class)
                    || hm.hasMethodAnnotation(RequiresPerm.class)
                    || hm.hasMethodAnnotation(PublicApi.class)
                    || hm.getBeanType().isAnnotationPresent(PublicApi.class);
            if (!registered) {
                missing.add(e.getKey() + "  ->  " + hm.getShortLogMessage());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "以下接口未登记权限码，拒绝启动：\n" + String.join("\n", missing));
        }
    }
}
