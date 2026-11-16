package org.example.backend.config;

import org.example.backend.security.LoginUser;
import org.springframework.stereotype.Component;

@Component
public class DataScopeResolver {

    /** null 表示不过滤（ADMIN）；否则返回需要强制注入的 userId */
    public Long forcedUserId(LoginUser user) {
        if (user == null) throw new IllegalStateException("未登录");
        String scope = user.getDataScope();
        if ("ALL".equals(scope)) return null;
        return user.getUserId();
    }
}
