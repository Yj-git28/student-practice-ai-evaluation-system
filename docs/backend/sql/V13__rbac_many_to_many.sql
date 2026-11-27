-- ============================================================
-- V13 RBAC 多对多角色 + 角色可维护 + 授权审计字段
-- 适用库：training_eval（MySQL 8）
-- 说明：可重复执行（幂等）。本脚本只动 RBAC 相关表与视图，
--       不改动业务表结构、不改动既有业务数据。
-- 改造点：
--   1. 新增 sys_user_role：用户-角色多对多（一个用户可绑多个角色）
--   2. sys_user.role_id 保留为主角色冗余列（兼容旧查询/JWT），
--      权威角色关系以 sys_user_role 为准；存量数据迁入关联表
--   3. sys_role 补 status / builtin / description：角色可启停、
--      内置角色不可删除、不可改角色码
--   4. sys_role_permission / sys_user_permission 补审计字段
--      （create_time / create_by）
--   5. 重建 v_user_permission 视图，按 sys_user_role 多对多口径取权限
-- ============================================================

-- ---------- 1. 用户-角色多对多表 ----------

DROP PROCEDURE IF EXISTS rbac_v13_ensure;
DELIMITER //
CREATE PROCEDURE rbac_v13_ensure()
BEGIN
    -- 1.1 关联表（不存在才建）
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role'
    ) THEN
        CREATE TABLE sys_user_role (
            user_id    BIGINT(20)   NOT NULL COMMENT '用户ID，关联 sys_user.user_id',
            role_id    BIGINT(20)   NOT NULL COMMENT '角色ID，关联 sys_role.role_id',
            create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
            PRIMARY KEY (user_id, role_id),
            KEY idx_ur_role (role_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表（多对多）';
    END IF;

    -- 1.2 外键：用户删除时级联清理关联；角色删除时拒绝仍被引用
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role'
          AND CONSTRAINT_NAME = 'fk_ur_user'
    ) THEN
        ALTER TABLE sys_user_role
            ADD CONSTRAINT fk_ur_user
            FOREIGN KEY (user_id) REFERENCES sys_user (user_id)
            ON DELETE CASCADE ON UPDATE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role'
          AND CONSTRAINT_NAME = 'fk_ur_role'
    ) THEN
        ALTER TABLE sys_user_role
            ADD CONSTRAINT fk_ur_role
            FOREIGN KEY (role_id) REFERENCES sys_role (role_id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;
    END IF;

    -- 1.3 sys_role 扩列：状态 / 内置标记 / 描述
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'status'
    ) THEN
        ALTER TABLE sys_role ADD COLUMN status TINYINT(4) NOT NULL DEFAULT 1
            COMMENT '状态：1 启用 0 停用';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'builtin'
    ) THEN
        ALTER TABLE sys_role ADD COLUMN builtin TINYINT(1) NOT NULL DEFAULT 0
            COMMENT '是否内置角色：1 内置（不可删除、不可改角色码） 0 自定义';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'description'
    ) THEN
        ALTER TABLE sys_role ADD COLUMN description VARCHAR(255) NULL COMMENT '角色描述';
    END IF;

    -- 1.4 授权关联表审计字段
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_permission' AND COLUMN_NAME = 'create_time'
    ) THEN
        ALTER TABLE sys_role_permission ADD COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_permission' AND COLUMN_NAME = 'create_by'
    ) THEN
        ALTER TABLE sys_role_permission ADD COLUMN create_by BIGINT(20) NULL COMMENT '授权操作人 user_id';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_permission' AND COLUMN_NAME = 'create_time'
    ) THEN
        ALTER TABLE sys_user_permission ADD COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_permission' AND COLUMN_NAME = 'create_by'
    ) THEN
        ALTER TABLE sys_user_permission ADD COLUMN create_by BIGINT(20) NULL COMMENT '授权操作人 user_id';
    END IF;
END //
DELIMITER ;

CALL rbac_v13_ensure();
DROP PROCEDURE IF EXISTS rbac_v13_ensure;

-- ---------- 2. 存量数据迁移：sys_user.role_id → sys_user_role ----------
-- INSERT IGNORE 保证幂等；已在关联表里的不重复插入
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, u.role_id
FROM sys_user u
WHERE u.role_id IS NOT NULL AND u.deleted = 0;

-- ---------- 3. 内置角色标记（种子三角色均为内置，不可删除） ----------
UPDATE sys_role SET builtin = 1, status = 1
WHERE role_code IN ('ADMIN', 'TEACHER', 'STUDENT');

-- ---------- 4. 列注释更新 ----------
ALTER TABLE sys_user
    MODIFY COLUMN role_id BIGINT(20) NOT NULL
    COMMENT '主角色ID（冗余列，便于旧查询与JWT）；权威多角色关系见 sys_user_role';

ALTER TABLE sys_role
    MODIFY COLUMN role_code VARCHAR(32) NOT NULL COMMENT '角色标识，内置角色不可修改';

-- ---------- 5. 重建用户权限视图（多对多口径） ----------
DROP VIEW IF EXISTS v_user_permission;
CREATE VIEW v_user_permission AS
SELECT
    u.user_id,
    u.username,
    u.real_name,
    ur.role_id,
    r.role_code,
    p.permission_id,
    p.perm_code,
    p.perm_name,
    p.perm_type,
    p.perm_path,
    'ROLE' AS source
FROM sys_user u
JOIN sys_user_role ur       ON ur.user_id = u.user_id
JOIN sys_role r             ON r.role_id = ur.role_id
JOIN sys_role_permission rp ON rp.role_id = r.role_id
JOIN sys_permission p       ON p.permission_id = rp.permission_id
WHERE u.status = 1 AND u.deleted = 0

UNION ALL

SELECT
    u.user_id,
    u.username,
    u.real_name,
    u.role_id,
    NULL AS role_code,
    p.permission_id,
    p.perm_code,
    p.perm_name,
    p.perm_type,
    p.perm_path,
    'USER' AS source
FROM sys_user u
JOIN sys_user_permission up ON up.user_id = u.user_id
JOIN sys_permission p       ON p.permission_id = up.permission_id
WHERE u.status = 1 AND u.deleted = 0;

-- ============================================================
-- 自检（只读）：
-- 1. 关联表是否迁入全部有效用户
--    SELECT COUNT(*) AS users, COUNT(DISTINCT user_id) AS users_distinct
--      FROM sys_user_role;
-- 2. 某用户绑定的角色
--    SELECT u.username, GROUP_CONCAT(r.role_code) AS roles
--      FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.user_id
--      JOIN sys_role r ON r.role_id=ur.role_id
--      GROUP BY u.user_id, u.username;
-- 3. 角色状态与内置标记
--    SELECT role_id, role_code, status, builtin, description FROM sys_role;
-- ============================================================
