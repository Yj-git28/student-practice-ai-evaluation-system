-- ============================================================
-- V8 实训增强：成果 AI 初评 / 教师复核字段
-- 适用库：training_eval（MySQL 8）
-- 说明：按 information_schema 逐列判断，脚本可重复执行。
-- ============================================================

SET @db_name = DATABASE();

-- ai_score
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE train_result ADD COLUMN ai_score INT NULL COMMENT ''AI初评分数''',
    'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'train_result' AND COLUMN_NAME = 'ai_score');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ai_comment
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE train_result ADD COLUMN ai_comment VARCHAR(500) NULL COMMENT ''AI初评评语''',
    'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'train_result' AND COLUMN_NAME = 'ai_comment');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- final_score
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE train_result ADD COLUMN final_score INT NULL COMMENT ''教师复核最终分数''',
    'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'train_result' AND COLUMN_NAME = 'final_score');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- teacher_comment
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE train_result ADD COLUMN teacher_comment VARCHAR(500) NULL COMMENT ''教师复核评语''',
    'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'train_result' AND COLUMN_NAME = 'teacher_comment');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- review_by
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE train_result ADD COLUMN review_by BIGINT NULL COMMENT ''复核教师ID''',
    'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'train_result' AND COLUMN_NAME = 'review_by');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- review_time
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE train_result ADD COLUMN review_time DATETIME NULL COMMENT ''复核时间''',
    'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'train_result' AND COLUMN_NAME = 'review_time');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 执行后自检（只读）：
-- SHOW COLUMNS FROM train_result;
