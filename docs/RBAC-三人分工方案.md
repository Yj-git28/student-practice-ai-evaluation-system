# RBAC 改进任务分工（前端 / 后端 / 数据库）

- 项目：`student-practice-ai-evaluation-system`
- 技术栈：Spring Boot 4.1.1 + JdbcClient + Spring Security + JWT；Vue 3 + Pinia + Element Plus + Vite
- 数据库：MySQL 8，库名 `training_eval`
- 文档用途：三人并行开发的唯一任务来源，任务编号可直接用于提 issue / 日报

---

## 0. 当前现状（已核实）

| 检查项 | 状态 |
|---|---|
| 后端 `@PreAuthorize` 覆盖 | 仅 6 处（TrainingController 3、TrainingResultController 1、AuthController 1） |
| `SecurityConfig` 放行列表 | 只有 `/api/auth/login`、`/test`，**缺 `/error`** |
| `TrainingMapper` | 仍查询不存在的 `status` 列 |
| 文件名大小写 | `hasperm.js`、`usepermissionstore.js` 与引用不一致 |
| 根目录 `package.json` | 与 `frontend/package.json` 重复 |
| logout / token 黑名单 / Redis | 均未实现 |
| 测试 | 仅 1 个空测试类 |

三个必须优先修复的 P0：

1. `/error` 未放行，导致所有 500 被伪装成 401
2. `training` 表缺 `status` 列，发布/统计接口必然报错
3. 前端 `/403` 是死循环，进去出不来

---

## 1. 分工总览

| 角色 | 负责范围 | 核心交付 |
|---|---|---|
| **数据库** | 表结构、索引、初始数据、迁移脚本 | 可重复执行的变更 SQL + 修正后的权限数据 |
| **后端** | 接口、鉴权、缓存、异常处理 | 鉴权闭环、`/error` 放行、logout、权限覆盖率校验 |
| **前端** | 路由、菜单、按钮权限、错误提示 | 403 死循环修复、文件名统一、菜单错位修复 |

依赖关系：

```
数据库改列 ──────→ 后端改 SQL ──────→ 前端联调
数据库改权限数据 ──→ 前端菜单正确
```

---

## 2. 数据库同学

### 2.1 任务清单

| 编号 | 任务 | SQL | 验收标准 |
|---|---|---|---|
| D1 | `training` 加发布状态列 | `ALTER TABLE training ADD COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0未发布 1已发布';` | `DESC training;` 能看到 `status` |
| D2 | 唯一约束防脏数据 | `ALTER TABLE sys_user ADD UNIQUE KEY uk_username (username);`<br>`ALTER TABLE sys_permission ADD UNIQUE KEY uk_perm_code (perm_code);` | 插重复 `username` 报 1062 |
| D3 | 常用查询索引 | `ALTER TABLE training ADD INDEX idx_teacher (teacher_id);`<br>`ALTER TABLE train_result ADD INDEX idx_student (student_id);` | `SHOW INDEX FROM training;` 有结果 |
| D4 | 账号状态与逻辑删除 | `ALTER TABLE sys_user ADD COLUMN status TINYINT NOT NULL DEFAULT 1, ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0;` | 三个账号 `status` 均为 1 |
| D5 | 角色数据范围 | `ALTER TABLE sys_role ADD COLUMN data_scope TINYINT NOT NULL DEFAULT 3 COMMENT '1全部 2本部门 3本人';` | ADMIN=1、TEACHER=3、STUDENT=3 |
| D6 | 权限数据修正 | 给 ADMIN 角色补齐全部权限；从 TEACHER 摘掉 `user:list`、`config:list` | `SELECT * FROM sys_role_permission WHERE role_id=3;` 非空 |
| D7 | 学生菜单修正 | 删除学生角色下的 `/training` 菜单，仅保留 `/training/list`、`/training/submit` | 学生登录首屏是「我的实训任务」 |
| D8 | 迁移脚本归档 | 在 `docs/db/` 下按编号拆分 `V1__p0.sql`、`V2__data_fix.sql` | 新环境可一键执行 |

