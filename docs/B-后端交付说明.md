# 后端部分执行说明（B1–B12）

- 项目：`student-practice-ai-evaluation-system` / `backend`
- 执行日期：2026-11-17
- 环境：JDK 21.0.9、Maven 3.9.16、Spring Boot 4.1.1、Spring Security 7.1.1
- 代码位置：`backend/src/main/java/org/example/backend/**`
- 构建结果：`mvn test` → **19 个测试，18 通过、0 失败、1 跳过**（跳过的 `BackendApplicationTests` 需要可用 MySQL）

> ⚠️ 当前执行机连不上数据库 `172.24.161.195:3306`（校园内网），所以 **B2/B7/B8/B9 的真实联调未做**。
> 代码按分工方案约定的列名编写，并额外提供了 `docs/backend/sql/V3__backend.sql` 作为前置变更。
> 数据库可达后，请按文末「联调自测」逐条验证。

---

## 1. 逐条完成情况

| 编号 | 状态 | 改动文件 | 说明 |
|---|---|---|---|
| B1 | ✅ | `security/SecurityConfig.java`、`common/GlobalExceptionHandler.java` | `permitAll` 增加 `/error`；同时兜底异常统一返回 500 + JSON，不再被 `/error` 的内容协商变成 HTML 或 401 |
| B2 | ✅（原有代码已对齐，仅加固） | `training/TrainingMapper.java` | 发布/统计已使用 `status`（0 未发布 / 1 已发布）；`stat` 的 `SUM` 外包 `COALESCE(...,0)`，空表不再返回 `null` |
| B3 | ✅ | `security/PermissionCoverageChecker.java` | 启动即扫描 `org.example.backend` 下所有接口，`@PreAuthorize / @RequiresPerm / @PublicApi` 任一缺失（方法级或类级）直接拒绝启动；新增 `PermissionCoverageCheckerTest` 验证 |
| B4 | ✅ | `security/JwtService.java`、`security/TokenBlacklistService.java`（新增）、`security/JwtAuthFilter.java`、`auth/AuthService.java`、`auth/AuthController.java` | 签发写入 `jti`（UUID）；新增 `POST /api/auth/logout`；过滤器校验黑名单，旧 token 立即 401；黑名单按 token 过期时间惰性清理 |
| B5 | ✅ | `system/SystemController.java`（新增）、`system/service/PermissionService.java`、`system/mapper/SysUserMapper.java` | `PermissionCacheService` 已有 version（`put`/`invalidate` 递增）；新增 `POST /api/system/permission/refresh`（仅 ADMIN），遍历 `sys_user` 逐人失效缓存并返回刷新用户数 |
| B6 | ✅ | `auth/LoginAttemptService.java`（新增）、`auth/AuthService.java`、`system/log/**`（新增） | 同一账号连续 5 次失败锁 15 分钟；被锁时先拦、不查库；第 5 次失败写 `sys_log`（`LOGIN_FAIL`）；第 N 次失败提示剩余次数 |
| B7 | ✅ | `system/entity/SysRole.java`、`system/mapper/SysUserMapper.java`、`system/service/PermissionService.java`、`security/LoginUser.java`、`security/DataScope.java`（新增）、`config/DataScopeResolver.java`、`training/TrainingController.java`、`training/TrainingResultService.java` | 删除按 `roleCode` 硬编码，改为读 `sys_role.data_scope`（1 全部 / 2 本部门 / 3 本人）；数据范围为空或非法时兜底 **3（本人）**，避免默认放开全量 |
| B8 | ✅（依赖新表） | `system/mapper/SysPermissionMapper.java`、`system/service/PermissionService.java` | 新增 `sys_user_permission` 查询；`buildLoginUser` 取「角色权限 ∪ 个人权限」的并集 |
| B9 | ✅ | `system/log/SysLog.java`、`SysLogMapper.java`、`SysLogService.java`、`SysLogAspect.java`（新增）；`auth/AuthController.java`、`system/service/RolePermissionService.java` | `@SysLog` + `@Aspect` 落库：登录（`LOGIN`）、改角色（`CHANGE_ROLE`）、改权限（`GRANT_PERM`）；失败/被锁另有 `LOGIN_FAIL`。日志用 `REQUIRES_NEW` 事务，写失败只打 error 不影响业务，且业务回滚时审计仍保留 |
| B10 | ✅ | `auth/UserInfoVO.java`、`auth/AuthService.java` | `GET /api/auth/getInfo` 的 `dataScope` 改为整数 `1\|2\|3`（由 B7 的字段直出） |
| B11 | ✅ | `../backend/src/main/resources/application.properties` | DB 连接、`jwt.secret`、有效期全部改为 `${ENV:默认值}`：`DB_URL / DB_USERNAME / DB_PASSWORD / JWT_SECRET / JWT_EXPIRE_SECONDS`。保留本地开发默认值，生产必须显式设置 |
| B12 | ✅ | `backend/src/test/java/org/example/backend/**`、`../backend/pom.xml` | 新增 6 个测试类共 18 个真实断言（见第 3 节）；`pom.xml` 增加 `aspectjweaver 1.9.20.1`（@Aspect 必需，本地仓库已有该版本，离线可构建） |

