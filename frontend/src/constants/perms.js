/**
 * RBAC 权限码常量（与后端 PermConstants、sys_permission.perm_code 一一对应）。
 * 模板里的 v-has-perm 优先引用这里的常量，避免散落字符串拼错。
 */
export const PERM = {
  // 用户管理
  USER_LIST: 'user:list',
  USER_ADD: 'user:add',
  USER_EDIT: 'user:edit',
  USER_REMOVE: 'user:remove',
  USER_RESETPWD: 'user:resetpwd',
  USER_IMPORT: 'user:import',

  // 班级
  CLASS_ADD: 'class:add',
  CLASS_EDIT: 'class:edit',
  CLASS_REMOVE: 'class:remove',

  // 实训
  TRAINING_LIST: 'training:list',
  TRAINING_MY_LIST: 'training:my-list',
  TRAINING_ADD: 'training:add',
  TRAINING_EDIT: 'training:edit',
  TRAINING_REMOVE: 'training:remove',
  TRAINING_PUBLISH: 'training:publish',
  TRAINING_SUBMIT: 'training:submit',

  // AI 评测 / 复核
  AI_LIST: 'ai:list',
  EVALUATION_LIST: 'evaluation:list',
  EVALUATION_REVIEW: 'evaluation:review',

  // 系统配置
  CONFIG_LIST: 'config:list',
  CONFIG_UPDATE: 'config:update',

  // 权限管理
  PERM_MANAGE: 'permission:manage',
  PERM_CREATE: 'permission:create',
  PERM_EDIT: 'permission:edit',
  PERM_REMOVE: 'permission:remove',
  PERM_GRANT: 'permission:grant',

  // 群聊
  CHAT_LIST: 'chat:list'
}
