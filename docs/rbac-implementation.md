# RBAC 落地实施步骤（training_eval + student-practice-ai-evaluation-system）

> 目标：在**已有表结构**上把「假登录」改成真 RBAC。全程不改表结构（第 2 章只列出建议补的字段，不写 DDL）。
> 你可以按章节顺序一步步做，每一步都有明确的文件路径和验证方法。

---

## 0. 先确认三件事（已完成解析，结论如下）

| 项目 | 实际情况 |
|---|---|
| 数据库 | MySQL 8.0.13，schema `training_eval`，13 张表 |
| 导出类型 | Navicat **"结构与数据"** 全量导出（含 `CREATE TABLE` + `INSERT`），不是纯结构，也不是纯数据 |
| 后端 | Spring Boot **4.1.1**，Java 17，依赖只有 `spring-boot-starter-webmvc` + `mysql-connector-j`，**没有** JDBC starter / Spring Security / MyBatis |
| 前端 | Vue 3.5 + Vite 8 + Pinia 4 + vue-router 4 + Element Plus 2.14，`base: '/QianDuan/'`，axios `baseURL: '/api'` |
| 现状 | `Login.vue` 里硬编码 3 个账号比对，token 是字符串 `mock-token-123456`；`router/index.js` 用 `meta.roles` 写死 7 个业务页 |

### 0.1 真实表清单

| 表名 | 主键 | 用途 |
|---|---|---|
| `sys_user` | `user_id` | 用户（`role_id` 必填 → **单角色**） |
| `sys_role` | `role_id` | 角色（TEACHER / STUDENT / ADMIN） |
| `sys_permission` | `permission_id` | 菜单 + 按钮 + 接口三合一权限表，`parent_id` 自关联 |
| `sys_role_permission` | `id` | 角色↔权限，**唯一索引 `uk_role_perm(role_id, permission_id)`** |
| `class` | `class_id` | 班级 |
| `training` | `training_id` | 实训任务，`teacher_id` 指向 `sys_user.user_id` |
| `train_result` | `result_id` | 学生提交成果，`student_id` 指向 `sys_user.user_id` |
| `indicator` | `indicator_id` | 评分指标，`training_id` 关联实训 |
| `evaluation` | `eval_id` | 打分记录 |
| `ai_check` / `ai_parse` | `check_id` / `parse_id` | AI 检查与解析结果，`result_id` 关联 `train_result` |
| `sys_config` / `sys_log` | `config_id` / `log_id` | 配置与操作日志 |

### 0.2 全库**不存在**的字段（不要凭想象加条件）

`status`、`deleted`、`is_deleted`、`tenant_id`、`dept_id`、`data_scope`、`create_by`、`update_by` —— **一个都没有**。

所以本文所有 SQL **不会**出现 `AND status = 1` 或 `AND deleted = 0`。这是本项目最容易踩的坑：网上模板抄来的 SQL 一带上 `status` 就直接报 `Unknown column`。

### 0.3 现有数据里 3 个必须先处理的问题

```sql
-- 问题1：管理员角色(role_id=3)在 sys_role_permission 里一条记录都没有
SELECT r.role_id, r.role_code, COUNT(rp.id) AS perm_cnt
FROM sys_role r LEFT JOIN sys_role_permission rp ON rp.role_id = r.role_id
GROUP BY r.role_id, r.role_code;
-- 结果：1=TEACHER:7  2=STUDENT:4  3=ADMIN:0  ← admin 登录后看不到任何菜单

-- 问题2：教师角色拥有 user:list(6) 和 config:list(7)，权限过宽
SELECT r.role_code, p.perm_code
FROM sys_role_permission rp
JOIN sys_role r ON r.role_id = rp.role_id
JOIN sys_permission p ON p.permission_id = rp.permission_id
WHERE r.role_code = 'TEACHER' ORDER BY p.permission_id;

-- 问题3：密码明文 '123456'
SELECT user_id, username, password FROM sys_user;
```

修复（3.0 节和第 5.6 节会用到，先记着，别现在执行）：

```sql
-- 给管理员补齐全部现有权限（INSERT IGNORE 依赖 uk_role_perm 唯一索引，可重复执行）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 3, permission_id FROM sys_permission;

-- 收缩教师权限（确认教师不该管用户/配置后再执行）
DELETE FROM sys_role_permission WHERE role_id = 1 AND permission_id IN (6, 7);
```

---

## 1. 权限模型对齐

### 1.1 关系映射（基于真实外键与字段）

```
sys_user.role_id  ──N:1──▶  sys_role.role_id            【单角色，一个用户只能有一个角色】
sys_role.role_id  ──M:N──▶  sys_permission.permission_id  （通过 sys_role_permission）
sys_permission.parent_id ──自关联──▶ sys_permission.permission_id   （0 = 顶级菜单）
```

结论：**用户↔角色是一对多（单角色），角色↔权限是多对多**。

`sys_user.role_id` 是 `NOT NULL` 且没有 `sys_user_role` 关联表，所以：

- ✅ 一个用户只有一个角色，`LoginUser` 里 `roles` 永远是长度 ≤1 的集合；
- ❌ 无法"给某个人单独多发一个权限"，只能给整个角色加权限（影响该角色下所有人）；
- ❌ 无法一人多角色（比如某人既是教师又是管理员）。

如果业务确实需要一人多角色或个别授权，**这部分需要补**：一张 `sys_user_role(user_id, role_id)` 关联表，或一张 `sys_user_permission(user_id, permission_id)` 用户直授权表。本文不建表，只在 1.4 说明影响。

### 1.2 菜单 / 按钮 / 接口如何共用一张表

`sys_permission.perm_type` 就是为此设计的：`1=菜单`、`2=按钮`、`3=接口`。

| perm_type | 是否有 `perm_path` | 前端怎么用 | 后端怎么用 |
|---|---|---|---|
| 1 菜单 | 有，如 `/training` | 生成路由 + 渲染侧边栏 | 通常不校验（能进页面靠路由） |
| 2 按钮 | 无（NULL） | `v-hasPerm` 控制显示/禁用 | 也可作为接口鉴权码复用 |
| 3 接口 | 无 | 不参与 | 只做 `hasAuthority` 校验 |

现有 7 条数据里，`perm_type=1` 的 5 条有 `perm_path`，`perm_type=2` 的 2 条 `perm_path` 为 NULL —— 这个约定是自洽的，照用即可。

**关键点**：菜单和按钮在**同一张表、同一个 `parent_id` 树上**，`parent_id` 只用于后台"角色授权"界面的树形勾选展示。前端生成路由时**必须只取 `perm_type = 1`**，否则按钮会被当成路由去 `addRoute`，页面直接白屏。

### 1.3 数据范围（本项目没有 `data_scope` 字段）

表里没有任何"数据范围"字段，但业务上天然有三档，靠**真实业务列**实现：

| 角色 | 数据范围 | 依赖的真实字段 | 过滤写法 |
|---|---|---|---|
| ADMIN | 全部 | 无 | 不加条件 |
| TEACHER | 自己发布的实训及其成果 | `training.teacher_id` | `AND t.teacher_id = #{userId}` |
| STUDENT | 只有自己的成果 | `train_result.student_id` / `evaluation.student_id` | `AND r.student_id = #{userId}` |
| （更细）按班 | 本班学生 | `sys_user.class_id` | `AND u.class_id = #{classId}` |

所以**数据范围不需要新字段也能做**，只是"范围"目前是硬编码在代码里的角色→范围映射。若要可配置（管理员能在界面上改某角色能看到多少数据），**需要补** `sys_role.data_scope` 字段，取值如 `1=全部 2=本部门/本班 3=仅本人`。

另外注意：`training` 与 `class` 之间**没有关联表**，所以"教师只能看自己所带班级"这个需求目前无法表达，需要补 `training.class_id` 或教师-班级关系表。当前 `training` 表只有 `teacher_id`，只能做到"教师看自己发布的实训"。

### 1.4 需要补什么（只说明，不写 DDL）

| 缺口 | 影响 | 是否必须 |
|---|---|---|
| `sys_user_role` 关联表 | 无法一人多角色 | 非必须（当前单角色够用） |
| `sys_user_permission` 用户直授权表 | 无法给个别人单独加权限 | 非必须 |
| `sys_role.data_scope` | 数据范围只能写死在代码里 | 建议补 |
| `sys_permission.component` | 后端无法告知前端"这个菜单对应哪个 vue 文件"，只能靠前端 path→component 约定 | 建议补（第 4 章给了不改表的替代方案） |
| `sys_permission.visible` / `hidden` | 无法配"有权限但不显示在侧边栏"的页面 | 可选 |
| `sys_user.status` | 无法禁用账号（只能删） | 建议补 |
| `sys_permission.perm_code` 唯一索引 | 权限码可重复录入，`hasAuthority` 语义会乱 | **建议补** |
| `sys_user.username` 唯一索引 | 可注册同名账号，登录查出多行 | **必须补** |
| `sys_user.password` 存 BCrypt | 现在是明文，`123456` 直接可见 | **必须改**（第 3.2 步） |

> 上表只列"缺什么、为什么缺"，具体字段类型由你按需确定，本文不提供建表语句。

---

## 2. 后端：从零加到能鉴权（按顺序做）

### 2.1 第 1 步：补依赖

`backend/pom.xml` 的 `<dependencies>` 里加三个（**不要**加 MyBatis-Plus，它目前没有适配 Spring Boot 4 的正式版本，会启动失败）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<!-- JWT，纯 Java，无 Spring 版本耦合 -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

> `spring-boot-starter-jdbc` 自带 `spring-jdbc` 和 HikariCP，用 `JdbcClient` 写 SQL 就够了。如果你的环境确实能跑 MyBatis-Plus（比如手动降 Boot 版本到 3.5.x），把 `JdbcClient` 换成 Mapper 即可，SQL 完全一样。

> 你的 pom 里用的是 Boot 4 的新命名 `spring-boot-starter-webmvc`。如果上面某个 starter 在 Boot 4 里改了名导致依赖飘红，直接在 IDEA 的 Maven 面板里按 `spring-boot-starter-*` 搜一下现有可用版本，换名字即可，代码不受影响。

