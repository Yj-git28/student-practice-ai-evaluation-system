数据库设计说明书

目   录

数据库设计说明书	1

目   录	2

1	引言	3

1.1	编写目的	3

1.2	术语表	3

2	数据库环境说明	3

3	数据库的命名规则	3

4逻辑设计	4

5物理设计	5

4.1表汇总	5

4.2表设计	5

6 脚本文件	9

# 引言

## 编写目的

本文档为本实训管理系统的数据库设计说明书，用于描述系统数据库整体架构、逻辑模型、物理表结构、字段约束、表之间外键关联关系。 供开发人员编码实现、测试人员测试、后期维护人员查阅参考；同时作为课程设计交付文档，明确数据库全部设计细节。

## 术语表

| 序号 | 术语或缩略语 | 说明性定义 |
| --- | --- | --- |
| 1 | PDM | Physical Data Model，物理数据模型，PowerDesigner 物理模型文件 |
| 2 | 主键 (PK) | 唯一标识一条记录的字段，非空且唯一 |
| 3 | 外键 (FK) | 引用另外一张表主键，维护表与表参照完整性 |
| 4 | 1:N | 一对多关系，一条父记录可以对应多条子记录 |
| 5 | 1:1 | 一对一关系，父记录与子记录一一对应 |
| 6 | InnoDB | MySQL 存储引擎，支持事务、外键约束 |

# 数据库环境说明

| 项目 | 数据库系统 | 部署环境 | 设计工具 | 说明 |
| --- | --- | --- | --- | --- |
| 参数 | MySQL 8.0 | Windows | PowerDesigner16.5 | 存储引擎 InnoDB，字符集 utf8mb4，排序规则 utf8mb4_general_ci |

# 数据库的命名规则

数据库、表、字段全部采用小写字母 + 下划线命名，禁止大写、中文、MySQL 关键字。

表命名：业务含义，见名知意；如sys_user系统用户表、training实训项目表。

字段命名：主键统一格式 xxx_id；外键字段名称和被引用主键名称保持一致。

时间字段：create_time 创建时间，update_time 修改时间，datetime 类型。

索引命名：idx_表名_字段名。

布尔状态字段使用tinyint：0 代表否，1 代表是。

所有对象命名不超过 32 字符。

# 4逻辑设计

模型包含 11 张数据表：sys_role、class、sys_user、training、train_result、ai_check、ai_parse、indicator、evaluation、sys_config、sys_log。

关系汇总：

sys_role (1) — sys_user (N) 角色与用户一对多

class (1) — sys_user (N) 班级与用户一对多

training (1) — train_result (N) 实训项目与实训提交记录一对多

sys_user (1) — train_result (N) 用户与实训提交记录一对多

train_result (1) — ai_check (N) 提交记录与 AI 检查记录一对多

train_result (1) — ai_parse (1) 提交记录与 AI 解析记录一对一

training (1) — indicator (N) 实训项目与评价指标一对多

training (1) — evaluation (N) 实训项目与评价记录一对多

sys_user (1) — evaluation (N) 用户与评价记录一对多

indicator (1) — evaluation (N) 评价指标与评价记录一对多

# 5物理设计

## 5.1表汇总

| 表名 | 说明 |
| --- | --- |
| sys_role | 角色表，存储系统角色信息，管理员、普通学生等角色 |
| class | 班级表，存储班级基础信息 |
| sys_user | 系统用户表，管理员、学生账号信息 |
| training | 实训项目表，保存各个实训任务信息 |
| train_result | 实训提交记录表，学生提交实训作业记录 |
| ai_check | AI 检查记录表，AI 对实训作业的检查结果 |
| ai_parse | AI 解析记录表，实训作业解析详情，和提交记录一对一 |
| indicator | 评价指标表，实训打分的评价指标项 |
| evaluation | 评价记录表，学生实训各项指标打分记录 |
| sys_config | 系统配置表，全局参数配置，无外键 |
| sys_log | 系统操作日志表，记录用户操作日志，无外键 |

## 5.2表设计

表 5-1 sys_role（角色表）

