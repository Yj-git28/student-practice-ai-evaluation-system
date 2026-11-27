# RBAC 权限体系梳理与改造说明

适用库：`training_eval`（MySQL 8）；后端 Spring Boot 4.1.1 + Spring Security 7；前端 Vue 3 + Element Plus。
本文基于实库表结构与后端源码核实，用于快速定位“所有权限项”和“RBAC 相关代码”。

## 一、权限模型概览

- **用户 → 角色：多对多**（V13 升级）。经 `sys_user_role` 关联表挂接，一个用户可拥有多个角色；`sys_user.role_id` 保留为主角色冗余列，兼容旧查询/JWT 单值展示，**权威关系以关联表为准**。
- **角色 → 权限：多对多**，经 `sys_role_permission` 授权。
- **用户个人附加权限**：`sys_user_permission`，与角色权限取**并集**，只追加权限码，不额外产生菜单。
- 登录后用户最终权限 = 各角色拥有的权限码 ∪ 个人附加权限码；角色码集合单独进入鉴权身份；数据范围取多角色中的最宽档。
- 权限项同表存储三类：菜单、按钮、接口（`perm_type` 区分）。

### 字典

| 字段 | 取值 | 含义 |
|---|---|---|
| `sys_permission.perm_type` | 1 | 菜单（前端路由，必须有 `perm_path`） |
| | 2 | 按钮（页面内操作，控制显隐/禁用） |
| | 3 | 接口（后端接口级权限标识，预留） |
| `sys_role.data_scope` | 1 | 全部数据 |
| | 2 | 本部门（班级） |
| | 3 | 本人 |

## 二、数据库表与关系

### 2.1 表清单（RBAC 相关 5 张）

| 表 | 作用 | 关键列 |
|---|---|---|
| `sys_user` | 用户 | `user_id`(PK)、`username`(UK)、`email`(UK)、`role_id`(主角色冗余列)、`class_id`、`status`(1启用)、`deleted`(0正常)、`current_jti`(单设备会话) |
| `sys_user_role` | 用户↔角色 多对多（V13 新增） | `user_id`+`role_id`(联合主键)；FK `user_id`→sys_user CASCADE、`role_id`→sys_role RESTRICT |
| `sys_role` | 角色 | `role_id`(PK)、`role_name`、`role_code`、`data_scope`、`status`、`builtin`(1内置不可删)、`description` |
| `sys_permission` | 权限项（菜单/按钮/接口） | `permission_id`(PK)、`parent_id`、`perm_name`、`perm_type`、`perm_code`(UK)、`perm_path`、`icon`、`sort` |
| `sys_role_permission` | 角色↔权限 多对多 | `id`(PK)、`role_id`、`permission_id`；唯一索引 `uk_role_perm(role_id,permission_id)`；含 `create_time/create_by` 审计列 |
| `sys_user_permission` | 用户个人附加权限 | `user_id`+`permission_id`(联合主键)；含 `create_time/create_by` 审计列 |

### 2.2 外键约束（V11 加固后）

| 约束 | 表.列 | 关联 | 删除策略 |
|---|---|---|---|
| `fk_user_role` | `sys_user.role_id` | `sys_role.role_id` | RESTRICT（角色仍被引用时禁止删除） |
| `fk_user_perm_user` | `sys_user_permission.user_id` | `sys_user.user_id` | CASCADE（删用户级联清理个人权限） |
| `fk_user_perm_perm` | `sys_user_permission.permission_id` | `sys_permission.permission_id` | CASCADE（删权限级联清理） |
| `sys_role_permission_ibfk_1` | `sys_role_permission.role_id` | `sys_role.role_id` | 既有 |
| `sys_role_permission_ibfk_2` | `sys_role_permission.permission_id` | `sys_permission.permission_id` | 既有 |

### 2.3 只读排查视图（V11 新增）

| 视图 | 用途 |
|---|---|
| `v_role_permission` | 角色 → 其被授予的全部权限项 |
| `v_user_permission` | 用户 → 全部权限（角色来源 `ROLE` + 个人来源 `USER`，含并集） |
| `v_permission_ref` | 每个权限被多少角色、多少个人引用，删除前判断占用 |

## 三、标准排查 SQL

