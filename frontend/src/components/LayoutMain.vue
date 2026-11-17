<template>
  <el-container style="height:100vh">
    <el-aside width="220px" style="background:#304156">
      <div style="height:60px;line-height:60px;color:#fff;text-align:center;font-size:16px">
        AI实训评价系统
      </div>
      <el-menu
        :default-active="$route.path"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        router
      >
        <template v-for="m in permStore.menus" :key="m.permissionId">
          <el-sub-menu v-if="m.children && m.children.length" :index="m.permPath || ('sub-' + m.permissionId)">
            <template #title>{{ m.permName }}</template>
            <template v-for="c in m.children" :key="c.permissionId">
              <el-menu-item v-if="c.permPath" :index="c.permPath">{{ c.permName }}</el-menu-item>
            </template>
          </el-sub-menu>
          <el-menu-item v-else :index="m.permPath">
            <span>{{ m.permName }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background:#fff;border-bottom:1px solid #eee;display:flex;justify-content:flex-end;align-items:center;padding-right:20px">
        <span>欢迎：{{ userStore.realName }} 【{{ userStore.roleName }}】</span>
        <el-button link @click="handleLogout">退出登录</el-button>
      </el-header>
      <el-main style="background:#f2f3f5;overflow:auto">
        <router-view></router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useUserStore } from '@/stores/useUserStore'
import { usePermissionStore } from '@/stores/usePermissionStore'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const permStore = usePermissionStore()
const router = useRouter()

const handleLogout = () => {
  userStore.logout()
  permStore.reset()
  // 删除动态添加的路由
  router.getRoutes().forEach(r => {
    if (r.meta?.dynamic && r.name) router.removeRoute(r.name)
  })
  ElMessage.success('已退出')
  router.push('/login')
}
</script>
