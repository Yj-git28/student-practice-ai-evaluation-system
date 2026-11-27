-- ============================================================
-- V11 RBAC 完整性加固 + 权限排查视图
-- 适用库：training_eval（MySQL 8）
-- 说明：可重复执行（幂等）。本脚本只动 RBAC 相关表与视图，
--       不改动任何业务表结构、不改动既有业务数据。
-- 加固点：
--   1. 补 sys_user.role_id → sys_role 的外键与索引（原仅有字段、无约束）
--   2. 补 sys_user_permission 到 sys_user / sys_permission 的外键
--   3. 补 perm_type / data_scope 的字典注释（仅注释，不改列类型与默认值）
--   4. 提供三张只读视图，用于准确排查“角色—权限—用户”的完整授权链路
-- ============================================================

-- ---------- 1. 索引与外键（先查 information_schema，存在则跳过） ----------

DROP PROCEDURE IF EXISTS rbac_ensure;
DELIMITER //
CREATE PROCEDURE rbac_ensure()
BEGIN
    -- sys_user.role_id 普通索引（外键列必须有索引）
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_user'
          AND INDEX_NAME = 'idx_sys_user_role'
    ) THEN
        ALTER TABLE sys_user ADD INDEX idx_sys_user_role (role_id);
    END IF;

    -- sys_user.role_id → sys_role(role_id)：删除角色时拒绝仍被引用（RESTRICT）
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_user'
          AND CONSTRAINT_NAME = 'fk_user_role'
    ) THEN
        ALTER TABLE sys_user
            ADD CONSTRAINT fk_user_role
            FOREIGN KEY (role_id) REFERENCES sys_role (role_id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;
    END IF;

    -- sys_user_permission.user_id → sys_user(user_id)：删用户时级联清理个人权限
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_user_permission'
          AND CONSTRAINT_NAME = 'fk_user_perm_user'
    ) THEN
        ALTER TABLE sys_user_permission
            ADD CONSTRAINT fk_user_perm_user
            FOREIGN KEY (user_id) REFERENCES sys_user (user_id)
            ON DELETE CASCADE ON UPDATE CASCADE;
    END IF;

    -- sys_user_permission.permission_id → sys_permission(permission_id)：删权限时级联清理
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_user_permission'
          AND CONSTRAINT_NAME = 'fk_user_perm_perm'
    ) THEN
        ALTER TABLE sys_user_permission
            ADD CONSTRAINT fk_user_perm_perm
            FOREIGN KEY (permission_id) REFERENCES sys_permission (permission_id)
            ON DELETE CASCADE ON UPDATE CASCADE;
    END IF;
END //
DELIMITER ;

CALL rbac_ensure();
DROP PROCEDURE IF EXISTS rbac_ensure;

-- ---------- 2. 字典注释（MODIFY 仅补 COMMENT，保留原类型 / 可空 / 默认值） ----------

ALTER TABLE sys_permission
    MODIFY COLUMN perm_type TINYINT(1) NOT NULL DEFAULT 1
    COMMENT '权限类型：1=菜单 2=按钮 3=接口';

ALTER TABLE sys_role
    MODIFY COLUMN data_scope TINYINT(4) NOT NULL DEFAULT 3
    COMMENT '数据范围：1=全部 2=本部门 3=本人';

ALTER TABLE sys_user
    MODIFY COLUMN role_id BIGINT(20) NOT NULL
    COMMENT '角色ID，关联 sys_role.role_id（单角色）';

-- ---------- 3. 只读排查视图 ----------

-- 3.1 角色 → 权限：每个角色被授予的全部权限项
DROP VIEW IF EXISTS v_role_permission;
CREATE VIEW v_role_permission AS
SELECT
    r.role_id,
    r.role_code,
    r.role_name,
    p.permission_id,
    p.perm_code,
    p.perm_name,
    p.perm_type,
    p.perm_path,
    p.parent_id,
    p.sort
FROM sys_role r
JOIN sys_role_permission rp ON rp.role_id = r.role_id
JOIN sys_permission p       ON p.permission_id = rp.permission_id;

-- 3.2 用户 → 全部权限：角色授权 ∪ 个人附加权限，source 区分来源
--     与登录加载口径一致：status=1 启用、deleted=0 未删除；视图仅做排查，状态列一并透出。
DROP VIEW IF EXISTS v_user_permission;
CREATE VIEW v_user_permission AS
SELECT
    u.user_id,
    u.username,
    u.real_name,
    u.role_id,
    r.role_code,
    p.permission_id,
    p.perm_code,
    p.perm_name,
    p.perm_type,
    p.perm_path,
    'ROLE' AS source
FROM sys_user u
JOIN sys_role r             ON r.role_id = u.role_id
JOIN sys_role_permission rp ON rp.role_id = r.role_id
JOIN sys_permission p       ON p.permission_id = rp.permission_id
WHERE u.status = 1 AND u.deleted = 0

UNION ALL

SELECT
    u.user_id,
    u.username,
    u.real_name,
    u.role_id,
    r.role_code,
    p.permission_id,
    p.perm_code,
    p.perm_name,
    p.perm_type,
    p.perm_path,
    'USER' AS source
FROM sys_user u
JOIN sys_role r             ON r.role_id = u.role_id
JOIN sys_user_permission up ON up.user_id = u.user_id
JOIN sys_permission p       ON p.permission_id = up.permission_id
WHERE u.status = 1 AND u.deleted = 0;

-- 3.3 权限引用计数：每个权限被多少角色、多少个人引用，便于删除前判断是否被占用
DROP VIEW IF EXISTS v_permission_ref;
CREATE VIEW v_permission_ref AS
SELECT
    p.permission_id,
    p.perm_code,
    p.perm_name,
    p.perm_type,
    (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.permission_id = p.permission_id) AS role_ref_count,
    (SELECT COUNT(*) FROM sys_user_permission up WHERE up.permission_id = p.permission_id)   AS user_ref_count
FROM sys_permission p;

-- ============================================================
-- 自检（只读，不会修改数据）：
-- 1. 外键是否就位
--    SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME
--      FROM information_schema.KEY_COLUMN_USAGE
--      WHERE TABLE_SCHEMA='training_eval' AND REFERENCED_TABLE_NAME IS NOT NULL;
-- 2. 某角色的全部权限
--    SELECT role_code, perm_code, perm_type FROM v_role_permission
--      WHERE role_code='ADMIN' ORDER BY perm_type, sort;
-- 3. 某用户的全部权限（角色 + 个人）
--    SELECT username, role_code, perm_code, perm_type, source FROM v_user_permission
--      WHERE username='admin' ORDER BY source, perm_code;
-- 4. 按钮权限清单（perm_type=2）
--    SELECT perm_code, perm_name, parent_id FROM sys_permission WHERE perm_type=2 ORDER BY sort;
-- ============================================================
