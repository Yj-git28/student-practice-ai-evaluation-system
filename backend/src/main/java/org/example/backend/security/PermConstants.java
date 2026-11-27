package org.example.backend.security;

/**
 * RBAC 权限码常量。与 sys_permission.perm_code 一一对应，
 * 后端 @PreAuthorize 统一引用这里的常量，避免散落在字符串里拼错、漏配。
 *
 * 命名约定：模块:动作。菜单码以 :list 结尾，按钮码为具体动作。
 */
public final class PermConstants {

    private PermConstants() {}

    // ===== 用户管理 =====
    public static final String USER_LIST   = "user:list";
    public static final String USER_ADD    = "user:add";
    public static final String USER_EDIT   = "user:edit";
    public static final String USER_REMOVE = "user:remove";
    public static final String USER_RESETPWD = "user:resetpwd";
    public static final String USER_IMPORT = "user:import";

    // ===== 班级 =====
    public static final String CLASS_ADD    = "class:add";
    public static final String CLASS_EDIT   = "class:edit";
    public static final String CLASS_REMOVE = "class:remove";

    // ===== 实训 =====
    public static final String TRAINING_LIST   = "training:list";
    public static final String TRAINING_MY_LIST = "training:my-list";
    public static final String TRAINING_ADD    = "training:add";
    public static final String TRAINING_EDIT   = "training:edit";
    public static final String TRAINING_REMOVE = "training:remove";
    public static final String TRAINING_PUBLISH = "training:publish";
    public static final String TRAINING_SUBMIT = "training:submit";

    // ===== AI 评测 / 复核 =====
    public static final String AI_LIST          = "ai:list";
    public static final String EVALUATION_LIST   = "evaluation:list";
    public static final String EVALUATION_REVIEW = "evaluation:review";

    // ===== 系统配置 =====
    public static final String CONFIG_LIST  = "config:list";
    public static final String CONFIG_UPDATE = "config:update";

    // ===== 权限管理 =====
    public static final String PERM_MANAGE = "permission:manage";
    public static final String PERM_CREATE  = "permission:create";
    public static final String PERM_EDIT     = "permission:edit";
    public static final String PERM_REMOVE  = "permission:remove";
    public static final String PERM_GRANT   = "permission:grant";

    // ===== 群聊 =====
    public static final String CHAT_LIST = "chat:list";
}