> `JdbcClient.query(XxxEntity.class)` 默认按"下划线转驼峰"匹配列名（`permission_id` → `permissionId`）。如果你的实体字段名和列名对不上，改成显式映射：
> ```java
> .query((rs, rowNum) -> {
>     SysPermission p = new SysPermission();
>     p.setPermissionId(rs.getLong("permission_id"));
>     // ...
>     return p;
> })
> ```

### 2.2 第 2 步：修 `application.properties`

现在这个文件的中文注释已经乱码（`# MySQL??????????????`），整段重写为 UTF-8：

```properties
server.port=8080

spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/training_eval?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
spring.datasource.username=root
spring.datasource.password=你的密码

# JWT 配置：secret 至少 32 字节，生产环境放环境变量
app.jwt.secret=training-eval-rbac-secret-key-please-change-me-2026
# token 有效期（秒），8 小时
app.jwt.expire-seconds=28800
```

> 注意 URL 里的 `...` 和 `&` 在 `.properties` 里不需要转义，但**别加空格**。

### 2.3 第 3 步：建包结构

在 `backend/src/main/java/org/example/backend/` 下新建下面的结构（一步步创建，不用一次建完）：

```
org.example.backend
├── common/           R.java（统一响应）、BizException.java、GlobalExceptionHandler.java
├── security/         LoginUser.java、JwtService.java、JwtAuthFilter.java、SecurityConfig.java
├── auth/             AuthController.java、AuthService.java、LoginDTO.java、LoginVO.java、UserInfoVO.java
├── system/
│   ├── entity/       SysUser.java、SysRole.java、SysPermission.java
│   ├── mapper/       SysUserMapper.java、SysPermissionMapper.java、SysRolePermissionMapper.java
│   └── service/      PermissionService.java、PermissionCacheService.java
└── config/           DataScopeResolver.java（数据范围，第 3.6 步）
```

### 2.4 第 4 步：实体（字段严格对应真实列）

`system/entity/SysUser.java`：

```java
package org.example.backend.system.entity;

public class SysUser {
    private Long userId;       // user_id
    private String username;   // username
    private String password;   // password
    private String realName;   // real_name
    private Long roleId;       // role_id
    private Long classId;      // class_id
    // getter / setter 省略，请自行生成
}
```

`system/entity/SysRole.java`：

```java
package org.example.backend.system.entity;

public class SysRole {
    private Long roleId;     // role_id
    private String roleName; // role_name
    private String roleCode; // role_code   TEACHER / STUDENT / ADMIN
    // getter / setter 省略
}
```

`system/entity/SysPermission.java`：

```java
package org.example.backend.system.entity;

public class SysPermission {
    private Long permissionId;  // permission_id
    private Long parentId;      // parent_id，0 为顶级菜单
    private String permName;    // perm_name
    private Integer permType;   // perm_type  1=菜单 2=按钮 3=接口
    private String permCode;    // perm_code
    private String permPath;    // perm_path，按钮为 null
    private String icon;        // icon
    private Integer sort;       // sort
    // getter / setter 省略
}
```

### 2.5 第 5 步：Mapper（`JdbcClient`，SQL 全部来自真实列名）

`system/mapper/SysUserMapper.java`：

```java
package org.example.backend.system.mapper;

import org.example.backend.system.entity.SysUser;
import org.example.backend.system.entity.SysRole;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SysUserMapper {

    private final JdbcClient jdbc;

    public SysUserMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 按用户名查用户。注意：表里没有 status/deleted 字段，所以不加任何状态过滤 */
    public Optional<SysUser> selectByUsername(String username) {
        return jdbc.sql("""
                SELECT user_id, username, password, real_name, role_id, class_id
                FROM sys_user
                WHERE username = :username
                """)
                .param("username", username)
                .query(SysUser.class)
                .optional();
    }

    public Optional<SysUser> selectById(Long userId) {
        return jdbc.sql("""
                SELECT user_id, username, password, real_name, role_id, class_id
                FROM sys_user WHERE user_id = :userId
                """)
                .param("userId", userId)
                .query(SysUser.class)
                .optional();
    }

    /** 按 userId 查角色（单角色模型，最多 1 行） */
    public Optional<SysRole> selectRoleByUserId(Long userId) {
        return jdbc.sql("""
                SELECT r.role_id, r.role_name, r.role_code
                FROM sys_user u
                INNER JOIN sys_role r ON r.role_id = u.role_id
                WHERE u.user_id = :userId
                """)
                .param("userId", userId)
                .query(SysRole.class)
                .optional();
    }

    /** 权限变更后按角色反查受影响用户，用于精准清缓存 */
    public java.util.List<Long> selectUserIdsByRoleId(Long roleId) {
        return jdbc.sql("SELECT user_id FROM sys_user WHERE role_id = :roleId")
                .param("roleId", roleId)
                .query(Long.class)
                .list();
    }

    /** 明文密码一次性升级为 BCrypt 用 */
    public java.util.List<SysUser> selectAllForPasswordUpgrade() {
        return jdbc.sql("SELECT user_id, username, password, real_name, role_id, class_id FROM sys_user")
                .query(SysUser.class).list();
    }

    public void updatePassword(Long userId, String encoded) {
        jdbc.sql("UPDATE sys_user SET password = :pwd WHERE user_id = :userId")
                .param("pwd", encoded).param("userId", userId).update();
    }

    /** 管理员给用户改角色：单角色模型下就是一条 UPDATE */
    public void updateRoleId(Long userId, Long roleId) {
        jdbc.sql("UPDATE sys_user SET role_id = :roleId WHERE user_id = :userId")
                .param("roleId", roleId).param("userId", userId).update();
    }
}
```

`system/mapper/SysPermissionMapper.java`：

```java
package org.example.backend.system.mapper;

import org.example.backend.system.entity.SysPermission;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SysPermissionMapper {

    private final JdbcClient jdbc;

    public SysPermissionMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 按 userId 查全部权限（菜单+按钮+接口），去重，按 sort 排序 */
    public List<SysPermission> selectByUserId(Long userId) {
        return jdbc.sql("""
                SELECT DISTINCT p.permission_id, p.parent_id, p.perm_name, p.perm_type,
                                p.perm_code, p.perm_path, p.icon, p.sort
                FROM sys_user u
                INNER JOIN sys_role_permission rp ON rp.role_id = u.role_id
                INNER JOIN sys_permission p ON p.permission_id = rp.permission_id
                WHERE u.user_id = :userId
                ORDER BY p.sort ASC, p.permission_id ASC
                """)
                .param("userId", userId)
                .query(SysPermission.class)
                .list();
    }

    /** 只取菜单（perm_type = 1），用于前端生成路由和侧边栏 */
    public List<SysPermission> selectMenusByUserId(Long userId) {
        return jdbc.sql("""
                SELECT DISTINCT p.permission_id, p.parent_id, p.perm_name, p.perm_type,
                                p.perm_code, p.perm_path, p.icon, p.sort
                FROM sys_user u
                INNER JOIN sys_role_permission rp ON rp.role_id = u.role_id
                INNER JOIN sys_permission p ON p.permission_id = rp.permission_id
                WHERE u.user_id = :userId AND p.perm_type = 1
                ORDER BY p.sort ASC, p.permission_id ASC
                """)
                .param("userId", userId)
                .query(SysPermission.class)
                .list();
    }

    /** 校验某角色是否有某权限码（自测/后台授权界面用） */
    public boolean existsByRoleAndCode(Long roleId, String permCode) {
        Integer cnt = jdbc.sql("""
                SELECT COUNT(1)
                FROM sys_role_permission rp
                INNER JOIN sys_permission p ON p.permission_id = rp.permission_id
                WHERE rp.role_id = :roleId AND p.perm_code = :permCode
                """)
                .param("roleId", roleId).param("permCode", permCode)
                .query(Integer.class).single();
        return cnt != null && cnt > 0;
    }
}
```

`system/mapper/SysRolePermissionMapper.java`：

```java
package org.example.backend.system.mapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SysRolePermissionMapper {

    private final JdbcClient jdbc;

    public SysRolePermissionMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 覆盖式授权：先删后插。FK 是 ON DELETE RESTRICT，删父表前必须先删这里 */
    public void deleteByRoleId(Long roleId) {
        jdbc.sql("DELETE FROM sys_role_permission WHERE role_id = :roleId")
                .param("roleId", roleId).update();
    }

    /** 批量插入。uk_role_perm 唯一索引保证重复不会插两行，配合 INSERT IGNORE 更稳 */
    public void batchInsert(Long roleId, List<Long> permissionIds) {
        for (Long pid : permissionIds) {
            jdbc.sql("INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (:rid, :pid)")
                    .param("rid", roleId).param("pid", pid).update();
        }
    }
}
```

> 这三个 Mapper 里的 SQL 就是本文档后面所有"按 userId 查角色/查权限"的**唯一出处**，字段名全部来自 `information_schema` 里的真实定义，没有一个臆测列。

### 2.6 第 6 步：`LoginUser`（权限的载体）

`security/LoginUser.java`：

```java
package org.example.backend.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;

public class LoginUser implements UserDetails {

    private Long userId;
    private String username;
    private String realName;
    private Long roleId;
    private String roleCode;
    private String roleName;
    private Long classId;
    private String password;

    /** 角色码集合，如 ["TEACHER"]。对应 sys_role.role_code */
    private Set<String> roles = new HashSet<>();
    /** 权限码集合，如 ["training:list","training:publish"]。对应 sys_permission.perm_code */
    private Set<String> permissions = new HashSet<>();
    /** 数据范围：ALL / TEACHER_SELF / STUDENT_SELF，第 3.6 步用 */
    private String dataScope;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> list = new ArrayList<>();
        // 角色统一加 ROLE_ 前缀，方便 hasRole('ADMIN')
        roles.forEach(r -> list.add(new SimpleGrantedAuthority("ROLE_" + r)));
        // 权限码原样放入，对应 hasAuthority('training:publish')
        permissions.forEach(p -> list.add(new SimpleGrantedAuthority(p)));
        return list;
    }

    @Override public String getPassword()          { return password; }
    @Override public String getUsername()          { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked()  { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()           { return true; }  // 表里没有 status 字段，恒 true

    // 其余 getter/setter 省略
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public void setPassword(String password) { this.password = password; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }
    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }
}
```

