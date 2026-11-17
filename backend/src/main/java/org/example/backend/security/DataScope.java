package org.example.backend.security;

/** 数据范围统一取值：与 sys_role.data_scope / 前端 dataScope 字段一一对应 */
public final class DataScope {

    /** 1 全部数据 */
    public static final int ALL = 1;
    /** 2 本部门（现有表只有 class_id，未接班级维度前降级为本人） */
    public static final int DEPT = 2;
    /** 3 本人 */
    public static final int SELF = 3;

    private DataScope() {}

    /** 兜底按“本人”处理，避免字段为空时越权拿到全量数据 */
    public static int normalize(Integer value) {
        if (value == null) return SELF;
        return (value >= ALL && value <= SELF) ? value : SELF;
    }
}
