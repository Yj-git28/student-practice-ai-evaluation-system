package org.example.backend.training;

import org.example.backend.config.DataScopeResolver;
import org.example.backend.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * 越权测试：验证 @PreAuthorize 真的拦住人。
 * 用真实的方法安全拦截器（不是 mock），学生缺 training:publish → AccessDeniedException，
 * 该异常由 SecurityConfig 的 accessDeniedHandler 映射为 HTTP 403。
 */
class TrainingPublishMethodSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        TrainingService trainingService() {
            return Mockito.mock(TrainingService.class);
        }

        @Bean
        DataScopeResolver dataScopeResolver() {
            return new DataScopeResolver();
        }

        @Bean
        JdbcClient jdbcClient() {
            return Mockito.mock(JdbcClient.class);
        }

        @Bean
        TrainingController trainingController(TrainingService service, DataScopeResolver resolver, JdbcClient jdbc) {
            return new TrainingController(service, resolver, jdbc);
        }
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("学生没有 training:publish → 调发布接口被拒（403）")
    void studentCannotPublish() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            TrainingController controller = ctx.getBean(TrainingController.class);
            LoginUser student = loginUser(9L, "STUDENT", "training:submit");
            authenticate(student);

            assertThatThrownBy(() -> controller.publish(1L, student))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    @DisplayName("教师有 training:publish → 发布成功，且归属校验用的是当前登录人")
    void teacherCanPublish() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            TrainingController controller = ctx.getBean(TrainingController.class);
            TrainingService service = ctx.getBean(TrainingService.class);
            LoginUser teacher = loginUser(5L, "TEACHER", "training:publish", "training:list");
            authenticate(teacher);

            controller.publish(1L, teacher);

            verify(service).publish(1L, 5L, "TEACHER");
        }
    }

    @Test
    @DisplayName("教师可删除实训，归属校验下沉到 Service（学生仍被拒）")
    void teacherDeleteDelegatesOwnershipCheck() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            TrainingController controller = ctx.getBean(TrainingController.class);
            TrainingService service = ctx.getBean(TrainingService.class);
            LoginUser teacher = loginUser(5L, "TEACHER", "training:publish");
            authenticate(teacher);

            controller.delete(1L, teacher);

            verify(service).delete(1L, teacher);
        }
    }

    private LoginUser loginUser(Long userId, String roleCode, String... permissions) {
        LoginUser u = new LoginUser();
        u.setUserId(userId);
        u.setUsername("user" + userId);
        u.setRoleCode(roleCode);
        u.setRoles(Set.of(roleCode));
        u.setPermissions(Set.of(permissions));
        return u;
    }

    private void authenticate(LoginUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