### 2.2 交付物

- `docs/db/` 目录，按编号拆分的 SQL 文件
- 一份「改了什么、影响哪些接口」的变更说明

### 2.3 完成信号

- D1–D5 执行完毕 → 通知后端可以改 SQL
- D6–D7 执行完毕 → 通知前端可以联调菜单

### 2.4 自测

```sql
SELECT role_id, COUNT(*) FROM sys_role_permission GROUP BY role_id;  -- ADMIN 应 >= 7
DESC training;                                                       -- 含 status
SHOW INDEX FROM sys_user;                                            -- 含 uk_username
```

---

## 3. 后端同学

### 3.1 第一批：P0（依赖 D1）

| 编号 | 任务 | 文件 | 验收标准 |
|---|---|---|---|
| B1 | 放行 `/error` | `backend/src/main/java/org/example/backend/security/SecurityConfig.java` | 故意触发异常，返回 500 + JSON，而不是 401 |
| B2 | 发布/统计 SQL 对齐 `status` | `backend/src/main/java/org/example/backend/training/TrainingMapper.java` | `POST /api/training/{id}/publish` 返回 200 |
| B3 | 确认 `PermissionCoverageChecker` 启动即校验 | `backend/src/main/java/org/example/backend/security/PermissionCoverageChecker.java` | 新增一个不带权限注解的接口，应用启动失败 |

`SecurityConfig` 具体改法：

```java
.requestMatchers("/api/auth/login", "/test", "/error").permitAll()
```

### 3.2 第二批：安全闭环

| 编号 | 任务 | 关键点 |
|---|---|---|
| B4 | logout + `jti` 黑名单 | `JwtService` 签发时写入 `jti`；新增 `POST /api/auth/logout`；`JwtAuthFilter` 校验黑名单 |
| B5 | 权限版本号 + 刷新接口 | `PermissionCacheService` 增加 version；新增 `POST /api/system/permission/refresh`（仅 ADMIN） |
| B6 | 登录失败限制 | 连续 5 次失败锁定 15 分钟，并写 `sys_log` |
| B7 | 数据范围改为读字段（依赖 D5） | `config/DataScopeResolver.java` 改读 `sys_role.data_scope`，删除按 roleCode 硬编码 |

### 3.3 第三批：模型与工程化

| 编号 | 任务 | 说明 |
|---|---|---|
| B8 | 个人附加权限 | 建 `sys_user_permission` 后，`PermissionService` 取「角色权限 ∪ 个人权限」 |
| B9 | `sys_log` AOP | 登录、改角色、改权限三类操作落库 |
| B10 | `getInfo` 增加 `dataScope` | 前端据此控制按钮显隐 |
| B11 | 配置外部化 | DB 密码、`jwt.secret` 改为 `${DB_PASSWORD}` 等环境变量 |
| B12 | 补测试 | 权限并集、数据范围过滤、越权返回 403，至少 3 个真实断言 |

### 3.4 需要对外承诺的接口契约

```
POST /api/auth/logout                 空 body，返回 R.ok()
POST /api/system/permission/refresh   仅 ADMIN，返回刷新的用户数
GET  /api/auth/getInfo                响应新增 dataScope: 1|2|3
GET  /api/auth/getInfo                menus[].component（可选，用于替换前端硬编码映射）
```

### 3.5 自测

```powershell
# 无 token 访问受保护接口 → 401
curl.exe -i http://localhost:8080/api/training/list

# 学生 token 调发布接口 → 403
curl.exe -i -X POST http://localhost:8080/api/training/1/publish -H "Authorization: Bearer <学生token>"

# 教师 token 调发布接口 → 200
curl.exe -i -X POST http://localhost:8080/api/training/1/publish -H "Authorization: Bearer <教师token>"

# 退出后旧 token 应立即失效 → 401
```

---

## 4. 前端同学

### 4.1 第一批：P0