```sql
-- 1. 某角色的全部权限
SELECT role_code, perm_code, perm_type, perm_name, perm_path
FROM v_role_permission
WHERE role_code = 'ADMIN'
ORDER BY perm_type, sort;

-- 2. 某用户的全部权限（角色 + 个人附加，source 区分来源）
SELECT username, role_code, perm_code, perm_type, source
FROM v_user_permission
WHERE username = 'admin'
ORDER BY source, perm_code;

-- 3. 全部按钮权限（perm_type=2）
SELECT permission_id, perm_code, perm_name, parent_id
FROM sys_permission
WHERE perm_type = 2
ORDER BY sort;

-- 4. 权限引用情况（删除前确认是否被占用）
SELECT perm_code, perm_name, perm_type, role_ref_count, user_ref_count
FROM v_permission_ref
ORDER BY role_ref_count DESC, user_ref_count DESC;

-- 5. 角色列表及其权限数
SELECT r.role_id, r.role_code, r.role_name, r.data_scope,
       COUNT(rp.permission_id) AS perm_count
FROM sys_role r
LEFT JOIN sys_role_permission rp ON rp.role_id = r.role_id
GROUP BY r.role_id, r.role_code, r.role_name, r.data_scope;
```

### 当前内置权限项（实库）

菜单（perm_type=1）：`training:list`、`ai:list`、`training:submit-page`、`evaluation:list`、`user:list`、`config:list`、`training:my-list`、`permission:manage`、`chat:list`。

按钮（perm_type=2）：`training:publish`（发布实训，挂在“实训管理”下）、`training:submit`（提交作业，挂在“实训管理”下）。

内置角色：`ADMIN`（管理员，data_scope=1）、`TEACHER`（教师，3）、`STUDENT`（学生，3）。

## 四、后端 RBAC 代码链路

包根：`org.example.backend`。

### 4.1 实体 `system/entity/`

| 文件 | 说明 |
|---|---|
| `SysUser.java` | 用户字段：userId/username/password/realName/roleId(主角色冗余)/classId/email |
| `SysRole.java` | 角色：roleId/roleName/roleCode/dataScope/status/builtin/description |
| `SysPermission.java` | 权限项：permissionId/parentId/permName/permType/permCode/permPath/icon/sort + 非表字段 `children`（菜单树） |

### 4.2 Mapper `system/mapper/`（JdbcClient）

| 文件 | 关键方法 |
|---|---|
| `SysUserMapper` | `selectByUsername/selectByEmail/selectById`（均过滤 `status=1 AND deleted=0`）；`selectUserList`（角色名/角色码/角色 ID 经关联表 `GROUP_CONCAT` 聚合）；`selectUserIdsByRoleId`；`insertUser/updateUser/logicalDelete/countActiveAdmins/hasAdminRole`；`updateCurrentJti/clearCurrentJti/selectCurrentJti`（单设备登录） |
| `SysUserRoleMapper`（V13 新增） | `selectRoleIdsByUserId/selectRolesByUserId/replaceUserRoles`（先删后插）/`selectUserIdsByRoleId/countUsersByRoleId/deleteByUserId/countInvalidRoles` |
| `SysRoleMapper` | `selectAll/selectById/selectByCode/existsByCode/insert/update/deleteById/countUsersByRoleId/selectPermIdsByRoleId` |
| `SysRolePermissionMapper` | `deleteByRoleId`、`batchInsert`（INSERT IGNORE） |
| `SysPermissionMapper` | `selectByUserId`（角色权限，走 `sys_user_role` 关联）、`selectPersonalByUserId`（个人权限）、`selectMenusByUserId`（仅菜单）；`selectAll/selectById/existsByCode/insert/update/deleteById/deleteRoleRef/deleteUserRef/countChild/countRoleRef/countUserRef` |

### 4.3 Service `system/service/`

