-- ============================================================
-- V10 实训群聊 + 权限管理后台
-- 适用库：training_eval（MySQL 8）
-- 说明：可重复执行；新增群聊消息表、权限管理菜单、群聊菜单及角色授权。
-- ============================================================

-- 实训任务群聊消息：每个 training_id 一个群，消息持久化到本表
CREATE TABLE IF NOT EXISTS chat_message (
    message_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息主键',
    training_id  BIGINT       NOT NULL COMMENT '实训任务ID，关联 training.training_id',
    sender_id    BIGINT       NOT NULL COMMENT '发送人ID，关联 sys_user.user_id',
    sender_name  VARCHAR(32)  NOT NULL COMMENT '发送人姓名快照',
    content      VARCHAR(2000) NOT NULL COMMENT '消息文本',
    message_type TINYINT      NOT NULL DEFAULT 1 COMMENT '消息类型：1=文本',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (message_id),
    KEY idx_chat_training (training_id, message_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '实训群聊消息';

-- ---------- 菜单与权限项 ----------

-- 权限管理菜单（仅管理员可见）
INSERT IGNORE INTO sys_permission(parent_id, perm_name, perm_type, perm_code, perm_path, icon, sort)
VALUES (0, '权限管理', 1, 'permission:manage', '/system/permission', 'Setting', 7);

-- 实训群聊菜单（教师 / 学生 / 管理员均可进入）
INSERT IGNORE INTO sys_permission(parent_id, perm_name, perm_type, perm_code, perm_path, icon, sort)
VALUES (0, '实训群聊', 1, 'chat:list', '/chat', 'ChatDotRound', 8);

-- 权限管理菜单授权给管理员（role_code=ADMIN）
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r CROSS JOIN sys_permission p
WHERE p.perm_code = 'permission:manage' AND r.role_code = 'ADMIN';

-- 群聊菜单授权给教师 / 学生 / 管理员
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r CROSS JOIN sys_permission p
WHERE p.perm_code = 'chat:list' AND r.role_code IN ('TEACHER', 'STUDENT', 'ADMIN');

-- 自检（只读）：
-- SELECT perm_code, perm_name, perm_path FROM sys_permission WHERE perm_code IN ('permission:manage','chat:list');
-- SELECT r.role_code, p.perm_code FROM sys_role_permission rp
--   JOIN sys_role r ON r.role_id = rp.role_id
--   JOIN sys_permission p ON p.permission_id = rp.permission_id
--   WHERE p.perm_code IN ('permission:manage','chat:list') ORDER BY p.perm_code, r.role_code;
