-- 班级增加年级字段，支持“按年级 / 按班级”筛选与分组
ALTER TABLE class
    ADD COLUMN grade VARCHAR(16) NULL COMMENT '年级，如 2024级' AFTER class_name;

-- 回填历史数据：班级名中的 2401 -> 2024级
UPDATE class
SET grade = '2024级'
WHERE class_name LIKE '%2401%';
