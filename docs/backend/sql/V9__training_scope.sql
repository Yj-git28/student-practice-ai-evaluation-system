-- ============================================================
-- V9 实训发布范围升级
-- 适用库：training_eval（MySQL 8）
-- 说明：可重复执行
-- ============================================================

-- 1. teacher_id 改为可空（管理员发布公共实训时无所属教师）
ALTER TABLE training MODIFY teacher_id BIGINT NULL COMMENT '所属教师ID（公共实训为 NULL）';

-- 2. 新增发布范围字段
ALTER TABLE training ADD COLUMN scope_type TINYINT NOT NULL DEFAULT 1
    COMMENT '发布范围：1=仅所属教师学生可见, 2=年级公共, 3=全校公共';
ALTER TABLE training ADD COLUMN scope_value VARCHAR(32) NULL
    COMMENT '范围值：scope_type=2 时填年级（如 2024级），其他情况为 NULL';

-- 3. 历史数据回填（全部视为教师专属）
UPDATE training SET scope_type = 1, scope_value = NULL WHERE scope_type IS NULL;
