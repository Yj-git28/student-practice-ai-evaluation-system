package org.example.backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** B3 验收：新增一个不带权限注解的接口，应用启动失败 */
class PermissionCoverageCheckerTest {

    private final RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
    private final PermissionCoverageChecker checker = new PermissionCoverageChecker(mapping);

    @Test
    @DisplayName("接口没有 @PreAuthorize/@RequiresPerm/@PublicApi → 拒绝启动")
    void missingAnnotationFailsStartup() throws NoSuchMethodException {
        HandlerMethod handler = new HandlerMethod(new DummyController(),
                DummyController.class.getMethod("unprotected"));
        when(mapping.getHandlerMethods()).thenReturn(Map.of(info("/api/demo/unprotected"), handler));

        assertThatThrownBy(() -> checker.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未登记权限码")
                .hasMessageContaining("/api/demo/unprotected");
    }

    @Test
    @DisplayName("接口声明了 @PreAuthorize → 启动校验通过")
    void annotatedEndpointPassesStartup() throws NoSuchMethodException {
        HandlerMethod handler = new HandlerMethod(new DummyController(),
                DummyController.class.getMethod("protectedApi"));
        when(mapping.getHandlerMethods()).thenReturn(Map.of(info("/api/demo/protected"), handler));

        assertThatCode(() -> checker.run(null)).doesNotThrowAnyException();
    }

    private RequestMappingInfo info(String path) {
        return RequestMappingInfo.paths(path).build();
    }

    @RestController
    static class DummyController {
        @GetMapping("/api/demo/unprotected")
        public String unprotected() { return "x"; }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/api/demo/protected")
        public String protectedApi() { return "x"; }
    }
}
