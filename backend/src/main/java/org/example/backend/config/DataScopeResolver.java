package org.example.backend.config;
import org.example.backend.security.DataScope;
import org.example.backend.security.LoginUser;
import org.springframework.stereotype.Component;

/**
 * 数据范围解析：唯一入口读 sys_role.data_scope（已经落在 LoginUser.dataScope）。
 * 1 全部 → 不过滤；2 本部门 → 按登录人 classId 过滤；3 本人 → 按登录人 userId 过滤。
 *
 * 说明：sys_user 只有 class_id（班级）作为部门维度。DEPT(2) 按班级过滤，
 * 相关 SQL 在 Mapper 层用 u.class_id 拼接；无班级的用户退回本人兜底。
 */
@Component
public class DataScopeResolver {

    /** 当前用户的数据范围：1 / 2 / 3 */
    public int scopeOf(LoginUser user) {
        if (user == null) throw new IllegalStateException("未登录");
        return DataScope.normalize(user.getDataScope());
    }

    public boolean isAll(LoginUser user) {
        return scopeOf(user) == DataScope.ALL;
    }

    /** null 表示不过滤（全部）；SELF(3) 返回需要强制注入的本人 userId；DEPT 走班级过滤，这里返回 null */
    public Long forcedUserId(LoginUser user) {
        int scope = scopeOf(user);
        if (scope == DataScope.ALL) return null;
        if (scope == DataScope.DEPT) return null;
        return user.getUserId();
    }

    /** DEPT(2) 范围需要按班级过滤时的班级 id；ADMIN / SELF / 无班级返回 null */
    public Long forcedClassId(LoginUser user) {
        if (scopeOf(user) != DataScope.DEPT) return null;
        return user.getClassId();
    }
}
