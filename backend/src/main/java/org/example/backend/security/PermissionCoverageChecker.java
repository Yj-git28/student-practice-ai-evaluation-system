package org.example.backend.security;

import org.example.backend.system.entity.SysPermission;
import org.example.backend.system.mapper.SysPermissionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 启动即校验：
 *  1. org.example.backend 下每个接口都必须显式声明权限（@PreAuthorize / @RequiresPerm / @PublicApi），
 *     否则应用直接启动失败（而不是悄悄裸奔）；
 *  2. 注解里引用的权限码必须能在 sys_permission 元数据中找到，缺失仅告警（不阻塞启动，
 *     便于本地/测试环境临时加码），但上线前应补齐元数据。
 */
@Component
public class PermissionCoverageChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionCoverageChecker.class);
    private static final Pattern AUTHORITY_CODE = Pattern.compile("hasAuthority\\('([^']+)'\\)");

    private final RequestMappingHandlerMapping mapping;
    private final SysPermissionMapper permissionMapper;

    public PermissionCoverageChecker(RequestMappingHandlerMapping mapping,
                                     SysPermissionMapper permissionMapper) {
        this.mapping = mapping;
        this.permissionMapper = permissionMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = new ArrayList<>();
        Set<String> usedCodes = new LinkedHashSet<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
            HandlerMethod hm = e.getValue();
            if (!hm.getBeanType().getPackageName().startsWith("org.example.backend")) continue;
            if (!declared(hm)) {
                missing.add(e.getKey() + "  ->  " + hm.getShortLogMessage());
                continue;
            }
            collectCodes(hm, usedCodes);
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "以下接口未登记权限码（@PreAuthorize / @RequiresPerm / @PublicApi），拒绝启动：\n"
                            + String.join("\n", missing));
        }

        // 对账：注解引用的权限码是否都在权限元数据表里
        Set<String> dbCodes = permissionMapper.selectAll().stream()
                .map(SysPermission::getPermCode)
                .collect(Collectors.toSet());
        List<String> orphan = usedCodes.stream()
                .filter(code -> !dbCodes.contains(code))
                .sorted()
                .toList();
        if (!orphan.isEmpty()) {
            log.warn("以下权限码在代码中使用，但 sys_permission 元数据中缺失，请补齐：\n  {}",
                    String.join("\n  ", orphan));
        }
    }

    private void collectCodes(HandlerMethod hm, Set<String> usedCodes) {
        PreAuthorize pre = hm.hasMethodAnnotation(PreAuthorize.class)
                ? hm.getMethodAnnotation(PreAuthorize.class)
                : hm.getBeanType().getAnnotation(PreAuthorize.class);
        if (pre != null) {
            Matcher m = AUTHORITY_CODE.matcher(pre.value());
            while (m.find()) usedCodes.add(m.group(1));
        }
        RequiresPerm rp = hm.hasMethodAnnotation(RequiresPerm.class)
                ? hm.getMethodAnnotation(RequiresPerm.class)
                : hm.getBeanType().getAnnotation(RequiresPerm.class);
        if (rp != null) {
            usedCodes.addAll(List.of(rp.value()));
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