| 列名 | 数据类型 | 长度 | 可为空 | 主键 | 注释 |
| --- | --- | --- | --- | --- | --- |
| role_id | int | 11 | 否 | √ | 角色主键 ID |
| role_name | varchar | 50 | 否 |  | 角色名称 |
| role_desc | varchar | 200 | 是 |  | 角色描述 |
| create_time | datetime |  | 是 |  | 创建时间 |
| update_time | datetime |  | 是 |  | 修改时间 |

表 5-2 class（班级表）

| 列名 | 数据类型 | 长度 | 可为空 | 主键 | 注释 |
| --- | --- | --- | --- | --- | --- |
| class_id | int | 11 | 否 | √ | 班级主键 ID |
| class_name | varchar | 50 | 否 |  | 班级名称 |
| class_remark | varchar | 200 | 是 |  | 班级备注 |
| create_time | datetime |  | 是 |  | 创建时间 |
| update_time | datetime |  | 是 |  | 修改时间 |

表 5-3 sys_user（用户表）

| 列名 | 数据类型 | 长度 | 可为空 | 主键 | 注释 |
| --- | --- | --- | --- | --- | --- |
| user_id | int | 11 | 否 | √ | 用户主键 ID |
| user_name | varchar | 50 | 否 |  | 登录账号 |
| password | varchar | 100 | 否 |  | 密码密文存储 |
| real_name | varchar | 30 | 否 |  | 真实姓名 |
| role_id | int | 11 | 否 |  | FK，关联 sys_role 角色 ID |
| class_id | int | 11 | 是 |  | FK，关联 class 班级 ID |
| phone | varchar | 20 | 是 |  | 联系电话 |
| status | tinyint | 1 | 否 |  | 账号状态 0 禁用 1 启用 |
| create_time | datetime |  | 是 |  | 创建时间 |
| update_time | datetime |  | 是 |  | 修改时间 |

表 5-4 training（实训项目表）

| 列名 | 数据类型 | 长度 | 可为空 | 主键 | 注释 |
| --- | --- | --- | --- | --- | --- |
| training_id | int | 11 | 否 | √ | 实训主键 ID |
| title | varchar | 100 | 否 |  | 实训项目标题 |
| content | text |  | 是 |  | 实训任务描述 |
| start_time | datetime |  | 是 |  | 实训开始时间 |
| end_time | datetime |  | 是 |  | 实训截止时间 |
| create_time | datetime |  | 是 |  | 创建时间 |
| update_time | datetime |  | 是 |  | 修改时间 |

表 5-5 train_result（实训提交记录表）

| 列名 | 数据类型 | 长度 | 可为空 | 主键 | 注释 |
| --- | --- | --- | --- | --- | --- |
| result_id | int | 11 | 否 | √ | 提交记录主键 |
| training_id | int | 11 | 否 |  | FK，关联实训项目 ID |
| student_id | int | 11 | 否 |  | FK，关联 sys_user 用户 ID |
| submit_content | text |  | 是 |  | 学生提交作业内容 |
| submit_file | varchar | 255 | 是 |  | 提交附件路径 |
| submit_time | datetime |  | 是 |  | 提交时间 |
| create_time | datetime |  | 是 |  | 记录创建时间 |

表 5-6 ai_check（AI 检查记录表）

| 列名 | 数据类型 | 长度 | 可为空 | 主键 | 注释 |
| --- | --- | --- | --- | --- | --- |
| check_id | int | 11 | 否 | √ | AI 检查主键 ID |
| result_id | int | 11 | 否 |  | FK，关联 train_result 提交记录 ID |
| check_content | text |  | 是 |  | AI 检查反馈内容 |
| check_score | decimal | 5,2 | 是 |  | AI 给出分数 |
| check_time | datetime |  | 是 |  | AI 检查时间 |

表 5-7 ai_parse（AI 解析记录表，一对一）

