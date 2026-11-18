-- ============================================================
-- V3 后端前置变更
-- 适用库：training_eval（MySQL 8）
-- 执行前提：sys_user、sys_permission 已存在
-- 说明：可重复执行；本脚本只补后端运行必需的个人权限表，
--       不重复添加已执行过的 training.status / sys_user.status 等列。
-- ============================================================

-- 个人附加权限：与角色权限取并集，只影响权限码，不额外产生菜单。
CREATE TABLE IF NOT EXISTS sys_user_permission (
    user_id       BIGINT NOT NULL COMMENT '用户ID，关联 sys_user.user_id',
    permission_id BIGINT NOT NULL COMMENT '权限ID，关联 sys_permission.permission_id',
    PRIMARY KEY (user_id, permission_id),
    KEY idx_user_permission_permission (permission_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '用户个人附加权限';

-- 执行后自检（只读）：
-- SHOW TABLES LIKE 'sys_user_permission';
-- DESC sys_user_permission;
-- 若实库 sys_user.user_id / sys_permission.permission_id 不是 BIGINT，
-- 请按实际类型调整上面两列，避免隐式转换影响索引命中。

-- ============================================================
-- 联调前只读检查（不会修改数据）
-- 确认后端已依赖的列都存在；缺失时再单独补，不要重复执行整组 ALTER。
-- ============================================================
-- SHOW COLUMNS FROM sys_user LIKE 'status';       -- 期望：0 禁用 / 1 启用
-- SHOW COLUMNS FROM sys_user LIKE 'deleted';      -- 期望：0 正常 / 1 删除
-- SHOW COLUMNS FROM sys_role LIKE 'data_scope';   -- 期望：1 全部 / 2 本部门 / 3 本人
-- SHOW COLUMNS FROM training LIKE 'status';       -- 期望：0 未发布 / 1 已发布
-- SELECT user_id, username, status, deleted FROM sys_user;  -- 三个演示账号应为 1 / 0

