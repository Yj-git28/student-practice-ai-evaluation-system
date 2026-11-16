大学生软件实训教学AI检查评价系统

接口设计文档

# 1、接口汇总表

说明：1、该表汇总系统全部接口，建议接口名提供超链接，便于查询。

2、是否需要鉴权：表示是否需要用户登录后才能使用该接口（填 Y/N）。

3、用户认证与权限管理 /auth，实训任务管理 /task，实训成果上传与解析 /submission，AI 智能核查与评分 /check、/evaluate，人工复核与成绩发布 /review，统计与报表导出 /report，系统管理与运维、管理员功能 /system。

| 接口名 | 是否需要鉴权 | 描述 |
| --- | --- | --- |
| POST /auth/register | N | 用户注册 |
| POST /auth/login | N | 用户登录 |
| POST /auth/logout | Y | 用户登出 |
| GET /auth/user/info | Y | 获取当前登录用户信息 |
| PUT /auth/user/password | Y | 修改密码 |
| POST /task/create | Y | 创建实训任务 |
| POST /task/update | Y | 更新实训任务 |
| DELETE /task/{taskId} | Y | 删除实训任务 |
| GET /task/list | Y | 实训任务列表 |
| GET /task/{taskId} | Y | 实训任务详情 |
| POST /task/publish | Y | 发布实训任务 |
| POST /task/indicator/save | Y | 保存评价指标与权重 |
| GET /task/indicator/list | Y | 查询评价指标列表 |
| POST /submission/upload | Y | 上传实训成果 |
| GET /submission/list | Y | 实训成果列表 |
| GET /submission/{id}/status | Y | 查询上传/解析状态 |
| GET /submission/{id}/parse-result | Y | 获取解析结果 |
| POST /check/run | Y | 发起智能核查 |
| GET /check/{submissionId}/result | Y | 查询智能核查结果 |
| POST /evaluate/ai-score | Y | AI客观评分 |
| GET /evaluate/{submissionId}/result | Y | 查询AI评价结果 |
| POST /review/manual-score | Y | 教师主观评分 |
| POST /review/confirm | Y | 确认最终成绩 |
| GET /review/{submissionId}/detail | Y | 查询复核详情 |
| POST /report/generate | Y | 生成实训评价报告 |
| GET /report/export | Y | 导出统计报表 |
| GET /report/statistics | Y | 多维度统计查询（返回例如返回平均分、通过率等多指标） |
| POST /system/user/save | Y | 新增/编辑用户 |
| DELETE /system/user/{userId} | Y | 删除用户 |
| GET /system/user/list | Y | 查询用户列表 |
| POST /system/llm/config | Y | 大模型服务配置 |
| GET /system/log/list | Y | 操作日志查询 |

# 2、接口定义

说明：1、{}内是路径参数（Path Variable / 路径变量），外面这一对 {} 就是标记符号。2、该表中的响应编码为系统全局统一编码，所有接口共用，不得自行更改。

## 2.1 统一接口返回编码定义

| Response_code | Response_desc |
| --- | --- |
| 200 | 请求成功 |
| 204 | 成功，无响应体（DELETE 常用） |
| 400 | 请求参数错误 |
| 401 | 未认证或登录失效 |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 500 | 内部服务器错误 |

## 2.2 统一接口响应格式说明

系统所有接口统一采用 JSON 格式返回数据。成功时 code 为 1，data 存放业务数据；失败时 code 为对应错误码，msg 描述错误原因。响应格式如下：

成功：{“code”: 1, “msg”: “success”, “data”: {...}}

失败：{“code”: 400, “msg”: “错误描述”, “data”: null}

## 2.3 接口描述

### 2.3.1 用户认证与权限管理

