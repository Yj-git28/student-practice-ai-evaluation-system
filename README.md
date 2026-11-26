# 大学生软件实训教学 AI 检查评价系统

前后端分离的 Web 实训评价平台：学生提交实训成果、教师复核打分、管理员管控权限；配套登录鉴权、RBAC 权限、实训与成果管理、评价统计、实时群聊与操作日志。

## 一、功能模块

### 按角色
- **学生**：实训列表查看、提交实训成果、查看 AI 初评与教师复核结果。
- **教师**：发布/管理实训、复核学生成果、查看班级数据统计看板。
- **管理员**：用户管理、角色权限管理、系统配置、操作日志、管理员注册邀请码。

### 通用能力
- **账号鉴权**：密码登录、邮箱验证码登录、图形验证码、单设备登录（jti 会话绑定）、退出后 token 拉黑。
- **权限体系**：RBAC（用户 / 角色 / 权限 / 数据范围）、菜单树、接口级权限校验与公开接口声明。
- **实训管理**：发布实训、成果提交（单文件上限 20MB）、AI 初评、教师复核、班级与年级管理。
- **实时协作**：基于 WebSocket 的实训群聊，消息实时推送。
- **数据统计**：评价结果看板，前端 ECharts 可视化。

## 二、技术栈

### 后端
- Java 17、Spring Boot 4.1、Spring MVC / Security / WebSocket / Mail / Validation
- 数据库：MySQL 8，Spring JDBC（`JdbcClient`，未使用 MyBatis）
- 缓存与消息：Redis（权限/评分缓存、token 黑名单、登录失败锁定、Redis Stream 异步评分）
- 鉴权：JWT（jjwt 0.12.6，HS256）

### 前端
- Vue 3.5、Vite 8、Element Plus、Pinia、Vue Router、Axios、ECharts 6、crypto-js

### 部署
- Nginx（开发 / 生产两份配置模板）、Redis Windows 启动脚本

## 三、目录结构

```
student-practice-ai-evaluation-system
├── backend/                        # Spring Boot 后端
│   ├── .mvn/
│   ├── src/main/java/org/example/backend
│   │   ├── auth/                   # 登录注册：controller / service / mapper / dto / vo
│   │   ├── chat/                  # 实训群聊（ws/ 为 WebSocket 处理器与配置）
│   │   ├── common/                 # 业务异常、统一返回包装 R
│   │   ├── config/                 # 跨域、启动 Runner、数据范围解析
│   │   ├── evaluation/             # 评价统计与 Redis 缓存
│   │   ├── mq/                    # Redis Stream 消费者、队列与配置
│   │   ├── security/              # JWT 过滤器、鉴权注解、安全配置
│   │   ├── system/                 # 用户 / 角色 / 权限 / 班级 / 日志管理
│   │   ├── training/               # 实训与成果提交链路
│   │   └── BackendApplication.java
│   ├── src/main/resources/         # application.properties 与本地配置样例
│   └── pom.xml
├── frontend/                       # Vue3 + Vite 前端
│   ├── src/
│   │   ├── components/  directives/  router/  stores/  utils/  views/
│   │   ├── App.vue  main.js  style.css
│   ├── vite.config.js              # /api、/ws 代理到后端 8080
│   └── package.json
├── deploy/                         # 部署：Nginx 模板、Redis 脚本、部署包
├── docs/                           # 项目文档、数据库 SQL 脚本
├── uploads/                        # 本地上传文件（运行时生成，不入库）
├── .gitignore
└── README.md
```

## 四、环境要求

| 组件 | 版本要求 |
|---|---|
| JDK | 17+ |
| Maven | 3.8+（也可使用项目自带 `mvnw`） |
| Node.js | 18+，配套 npm |
| MySQL | 8.x |
| Redis | 5+（需支持 Stream） |

## 五、快速开始

### 1. 克隆仓库

```bash
git clone <仓库地址>
cd student-practice-ai-evaluation-system
```

### 2. 初始化数据库

1. 新建数据库 `training_eval`（字符集 `utf8mb4`）。
2. 按 `docs/backend/sql/README.md` 中的顺序执行 SQL 脚本（V3 → V10），脚本均为幂等、可重复执行。

### 3. 启动后端

1. 进入 `backend/src/main/resources`。
2. 复制 `application-local.properties.example` 为 `application-local.properties`，填写本机 MySQL、Redis 连接信息与 JWT 密钥。
   - 该文件已被 `.gitignore` 忽略，属于每个人本地独有配置，不会随仓库下载。
3. 启动服务：

```bash
cd backend
mvn spring-boot:run
```

后端默认地址：`http://localhost:8080`。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发地址：`http://localhost:5173`。Vite 已配置 `/api`、`/ws` 代理到后端 8080，**开发阶段不需要 Nginx**。

## 六、配置说明

- 主配置 `application.properties` 使用环境变量占位：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`JWT_SECRET`、`MAIL_*`、`MQ_*` 等；本地配置 `application-local.properties` 覆盖默认值。
- **邮件验证码**：SMTP 配置留空时走 mock 模式，验证码打印到后端日志；填齐后走真实 JavaMailSender 发送。
- **异步评分**：`app.mq.enabled=true` 时提交成果后投递 Redis Stream、后台异步初评；`false` 时退回同步算分。

## 七、部署（可选）

- `deploy/nginx.dev.conf`：开发模式，其余请求反代到 Vite 5173。
- `deploy/nginx.prod.conf`：生产模式，直接服务前端 `dist` 静态文件。
- **证书注意**：本地自签名 / mkcert 生成的证书不能随仓库分发；部署机器需自行生成证书，并在 Nginx 配置中替换证书与私钥路径。

## 八、端口与路由

| 服务 | 端口 | 说明 |
|---|---|---|
| 后端 Spring Boot | 8080 | 提供 API 与 WebSocket |
| 前端 Vite Dev | 5173 | 开发调试，代理 `/api`、`/ws` |
| Nginx | 80 / 443 | 可选，HTTP 301 跳转 HTTPS |

## 九、注意事项

- **不要提交**：`application-local.properties`（数据库密码、JWT 密钥）、证书私钥、`node_modules`、`target`、`dist`、运行日志、`uploads/`。
- 仓库不含依赖：前端必须执行 `npm install`，后端由 Maven 自动拉取依赖。
- **AI 初评当前为模拟评分**；大模型接入点已预留于 `mq/AiScoreConsumer` 与实训评分链路，后续可在此接入云端 / 本地大模型 API。