| 文件 | 职责 |
|---|---|
| `PermissionService.java` | `buildLoginUser(userId)`：校验账号状态 → 读缓存 → 取全部启用角色 → 角色权限 ∪ 个人权限组装权限码集合；`dataScope` 取最宽档；主角色取第一个回填单值；构造 `LoginUser` → 写缓存；`menusOf`；`evict`；`refreshAll` |
| `PermissionCacheService.java` | Redis 权限缓存：`perm:user:{userId}`（LoginUser JSON，TTL 30 分钟）、`perm:ver:{userId}`（版本号）；读失败回落到查库重建 |
| `RolePermissionService.java` | `assignPermissions`（先删后插角色权限；内置角色拒绝清空权限集；提交后清该角色下全部用户缓存，记 `@SysLog GRANT_PERM`）；`changeUserRole`（改角色后清缓存） |
| `RoleAdminService.java`（V13 新增） | 角色维护 `list/create/update/delete`：内置角色不可删、角色码不可改、删除前校验绑定用户数；`RoleSaveDTO` 入参 |
| `PermissionAdminService.java` | 权限树 `tree()`；角色+已授权权限 `roles()`；`create/update/delete`（删除前校验子项/角色引用/个人引用）；`assign`；`validate`（类型 1/2/3、code 唯一、菜单必须有 path） |
| `UserAdminService.java` | 师生账号查询/新增/编辑/重置密码/逻辑删除/批量导入；角色写入关联表并清缓存；管理员保护改 `hasAdminRole` + `countActiveAdmins`；角色合法性校验 |

### 4.4 安全层 `security/`

| 文件 | 职责 |
|---|---|
| `LoginUser.java` | 实现 `UserDetails`：`roles` 集合加 `ROLE_` 前缀进 `GrantedAuthority`；权限码原样进 authorities；`dataScope` |
| `SecurityConfig.java` | `@EnableMethodSecurity`；无状态会话；放行登录/验证码/邮箱注册/找回密码/`/ws/**`/`/test`/`/error`；其余 `authenticated()`；401/403 JSON 处理器；注册 `BCryptPasswordEncoder` |
| `JwtAuthFilter.java` | 解析 Bearer token → 黑名单校验 → 单设备 jti 校验 → `permissionService.buildLoginUser` 重建 `LoginUser` → 放入 `SecurityContext` |
| `RequiresPerm.java` | 自定义权限注解：`value[]`、`logical()`（AND/OR），可标在方法或类上 |
| `RequiresPermAspect.java` | **V11 新增**：执行 `@RequiresPerm` 的 AOP 切面；按 AND/OR 校验当前用户权限码，不通过抛 `AccessDeniedException` → 走 403 |
| `PermissionCoverageChecker.java` | 启动 `ApplicationRunner`：扫描 `org.example.backend` 下所有接口，必须标注 `@PreAuthorize` / `@RequiresPerm` / `@PublicApi` 之一，否则拒绝启动；另做注解权限码 ↔ 库内元数据对账，缺失仅 `log.warn` 不阻塞 |
| `PermConstants.java`（V13 新增） | 后端权限码常量，集中定义全部 `perm_code`，避免三处硬编码 |
| `PublicApi.java` | 匿名放行标记注解 |
| `DataScope.java` | 数据范围常量（1 全部 / 2 本部门 / 3 本人）与归一化 |
| `DataScopeResolver.java` | 数据范围解析：`ALL` 不过滤；`DEPT` 返回 `forcedClassId`（班级）走部门过滤；`SELF` 返回本人 `forcedUserId`；供业务查询透传 |
| `JwtService.java` | JWT 签发/解析（HS256） |
| `TokenBlacklistService.java` | 退出登录 token 黑名单 |

### 4.5 鉴权入口与控制器

- 登录/当前用户：`auth/controller/AuthController.java`（`/api/auth/login`、`/api/auth/getInfo`、`/api/auth/logout` 等）；组装在 `auth/service/AuthService.java`；返回结构 `auth/vo/UserInfoVO.java`（`roles`、`permissions`、`menus`、`dataScope`、roleId/roleCode/roleName）。
- 权限维护：`system/controller/PermissionAdminController.java`（`/api/system/permission/**`，方法级权限码：`permission:manage/create/edit/remove/grant`）。
- 角色维护：`system/controller/RoleAdminController.java`（V13 新增，`/api/system/roles` GET/POST/PUT/DELETE，权限码 `permission:manage/create/edit/remove`）。
- 用户/配置/班级等管理：`system/controller/SystemController.java`（`hasRole('ADMIN')` / `hasAuthority(...)`）。
- 业务接口：`training/`、`evaluation/`、`chat/` 各 Controller，方法级 `@PreAuthorize`。

> 说明：线上接口鉴权以 Spring `@PreAuthorize` 为主；`@RequiresPerm` 现已由 `RequiresPermAspect` 真正执行（AND/OR），二者拒绝时都映射为 403。