| 编号 | 任务 | 文件 | 验收标准 |
|---|---|---|---|
| F1 | 修 403 死循环 | `frontend/src/router/index.js` | 登录后直接进有权限菜单；/403 刷新能自动跳出 |
| F2 | 403 页加返回/重登按钮 | `frontend/src/views/403.vue` | 两个按钮均可用 |
| F3 | 文件名大小写统一 | `directives/hasperm.js` → `hasPerm.js`<br>`stores/usepermissionstore.js` → `usePermissionStore.js` | Linux 构建不报模块找不到 |

F1 的核心改法：守卫里加载完动态路由后，不要让 `/` 和 `/403` 走 `next({ ...to })` 重放。

```javascript
const firstPath = permStore.menus.length ? permStore.menus[0].permPath : ''
if (to.path === '/' || to.path === '/403') {
  return next({ path: firstPath || '/403', replace: true })
}
return next({ ...to, replace: true })
```

### 4.2 第二批：体验与正确性

| 编号 | 任务 | 说明 |
|---|---|---|
| F4 | 学生菜单错位（依赖 D7） | 学生首屏不再进入教师页 `TaskManage.vue` |
| F5 | 401 / 403 / 500 提示区分 | `utils/request.js`：401 跳登录、403 提示无权限、500 展示服务端错误信息 |
| F6 | 按钮级权限全覆盖 | 检查所有增删改按钮是否都用了 `v-has-perm`，遗漏补齐 |
| F7 | 清理重复工程 | 删除根目录 `package.json`、根目录 `node_modules` |

### 4.3 第三批：可维护性

| 编号 | 任务 | 说明 |
|---|---|---|
| F8 | `componentMap` 改为后端驱动 | 依赖 B10 返回 `component`，前端用 `import.meta.glob` 动态匹配 |
| F9 | 二级菜单支持 | `generateRoutes` 递归处理 `parentId` |
| F10 | 全局错误边界 | 注册 `app.config.errorHandler` 统一记录前端异常 |

### 4.4 自测

- 学生账号登录：首屏为「我的实训任务」，侧边栏无「用户管理」「系统配置」
- 在 `/403` 页面按 F5，能自动跳回有权限页面
- 学生直接访问 `/system/user`，应看到 403 页面而不是白屏
- 服务端返回 500 时，页面展示具体错误，而不是"登录已过期"

---

## 5. 三方必须先对齐的 3 个契约

1. **`status` 取值**：0 未发布 / 1 已发布 —— 数据库加列、后端 SQL、前端文案必须一致
2. **`dataScope` 取值**：1 全部 / 2 本部门 / 3 本人 —— 由后端返给前端，前端不得自行推断
3. **菜单路径与组件的对应关系** —— 要么数据库保证 `perm_path` 都在前端映射表内，要么改成后端返回 `component`

---

## 6. 推进节奏（按阶段）

### 阶段 1｜打通

数据库 D1–D7 → 后端 B1–B3 → 前端 F1–F3 → 三方联调

完成标志：学生登录进「我的实训任务」；教师点「发布实训」返回 200。

### 阶段 2｜安全

后端 B4–B7 → 前端 F5–F6（数据库无新增）

完成标志：退出登录后旧 token 立即失效；新接口漏加权限注解时应用无法启动。

### 阶段 3｜收尾

后端 B8–B12 → 前端 F8–F10 → 数据库补迁移脚本

完成标志：`mvn test` 有 3 个以上真实断言；新环境一条命令完成库初始化。

---

## 7. 联调检查表

| 场景 | 预期 |
|---|---|
| 未登录访问 `/api/training/list` | 401，JSON 响应体 |
| 学生调 `POST /api/training/1/publish` | 403 |
| 教师调 `POST /api/training/1/publish` | 200，`status` 变 1 |
| 触发一个 SQL 错误 | 500 + 具体错误，**不是** 401 |
| 学生登录 | 首屏「我的实训任务」，菜单 5 项内无管理菜单 |
| 管理员登录 | 可见全部 7 个菜单 |
| `/403` 页面刷新 | 自动跳回有权限页面 |
| 退出登录后重放旧 token | 401 |
