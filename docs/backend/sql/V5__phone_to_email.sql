-- ============================================================
-- V5 登录方式从手机号切换为邮箱
-- 适用库：training_eval（MySQL 8）
-- 执行前提：V4 已执行（sys_user.phone 存在）
-- 说明：把 sys_user.phone 改名为 email，重建唯一索引，写入演示邮箱。
-- ============================================================

-- 1. phone → email（邮箱最长 254，这里用 VARCHAR(100) 足够）
-- 用 CHANGE 重命名并改类型；同时把 NULL 默认保留（未绑邮箱的账号不冲突）
ALTER TABLE sys_user CHANGE COLUMN phone email VARCHAR(100) NULL COMMENT '邮箱，可用于登录';

-- 2. 重建唯一索引：先删旧的 uk_phone，再加 uk_email
ALTER TABLE sys_user DROP INDEX uk_phone;
ALTER TABLE sys_user ADD UNIQUE KEY uk_email (email);

-- 3. 演示账号绑定邮箱（按实际改）
UPDATE sys_user SET email = 'admin@example.com'    WHERE username = 'admin';
UPDATE sys_user SET email = 'teacher01@example.com' WHERE username = 'teacher01';
UPDATE sys_user SET email = 'stu01@example.com'     WHERE username = 'stu01';
UPDATE sys_user SET email = 'stu02@example.com'     WHERE username = 'stu02';

-- 4. 验证码表 target 列注释更新（列本身 VARCHAR(64) 不变，邮箱够用）
ALTER TABLE sys_login_code MODIFY COLUMN target VARCHAR(64) NOT NULL COMMENT '手机号或邮箱';

-- ---------- 只读自检 ----------
-- DESC sys_user;            -- 确认 email 列存在、phone 已消失
-- SHOW INDEX FROM sys_user; -- 确认 uk_email 存在、uk_phone 已删
-- SELECT username, email FROM sys_user;