**为什么把 `permissions` 直接当 `GrantedAuthority`**：这样 `hasAuthority('training:publish')` 才能生效，不需要再写一层转换。

### 2.7 第 7 步：`JwtService`

`security/JwtService.java`：

```java
package org.example.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expireSeconds;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expire-seconds}") long expireSeconds) {
        // HS256 要求 key >= 32 字节，secret 太短会直接抛 WeakKeyException
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    public String createToken(Long userId, String username, String roleCode) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireSeconds * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", roleCode)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /** 解析失败（签名不对/过期/格式错）会抛 JwtException，调用方统一按 401 处理 */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}
```

> **JWT 里只放 userId / username / role，不放权限码**。权限码放 token 会导致改权限必须等 token 过期才生效——这是第 5.3 条坑的根源。

### 2.8 第 8 步：`JwtAuthFilter`（每次请求把权限装进 SecurityContext）

`security/JwtAuthFilter.java`：

```java
package org.example.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.backend.system.service.PermissionService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final PermissionService permissionService;

    public JwtAuthFilter(JwtService jwtService, PermissionService permissionService) {
        this.jwtService = jwtService;
        this.permissionService = permissionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                Long userId = Long.valueOf(claims.getSubject());

                // 每次请求都从缓存/库里重建 LoginUser —— 权限改了立刻生效
                LoginUser loginUser = permissionService.buildLoginUser(userId);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException e) {
                // 解析失败不抛异常，交给 Security 的 EntryPoint 返回 401
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
```

### 2.9 第 9 步：`SecurityConfig`（含"默认拒绝"）

`security/SecurityConfig.java`：

```java
package org.example.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.common.R;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity   // Spring Security 6/7 用这个，@EnableGlobalMethodSecurity 已废弃
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})   // 复用已有的 CorsConfig
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/test").permitAll()
                .anyRequest().authenticated()     // ① 兜底：必须登录
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> write(res, HttpStatus.UNAUTHORIZED.value(), "未登录或登录已过期"))
                .accessDeniedHandler((req, res, e) -> write(res, HttpStatus.FORBIDDEN.value(), "没有访问权限"))
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void write(jakarta.servlet.http.HttpServletResponse res, int code, String msg) throws java.io.IOException {
        res.setStatus(code);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(objectMapper.writeValueAsString(R.fail(code, msg)));
    }
}
```

> `anyRequest().authenticated()` **只保证"登录了"，不保证"有权限"**。真正保证"新接口必须登记权限码"的是 2.10 的启动扫描器，这一步不能省。

### 2.10 第 10 步：`PermissionService` + 缓存 + 权限版本号

`system/service/PermissionCacheService.java`（先不做 Redis，用本地 `ConcurrentHashMap`，单机跑得通；第 2.13 步给 Redis 版）：

```java
package org.example.backend.system.service;

import org.example.backend.security.LoginUser;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PermissionCacheService {

    /** userId -> (权限版本号, LoginUser) */
    private record Entry(int version, LoginUser user) {}

    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    public LoginUser get(Long userId) {
        Entry e = cache.get(userId);
        return e == null ? null : e.user();
    }

    public int version(Long userId) {
        Entry e = cache.get(userId);
        return e == null ? 0 : e.version();
    }

    public void put(Long userId, LoginUser user) {
        int v = version(userId) + 1;
        cache.put(userId, new Entry(v, user));
    }

    /** 只递增版本号并丢弃旧对象 —— 下一次请求会重建 */
    public void invalidate(Long userId) {
        Entry e = cache.get(userId);
        if (e == null) {
            cache.put(userId, new Entry(1, null));
        } else {
            cache.put(userId, new Entry(e.version() + 1, null));
        }
    }
}
```

`system/service/PermissionService.java`：

```java
package org.example.backend.system.service;

import org.example.backend.security.LoginUser;
import org.example.backend.system.entity.SysPermission;
import org.example.backend.system.entity.SysRole;
import org.example.backend.system.entity.SysUser;
import org.example.backend.system.mapper.SysPermissionMapper;
import org.example.backend.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;
    private final PermissionCacheService cache;

    public PermissionService(SysUserMapper userMapper,
                             SysPermissionMapper permissionMapper,
                             PermissionCacheService cache) {
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
        this.cache = cache;
    }

    /** 组装 LoginUser：这是整个 RBAC 的核心，登录和每次请求都走它 */
    public LoginUser buildLoginUser(Long userId) {
        LoginUser cached = cache.get(userId);
        if (cached != null) return cached;

        SysUser user = userMapper.selectById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + userId));
        SysRole role = userMapper.selectRoleByUserId(userId)
                .orElseThrow(() -> new RuntimeException("用户未绑定角色: " + userId));

        List<SysPermission> perms = permissionMapper.selectByUserId(userId);
        Set<String> codes = perms.stream()
                .map(SysPermission::getPermCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        LoginUser lu = new LoginUser();
        lu.setUserId(user.getUserId());
        lu.setUsername(user.getUsername());
        lu.setRealName(user.getRealName());
        lu.setClassId(user.getClassId());
        lu.setPassword(user.getPassword());
        lu.setRoleId(role.getRoleId());
        lu.setRoleCode(role.getRoleCode());
        lu.setRoleName(role.getRoleName());
        lu.setRoles(Set.of(role.getRoleCode()));
        lu.setPermissions(codes);
        lu.setDataScope(resolveScope(role.getRoleCode()));

        cache.put(userId, lu);
        return lu;
    }

    /** 表里没有 data_scope 字段，先用角色码集中映射一处，避免散落 */
    private String resolveScope(String roleCode) {
        return switch (roleCode) {
            case "ADMIN"   -> "ALL";
            case "TEACHER" -> "TEACHER_SELF";
            default        -> "STUDENT_SELF";
        };
    }

    public List<SysPermission> menusOf(Long userId) {
        return permissionMapper.selectMenusByUserId(userId);
    }

    public void evict(Long userId) {
        cache.invalidate(userId);
    }
}
```

### 2.11 第 11 步：启动时扫描"未登记权限码"的接口（fail-closed 的真正保障）

`security/PermissionCoverageChecker.java`：

```java
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

/**
 * 启动即校验：每个 Controller 方法必须有 @PreAuthorize / @RequiresPerm / @PublicApi 之一，
 * 否则直接启动失败。这样"新加接口忘了配权限码"不可能上线。
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

            boolean registered = hm.hasMethodAnnotation(PreAuthorize.class)
                    || hm.hasMethodAnnotation(RequiresPerm.class)
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
```

配套两个注解（`security/RequiresPerm.java`、`security/PublicApi.java`）：

```java
package org.example.backend.security;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPerm {
    String[] value();
    /** 多个权限码之间的关系，默认全部满足 */
    Logical logical() default Logical.AND;

    enum Logical { AND, OR }
}
```

```java
package org.example.backend.security;

import java.lang.annotation.*;

/** 标记"允许匿名访问"，只有它和上面两个注解能通过启动扫描 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicApi {
}
```

> 已有的 `TestController` 会被扫描到 → 给它方法上加 `@PublicApi`，否则启动直接报错。这正是我们要的效果。

### 2.12 第 12 步：登录接口 + getInfo 接口

`common/R.java`：

```java
package org.example.backend.common;

public class R<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> R<T> ok(T data) { return new R<>(200, "ok", data); }
    public static <T> R<T> fail(int code, String msg) { return new R<>(code, msg, null); }

    public R() {}
    public R(int code, String msg, T data) { this.code = code; this.msg = msg; this.data = data; }
    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
```

`auth/LoginDTO.java`：

```java
package org.example.backend.auth;

public class LoginDTO {
    private String username;
    private String password;
    // getter/setter 省略
}
```

`auth/LoginVO.java`（登录响应）：

```java
package org.example.backend.auth;

public class LoginVO {
    private String token;
    private long expiresIn;
    // getter/setter、构造器省略
}
```

`auth/UserInfoVO.java`（getInfo 响应，字段名与表列一一对应）：

```java
package org.example.backend.auth;

import org.example.backend.system.entity.SysPermission;

import java.util.List;
import java.util.Set;

public class UserInfoVO {
    private Long userId;        // sys_user.user_id
    private String username;    // sys_user.username
    private String realName;    // sys_user.real_name
    private Long roleId;        // sys_user.role_id
    private String roleCode;    // sys_role.role_code
    private String roleName;    // sys_role.role_name
    private Long classId;       // sys_user.class_id
    private Set<String> roles;        // ["TEACHER"]
    private Set<String> permissions;  // ["training:list", "training:publish", ...]
    private List<SysPermission> menus; // 只含 perm_type = 1 的树
    private String dataScope;         // ALL / TEACHER_SELF / STUDENT_SELF
    // getter/setter 省略
}
```

`auth/AuthService.java`：