## 五、前端 RBAC 链路（`frontend/src/`）

| 文件 | 职责 |
|---|---|
| `stores/useUserStore.js` | 持有 token、roles 集合、permissions、roleCode/roleName 等；`getInfo()` 拉取用户信息；`hasPerm(code)` 判断权限码 |
| `stores/usePermissionStore.js` | 把后端下发的 `menus` 递归转成动态路由；`permPath → 组件` 本地映射兜底 |
| `directives/hasperm.js` | `v-has-perm` 指令：`mounted`/`updated` 双钩子，权限异步刷新后重新判定；无权限移除 DOM；`.disable` 修饰符置灰并可恢复 |
| `constants/perms.js`（V13 新增） | 前端权限码常量 `PERM`，与后端 `PermConstants`、库内 `perm_code` 一一对应 |
| `views/layout/admin/UserManage.vue` | 用户角色多选（`roleIds` 数组）；角色选项从 `/api/system/roles` 拉取；提交同时带 `roleIds` 与兼容单值 `roleId` |
| `router/index.js` | 路由守卫：无 token 跳登录；已登录但动态路由未加载时调 `getInfo` → 生成并注册路由；404 兜底重定向 403 |

## 六、本次改造边界（V11）

### 6.1 已改动 / 新增

- `docs/backend/sql/V11__rbac_integrity.sql`（新增，幂等）：补外键、索引、字典注释；新增 3 张排查视图。
- `backend/.../security/RequiresPermAspect.java`（新增）：让 `@RequiresPerm` 真正参与鉴权。
- `docs/backend/sql/README.md`：登记 V11 脚本。
- 实库已执行 V11；外键、索引、视图、注释均已核验就位。

### 6.2 明确未改动

- 未改动任何业务 Controller / Service / Mapper / 实体 / DTO / VO。
- 未改动前端任何文件；接口路径、入参、响应 JSON 保持不变。
- 未把“单角色 `sys_user.role_id`”升级为多对多 `sys_user_role`：该改造会波及用户管理服务、登录组装与前端角色选择，超出“仅改 RBAC 内部、不动其他代码”的范围，故保留单角色模型。

### 6.3 验证记录

- `mvn -o compile` 通过；临时实例在 8081 端口启动成功（权限覆盖检查通过、连库正常）。
- `admin/123456` 登录 200，`getInfo` 返回角色 `ADMIN` 与 8 条权限码、菜单树；与 `v_user_permission` 视图结果一致。
- 未带 token 访问受保护接口 → 401；学生账号访问管理员接口 → 403。

## 七、按钮权限闭环（V12）

在 V11 加固基础上，把菜单内部小按钮从“前端写死展示”改为完整走 RBAC：数据库按钮权限元数据 → 前端 `v-has-perm` 指令控制显隐 → 后端 `@PreAuthorize` 权限码注解兜底 → 角色授权。业务逻辑、接口路径/入参/响应、前端非按钮代码均未改动。

### 7.1 按钮权限码与映射（perm_type=2，parent_id 为父菜单）

| 权限码 | 按钮 | 后端接口 | 前端落点 |
|---|---|---|---|
| `user:add` | 新增账号 | `SystemController.createUser` | UserManage 新增账号 / 弹窗确定 |
| `user:edit` | 编辑 | `SystemController.updateUser` | UserManage 编辑 / 弹窗确定 |
| `user:remove` | 删除 | `SystemController.deleteUser` | UserManage 删除 |
| `user:resetpwd` | 重置密码 | `SystemController.resetPassword` | UserManage 重置密码 |
| `user:import` | 批量导入 | `SystemController.importUsers` | UserManage 开始导入 |
| `class:add` | 新增班级 | `SystemController.createClass` | UserManage 班级保存 |
| `class:edit` | 编辑班级 | `SystemController.updateClass` | UserManage 班级保存 / 班级编辑 |
| `class:remove` | 删除班级 | `SystemController.deleteClass` | UserManage 班级删除 |
| `training:add` | 新增实训 | `TrainingController.create` | TaskManage 新增 / 弹窗保存 |
| `training:edit` | 编辑实训 | `TrainingController.update` | TaskManage 编辑 / 弹窗保存 |
| `training:remove` | 删除实训 | `TrainingController.delete` | TaskManage 删除 |
| `evaluation:review` | 人工复核 | `TrainingResultController.review` | WorkReview 复核 / 弹窗保存 |
| `config:update` | 保存配置 | `SystemController.saveConfig` | SysConfig 保存 |
| `permission:create` | 新增权限 | `PermissionAdminController.create` | PermissionManage 新增 / 弹窗确定 |
| `permission:edit` | 编辑权限 | `PermissionAdminController.update` | PermissionManage 编辑 / 弹窗确定 |
| `permission:remove` | 删除权限 | `PermissionAdminController.delete` | PermissionManage 删除 |
| `permission:grant` | 保存授权 | `PermissionAdminController.assign` | PermissionManage 保存授权 |