| 类别 | 含义 |
| --- | --- |
| 接口名称 | POST /auth/register |
| 接口描述 | 用户注册：学生/教师/管理员注册系统账号 |
| 认证方式 | 无（注册后返回令牌） |
| http方式 | POST |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| username | String | Y | 登录用户名/账号 |
| password | String | Y | 登录密码 |
| realName | String | N | 真实姓名 |
| role | String | Y | 角色：student/teacher/admin |
| className | String | N | 所属班级 |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| userId | Long | Y | 用户ID |
| username | String | Y | 用户名 |
| token | String | Y | 登录令牌 |
| role | String | Y | 角色 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | POST /auth/login |
| 接口描述 | 用户登录：校验账号密码并返回令牌 |
| 认证方式 | 无（登录返回令牌） |
| http方式 | POST |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| username | String | Y | 登录用户名/账号 |
| password | String | Y | 登录密码 |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| userId | Long | Y | 用户ID |
| username | String | Y | 用户名 |
| realName | String | N | 真实姓名 |
| role | String | Y | 角色 |
| className | String | N | 所属班级 |
| token | String | Y | 登录令牌 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | POST /auth/logout |
| 接口描述 | 用户登出：注销当前登录态 |
| 认证方式 | Token |
| http方式 | POST |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| message | String | Y | 提示信息 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | GET /auth/user/info |
| 接口描述 | 获取当前登录用户信息 |
| 认证方式 | Token |
| http方式 | GET |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| userId | Long | Y | 用户ID |
| username | String | Y | 用户名 |
| realName | String | Y | 真实姓名 |
| role | String | Y | 角色 |
| className | String | N | 所属班级 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | PUT /auth/user/password |
| 接口描述 | 修改密码：修改当前用户登录密码 |
| 认证方式 | Token |
| http方式 | PUT |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| oldPassword | String | Y | 原密码 |
| newPassword | String | Y | 新密码 |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| message | String | Y | 提示信息 |

### 2.3.2 实训任务管理

| 类别 | 含义 |
| --- | --- |
| 接口名称 | POST /task/create |
| 接口描述 | 创建实训任务：教师创建实训任务并配置实训要求 |
| 认证方式 | Token（教师） |
| http方式 | POST |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| taskName | String | Y | 实训任务名称 |
| description | String | N | 任务说明 |
| requireText | String | Y | 实训要求说明 |
| deadline | String | N | 截止时间 |
| status | String | N | 任务状态，默认草稿 |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| taskId | Long | Y | 实训任务ID |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | POST /task/update |
| 接口描述 | 更新实训任务：更新任务基本信息 |
| 认证方式 | Token（教师） |
| http方式 | POST |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| taskId | Long | Y | 实训任务ID |
| taskName | String | N | 实训任务名称 |
| description | String | N | 任务说明 |
| requireText | String | N | 实训要求说明 |
| deadline | String | N | 截止时间 |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| message | String | Y | 提示信息 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | DELETE /task/{taskId} |
| 接口描述 | 删除实训任务：删除指定实训任务 |
| 认证方式 | Token（教师/管理员） |
| http方式 | DELETE |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| taskId | Long | Y | 实训任务ID（路径参数） |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| message | String | Y | 提示信息 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | GET /task/list |
| 接口描述 | 查询实训任务列表：分页查询，支持状态与关键字筛选 |
| 认证方式 | Token |
| http方式 | GET |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| pageNo | Integer | Y | 页码，从1开始 |
| pageSize | Integer | Y | 每页条数 |
| status | String | N | 任务状态：草稿/已发布/进行中/已结束 |
| keyword | String | N | 关键字（任务名） |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| total | Long | Y | 总条数 |
| records | Array | Y | 任务列表：taskId（实训任务编号（主键 id））/taskName（实训任务名称）/description（任务描述、说明）/status（任务状态：草稿 / 已发布 / 进行中 / 已结束）/deadline（截止时间）/teacherId（创建该任务的老师 id ）/teacherName（创建该任务的老师姓名） |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | GET /task/{taskId} |
| 接口描述 | 查询实训任务详情：含评价指标列表 |
| 认证方式 | Token |
| http方式 | GET |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| taskId | Long | Y | 实训任务ID（路径参数） |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| taskId | Long | Y | 实训任务ID |
| taskName | String | Y | 实训任务名称 |
| description | String | N | 任务说明 |
| requireText | String | Y | 实训要求说明 |
| status | String | Y | 任务状态 |
| deadline | String | N | 截止时间 |
| teacherId | Long | Y | 创建教师ID |
| indicatorList | Array | N | 评价指标：indicatorId（评价指标编号）/name（指标名称（比如：代码规范性、文档完整性））/weight（权重（0~1，所有指标权重加起来一般等于 1，AI 打分按权重加权计算总分）） |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | POST /task/publish |
| 接口描述 | 发布实训任务：发布后学生可见并可提交 |
| 认证方式 | Token（教师） |
| http方式 | POST |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| taskId | Long | Y | 实训任务ID |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| message | String | Y | 提示信息 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | POST /task/indicator/save |
| 接口描述 | 保存评价指标与权重：为实训任务配置自定义指标及权重 |
| 认证方式 | Token（教师） |
| http方式 | POST |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| taskId | Long | Y | 实训任务ID |
| indicatorList | Array | Y | 指标列表：name（String，指标名）、weight（Double，权重0-1） |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| message | String | Y | 提示信息 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | GET /task/indicator/list |
| 接口描述 | 查询评价指标列表：查询某实训任务的评价指标 |
| 认证方式 | Token |
| http方式 | GET |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| taskId | Long | Y | 实训任务ID |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| indicatorList | Array | Y | 评价指标：indicatorId（指标编号）/name（指标名称）/weight（权重） |

