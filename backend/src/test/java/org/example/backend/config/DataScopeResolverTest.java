package org.example.backend.config;

import org.example.backend.security.DataScope;
import org.example.backend.security.LoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeResolverTest {

    private final DataScopeResolver resolver = new DataScopeResolver();

    @Test
    @DisplayName("data_scope=1（全部）→ forcedUserId 为 null，不注入过滤条件")
    void allScopeDoesNotFilter() {
        LoginUser user = user(10L, DataScope.ALL);

        assertThat(resolver.isAll(user)).isTrue();
        assertThat(resolver.forcedUserId(user)).isNull();
        assertThat(resolver.forcedClassId(user)).isNull();
    }

    @Test
    @DisplayName("data_scope=3（本人）→ 强制注入当前 userId")
    void selfScopeForcesCurrentUser() {
        LoginUser user = user(11L, DataScope.SELF);

        assertThat(resolver.forcedUserId(user)).isEqualTo(11L);
        assertThat(resolver.isAll(user)).isFalse();
    }

    @Test
    @DisplayName("data_scope=2（本部门）→ 按本人过滤，并给出班级维度 classId")
    void deptScopeExposesClassId() {
        LoginUser user = user(12L, DataScope.DEPT);
        user.setClassId(5L);

        assertThat(resolver.forcedUserId(user)).isEqualTo(12L);
        assertThat(resolver.forcedClassId(user)).isEqualTo(5L);
    }

    @Test
    @DisplayName("data_scope 为空 → 按本人（3）处理")
    void nullScopeFallsBackToSelf() {
        LoginUser user = user(13L, null);

        assertThat(resolver.scopeOf(user)).isEqualTo(DataScope.SELF);
        assertThat(resolver.forcedUserId(user)).isEqualTo(13L);
    }

    private LoginUser user(Long userId, Integer scope) {
        LoginUser u = new LoginUser();
        u.setUserId(userId);
        u.setDataScope(scope);
        return u;
    }
}