```java
package org.example.backend.auth;

import org.example.backend.common.BizException;
import org.example.backend.security.JwtService;
import org.example.backend.security.LoginUser;
import org.example.backend.system.entity.SysPermission;
import org.example.backend.system.entity.SysUser;
import org.example.backend.system.mapper.SysUserMapper;
import org.example.backend.system.service.PermissionService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final PermissionService permissionService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(SysUserMapper userMapper, PermissionService permissionService,
                       JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.permissionService = permissionService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginVO login(LoginDTO dto) {
        SysUser user = userMapper.selectByUsername(dto.getUsername())
                .orElseThrow(() -> new BizException(4001, "用户名或密码错误"));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(4001, "用户名或密码错误");
        }

        // 登录时就构建一次，顺便校验角色是否配置完整
        LoginUser lu = permissionService.buildLoginUser(user.getUserId());
        String token = jwtService.createToken(lu.getUserId(), lu.getUsername(), lu.getRoleCode());
        return new LoginVO(token, jwtService.getExpireSeconds());
    }

    public UserInfoVO getInfo(LoginUser lu) {
        List<SysPermission> menus = permissionService.menusOf(lu.getUserId());

        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(lu.getUserId());
        vo.setUsername(lu.getUsername());
        vo.setRealName(lu.getRealName());
        vo.setRoleId(lu.getRoleId());
        vo.setRoleCode(lu.getRoleCode());
        vo.setRoleName(lu.getRoleName());
        vo.setClassId(lu.getClassId());
        vo.setRoles(lu.getRoles());
        vo.setPermissions(lu.getPermissions());
        vo.setDataScope(lu.getDataScope());
        vo.setMenus(buildTree(menus));
        return vo;
    }

    /** parent_id = 0 为根；孤儿节点（父节点没授权）提升为根，避免菜单消失 */
    private List<SysPermission> buildTree(List<SysPermission> flat) {
        Map<Long, SysPermission> nodeMap = new LinkedHashMap<>();
        flat.forEach(p -> {
            p.setChildren(new ArrayList<>());
            nodeMap.put(p.getPermissionId(), p);
        });

        List<SysPermission> roots = new ArrayList<>();
        for (SysPermission p : flat) {
            Long pid = p.getParentId() == null ? 0L : p.getParentId();
            SysPermission parent = nodeMap.get(pid);
            if (pid == 0L || parent == null) {
                roots.add(p);          // 顶层 或 孤儿 → 都当根
            } else {
                parent.getChildren().add(p);
            }
        }
        return roots;
    }
}
```

> `SysPermission` 需要加一个 `private List<SysPermission> children = new ArrayList<>();` 字段（不加 `@TableField`，Jackson 会自动序列化成 `children`）。**这个字段不是表里的列**，只是返回给前端的树结构载体。

`auth/AuthController.java`：

```java
package org.example.backend.auth;

import org.example.backend.common.R;
import org.example.backend.security.LoginUser;
import org.example.backend.security.PublicApi;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PublicApi
    @PostMapping("/login")
    public R<LoginVO> login(@RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    // 注意：必须给个权限注解，否则第 2.11 步的启动扫描会让应用起不来。
    // 这里只要求"登录"，不要求具体权限码。
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/getInfo")
    public R<UserInfoVO> getInfo(@AuthenticationPrincipal LoginUser loginUser) {
        return R.ok(authService.getInfo(loginUser));
    }
}
```

`common/BizException.java` + `GlobalExceptionHandler`：

```java
package org.example.backend.common;

public class BizException extends RuntimeException {
    private final int code;
    public BizException(int code, String msg) { super(msg); this.code = code; }
    public int getCode() { return code; }
}
```

```java
package org.example.backend.common;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    // 关键：@PreAuthorize 抛的 AccessDeniedException 必须原样往上传，
    // 否则会被 @RestControllerAdvice 吞成 200，前端以为成功
    @ExceptionHandler(AccessDeniedException.class)
    public void handleDenied(AccessDeniedException e) throws AccessDeniedException {
        throw e;
    }
}
```

> 这条注释是踩坑重点：`@RestControllerAdvice` 如果加了 `@ExceptionHandler(Exception.class)` 兜底，会把 Spring Security 的 `AccessDeniedException` 转成 200 响应，导致"该 403 的接口返回成功"。要么不加全局兜底，要么像上面这样显式 rethrow。

### 2.13 第 13 步：把明文密码一次性升级成 BCrypt

`config/PasswordUpgradeRunner.java`（**只跑一次，跑完删掉或加开关**）：

```java
package org.example.backend.config;

import org.example.backend.system.entity.SysUser;
import org.example.backend.system.mapper.SysUserMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordUpgradeRunner implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder encoder;

    public PasswordUpgradeRunner(SysUserMapper userMapper, PasswordEncoder encoder) {
        this.userMapper = userMapper;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (SysUser u : userMapper.selectAllForPasswordUpgrade()) {
            String pwd = u.getPassword();
            // BCrypt 密文固定以 $2a$ / $2b$ / $2y$ 开头，已经是密文就跳过
            if (pwd != null && (pwd.startsWith("$2a$") || pwd.startsWith("$2b$") || pwd.startsWith("$2y$"))) {
                continue;
            }
            userMapper.updatePassword(u.getUserId(), encoder.encode(pwd));
        }
    }
}
```

跑完确认：

```sql
SELECT user_id, username, LEFT(password, 7) AS prefix FROM sys_user;
-- 期望全部是 $2a$10$
```

### 2.14 第 14 步：接口契约

**登录**

```
POST /api/auth/login
Content-Type: application/json

{ "username": "teacher01", "password": "123456" }
```

响应：

```json
{
  "code": 200,
  "msg": "ok",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwi...",
    "expiresIn": 28800
  }
}
```

**取用户信息与权限**（前端守卫调用）

```
GET /api/auth/getInfo
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

响应：

```json
{
  "code": 200,
  "msg": "ok",
  "data": {
    "userId": 1,
    "username": "teacher01",
    "realName": "张老师",
    "roleId": 1,
    "roleCode": "TEACHER",
    "roleName": "教师",
    "classId": null,
    "roles": ["TEACHER"],
    "permissions": ["training:list", "training:publish", "training:submit", "ai:list", "evaluation:list"],
    "dataScope": "TEACHER_SELF",
    "menus": [
      {
        "permissionId": 1, "parentId": 0, "permName": "实训管理", "permType": 1,
        "permCode": "training:list", "permPath": "/training", "icon": null, "sort": 1,
        "children": [
          { "permissionId": 2, "parentId": 1, "permName": "发布实训", "permType": 2,
            "permCode": "training:publish", "permPath": null, "icon": null, "sort": 1, "children": [] }
        ]
      },
      { "permissionId": 4, "parentId": 0, "permName": "AI评测", "permType": 1,
        "permCode": "ai:list", "permPath": "/ai-check", "icon": null, "sort": 2, "children": [] }
    ]
  }
}
```

> `menus` 里只含 `perm_type = 1`。按钮权限通过 `permissions` 数组下发就够了，不要塞进 `menus` 当路由用。

### 2.15 第 15 步：两种接口鉴权写法（各写一份，任选其一或混用）

**写法 A：`@PreAuthorize`（推荐，零额外代码）**

```java
@RestController
@RequestMapping("/api/training")
public class TrainingController {

    @PreAuthorize("hasAuthority('training:publish')")
    @PostMapping("/{trainingId}/publish")
    public R<Void> publish(@PathVariable Long trainingId,
                           @AuthenticationPrincipal LoginUser user) {
        // 业务代码
        return R.ok(null);
    }

    // 需要同时具备两个权限
    @PreAuthorize("hasAuthority('training:list') and hasAuthority('evaluation:list')")
    @GetMapping("/stat")
    public R<Object> stat() { return R.ok(null); }

    // 按角色
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{trainingId}")
    public R<Void> delete(@PathVariable Long trainingId) { return R.ok(null); }
}
```

**写法 B：自定义注解 + 拦截器（适合想统一返回体、想记日志的场景）**

`security/PermissionInterceptor.java`：

```java
package org.example.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) return true;

        RequiresPerm ann = hm.getMethodAnnotation(RequiresPerm.class);
        if (ann == null) ann = hm.getBeanType().getAnnotation(RequiresPerm.class);
        if (ann == null) return true;   // 没标注 → 交给 @PreAuthorize 或已通过认证

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("未登录");
        }

        Set<String> owned = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean pass = ann.logical() == RequiresPerm.Logical.AND
                ? Arrays.stream(ann.value()).allMatch(owned::contains)
                : Arrays.stream(ann.value()).anyMatch(owned::contains);

        if (!pass) {
            throw new AccessDeniedException("缺少权限: " + Arrays.toString(ann.value()));
        }
        return true;
    }
}
```

注册拦截器（`config/WebConfig.java`）：

```java
package org.example.backend.config;

import org.example.backend.security.PermissionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PermissionInterceptor permissionInterceptor;

    public WebConfig(PermissionInterceptor permissionInterceptor) {
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionInterceptor).addPathPatterns("/api/**");
    }
}
```

用法：

```java
@RequiresPerm("training:publish")
@PostMapping("/{trainingId}/publish")
public R<Void> publish(@PathVariable Long trainingId) { ... }

@RequiresPerm(value = {"training:list", "evaluation:list"}, logical = RequiresPerm.Logical.OR)
@GetMapping("/stat")
public R<Object> stat() { ... }
```

**两种写法怎么选**：`@PreAuthorize` 适合细粒度表达式（`and`/`or`/`hasRole`）；自定义注解适合"一个权限码对应一批接口"或需要在拦截器里写审计日志。两者可以共存，`@PreAuthorize` 在方法调用时生效、拦截器在进入 Controller 前生效。

### 2.16 第 16 步：数据范围参与过滤（防越权查别人数据）

`config/DataScopeResolver.java`：

```java
package org.example.backend.config;

import org.example.backend.security.LoginUser;
import org.springframework.stereotype.Component;

@Component
public class DataScopeResolver {

    /** 返回 null 表示不过滤（ADMIN）；否则返回需要强制注入的 userId */
    public Long forcedUserId(LoginUser user) {
        return switch (user.getDataScope()) {
            case "ALL" -> null;
            case "TEACHER_SELF", "STUDENT_SELF" -> user.getUserId();
            default -> user.getUserId();   // 未知范围一律按最严格处理
        };
    }
}
```

`train_result`（学生提交成果）分页查询 SQL：

```sql
SELECT r.result_id, r.training_id, r.student_id, r.file_name, r.file_type, r.submit_time
FROM train_result r
INNER JOIN training t ON t.training_id = r.training_id
WHERE 1 = 1
  /* TEACHER：只能看自己发布的实训下的成果 */
  AND (:teacherId IS NULL OR t.teacher_id = :teacherId)
  /* STUDENT：只能看自己的成果 */
  AND (:studentId IS NULL OR r.student_id = :studentId)