### 关键实现取舍

1. **`dataScope` 从字符串改为整数**：原 `ALL / TEACHER_SELF / STUDENT_SELF` 改为 `1 / 2 / 3`，与前端契约、`sys_role.data_scope` 完全一致。
   业务侧统一通过 `DataScopeResolver` 取数范围，不再各自 `equals` 字符串。
2. **`2（本部门）` 暂时降级为本人**：现有 `sys_user` 只有 `class_id`，没有 `dept_id`。
   已预留 `DataScopeResolver.forcedClassId()`，等班级维度接口接好只需改这一处。
3. **黑名单 / 失败计数为单机内存实现**：多人部署或多实例时需换 Redis（代码注释里已标明 key 设计与 TTL）。
4. **`menus[].component`（第 4 条可选契约）未实现**：方案标注为「可选」，且需要给 `sys_permission` 增加 `component` 列，属数据库同学范围。前端 F8 可等该列就绪后再切换。

---

## 2. 接口契约落地

| 接口 | 实现 | 返回 |
|---|---|---|
| `POST /api/auth/logout` | `AuthController.logout`，`@PreAuthorize("isAuthenticated()")`，空 body | `R.ok()`；旧 token 的 `jti` 进黑名单，后续请求 401 |
| `POST /api/system/permission/refresh` | `SystemController.refreshPermissions`，`@PreAuthorize("hasRole('ADMIN')")` | `R<Integer>`，data 为刷新的用户数 |
| `GET /api/auth/getInfo` | `UserInfoVO.dataScope` | `dataScope: 1 \| 2 \| 3` |
| `GET /api/auth/getInfo` → `menus[].component` | 未实现（可选） | 见上「实现取舍 4」 |

`logout` 请求示例：

```http
POST /api/auth/logout
Authorization: Bearer <当前 token>

HTTP/1.1 200
{"code":200,"msg":"ok","data":null}
```

`refresh` 请求示例：

```http
POST /api/system/permission/refresh
Authorization: Bearer <ADMIN token>

HTTP/1.1 200
{"code":200,"msg":"ok","data":3}
```

---

## 3. 测试（B12）

`cd backend && mvn test`

| 测试类 | 断言内容 |
|---|---|
| `system/service/PermissionServiceTest`（3） | 权限并集包含角色权限 + 个人权限；`ADMIN` 角色 `data_scope=2` 时结果就是 2（证明不再按 roleCode 硬编码）；非法范围兜底为 3；刷新接口返回人数并逐个 `invalidate` |
| `config/DataScopeResolverTest`（4） | 1 → 不过滤；3 → 强制注入本人；2 → 按本人且给出 `classId`；空值 → 3 |
| `training/TrainingPublishMethodSecurityTest`（3） | 真实方法安全拦截器：学生缺 `training:publish` → `AccessDeniedException`（由 `SecurityConfig` 的 `accessDeniedHandler` 映射为 **403**）；教师有权限 → 发布成功且 `teacherId` 取当前登录人；教师调 ADMIN 专属删除 → 被拒 |
| `security/PermissionCoverageCheckerTest`（2） | 无权限注解的接口 → 启动抛 `IllegalStateException`（B3 验收）；有 `@PreAuthorize` → 通过 |
| `security/TokenBlacklistServiceTest`（2） | logout 后 `jti` 立即判定已拉黑；自然过期后不再拦截 |
| `auth/LoginAttemptServiceTest`（3） | 连续 5 次失败即锁定并拒绝登录；15 分钟后自动解除；成功后计数清零 |
| `system/log/SysLogAspectTest`（1） | `@SysLog` 切面真实织入并调用 `SysLogService.record`（同时验证 AspectJ 与 Spring AOP 版本兼容） |
| `BackendApplicationTests`（1，跳过） | 原空测试类；需要可用 MySQL，已 `@Disabled` 并注明原因 |