### 2.3.3 实训成果上传与解析

| 类别 | 含义 |
| --- | --- |
| 接口名称 | POST /submission/upload |
| 接口描述 | 上传实训成果：上传Word/PDF/截图等多格式成果并触发解析 |
| 认证方式 | Token（学生） |
| http方式 | POST（multipart/form-data） |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| taskId | Long | Y | 实训任务ID |
| file | File | Y | 成果文件：支持 doc/docx/pdf/jpg/png |
| files | Array | N | 多文件批量上传 |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| submissionId | Long | Y | 提交记录ID |
| uploadStatus | String | Y | 上传状态：SUCCESS/FAIL |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | GET /submission/list |
| 接口描述 | 查询实训成果列表：查询本人实训成果提交记录 |
| 认证方式 | Token |
| http方式 | GET |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| taskId | Long | N | 实训任务ID |
| pageNo | Integer | Y | 页码 |
| pageSize | Integer | Y | 每页条数 |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| total | Long | Y | 总条数 |
| records | Array | Y | 提交记录：submissionId（提交记录唯一 ID）/fileName（上传的文件名称）/fileType（文件类型）/uploadStatus（上传状态）/parseStatus（文档解析状态）/submitTime（提交时间） |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | GET /submission/{id}/status |
| 接口描述 | 查询上传/解析状态：查询成果上传与解析处理状态 |
| 认证方式 | Token |
| http方式 | GET |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| id | Long | Y | 提交记录ID（路径参数） |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| submissionId | Long | Y | 提交记录ID |
| uploadStatus | String | Y | 上传状态 |
| parseStatus | String | Y | 解析状态：待解析/解析中/已完成/失败 |
| progress | String | N | 进度说明 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | GET /submission/{id}/parse-result |
| 接口描述 | 获取解析结果：获取大模型解析出的结构化成果内容 |
| 认证方式 | Token |
| http方式 | GET |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| id | Long | Y | 提交记录ID（路径参数） |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| submissionId | Long | Y | 提交记录ID |
| parseResult | Object | Y | 结构化解析结果：title（文档标题）/content（正文主体内容）/keyPoints（提取出来的重点要点） |
| parseText | String | N | 清洗后文本 |

### 2.3.4 AI智能核查与评分