| 列名 | 数据类型 | 长度 | 可为空 | 主键 | 注释 |
| --- | --- | --- | --- | --- | --- |
| parse_id | int | 11 | 否 | √ | 解析主键 ID |
| result_id | int | 11 | 否 |  | FK，关联 train_result 提交记录 ID（唯一约束，一对一） |
| parse_data | text |  | 是 |  | AI 解析结构化数据 JSON |
| parse_time | datetime |  | 是 |  | 解析时间 |

表 5-8 indicator（评价指标表）

| 列名 | 数据类型 | 长度 | 可为空 | 主键 | 注释 |
| --- | --- | --- | --- | --- | --- |
| indicator_id | int | 11 | 否 | √ | 指标主键 ID |
| training_id | int | 11 | 否 |  | FK，关联实训项目 ID |
| indicator_name | varchar | 100 | 否 |  | 评价指标名称 |
| weight | decimal | 5,2 | 否 |  | 指标权重，总和 100 |
| indicator_desc | varchar | 200 | 是 |  | 指标说明 |

表 5-9 evaluation（评价打分记录表）

| 列名 | 数据类型 | 长度 | 可为空 | 主键 | 注释 |
| --- | --- | --- | --- | --- | --- |
| eva_id | int | 11 | 否 | √ | 评价记录主键 |
| training_id | int | 11 | 否 |  | FK，实训项目 ID |
| student_id | int | 11 | 否 |  | FK，被评价学生 user_id |
| indicator_id | int | 11 | 否 |  | FK，评价指标 ID |
| score | decimal | 5,2 | 否 |  | 该项指标得分 |
| eva_comment | varchar | 300 | 是 |  | 评语 |
| eva_time | datetime |  | 是 |  | 评价时间 |

表 5-10 sys_config（系统配置表）

| 列名 | 数据类型 | 长度 | 可为空 | 主键 | 注释 |
| --- | --- | --- | --- | --- | --- |
| config_id | int | 11 | 否 | √ | 配置主键 |
| config_key | varchar | 50 | 否 |  | 配置项 key |
| config_value | varchar | 500 | 是 |  | 配置项值 |
| remark | varchar | 200 | 是 |  | 配置说明 |

表 5-11 sys_log（系统操作日志表）

| 列名 | 数据类型 | 长度 | 可为空 | 主键 | 注释 |
| --- | --- | --- | --- | --- | --- |
| log_id | int | 11 | 否 | √ | 日志主键 |
| operator | varchar | 50 | 是 |  | 操作人账号 |
| operate_type | varchar | 50 | 是 |  | 操作类型 |
| operate_content | text |  | 是 |  | 操作详情 |
| operate_time | datetime |  | 是 |  | 操作时间 |

# 6 脚本文件

/*==============================================================*/

/* DBMS name:      MySQL 5.0                                    */

/* Created on:     2026/10/15 14:31:44                          */

/*==============================================================*/

drop table if exists ai_check;

drop table if exists ai_parse;

drop table if exists class;

drop table if exists evaluation;

drop table if exists indicator;

drop table if exists sys_config;

drop table if exists sys_log;

drop table if exists sys_role;

drop table if exists sys_user;

drop table if exists train_result;

drop table if exists training;

/*==============================================================*/

/* Table: ai_check                                              */

/*==============================================================*/

create table ai_check

(

check_id             BIGINT not null auto_increment,

result_id            BIGINT not null,

require_check        TEXT,

step_check           TEXT,

logic_check          TEXT,

check_summary        TEXT,

create_time          DATETIME default CURRENT_TIMESTAMP,

primary key (check_id)

)

ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/*==============================================================*/

/* Table: ai_parse                                              */

/*==============================================================*/

create table ai_parse

(

parse_id             BIGINT not null auto_increment,

result_id            BIGINT not null,

parse_content        LONGTEXT,

create_time          DATETIME default CURRENT_TIMESTAMP,

primary key (parse_id)

)

ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/*==============================================================*/

/* Table: class                                                 */

/*==============================================================*/

create table class

(

class_id             BIGINT not null auto_increment,

class_name           VARCHAR(64) not null,

create_time          DATETIME default CURRENT_TIMESTAMP,

primary key (class_id)

)

ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/*==============================================================*/

