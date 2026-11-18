-- 单设备登录：记录账号当前有效会话的 jti
-- 登录时写入新 jti，旧设备 token 的 jti 与之不匹配即失效（被新登录踢下线）
-- 退出登录时置空
ALTER TABLE sys_user
    ADD COLUMN current_jti VARCHAR(64) NULL COMMENT '当前有效会话 jti，用于单设备登录';