| 类别 | 含义 |
| --- | --- |
| 接口名称 | POST /check/run |
| 接口描述 | 发起智能核查：对已解析成果执行要求校验/步骤完整性/逻辑漏洞核查 |
| 认证方式 | Token（教师/系统） |
| http方式 | POST |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| submissionId | Long | Y | 提交记录ID |
| taskId | Long | Y | 实训任务ID |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| checkId | Long | Y | 核查记录ID |
| checkStatus | String | Y | 核查状态：执行中/已完成 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | GET /check/{submissionId}/result |
| 接口描述 | 查询智能核查结果：返回三项核查结论 |
| 认证方式 | Token |
| http方式 | GET |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| submissionId | Long | Y | 提交记录ID（路径参数） |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| checkId | Long | Y | 核查记录ID |
| submissionId | Long | Y | 提交记录ID |
| requirementCheck | Object | Y | 要求与效果校验：name（校验项名称）/result（结论：达标 / 不达标）/remark（AI 说明理由） |
| stepCheck | Object | Y | 步骤完整性核查：result（完整 / 缺失）/remark（缺少哪些步骤的说明） |
| logicCheck | Object | Y | 逻辑漏洞识别：result（无漏洞 / 存在漏洞）/remark（描述漏洞位置和问题） |
| checkStatus | String | Y | 核查状态 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | POST /evaluate/ai-score |
| 接口描述 | AI客观评分：按评价指标调用大模型给出客观评分与评语 |
| 认证方式 | Token（教师/系统） |
| http方式 | POST |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| submissionId | Long | Y | 提交记录ID |
| taskId | Long | Y | 实训任务ID |
| indicatorList | Array | Y | 指标列表：indicatorId（指标编号）/name（指标名称）/weight（权重） |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| evaluateId | Long | Y | 评价记录ID |
| aiScore | Double | Y | AI加权综合分 |
| aiComment | String | Y | AI评语 |
| scoreDetail | Array | Y | 分项得分：indicatorId（指标编号）/name（指标名称）/score（该指标的单项分数） |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | GET /evaluate/{submissionId}/result |
| 接口描述 | 查询AI评价结果：查询客观评分与评语 |
| 认证方式 | Token |
| http方式 | GET |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| submissionId | Long | Y | 提交记录ID（路径参数） |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| evaluateId | Long | Y | 评价记录ID |
| submissionId | Long | Y | 提交记录ID |
| aiScore | Double | Y | AI综合分 |
| aiComment | String | Y | AI评语 |
| scoreDetail | Array | Y | 分项得分：indicatorId（指标编号）/name（指标名称）/score（该指标的单项分数） |

### 2.3.5 人工复核与成绩发布

| 类别 | 含义 |
| --- | --- |
| 接口名称 | POST /review/manual-score |
| 接口描述 | 教师主观评分：在AI初评基础上补充/修正主观评分 |
| 认证方式 | Token（教师） |
| http方式 | POST |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| submissionId | Long | Y | 提交记录ID |
| manualScore | Double | Y | 主观综合分 |
| manualComment | String | N | 主观评语 |
| adjustList | Array | N | 调整项：indicatorId（指标编号）/name（指标名称）/score（该指标的单项分数） |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| message | String | Y | 提示信息 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | POST /review/confirm |
| 接口描述 | 确认最终成绩：教师确认并发布最终成绩 |
| 认证方式 | Token（教师） |
| http方式 | POST |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| submissionId | Long | Y | 提交记录ID |
| finalScore | Double | Y | 最终成绩 |
| finalComment | String | N | 最终评语 |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| message | String | Y | 提示信息 |
| finalScore | Double | Y | 最终成绩 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | GET /review/{submissionId}/detail |
| 接口描述 | 查询复核详情：查看AI初评与人工复核合并的评价详情 |
| 认证方式 | Token |
| http方式 | GET |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| submissionId | Long | Y | 提交记录ID（路径参数） |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| submissionId | Long | Y | 提交记录ID |
| aiScore | Double | Y | AI客观分 |
| manualScore | Double | N | 教师主观分 |
| finalScore | Double | N | 最终成绩 |
| aiComment | String | N | AI评语 |
| manualComment | String | N | 主观评语 |
| finalComment | String | N | 最终评语 |
| scoreDetail | Array | N | 分项得分 |

### 2.3.6 统计与报表导出

| 类别 | 含义 |
| --- | --- |
| 接口名称 | POST /report/generate |
| 接口描述 | 生成实训评价报告：汇总核查与评分数据生成个人评价报告 |
| 认证方式 | Token（教师/管理员） |
| http方式 | POST |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| submissionId | Long | Y | 提交记录ID |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| reportId | Long | Y | 报告ID |
| reportStatus | String | Y | 报告状态：生成中/完成 |
| reportUrl | String | N | 报告下载地址 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | GET /report/export |
| 接口描述 | 导出统计报表：按科目/班级等维度导出Excel/PDF报表 |
| 认证方式 | Token（教师/管理员） |
| http方式 | GET |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| format | String | Y | 导出格式：excel/pdf |
| taskId | Long | N | 实训任务ID |
| classId | Long | N | 班级ID |
| startTime | String | N | 开始时间 |
| endTime | String | N | 结束时间 |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| reportUrl | String | Y | 报表下载地址 |
| fileName | String | Y | 文件名 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | GET /report/statistics |
| 接口描述 | 多维度统计查询：统计实训成绩数据，返回图表所需数据 |
| 认证方式 | Token（教师/管理员） |
| http方式 | GET |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| taskId | Long | N | 实训任务ID |
| classId | Long | N | 班级ID |
| dimension | String | N | 统计维度 |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| statisticsData | Array | Y | 统计数据：label（分类名称）/value（对应数量） |
| totalCount | Long | N | 提交总数 |
| avgScore | Double | N | 平均分 |
| passRate | Double | N | 通过率 |