/* Table: evaluation                                            */

/*==============================================================*/

create table evaluation

(

eval_id              BIGINT not null auto_increment,

training_id          BIGINT not null,

user_id              BIGINT,

student_id           BIGINT not null,

score                DECIMAL(5,2) not null,

eval_time            DATETIME,

primary key (eval_id)

)

ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/*==============================================================*/

/* Table: indicator                                             */

/*==============================================================*/

create table indicator

(

indicator_id         BIGINT not null auto_increment,

training_id          BIGINT not null,

eval_id              BIGINT,

indicator_name       VARCHAR(64) not null,

weight               DECIMAL(4,2) not null,

primary key (indicator_id)

)

ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/*==============================================================*/

/* Table: sys_config                                            */

/*==============================================================*/

create table sys_config

(

config_id            BIGINT not null auto_increment,

config_key           VARCHAR(64) not null,

config_value         TEXT,

remark               VARCHAR(256),

primary key (config_id)

)

ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/*==============================================================*/

/* Table: sys_log                                               */

/*==============================================================*/

create table sys_log

(

log_id               BIGINT not null auto_increment,

oper_user_id         BIGINT,

oper_content         VARCHAR(512) not null,

oper_time            DATETIME default CURRENT_TIMESTAMP,

primary key (log_id)

)

ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/*==============================================================*/

/* Table: sys_role                                              */

/*==============================================================*/

create table sys_role

(

role_id              BIGINT not null auto_increment,

role_name            VARCHAR(32) not null,

role_code            VARCHAR(32) not null,

primary key (role_id)

)

ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/*==============================================================*/

/* Table: sys_user                                              */

/*==============================================================*/

create table sys_user

(

user_id              BIGINT not null auto_increment,

role_id              BIGINT,

username             VARCHAR(64) not null,

password             VARCHAR(128) not null,

real_name            VARCHAR(32) not null,

class_id             BIGINT,

primary key (user_id)

)

ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/*==============================================================*/

/* Table: train_result                                          */

/*==============================================================*/

create table train_result

(

result_id            BIGINT not null auto_increment,

training_id          BIGINT not null,

user_id              BIGINT,

student_id           BIGINT not null,

file_name            VARCHAR(256) not null,

file_path            VARCHAR(512) not null,

file_type            VARCHAR(32) not null,

parse_status         TINYINT default 0,

submit_time          DATETIME default CURRENT_TIMESTAMP,

primary key (result_id)

)

ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/*==============================================================*/

/* Table: training                                              */

/*==============================================================*/

create table training

(

training_id          BIGINT not null auto_increment,

training_name        VARCHAR(128) not null,

teacher_id           BIGINT not null,

start_time           DATETIME,

end_time             DATETIME,

description          TEXT,

primary key (training_id)

)

ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

alter table ai_check add constraint FK_Reference_5 foreign key (result_id)

references train_result (result_id) on delete restrict on update restrict;

alter table ai_parse add constraint FK_Reference_6 foreign key (result_id)

references train_result (result_id) on delete restrict on update restrict;

alter table evaluation add constraint FK_Reference_8 foreign key (training_id)

references training (training_id) on delete restrict on update restrict;

alter table evaluation add constraint FK_Reference_9 foreign key (user_id)

references sys_user (user_id) on delete restrict on update restrict;

alter table indicator add constraint FK_Reference_10 foreign key (eval_id)

references evaluation (eval_id) on delete restrict on update restrict;

alter table indicator add constraint FK_Reference_7 foreign key (training_id)

references training (training_id) on delete restrict on update restrict;

alter table sys_user add constraint FK_Reference_1 foreign key (role_id)

references sys_role (role_id) on delete restrict on update restrict;

alter table sys_user add constraint FK_Reference_2 foreign key (class_id)

references class (class_id) on delete restrict on update restrict;

alter table train_result add constraint FK_Reference_3 foreign key (training_id)

references training (training_id) on delete restrict on update restrict;

alter table train_result add constraint FK_Reference_4 foreign key (user_id)

references sys_user (user_id) on delete restrict on update restrict;
