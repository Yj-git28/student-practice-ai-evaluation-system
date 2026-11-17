<template>
  <div class="forbidden">
    <h2>403 没有访问权限</h2>
    <p class="tip">当前账号无权访问该页面，或该页面不存在。</p>
    <div class="actions">
      <el-button type="primary" @click="goHome">返回首页</el-button>
      <el-button @click="goLogin">重新登录</el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/useUserStore'
import { usePermissionStore } from '@/stores/usePermissionStore'

const router = useRouter()
const userStore = useUserStore()
const permStore = usePermissionStore()

// 回首页：守卫会按权限树把 / 解析到第一个可访问菜单
function goHome() {
  router.replace('/')
}

// 兜底：权限数据错乱时，清掉登录态重新来过
function goLogin() {
  userStore.logout()
  permStore.reset()
  router.replace('/login')
}
</script>

<style scoped>
.forbidden {
  text-align: center;
  margin-top: 100px;
}
.tip {
  color: #909399;
  margin: 12px 0 24px;
}
.actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>