ORDER BY r.submit_time DESC
```

Java 侧调用（**关键：`studentId`/`teacherId` 只能从 `LoginUser` 取，绝不能从请求参数取**）：

```java
@GetMapping("/api/result/page")
@PreAuthorize("hasAuthority('training:list')")
public R<List<Object>> page(@AuthenticationPrincipal LoginUser user,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "10") int size) {
    Long teacherId = "TEACHER_SELF".equals(user.getDataScope()) ? user.getUserId() : null;
    Long studentId = "STUDENT_SELF".equals(user.getDataScope()) ? user.getUserId() : null;
    // 把两个参数传进 Mapper，SQL 里用 IS NULL 兜底
    return R.ok(resultMapper.page(teacherId, studentId, page, size));
}
```

> 为什么不用"前端传 studentId 后台再校验"：一旦某处漏校验就是越权。让**范围参数只能由服务端从 token 里取**，是最不容易出错的做法。

### 2.17 第 17 步：权限变更后立即失效

单机版（配合 2.10 的 `PermissionCacheService`）：

```java
package org.example.backend.system.service;

import org.example.backend.system.mapper.SysRolePermissionMapper;
import org.example.backend.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class RolePermissionService {

    private final SysRolePermissionMapper rpMapper;
    private final SysUserMapper userMapper;
    private final PermissionService permissionService;

    public RolePermissionService(SysRolePermissionMapper rpMapper, SysUserMapper userMapper,
                                 PermissionService permissionService) {
        this.rpMapper = rpMapper;
        this.userMapper = userMapper;
        this.permissionService = permissionService;
    }

    /** 后台"角色授权"保存：覆盖式 */
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        rpMapper.deleteByRoleId(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            rpMapper.batchInsert(roleId, permissionIds);
        }
        // 事务提交后再清缓存：否则回滚了缓存却已失效（虽然只是多查一次库，但语义要正确）
        afterCommit(() -> userMapper.selectUserIdsByRoleId(roleId)
                .forEach(permissionService::evict));
    }

    /** 后台"用户管理"改角色 */
    @Transactional(rollbackFor = Exception.class)
    public void changeUserRole(Long userId, Long roleId) {
        userMapper.updateRoleId(userId, roleId);
        afterCommit(() -> permissionService.evict(userId));
    }

    private void afterCommit(Runnable task) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { task.run(); }
        });
    }
}
```

**Redis 版（多实例部署时才需要）**，key 设计：

| key | 类型 | 说明 |
|---|---|---|
| `login:token:{uuid}` | String(JSON) | 登录态，value 为 LoginUser，TTL 30min，可踢人 |
| `login:uid:{userId}` | Set | 该用户所有登录 uuid，用于"按用户踢下线" |
| `perm:ver:{userId}` | String(int) | 权限版本号，`INCR` 即失效 |
| `perm:uid:{userId}:v{ver}` | Set | 权限码集合，key 里带版本号，旧 key 自然过期 |

```java
public void invalidateByUser(Long userId) {
    Set<String> uuids = redis.opsForSet().members("login:uid:" + userId);
    if (uuids != null) {
        uuids.forEach(u -> redis.delete("login:token:" + u));
        redis.delete("login:uid:" + userId);
    }
    redis.opsForValue().increment("perm:ver:" + userId);   // 版本+1，旧权限 key 不再被读到
}

public void invalidateByRole(Long roleId) {
    // 不要用 KEYS/SCAN 全库扫，按 role_id 反查用户精准失效
    userMapper.selectUserIdsByRoleId(roleId).forEach(this::invalidateByUser);
}
```

> 版本号编进 key 的好处：**不需要显式删权限缓存**，旧 key 靠 TTL 自然回收，避免"删了但删错 key"的问题。

---

## 3. 前端：从"写死路由"改成"权限驱动"

### 3.0 先解决一个必须做的对齐（否则一定白屏）

数据库里的 `perm_path` 是 **`/training`、`/ai-check`、`/evaluation`、`/system/user`、`/system/config`**；
你现有的前端路由是 **`/student/task`、`/teacher/taskManage`、`/admin/user`……**，**一个都对不上**。

两种处理方式，选一种：

- **方案 A（推荐，零文件移动）**：不动数据库的 path，在前端维护一张 `perm_path → 组件` 的映射表。缺点是多一个映射要维护。
- **方案 B**：把 `perm_path` 改成真实前端路径。缺点是要改 5 条数据，且 `/teacher/taskManage` 这种"带角色前缀"的路径和"菜单按功能共享"的语义冲突（学生也要看实训列表，但学生不该进 `/teacher/*`）。

本文用 **方案 A**，只补两条学生菜单数据（学生现有 4 条权限里没有"任务列表"和"提交成果"两个入口）：

```sql
-- 补两个学生菜单。parent_id 必须为 0：
-- 本文的侧边栏和 generateRoutes 都按"一层顶级菜单"实现，
-- 挂到 parent_id=1 会变成二级菜单，当前代码不会渲染/生成路由。
INSERT INTO sys_permission (parent_id, perm_name, perm_type, perm_code, perm_path, icon, sort)
VALUES (0, '我的实训任务', 1, 'training:my-list', '/training/list', NULL, 6),
       (0, '提交实训成果', 1, 'training:submit-page', '/training/submit', NULL, 2);

-- 绑定给学生角色（role_id=2）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 2, permission_id FROM sys_permission
WHERE perm_code IN ('training:my-list', 'training:submit-page');

-- 顺手把管理员的权限补齐（前面发现的 ADMIN 0 权限问题）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 3, permission_id FROM sys_permission;
```

验证：

```sql
SELECT r.role_code, COUNT(*) FROM sys_role_permission rp
JOIN sys_role r ON r.role_id = rp.role_id GROUP BY r.role_code;
```

### 3.1 第 18 步：修 `main.js` 的顺序（不改会报 `getActivePinia` 错误）

现在 `main.js` 是：

```js
app.use(router)
app.use(createPinia())   // ← router 先装了，守卫里 useUserStore() 会拿不到 pinia
```

改成：

```js
const app = createApp(App)
// ...图标注册
const pinia = createPinia()
app.use(pinia)      // 必须在 router 之前
app.use(router)
app.use(ElementPlus)
app.mount('#app')
```

### 3.2 第 19 步：改造 `stores/useUserStore.js`

```js
// frontend/src/stores/useUserStore.js
import { defineStore } from 'pinia'
import request from '@/utils/request'

export const useUserStore = defineStore('user', {
  state() {
    return {
      token: localStorage.getItem('token') || '',
      userId: null,
      username: '',
      realName: '',
      roleId: null,
      roleCode: '',
      roleName: '',
      classId: null,
      permissions: [],   // ['training:list', 'training:publish', ...]
      roles: [],
      dataScope: ''
    }
  },
  actions: {
    setToken(val) {
      this.token = val
      localStorage.setItem('token', val)
    },
    /** 登录：只拿 token，用户信息统一走 getInfo */
    async login(form) {
      const res = await request.post('/auth/login', form)
      if (res.code !== 200) throw new Error(res.msg)
      this.setToken(res.data.token)
    },
    /** 拉用户信息 + 权限 + 菜单 */
    async getInfo() {
      const res = await request.get('/auth/getInfo')
      if (res.code !== 200) throw new Error(res.msg)
      const d = res.data
      this.userId = d.userId
      this.username = d.username
      this.realName = d.realName
      this.roleId = d.roleId
      this.roleCode = d.roleCode
      this.roleName = d.roleName
      this.classId = d.classId
      this.roles = d.roles || []
      this.permissions = d.permissions || []
      this.dataScope = d.dataScope
      return d
    },
    /** 按钮级判断，v-hasPerm 和页面里都用它 */
    hasPerm(code) {
      return this.permissions.includes(code)
    },
    logout() {
      this.token = ''
      this.userId = null
      this.username = ''
      this.realName = ''
      this.roleId = null
      this.roleCode = ''
      this.roleName = ''
      this.classId = null
      this.permissions = []
      this.roles = []
      this.dataScope = ''
      localStorage.removeItem('token')
    }
  }
})
```

### 3.3 第 20 步：新增 `stores/usePermissionStore.js`

```js
// frontend/src/stores/usePermissionStore.js
import { defineStore } from 'pinia'

// 数据库缺 component 字段，这里按 perm_path 做映射（第 1.4 节说的替代方案）
// 新增菜单时，这里要同步加一行，否则该菜单会加载失败
const componentMap = {
  '/training':        () => import('@/views/layout/teacher/TaskManage.vue'),
  '/training/list':   () => import('@/views/layout/student/TaskList.vue'),
  '/training/submit': () => import('@/views/layout/student/SubmitWork.vue'),
  '/ai-check':        () => import('@/views/layout/teacher/WorkReview.vue'),
  '/evaluation':      () => import('@/views/layout/teacher/DataStat.vue'),
  '/system/user':     () => import('@/views/layout/admin/UserManage.vue'),
  '/system/config':   () => import('@/views/layout/admin/SysConfig.vue')
}

const Layout = () => import('@/components/LayoutMain.vue')

export const usePermissionStore = defineStore('permission', {
  state() {
    return {
      routesLoaded: false,
      menus: [],       // 后端返回的菜单树，侧边栏直接渲染它
      dynamicRoutes: []
    }
  },
  actions: {
    /** 把后端菜单树转成 vue-router 路由（只处理 perm_type=1，后端已过滤） */
    generateRoutes(menus) {
      this.menus = menus || []
      const children = this.menus.map(m => this.toRoute(m))
      // 所有业务页统一挂在 Layout 下
      const routes = [{
        path: '/',
        component: Layout,
        redirect: children.length ? children[0].path : '/403',
        meta: { dynamic: true },
        children
      }]
      this.dynamicRoutes = routes
      return routes
    },

    toRoute(menu) {
      const comp = componentMap[menu.permPath]
      if (!comp) {
        console.warn('[permission] 未配置组件映射:', menu.permPath, menu.permName)
      }
      return {
        path: menu.permPath,
        name: menu.permCode || ('menu-' + menu.permissionId),
        component: comp || (() => import('@/views/403.vue')),
        meta: {
          title: menu.permName,
          icon: menu.icon,
          permission: menu.permCode,
          dynamic: true
        }
      }
    },

    reset() {
      this.routesLoaded = false
      this.menus = []
      this.dynamicRoutes = []
    }
  }
})
```

> `name` 用 `permCode` 而不是 `permName`，因为 `permName` 是中文，路由名用中文容易在 `addRoute` 重名时出错。`perm_code` 在库里是唯一的（虽然没加唯一索引，实际数据唯一）。

### 3.4 第 21 步：`router/index.js` 彻底改写

```js
// frontend/src/router/index.js
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/useUserStore'
import { usePermissionStore } from '@/stores/usePermissionStore'