沿用既有按钮码：`training:publish`（发布实训）、`training:submit`（学生提交成果）。

### 7.2 角色授权（V12 SQL，幂等 INSERT IGNORE）

- ADMIN：全部按钮码。
- TEACHER：`training:add/edit/remove/publish` + `evaluation:review`。
- STUDENT：`training:submit`。

只读/辅助动作（查询、重置、刷新、取消、关闭、全选/清空/展开、下载模板、选择文件、群聊发送、登录/注册/找回/绑定邮箱/登出）不设独立按钮码，由菜单码或类级 `chat:list` 等覆盖。

### 7.3 落地文件

- 新增 `docs/backend/sql/V12__rbac_button_perms.sql`：按钮权限元数据 + 角色授权（已在本机库执行）。
- 后端注解：`SystemController`、`TrainingController`、`TrainingResultController`、`PermissionAdminController` 仅方法级补 `@PreAuthorize`，未改业务逻辑。
- 前端指令：`UserManage.vue`、`TaskManage.vue`、`WorkReview.vue`、`SysConfig.vue`、`PermissionManage.vue`、`SubmitWork.vue`、`TaskList.vue` 仅补 `v-has-perm`，未改事件/文字/样式。

### 7.4 验证记录（缓存清空后）

- 后端 `mvn -o compile` 通过；前端 `npm run build` 成功。
- ADMIN `getInfo` 返回全部按钮码；权限树包含按钮子节点；权限树接口 200。
- STUDENT 仅含 `training:submit`；TEACHER 持教学按钮码。
- STUDENT/TEACHER 调 `POST /system/users` → 403；TEACHER 提交复核列表 200。

> 注意：IDEA 中 8080 实例加载的是旧编译产物，需在 IDEA 重启后端才会加载本轮注解改动；数据库改动（V11/V12）已实时生效。权限缓存键 `perm:user:*`、`perm:ver:*` 失效后会重建。

## 八、多对多角色 + 角色维护 + 数据权限落地（V13）

在按钮闭环基础上，把“单角色、角色不可维护、部门数据范围未落地”三项缺口补齐。数据库脚本 `V13__rbac_many_to_many.sql` 已在本机库执行；存量有效用户 132 条已全部迁入 `sys_user_role`（核对 `ur_rows=132`、`users=132`）。

### 8.1 数据库改造

- 新建 `sys_user_role`（`user_id`+`role_id` 联合主键；`user_id` FK→`sys_user` CASCADE，`role_id` FK→`sys_role` RESTRICT）。
- `sys_role` 补 `status`、`builtin`（1 内置）、`description`；TEACHER/STUDENT/ADMIN 均置 `builtin=1`、`status=1`。
- `sys_role_permission`、`sys_user_permission` 补 `create_time`/`create_by` 审计列。
- 重建 `v_user_permission` 视图，改走 `sys_user_role` 多对多口径。
- `sys_user.role_id` 保留为主角色冗余列（兼容旧查询/JWT/单值展示），权威关系以关联表为准。

### 8.2 后端落地清单（`backend/src/main/java/org/example/backend/`）

**新增文件**

| 文件 | 职责 |
|---|---|
| `security/PermConstants.java` | 权限码常量集中定义 |
| `system/mapper/SysUserRoleMapper.java` | 用户-角色关联表读写 |
| `system/dto/RoleSaveDTO.java` | 角色新增/编辑入参（roleName/roleCode/dataScope/description/status） |
| `system/service/RoleAdminService.java` | 角色列表/新增/编辑/删除，内置保护与占用校验 |
| `system/controller/RoleAdminController.java` | `/api/system/roles` GET/POST/PUT/DELETE |

**改动文件**

