-- sys_config 已存在（主键 config_id，config_key 无唯一约束）：
-- 邀请码按 key 覆盖写入时会产生多行，导致查询报 "expected 1, actual N"。
-- 1) 先按 key 去重，只保留最新一行；2) 给 config_key 加唯一约束，使 upsert 生效。
DELETE c1
FROM sys_config c1
         JOIN sys_config c2 ON c1.config_key = c2.config_key AND c1.config_id < c2.config_id;

ALTER TABLE sys_config
    ADD UNIQUE KEY uk_config_key (config_key);