> 首次 `mvn test` 需要联网下载 surefire 插件（本机已下载完成，之后可离线执行）。

---

## 4. 部署前置（数据库）

**必须先把下面脚本执行完，再启动新后端**，否则登录会因为 `sys_user_permission` 不存在而报错：

- `docs/backend/sql/V3__backend.sql`
  - `sys_user_permission`（B8 必需）
  - `sys_log`（B9 必需，列名须为 `oper_user_id / oper_content`；若库中已有同名表但列不同，改 `SysLogMapper.insert` 的列名即可，日志写失败不会影响业务）

同时确认数据库同学的：

- D1：`training.status`（B2）
- D5：`sys_role.data_scope`（B7），ADMIN=1、TEACHER/STUDENT=3
- D6：ADMIN 角色权限补齐（否则管理员也进不去管理菜单）

---

## 5. 待联调自测（数据库可达后执行）

```powershell
cd backend; mvn spring-boot:run

# 1) 无 token → 401 + JSON
curl.exe -i http://localhost:8080/api/training/list

# 2) 学生 token 调发布 → 403
curl.exe -i -X POST http://localhost:8080/api/training/1/publish -H "Authorization: Bearer <学生token>"

# 3) 教师 token 调发布 → 200，status 变 1
curl.exe -i -X POST http://localhost:8080/api/training/1/publish -H "Authorization: Bearer <教师token>"

# 4) 触发 SQL 错误（例如请求不存在的实训 id 再查结果）→ 500 + JSON，而不是 401
curl.exe -i http://localhost:8080/api/training/stat -H "Authorization: Bearer <教师token>"

# 5) 退出登录后重放旧 token → 401
curl.exe -i -X POST http://localhost:8080/api/auth/logout -H "Authorization: Bearer <token>"
curl.exe -i http://localhost:8080/api/auth/getInfo -H "Authorization: Bearer <token>"

# 6) 连续 5 次错误密码 → 第 6 次提示已锁定；sys_log 有 LOGIN_FAIL
curl.exe -i -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"student01\",\"password\":\"wrong\"}"

# 7) 管理员刷新权限 → 返回用户数
curl.exe -i -X POST http://localhost:8080/api/system/permission/refresh -H "Authorization: Bearer <ADMIN token>"
```

---

## 6. 风险与未做事项

| 项 | 说明 |
|---|---|
| 未做真实联调 | 数据库不可达，401/403/500/发布/登录锁定均只做了单元级与编译级验证 |
| `sys_user.status / deleted` 未参与登录校验 | 属 D4 新增列；方案未要求后端读取，本轮不改，避免误伤现有账号。后续可在 `SysUserMapper` 加过滤 + `LoginUser.isEnabled` |
| 个人附加权限只影响权限码，不增加菜单 | `menusOf` 仍按角色菜单返回；若需要个人菜单，扩展 `selectMenusByUserId` 即可 |
| 内存态黑名单 / 失败计数 | 重启即清空、多实例不共享；单机部署可接受，集群需换 Redis |
| `/api/auth/login` 的 `LOGIN` 日志 `oper_user_id` 为 NULL | 登录成功时 SecurityContext 还是空的，日志里记录了 `username`；如需 user_id 可在 `AuthService` 登录成功后补一条 |
| 400/401 响应体 | `BizException` 仍按旧约定返回 HTTP 200 + `code` 业务码（前端 F5 已按 code 分流）；未擅自改状态码，避免破坏现有前端 |