// 只保留"不需要登录"和"兜底"的静态路由
const constantRoutes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
  { path: '/403',  name: 'NoPermission', component: () => import('@/views/403.vue') }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: constantRoutes
})

const WHITE_LIST = ['/login', '/403']

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const permStore = usePermissionStore()
  const token = userStore.token

  // 1. 没有 token
  if (!token) {
    if (WHITE_LIST.includes(to.path)) return next()
    return next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
  }

  // 2. 有 token，去登录页 → 回首页
  if (to.path === '/login') return next('/')

  // 3. 有 token 但路由还没生成（首次进入 / 刷新页面）
  if (!permStore.routesLoaded) {
    try {
      const info = await userStore.getInfo()               // GET /api/auth/getInfo
      const routes = permStore.generateRoutes(info.menus)  // 后端菜单树 → 路由
      routes.forEach(r => router.addRoute(r))
      router.addRoute({ path: '/:pathMatch(.*)*', redirect: '/403' })  // 兜底必须最后加
      permStore.routesLoaded = true
      return next({ ...to, replace: true })   // 关键：重新匹配一次，否则当前 to 还是空
    } catch (e) {
      userStore.logout()
      permStore.reset()
      return next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    }
  }

  next()
})

export default router
```

三个必须理解的细节：

1. `next({ ...to, replace: true })` —— `addRoute` 之后当前这次导航的目标路由在初始化时还不存在，直接 `next()` 会白屏。重新触发一次导航才能匹配到新路由。
2. 通配 `/:pathMatch(.*)*` **必须放在 `addRoute` 之后**。如果放进 `constantRoutes`，它会先匹配到所有路径，导致动态路由永远进不去。
3. `routesLoaded` 是内存状态，页面刷新就丢 → 刷新时走第 3 步重新拉取 + 重建，这就是"刷新后路由丢失"的标准解法。

### 3.5 第 22 步：`LayoutMain.vue` 改成菜单数据驱动

把现在三个 `<template v-if="role === 'xxx'">` 全部删掉，改成递归渲染后端菜单树：

```vue
<template>
  <el-container style="height:100vh">
    <el-aside width="220px" style="background:#304156">
      <div style="height:60px;line-height:60px;color:#fff;text-align:center;font-size:16px">
        AI实训评价系统
      </div>
      <el-menu
        :default-active="$route.path"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        router
      >
        <template v-for="m in permStore.menus" :key="m.permissionId">
          <el-menu-item :index="m.permPath">
            <span>{{ m.permName }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header style="background:#fff;border-bottom:1px solid #eee;display:flex;justify-content:flex-end;align-items:center;padding-right:20px">
        <span>欢迎：{{ userStore.realName }} 【{{ userStore.roleName }}】</span>
        <el-button type="text" @click="handleLogout">退出登录</el-button>
      </el-header>
      <el-main style="background:#f2f3f5;overflow:auto">
        <router-view></router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useUserStore } from '@/stores/useUserStore'
import { usePermissionStore } from '@/stores/usePermissionStore'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const permStore = usePermissionStore()
const router = useRouter()

const handleLogout = () => {
  userStore.logout()
  permStore.reset()
  // 动态路由是加到全局 router 上的，退出时必须清掉，否则换个账号还能访问
  router.getRoutes().forEach(r => {
    if (r.meta?.dynamic && r.name) router.removeRoute(r.name)
  })
  ElMessage.success('已退出')
  router.push('/login')
}
</script>
```

> 有二级菜单时用 `<el-sub-menu>` + 递归子组件，`menus` 里 `children` 就是后端树的 `children`。当前 5 个菜单都是顶级，先按上面写即可。

### 3.6 第 23 步：按钮级权限指令 `v-hasPerm`

新建 `frontend/src/directives/hasPerm.js`：

```js
// Vue 3 版本
import { useUserStore } from '@/stores/useUserStore'

