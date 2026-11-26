-- ============================================================
-- V4 新增登录方式：手机号账号登录 + 短信验证码登录
-- 适用库：training_eval（MySQL 8）
-- 执行前提：sys_user 已存在
-- 说明：可重复执行；对列/唯一键做了存在性判断，重复跑不会报错。
-- ============================================================

-- ---------- A-1：sys_user 增加 phone 列 + 唯一索引 ----------
DROP PROCEDURE IF EXISTS sp_add_phone_col;
DELIMITER $$
CREATE PROCEDURE sp_add_phone_col()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_user'
          AND COLUMN_NAME = 'phone'
    ) THEN
        ALTER TABLE sys_user ADD COLUMN phone VARCHAR(20) NULL COMMENT '手机号，可用于登录';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_user'
          AND INDEX_NAME = 'uk_phone'
    ) THEN
        ALTER TABLE sys_user ADD UNIQUE KEY uk_phone (phone);
    END IF;
END$$
DELIMITER ;
CALL sp_add_phone_col();
DROP PROCEDURE sp_add_phone_col;

-- 给现有演示账号补手机号（按实际账号改）
-- UNIQUE 允许多行 NULL，未绑手机号的账号不会冲突。
UPDATE sys_user SET phone = '13800000001' WHERE username = 'admin';
UPDATE sys_user SET phone = '13800000002' WHERE username = 'teacher01';
UPDATE sys_user SET phone = '13800000003' WHERE username = 'stu01';

-- ---------- B-1：短信验证码表 ----------
CREATE TABLE IF NOT EXISTS sys_login_code (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    target      VARCHAR(64)  NOT NULL COMMENT '手机号',
    code        VARCHAR(8)   NOT NULL,
    expire_at   DATETIME     NOT NULL,
    used        TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_target_time (target, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信登录验证码';

-- ---------- 只读自检 ----------
-- DESC sys_user;            -- 确认 phone 列存在
-- SHOW INDEX FROM sys_user; -- 确认 uk_phone 存在
-- SELECT username, phone FROM sys_user;
-- SHOW TABLES LIKE 'sys_login_code';