| 文件 | 改动要点 |
|---|---|
| `system/entity/SysRole.java` | 扩 `status/builtin/description` |
| `system/mapper/SysUserMapper.java` | 列表角色聚合走关联表；roleId 筛选改 EXISTS；管理员判断走关联表；逻辑删除级联清关联；保留主角色读写 |
| `system/mapper/SysRoleMapper.java` | 扩 `selectByCode/existsByCode/insert/update/deleteById/countUsersByRoleId` |
| `system/mapper/SysPermissionMapper.java` | 角色权限/菜单查询改走 `sys_user_role` 关联 |
| `system/service/PermissionService.java` | 取全部启用角色；角色码集合；权限并集；dataScope 取最宽档；主角色回填单值 |
| `system/service/UserAdminService.java` | 注入关联表/角色表/权限服务；创建/编辑写关联表并清缓存；管理员保护改 `hasAdminRole`；角色合法性校验 |
| `system/service/RolePermissionService.java` | 内置角色拒绝清空权限集；`changeUserRole` 改 `replaceUserRoles`；提交后清该角色全部用户缓存 |
| `system/dto/UserSaveDTO.java` | 新增 `List<Long> roleIds`，保留 `roleId` 兼容旧前端/CSV 导入 |
| `auth/service/AuthService.java` | 注册成功后写 `sys_user_role` |
| `config/DataScopeResolver.java` | ALL 不过滤；DEPT 返回 forcedClassId；SELF 返回 forcedUserId |
| `security/DataScope.java` | 注释与归一化对齐三档范围 |
| `security/PermissionCoverageChecker.java` | 增加注解权限码 ↔ 库内元数据对账（缺失仅告警） |
| `training/mapper/TrainingResultMapper.java` | 分页/计数增加 `classId` 过滤（JOIN `sys_user`） |
| `training/service/TrainingResultService.java` | 取 `forcedClassId` 透传查询 |

### 8.3 角色维护规则（内置保护）

- 内置角色（`builtin=1`）不可删除；角色码 `role_code` 不可修改。
- 删除角色前校验绑定用户数，仍有用户占用时拒绝删除。
- 内置角色的权限集不可被清空（`assignPermissions` 拒绝把权限集置空）。
- 最后一个启用管理员不可被改角色/删除。

### 8.4 数据权限落地

- `ALL`（全部数据）：查询不加归属限制。
- `DEPT`（本部门/班级）：实训成果列表按学生 `class_id` 过滤；`forcedClassId` 由 `DataScopeResolver` 解析透传。
- `SELF`（本人）：仅返回当前用户记录。
- 实训/成果的增改删查与发布、复核接口的归属校验此前已具备，本轮未改业务逻辑。

### 8.5 前端落地（`frontend/src/`）

- `directives/hasperm.js`：抽 `apply(el,binding)`，`mounted`/`updated` 双钩子，权限刷新后重判；`.disable` 可恢复置灰。
- `constants/perms.js`：权限码常量 `PERM`。
- `views/layout/admin/UserManage.vue`：角色多选 `roleIds`；选项拉 `/api/system/roles`；列表回显聚合角色名；提交 `roleIds` 并兼容 `roleId`。

### 8.6 验证记录

- 后端 `mvnw -o -q compile` 通过（EXIT=0）；前端 `npm run build` 通过（EXIT=0）。
- 库内核对：`sys_user_role` 132 行、有效用户 132；3 个内置角色 `builtin=1`；`sys_permission` 28 条（菜单 + 按钮）。
- 8081 临时实例启动成功，权限覆盖检查未拦截；ADMIN `getInfo` 返回 `roles=[ADMIN]`、`dataScope=1`、25 条权限码；`GET /api/system/roles` 200，三个内置角色 `builtin=1`，绑定用户数 TEACHER 9 / STUDENT 122 / ADMIN 1；STUDENT 调角色列表返回 403；内置角色删除被业务码 4003 拦截，库内角色与关联表无损伤。

### 8.7 仍属遗留的缺口（本轮未落地）

- 个人附加权限 `sys_user_permission` 尚无前端写入口（后端读取并集、级联清理已具备）。
- 前端按钮级无路由二次兜底（指令控制显隐，路由守卫只校验菜单）。
- `perm_type=3` 接口元数据尚未在库内逐条登记。
- 角色新增/编辑/删除的前端管理页面未做（后端 CRUD 接口已就位）。