export default {
  mounted(el, binding) {
    // 用法：
    //   v-has-perm="'training:publish'"           单个
    //   v-has-perm="['a','b']"                    任一满足即可（默认 OR）
    //   v-has-perm.all="['a','b']"                必须全部满足（AND）
    //   v-has-perm.disable="'training:publish'"   无权限时置灰而不是移除
    const perms = useUserStore().permissions || []
    const need = Array.isArray(binding.value) ? binding.value : [binding.value]
    const pass = binding.modifiers.all
      ? need.every(p => perms.includes(p))
      : need.some(p => perms.includes(p))

    if (pass) return

    if (binding.modifiers.disable) {
      el.setAttribute('disabled', 'disabled')
      el.classList.add('is-disabled')
      el.style.pointerEvents = 'none'
      el.style.opacity = '0.5'
      el.title = '无操作权限'
    } else {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}
```

注册（`main.js` 里）：

```js
import hasPerm from '@/directives/hasPerm'
app.directive('has-perm', hasPerm)
```

页面里用：

```vue
<el-button type="primary" v-has-perm="'training:publish'" @click="onPublish">
  发布实训
</el-button>

<!-- 需要提示用户"你有这个功能但当前不可用"时 -->
<el-button v-has-perm.disable="'training:publish'">发布实训</el-button>
```

**Vue 2 的差异（如果你以后要迁回去）**：

| 项目 | Vue 3 | Vue 2 |
|---|---|---|
| 注册方式 | `app.directive('has-perm', obj)` | `Vue.directive('has-perm', obj)` |
| 生命周期钩子 | `mounted` | `inserted` |
| 取状态 | `useUserStore()` | `store.getters.permissions` |
| 组件内使用 | 直接可用 | 需 `directives: { hasPerm }` |

Vue 2 版：

```js
// Vue 2
import store from '@/store'
export default {
  inserted(el, binding) {
    const perms = store.getters.permissions || []
    const need = Array.isArray(binding.value) ? binding.value : [binding.value]
    const pass = binding.modifiers.all
      ? need.every(p => perms.includes(p))
      : need.some(p => perms.includes(p))
    if (!pass) el.parentNode && el.parentNode.removeChild(el)
  }
}
```

**隐藏还是禁用？建议**：

- **删除类、危险类操作**（删除、重置密码、发布、归档）→ **隐藏**。避免用户反复点击后弹 403，体验更干净。
- **用户需要知道"存在但不能用"的操作**（如"审批"按钮，要提示"当前无审批权限，请联系管理员"）→ **禁用 + tooltip**。

另外必须知道：`mounted` 只在挂载时执行一次，**运行时权限变了按钮不会自动变**。所以改权限后要刷新页面（或把 `permissions` 换成一个响应式对象并加 `updated` 钩子）。这在学生项目里最省事的做法就是"改完权限重新登录"。

### 3.7 第 24 步：改写 `Login.vue`

删掉硬编码的 `userList` 和"角色下拉框"（角色由数据库决定，不该让用户自己选）：

```vue
<template>
  <div style="display:flex;justify-content:center;align-items:center;height:100vh;background:#f5f5f5;">
    <el-card style="width:420px;padding:20px;">
      <h2 style="text-align:center;margin-bottom:24px;">软件实训AI检查评价系统</h2>
      <el-form :model="form" @keyup.enter="handleLogin">
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="teacher01 / stu01 / admin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width:100%" :loading="loading" @click="handleLogin">
            登录系统
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/useUserStore'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)
const form = ref({ username: '', password: '' })

const handleLogin = async () => {
  if (!form.value.username || !form.value.password) {
    return ElMessage.warning('请输入账号和密码')
  }
  loading.value = true
  try {
    await userStore.login(form.value)          // POST /api/auth/login
    // 登录成功不在这里跳固定页，交给守卫按权限跳
    const redirect = route.query.redirect || '/'
    await router.replace(redirect)
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>
```

登录后**不要**再写 `if (role === 'student') router.push('/student/task')`。跳到 `/`，由守卫拉 `getInfo` + `generateRoutes` 后自动重定向到**该用户第一个有权限的菜单**（`generateRoutes` 里的 `redirect` 已经做了这件事）。这样加新角色时前端不用改代码。

### 3.8 第 25 步：更新 `utils/request.js`（401/403 处理）

```js
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/stores/useUserStore'

const service = axios.create({ baseURL: '/api', timeout: 30000 })

service.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

service.interceptors.response.use(
  res => res.data,
  err => {
    const status = err.response?.status
    if (status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      ElMessage.error('登录已过期，请重新登录')
      router.replace('/login')
    } else if (status === 403) {
      ElMessage.error(err.response?.data?.msg || '没有操作权限')
    } else {
      ElMessage.error(err.response?.data?.msg || '接口请求失败')
    }
    return Promise.reject(err)
  }
)
export default service
```

> 注意：`/api` 前缀靠 `baseURL` 处理，但 `vite.config.js` 里**没有配 proxy**，且后端 `SecurityConfig` 的放行路径写的是 `/api/auth/login`。两者对齐方式二选一：
> - 后端所有 Controller 都带 `/api` 前缀（本文按这个写）；
> - 或加 `vite.config.js` 的 `server.proxy` 把 `/api` 转发到 `http://localhost:8080` 并 `rewrite` 去掉 `/api`。
>
> 如果加 proxy，`CorsConfig` 的 `allowedOrigins` 就不用管了（同源）。

### 3.9 一句话结论（必须写进你的答辩 PPT）

前端所有的权限控制（路由过滤、菜单过滤、`v-hasPerm`）**都只是体验层**：

- 用户可以在浏览器地址栏直接输入 `/system/user` → 路由不存在所以进不去，但他可以直接 `curl` 接口；
- 他可以改 localStorage 里的 token、可以改 `permissions` 数组；
- 真正的拦截**只有服务端**的 `@PreAuthorize` / `@RequiresPerm` + 启动扫描器 + 数据范围过滤。

所以"前端隐藏了按钮 = 安全"是错的。任何按钮对应的接口都必须独立标注权限码。

---

## 4. 一条完整链路：从分配角色到被服务端拦住

下面用你库里**真实存在的账号**走一遍，每一步都能验证。

### 4.1 现状数据

| user_id | username | real_name | role_id | 角色 |
|---|---|---|---|---|
| 1 | teacher01 | 张老师 | 1 | TEACHER |
| 2 | stu01 | 李明 | 2 | STUDENT |
| 3 | stu02 | 王华 | 2 | STUDENT |
| 4 | admin | 系统管理员 | 3 | ADMIN |

`training` 表：`training_id=1`，`teacher_id=1`（张老师发布）。
`train_result` 表：`result_id=1`，`student_id=2`（李明的成果）。

### 4.2 第 1 步：管理员给用户分配角色

用 `admin` 登录拿到 token：

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

调改角色接口（你需要自己加这个 Controller 方法，代码在 4.6）：

```
PUT /api/user/3/role
Authorization: Bearer {adminToken}
{ "roleId": 1 }
```

服务端执行的真实 SQL（`SysUserMapper.updateRoleId`）：

```sql
UPDATE sys_user SET role_id = 1 WHERE user_id = 3;
```

同时 `RolePermissionService.changeUserRole` 在事务提交后调用 `permissionService.evict(3)`，把 stu02 的权限缓存版本 +1。

> 单角色模型的局限在这里暴露：**你只能改角色，不能"给 stu02 单独加一个训练发布权限"**。要做到后者必须补 `sys_user_permission` 表（第 1.4 节）。

### 4.3 第 2 步：stu02 重新登录

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"stu02","password":"123456"}'
```

拿到的 token 里 `role=TEACHER`（因为 `sys_user.role_id` 已改成 1）。

### 4.4 第 3 步：前端守卫拉权限、生成路由

浏览器打开 `http://localhost:5173/QianDuan/`（`base` 是 `/QianDuan/`），守卫流程：

```
有 token? → 是
  to.path === '/login'? → 否
  permStore.routesLoaded? → false（刚登录/刚刷新）
    → GET /api/auth/getInfo
```

服务端执行的 SQL（`SysPermissionMapper.selectByUserId`）：

```sql
SELECT DISTINCT p.permission_id, p.parent_id, p.perm_name, p.perm_type,
                p.perm_code, p.perm_path, p.icon, p.sort
FROM sys_user u
INNER JOIN sys_role_permission rp ON rp.role_id = u.role_id
INNER JOIN sys_permission p ON p.permission_id = rp.permission_id
WHERE u.user_id = 3
ORDER BY p.sort, p.permission_id;
```

因为 `user_id=3` 现在 `role_id=1`（TEACHER），返回 7 条：`training:list / training:publish / training:submit / ai:list / evaluation:list / user:list / config:list`。

前端 `generateRoutes(info.menus)` → 5 个菜单 → `router.addRoute(...)` → 侧边栏出现"实训管理 / AI评测 / 评价打分 / 用户管理 / 系统配置"。

### 4.5 第 4 步：按钮出现

`TaskManage.vue` 里：

```vue
<el-button type="primary" v-has-perm="'training:publish'" @click="onPublish">发布实训</el-button>
```

`permissions` 里含 `training:publish` → 按钮渲染。换成 `stu01`（role_id=2）登录，`permissions` 里没有这个码 → 按钮被 `removeChild` 移除。

### 4.6 第 5 步：点按钮 → 服务端校验 → 直接调接口仍被拦

点击后：

```bash
curl -X POST http://localhost:8080/api/training/1/publish \
  -H "Authorization: Bearer {stu02Token}"
```

服务端：

```java
@PreAuthorize("hasAuthority('training:publish')")
@PostMapping("/api/training/{trainingId}/publish")
public R<Void> publish(@PathVariable Long trainingId) { ... }
```

**第 6 步：把 stu02 改回学生，再试同一个接口（这一刻是答辩的关键）**

```sql
UPDATE sys_user SET role_id = 2 WHERE user_id = 3;
```

重新登录拿到新 token（旧的 token 里 `role=TEACHER`，但服务端每次请求都会重建 `LoginUser`，所以旧 token 也会立刻按 STUDENT 处理——这正是 2.8 每请求重建的好处）：

```bash
curl -X POST http://localhost:8080/api/training/1/publish \
  -H "Authorization: Bearer {stu02Token新}"
# → HTTP 403 {"code":403,"msg":"没有访问权限"}
```

前端此时连"发布实训"按钮都不存在（`v-has-perm` 已移除），但**直接调接口依然被服务端拦住**——这两件事是独立的，缺一不可。

**第 7 步：越权查别人数据**

用 `stu02`（user_id=3）的 token 请求 `result_id=1`，这条记录属于 `student_id=2`（李明）：

```bash
curl "http://localhost:8080/api/result/1" -H "Authorization: Bearer {stu02Token}"
# → 403 / 404
```

服务端数据范围 SQL：

```sql
SELECT r.result_id, r.training_id, r.student_id, r.file_name, r.file_path, r.file_type, r.submit_time
FROM train_result r
WHERE r.result_id = 1
  AND r.student_id = 3        -- ← 从 token 里取，不是从请求参数取
```

查不到 → 返回 403/404。**注意**：这里 `student_id = 3` 是服务端从 `LoginUser.userId` 取的，不是前端传的 `studentId=3`。

如果只用 `@PreAuthorize` 而不加这个条件，`stu02` 只要拥有 `training:list` 就能看到李明的成果——这就是第 5.5 条坑。

### 4.7 链路里各类"缓存失效"的实际触发点

| 操作 | 触发位置 | 效果 |
|---|---|---|
| 改用户角色 | `RolePermissionService.changeUserRole` → `afterCommit` → `evict(userId)` | 该用户下次请求重建 `LoginUser` |
| 改角色权限 | `RolePermissionService.assignPermissions` → `afterCommit` → `selectUserIdsByRoleId(roleId)` 逐个 `evict` | 该角色下所有人失效 |
| Token 过期 | JWT `exp` | 401 → 前端跳登录 |
| 退出登录 | 前端 `logout()` + 清 localStorage + 清动态路由 | 客户端侧 |

---

## 5. 常见坑（每条都对应本项目的具体位置）

### 5.1 只在前端隐藏按钮，不做服务端校验

**现象**：`TaskManage.vue` 里 `v-has-perm` 把"发布实训"藏了，学生用 Postman 直接 `POST /api/training/1/publish` 成功。

**本项目具体位置**：`Login.vue` 现在是纯 mock，`router/index.js` 的 `meta.roles` 也只是前端判断——这套如果只加个 `v-hasPerm` 就上线，等于没做鉴权。

**正确做法**：每个写操作接口都加 `@PreAuthorize` 或 `@RequiresPerm`；2.11 的 `PermissionCoverageChecker` 保证"漏加就启动失败"。

### 5.2 权限码与接口未一一对应，漏配即放行

**现象**：新写 `DELETE /api/training/{id}`，忘了加注解。因为 `SecurityConfig` 里是 `anyRequest().authenticated()`，**任何登录用户都能调**。

**为什么 `anyRequest().authenticated()` 不够**：它只回答"你有没有登录"，不回答"你能不能做这件事"。网上很多教程写到这一步就结束了，这是最危险的半成品。

**本项目正确做法**：
1. 所有 `/api/**` 的 Controller 方法必须有 `@PreAuthorize` / `@RequiresPerm` / `@PublicApi`；
2. `PermissionCoverageChecker` 启动时扫描 `RequestMappingHandlerMapping`，缺一个就 `throw IllegalStateException`，应用起不来；
3. 定期用 SQL 对账"代码里的权限码"与 `sys_permission.perm_code` 是否一致：

```sql
-- 找出 sys_permission 里登记了但可能没人用的权限码（人工核对）
SELECT p.perm_code, p.perm_name, p.perm_type
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.permission_id
WHERE rp.id IS NULL;
```

### 5.3 缓存未失效，改权限不生效

**现象**：给角色加了 `training:publish`，用户界面还是没按钮，必须重启后端。

**根因**：权限 `Set` 在登录时算好放进内存/JWT，之后再也不更新。

**本项目正确做法**：
- 不要在 JWT 里塞 `permissions`（`JwtService.createToken` 里只放 `uid/username/role`）；
- `JwtAuthFilter` 每次请求都 `permissionService.buildLoginUser(userId)`；
- 改权限必须走 `RolePermissionService`，它在 `afterCommit` 里 `evict`；
- **易漏点**：直接在 Navicat 里手改 `sys_role_permission` 是不会触发 `evict` 的。手改数据后必须重启后端（或临时暴露一个 `/api/admin/cache/clear` 接口）。

### 5.4 多角色：并集还是交集

**当前不涉及**：`sys_user.role_id` 是单值，天然只有一个角色。

**扩成多角色后要注意**：
- 权限应是**并集**（union），SQL 写成 `WHERE rp.role_id IN (...)` 或 `JOIN sys_user_role`，配 `DISTINCT`；
- 常见错误写法 1：想当然写成 `WHERE role_id = 1 AND role_id = 2` → 永远查不到；
- 常见错误写法 2：`GROUP BY p.perm_code HAVING COUNT(DISTINCT rp.role_id) = 2` → 这变成**交集**，权限会莫名变少；
- 真正的"必须两个角色同时具备"属于**业务审批流**（如双人复核），应该用业务表记录，不要用权限交集表达。

### 5.5 越权查别人数据（数据范围没参与过滤）

**现象**：`stu02` 把 URL 里的 `resultId` 改成 `1`，能看到李明的成果。`@PreAuthorize("hasAuthority('training:list')")` 是**通过**的，因为他确实有列表权限。

**根因**：功能权限管"能不能用这个功能"，数据权限管"能看哪些行"，两件事。

**本项目正确做法**：见 2.16。`train_result` 必须带 `AND r.student_id = #{当前登录userId}`，`training` 必须带 `AND t.teacher_id = #{当前登录userId}`。**这两个值只能从 `@AuthenticationPrincipal LoginUser` 取**。

### 5.6 超级管理员硬编码判断散落各处

**现象**：`if (user.getRoleCode().equals("ADMIN")) return true;` 出现在 5 个 Service 里。后来要加"审计员"角色，改漏一处就是漏洞。

**本项目现状**：`sys_role_permission` 里 ADMIN(role_id=3) **一条权限都没有**，说明之前的实现很可能是硬编码 `ADMIN` 绕过权限判断的。这必须修。

**正确做法**：
```sql
-- 1) 让 ADMIN 拥有全部权限（数据层面解决，不写代码）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 3, permission_id FROM sys_permission;

-- 2) 新增权限时自动补给 ADMIN（触发器或启动任务，这里只给查询）
SELECT p.permission_id FROM sys_permission p
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 3 AND rp.permission_id = p.permission_id);
```
**唯一允许的硬编码位置**：`PermissionService.resolveScope()` 里把 `ADMIN` 映射成 `ALL`，且只此一处。不要在 Controller / Mapper 里再写 `if ADMIN`。

### 5.7 另外 3 个本项目特有的坑

**坑 A：`utf8mb4_0900_ai_ci` 是大小写不敏感**

`sys_permission.perm_code` 的排序规则是 `utf8mb4_0900_ai_ci`，SQL 里 `'TRAINING:PUBLISH' = 'training:publish'` 为 **true**；但 Java 的 `Set.contains("TRAINING:PUBLISH")` 为 **false**。前后端一旦大小写不一致，SQL 能查到、`hasAuthority` 却拦你。
→ 约定：权限码**全小写**，且在 `sys_permission.perm_code` 上补唯一索引。

**坑 B：`sys_role_permission` 的外键是 `ON DELETE RESTRICT`**

想删一个权限时，如果还有角色绑着它，MySQL 直接报 `1451 Cannot delete or update a parent row`。
→ 正确顺序：先 `DELETE FROM sys_role_permission WHERE permission_id = ?`，再删 `sys_permission`。

**坑 C：`GlobalExceptionHandler` 吞掉 `AccessDeniedException`**

如果写了 `@ExceptionHandler(Exception.class)` 兜底返回 `R.fail(500, ...)`，Spring Security 的 403 会变成 HTTP 200 + code 500，前端拦截器拿不到 403，看起来"接口成功但没数据"。
→ 按 2.12 的写法显式 rethrow，或者干脆不要 `Exception.class` 兜底。

---

## 6. 换成 Vue 2 / Shiro 时怎么改

| 环节 | Vue 3 + Spring Security（本文） | Vue 2 + Shiro |
|---|---|---|
| 指令注册 | `app.directive('has-perm', obj)` | `Vue.directive('has-perm', obj)` |
| 指令钩子 | `mounted` | `inserted` |
| 状态管理 | Pinia `useUserStore()` | Vuex `store.getters.permissions` |
| 路由守卫 | `router.beforeEach(async (to,from,next)=>{})` | 一样，但 `next({...to, replace:true})` 需 Vue Router 3.1+ |
| 动态路由 | `router.addRoute(route)` | `router.addRoutes(routes)`（数组） |
| 兜底路由 | `addRoute` 之后加 `/:pathMatch(.*)*` | `addRoutes` 之后加 `*` |
| 方法鉴权 | `@PreAuthorize` / `@RequiresPerm` | `@RequiresPermissions("training:publish")` |
| 默认拒绝 | `anyRequest().authenticated()` + 启动扫描 | `filterChainDefinitionMap` 最后写 `/** = authc` |
| 权限装配 | `LoginUser implements UserDetails` | 自定义 `AuthorizingRealm.doGetAuthorizationInfo` |
| 密码 | `BCryptPasswordEncoder` | `SimpleHash("MD5", pwd, salt, 2)` 或 `HashedCredentialsMatcher` |
| 数据范围 | 手写 `AND r.student_id = ?` | 一样，Shiro 不管数据权限 |

Shiro 的 Realm 里查权限也是同一句 SQL：

```java
@Override
protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
    Long userId = ((LoginUser) principals.getPrimaryPrincipal()).getUserId();
    SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
    info.setRoles(Set.of(role.getRoleCode()));
    info.setStringPermissions(new HashSet<>(permissionMapper.selectCodesByUserId(userId)));
    return info;
}
```

> 无论哪套框架，**SQL 和表结构不用变**，变的只是"权限集合往哪塞、在哪拦"。

---

## 7. 执行清单（照着打勾）

### 后端

- [ ] 1. `pom.xml` 加 `spring-boot-starter-jdbc`、`spring-boot-starter-security`、jjwt 三个依赖
- [ ] 2. 重写 `application.properties`（数据源 + JWT 配置，注意 UTF-8）
- [ ] 3. 建 `common / security / auth / system / config` 五个包
- [ ] 4. 写 `R`、`BizException`、`GlobalExceptionHandler`（注意 `AccessDeniedException` rethrow）
- [ ] 5. 写 `SysUser`、`SysRole`、`SysPermission` 实体（`SysPermission` 加 `children` 非表字段）
- [ ] 6. 写 `SysUserMapper`、`SysPermissionMapper`、`SysRolePermissionMapper`
- [ ] 7. 写 `LoginUser`（`implements UserDetails`）
- [ ] 8. 写 `JwtService`（只放 uid/username/role）
- [ ] 9. 写 `PermissionCacheService` + `PermissionService`
- [ ] 10. 写 `JwtAuthFilter`（每次请求重建 LoginUser）
- [ ] 11. 写 `SecurityConfig`（`anyRequest().authenticated()`）
- [ ] 12. 写 `RequiresPerm`、`PublicApi`、`PermissionCoverageChecker`
- [ ] 13. 给已有 `TestController` 加 `@PublicApi`（否则启动失败）
- [ ] 14. 写 `LoginDTO/LoginVO/UserInfoVO/AuthService/AuthController`
- [ ] 15. 写 `PasswordUpgradeRunner` 并启动一次，然后确认 `password` 变成 `$2a$`
- [ ] 16. 写 `DataScopeResolver` + 带数据范围的查询 SQL
- [ ] 17. 写 `RolePermissionService`（`afterCommit` 清缓存）

### 数据库（DML，不是建表）

- [ ] 18. `INSERT IGNORE ... SELECT 3, permission_id FROM sys_permission`（补 ADMIN 权限）
- [ ] 19. 补两个学生菜单（`/training/list`、`/training/submit`）并绑定 role_id=2
- [ ] 20. 评估是否 `DELETE FROM sys_role_permission WHERE role_id=1 AND permission_id IN (6,7)`
- [ ] 21. 补 `sys_user.username` 唯一索引、`sys_permission.perm_code` 唯一索引

### 前端

- [ ] 22. `main.js`：`app.use(pinia)` 放到 `app.use(router)` 之前；注册 `v-has-perm`
- [ ] 23. 重写 `stores/useUserStore.js`（加 `login/getInfo/hasPerm`，token 从 localStorage 初始化）
- [ ] 24. 新增 `stores/usePermissionStore.js`（`componentMap` + `generateRoutes`）
- [ ] 25. 重写 `router/index.js`（删掉 `asyncRoutes` 和 `meta.roles`，加动态 addRoute 守卫）
- [ ] 26. 改 `LayoutMain.vue`（菜单由 `permStore.menus` 驱动，删掉三个 `v-if="role==="`）
- [ ] 27. 新增 `directives/hasPerm.js`
- [ ] 28. 改 `Login.vue`（删角色下拉框和硬编码账号，走真实接口）
- [ ] 29. 改 `utils/request.js`（401 清 token、403 提示）
- [ ] 30. 删掉重复的 `src/api/request.js`（目前是空文件，容易误 import）

### 验证

- [ ] 31. `teacher01 / 123456` 登录 → 侧边栏 5 个菜单
- [ ] 32. `stu01 / 123456` 登录 → 只有"实训管理 / 我的实训任务 / 提交成果 / AI评测 / 评价打分"，**没有"用户管理"**
- [ ] 33. `admin / 123456` 登录 → 5 个菜单全有（补齐权限后）
- [ ] 34. 用 `stu01` 的 token `POST /api/training/1/publish` → **403**
- [ ] 35. 用 `stu02` 的 token `GET /api/result/1` → **403/404**（`result_id=1` 属于 `student_id=2`）
- [ ] 36. Navicat 里给 STUDENT 加 `training:publish` → 重启后端 → `stu01` 重新登录 → 按钮出现
- [ ] 37. 故意删掉某个 Controller 的注解 → 重启后端应当**启动失败**

### 一定要知道的一句话

> `sys_user.role_id` 是单值，所以"给某个用户单独加一个权限"这件事在当前表结构下**做不到**，只能改角色（影响该角色所有人）。如果老师问你"能不能只给李明开权限"，答案是：需要补 `sys_user_permission` 授权表 —— 而不是硬编码一个 `if (userId == 2)`。

---

## 附：现有 7 条权限码与建议

| permission_id | perm_type | perm_code | perm_path | 建议 |
|---|---|---|---|---|
| 1 | 1 菜单 | `training:list` | `/training` | 保留 |
| 2 | 2 按钮 | `training:publish` | NULL | 保留，教师专用 |
| 3 | 2 按钮 | `training:submit` | NULL | 保留，学生专用 |
| 4 | 1 菜单 | `ai:list` | `/ai-check` | 保留 |
| 5 | 1 菜单 | `evaluation:list` | `/evaluation` | 保留 |
| 6 | 1 菜单 | `user:list` | `/system/user` | 应从教师角色移除 |
| 7 | 1 菜单 | `config:list` | `/system/config` | 应从教师角色移除 |

**权限码规范**：目前全是 `资源:操作` 两段式（`training:list`）。系统只有一个模块，加 `sys:` 这种第三层没有实际收益，建议**沿用两段式**。若要统一成 `模块:资源:操作` 三段式，必须一次性改全（SQL + 所有 `@PreAuthorize` + 所有 `v-hasPerm`），不要新旧混用——混用会出现"注解写三段、库里存两段"然后永远 403 的情况。

