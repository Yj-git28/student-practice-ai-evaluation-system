-- ============================================================
-- V12 菜单内部按钮权限补齐（RBAC 细粒度按钮码）
-- 适用库：training_eval（MySQL 8）
-- 说明：幂等可重复执行。仅向 sys_permission 写入按钮权限（perm_type=2），
--       并向 sys_role_permission 授权；不改业务表、不改业务数据。
-- 父菜单：1=实训管理 training:list；4=AI评测 ai:list；6=用户管理 user:list；
--         7=系统配置 config:list；10=权限管理 permission:manage
-- ============================================================

-- ---------- 1. 按钮权限元数据（perm_code 唯一，INSERT IGNORE 幂等） ----------

-- 用户管理（父菜单 id=6）
INSERT IGNORE INTO sys_permission(parent_id, perm_name, perm_type, perm_code, perm_path, icon, sort) VALUES
(6, '新增账号',   2, 'user:add',      NULL, NULL, 1),
(6, '编辑账号',   2, 'user:edit',     NULL, NULL, 2),
(6, '删除账号',   2, 'user:remove',    NULL, NULL, 3),
(6, '重置密码',   2, 'user:resetpwd',  NULL, NULL, 4),
(6, '批量导入',   2, 'user:import',    NULL, NULL, 5),
(6, '新增班级',   2, 'class:add',     NULL, NULL, 6),
(6, '编辑班级',   2, 'class:edit',    NULL, NULL, 7),
(6, '删除班级',   2, 'class:remove',  NULL, NULL, 8);

-- 实训管理（父菜单 id=1；training:publish 已存在）
INSERT IGNORE INTO sys_permission(parent_id, perm_name, perm_type, perm_code, perm_path, icon, sort) VALUES
(1, '新增实训', 2, 'training:add',    NULL, NULL, 2),
(1, '编辑实训', 2, 'training:edit',  NULL, NULL, 3),
(1, '删除实训', 2, 'training:remove',NULL, NULL, 4);

-- AI 评测 / 人工复核（父菜单 id=4）
INSERT IGNORE INTO sys_permission(parent_id, perm_name, perm_type, perm_code, perm_path, icon, sort) VALUES
(4, '人工复核', 2, 'evaluation:review', NULL, NULL, 1);

-- 系统配置（父菜单 id=7）
INSERT IGNORE INTO sys_permission(parent_id, perm_name, perm_type, perm_code, perm_path, icon, sort) VALUES
(7, '保存配置', 2, 'config:update', NULL, NULL, 1);

-- 权限管理（父菜单 id=10）
INSERT IGNORE INTO sys_permission(parent_id, perm_name, perm_type, perm_code, perm_path, icon, sort) VALUES
(10, '新增权限', 2, 'permission:create',  NULL, NULL, 1),
(10, '编辑权限', 2, 'permission:edit',    NULL, NULL, 2),
(10, '删除权限', 2, 'permission:remove',  NULL, NULL, 3),
(10, '分配权限', 2, 'permission:grant',   NULL, NULL, 4);

-- ---------- 2. 角色授权（按 role_code / perm_code 关联，INSERT IGNORE 幂等） ----------

-- ADMIN：拥有全部按钮权限
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN'
  AND p.perm_code IN (
    'user:add','user:edit','user:remove','user:resetpwd','user:import',
    'class:add','class:edit','class:remove',
    'training:add','training:edit','training:remove','training:publish',
    'evaluation:review',
    'config:update',
    'permission:create','permission:edit','permission:remove','permission:grant'
  );

-- TEACHER：教学相关按钮（建/改/删实训、发布、人工复核）
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'TEACHER'
  AND p.perm_code IN (
    'training:add','training:edit','training:remove','training:publish','evaluation:review'
  );

-- STUDENT：补齐提交作业按钮码（菜单 training:submit-page 已有，按钮码 training:submit 此前未授权）
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'STUDENT'
  AND p.perm_code = 'training:submit';

-- ---------- 3. 自检（只读） ----------
-- SELECT perm_code, perm_name FROM sys_permission WHERE perm_type = 2 ORDER BY parent_id, sort;
-- SELECT r.role_code, p.perm_code FROM sys_role_permission rp
--   JOIN sys_role r ON r.role_id = rp.role_id
--   JOIN sys_permission p ON p.permission_id = rp.permission_id
--   WHERE p.perm_type = 2 ORDER BY r.role_code, p.perm_code;