### 2.3.7 系统管理与运维

| 类别 | 含义 |
| --- | --- |
| 接口名称 | POST /system/user/save |
| 接口描述 | 新增/编辑用户：管理员维护用户账号与角色 |
| 认证方式 | Token（管理员） |
| http方式 | POST |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| userId | Long | N | 用户ID（编辑时必填） |
| username | String | Y | 用户名 |
| password | String | N | 密码（新增时必填） |
| realName | String | N | 真实姓名 |
| role | String | Y | 角色：student/teacher/admin |
| className | String | N | 所属班级 |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| message | String | Y | 提示信息 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | DELETE /system/user/{userId} |
| 接口描述 | 删除用户：管理员删除指定用户 |
| 认证方式 | Token（管理员） |
| http方式 | DELETE |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| userId | Long | Y | 用户ID（路径参数） |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| message | String | Y | 提示信息 |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | GET /system/user/list |
| 接口描述 | 查询用户列表：分页查询，支持角色/班级筛选 |
| 认证方式 | Token（管理员） |
| http方式 | GET |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| pageNo | Integer | Y | 页码 |
| pageSize | Integer | Y | 每页条数 |
| role | String | N | 角色 |
| className | String | N | 班级 |
| keyword | String | N | 关键字（用户名/姓名） |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| total | Long | Y | 总条数 |
| records | Array | Y | 用户列表：userId（用户唯一 ID ）/username（登录账号）/realName(真实姓名)/role（用户角色）/className（所属班级）/status（账号状态） |

| 类别 | 含义 |
| --- | --- |
| 接口名称 | POST /system/llm/config |
| 接口描述 | 大模型服务配置：配置云端大模型服务接入参数 |
| 认证方式 | Token（管理员） |
| http方式 | POST |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| apiUrl | String | Y | 大模型API地址 |
| apiKey | String | Y | API密钥 |
| modelName | String | Y | 模型名称 |
| enabled | Boolean | Y | 是否启用 |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| message | String | Y | 提示信息 |
| 类别 |  | 含义 |  |
| 接口名称 |  | GET /system/log/list |  |
| 接口描述 |  | 操作日志查询：分页查询系统操作日志与AI调用日志 |  |
| 认证方式 |  | Token（管理员） |  |
| http方式 |  | GET |  |

### 请求参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| pageNo | Integer | Y | 页码 |
| pageSize | Integer | Y | 每页条数 |
| logType | String | N | 日志类型 |
| startTime | String | N | 开始时间 |
| endTime | String | N | 结束时间 |

### 响应参数

| 参数名 | 类型 | 是否必须 | 描述 |
| --- | --- | --- | --- |
| total | Long | Y | 总条数 |
| records | Array | Y | 日志列表：logId（日志唯一 ID）/logType（日志类型）/operator（操作人）/content（操作详情描述）/createTime（操作发生时间） |

# 3、接口定义实例

### 3.1 用户注册接口

以“用户注册”接口（POST /auth/register）为例，说明接口的请求与响应示例。

### 3.2 请求示例

request请求：

{

"username": "student01",

"password": "123456",

“realName”: “张三”,

"role": "student",

“className”: “软件工程2401班”

}

### 3.3 响应示例

(1) 成功响应：

{

"code": 1,

"msg": "success",

"data": {

"userId": 10001,

"username": "student01",

"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",

"role": "student"

}

}

(2) 失败响应：

{

"code": 400,

“msg”: “用户名已存在”,

"data": null

}
