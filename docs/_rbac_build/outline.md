# outline.md — RBAC 权限模块开发实施步骤说明

写作类型：专业领域文书（工程技术方案/实施步骤说明）→ Word。
读者：第10组开发成员（Java 后端 1—2 人、Vue 前端 1—2 人、测试/文档 1 人）。
用途：课程项目"第二步—第七步"权限模块的分步实施指引，只给步骤与判定标准，不落地代码。
编号体系：阿拉伯小数层级（1 → 1.1 → 1.1.1）。

## 引用核查表
| 引用名称 | 标准编号/文号 | 发布机关 | 年份 | 现行有效性 | 核实状态 | 来源 URL |
|---|---|---|---|---|---|---|
| Spring Boot 4 将 web starter 更名为 webmvc | — | Spring 官方博客 | 2025 | 现行 | 已核实 | https://spring.io/blog/2025/10/28/modularizing-spring-boot/ |
| spring-boot-starter-data-redis 为 Boot 4.1 可用 starter | — | Spring Initializr | 2026 | 现行 | 已核实 | https://start.spring.io/dependencies |
| jjwt 0.12.6（jjwt-api/impl/jackson） | — | jsonwebtoken.io | 2026 | 现行 | 已核实 | https://jsonwebtoken.io/ |
（其余内容为项目内部事实，来自附件 A1—A4，不需外部核实）

## 章节与要点
1 文档说明（目的、边界=只给步骤、读者）
2 现状盘点（后端仅 pom 与 properties、前端角色路由写死菜单、实库 13 张表含权限三表）
3 四项口径纠正（perm_code；无 user_role，单角色 role_id；perm_type 1/2；code/msg/data 与 401/403）—— 全文最关键一节
4 阶段A 准备（依赖、配置、包结构、公共返回体、异常）
5 阶段B 权限数据（命名规范、菜单树、初始化清单、校验 SQL、与前端 path 对应）
6 阶段C 基础 CRUD（用户/角色/权限，写库动作、事务、审计、验收）
7 阶段D 登录认证（步骤、JWT、Redis、BCrypt、失败分支）
8 阶段E 全局鉴权（方案对比、拦截器五步、白名单、上下文、403/401）
9 阶段F 前端（store、meta.perm、菜单树渲染、v-perm、拦截器）
10 阶段G 刷新（三个触发点、事务提交后清缓存、场景表、bug 清单）
11 阶段H 扩展（RBAC1/ABAC/SoD 取舍）
12 联调验收（测试用例表、交付清单）
13 风险与待确认（sys_user 缺 status；sys_role_permission 字段未确认；training 字段与接口不符；Redis 环境；HTTP 状态码约定）
附录A 建议权限码清单

## 图表
图1 登录认证与请求鉴权链路（两行流程 + 401/403 分支）
表1 权限相关表字段对照；表2 与通用方案差异；表3 依赖清单；表4 权限码清单；表5 接口—表—字段对照；表6 写库动作；表7 鉴权方案对比；表8 前端路由映射；表9 缓存刷新场景；表10 测试用例；表11 风险
