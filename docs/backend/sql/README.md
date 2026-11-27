# 数据库脚本执行顺序

库名：`training_eval`（字符集 utf8mb4）。在 MySQL 8 中按下面编号顺序逐个人工执行；
脚本均为幂等/可重复执行（重复跑不会重复加列）。

| 顺序 | 脚本 | 作用 |
|---|---|---|
| 1 | `V3__backend.sql` | 基础表、`sys_user_permission`、`sys_log` |
| 2 | `V4__phone_sms_login.sql` | 短信/手机登录字段 |
| 3 | `V5__phone_to_email.sql` | 手机登录改为邮箱登录 |
| 4 | `V6__single_session.sql` | 单设备登录（jti 会话绑定） |
| 5 | `V7__sys_config.sql` | 系统配置表 |
| 6 | `V8__class_grade.sql` | `class` 表增加年级字段 |
| 7 | `V8__training_enhance.sql` | `train_result` 增加 AI 初评/教师复核字段 |
| 8 | `V9__training_scope.sql` | 实训数据范围字段 |
| 9 | `V10__chat_and_perm_admin.sql` | 群聊表与权限管理补充 |
| 10 | `V11__rbac_integrity.sql` | RBAC 外键/索引/字典注释加固 + 角色-权限-用户排查视图 |
| 11 | `V12__rbac_button_perms.sql` | 菜单内部按钮权限元数据 + 角色按钮授权 |
| 12 | `V13__rbac_many_to_many.sql` | 用户-角色升级多对多（`sys_user_role`）+ 角色内置标记/状态/描述 + 关联表审计列 + 权限视图重建 |

> 说明：两个 `V8__` 脚本分别作用于 `class` 与 `train_result`，互不依赖，先后执行均可；
> 编号重复仅为历史遗留，未改动文件名以免影响已有文档与备份引用。
