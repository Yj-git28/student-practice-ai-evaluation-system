package org.example.backend.config;
import org.example.backend.security.DataScope;
import org.example.backend.security.LoginUser;
import org.springframework.stereotype.Component;

/**
 * 数据范围解析：唯一入口读 sys_role.data_scope（已经落在 LoginUser.dataScope）。
 * 1 全部 → 不过滤；2 本部门 / 3 本人 → 需要按登录人过滤。
 *
 * 说明：现有 sys_user 只有 class_id，没有 dept_id。所以 2（本部门）暂时降级为本人，
 * 等班级/部门维度接口接好后再改成按 class_id 过滤，这里集中改一处即可。
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

    /** null 表示不过滤（全部数据）；否则返回需要强制注入的 userId */
    public Long forcedUserId(LoginUser user) {
        int scope = scopeOf(user);
        if (scope == DataScope.ALL) return null;
        // DEPT(2) 与 SELF(3) 当前都按本人过滤
        return user.getUserId();
    }

    /** 需要按班级过滤时的班级 id；ADMIN/无班级返回 null */
    public Long forcedClassId(LoginUser user) {
        return scopeOf(user) == DataScope.DEPT ? user.getClassId() : null;
    }
}
