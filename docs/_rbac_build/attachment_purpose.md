# attachment_purpose

| ID | 附件与角色 | 完整读取证据 | 源定位 | 必须使用的信息 | 目标位置 | 状态 |
|---|---|---|---|---|---|---|
| A1 | 10组接口设计文档.docx（交付结构+事实来源） | read.py 维度0 全文（接口汇总表、统一编码、7 个模块接口定义、示例） | 第 1 章接口汇总表；2.1 统一编码；2.3.1–2.3.7 | 接口路径与鉴权标记、/auth 系列与 /system/user 系列参数、code/msg/data 返回体、401/403 语义 | 第 3、6、7、8 章接口对照 | 采用 |
| A2 | 数据库设计说明书（.doc→docx 转换后读取） | read.py 维度0 全文（逻辑关系、11 张表字段、DDL 脚本） | 第 4 章关系；5.2 表设计；第 6 章脚本 | sys_user(user_id, role_id, username, password, real_name, class_id)、sys_role(role_id, role_name, role_code)、字符集 utf8mb4、命名规范 | 第 2.3、3、6 章字段映射 | 采用；与实库差异处已在第 3 章标注 |
| A3 | 截图 2026_11_12_10_34_48（sys_permission 实库数据） | 图像直读：7 行记录与 10 列字段 | 表格列头与 7 行数据 | 字段 permission_id/parent_id/perm_name/perm_type/perm_code/perm_path/icon/sort/create_time/update_time；示例码 training:list、training:publish、training:submit、ai:list、evaluation:list、user:list、config:list；perm_type 1/2；parent_id 0/1 | 第 3.1–3.3、5.3、9.2 | 采用 |
| A4 | 截图 2026_11_12_10_34_58 / 10_35_44（sys_role 实库数据） | 图像直读：3 行记录与 3 列字段 | 表格列头与数据 | role_id 1/2/3、role_name 教师/学生/管理员、role_code TEACHER/STUDENT/ADMIN；无 create_time 列 | 第 3.2、6.2、9.1 | 采用 |
| A5 | 用户本轮给出的二至七步要点（需求边界） | 用户消息原文 | 第二至第七步 | 需要的接口范围、登录流程、缓存方案二选一、拦截器伪代码、前端路由与按钮、刷新场景、扩展项 | 全文骨架 | 采用；与实库冲突处以第 3 章纠正，不直接实现代码，仅给步骤 |
| A6 | 命名规范.docx（格式参考） | read.py 维度0 全文 | 全文 | PascalCase 类名、文件夹全小写、英文命名、避免 info/data 等模糊词 | 第 4.3 包与类命名 | 采用 |

## 关系说明
A1 决定接口契约与返回体，A2 决定字段与主外键，A3/A4 是实库最新状态，优先于 A2 中被后续变更覆盖的部分（A2 未含 sys_permission 与 sys_role_permission）。A5 是要实现的目标，A6 约束命名。
