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
        <span style="margin-right:14px">欢迎：{{ userStore.realName }} 【{{ userStore.roleName }}】（{{ userStore.email || '未绑定邮箱' }}）</span>
        <el-tooltip content="设置" placement="bottom">
          <el-icon class="setting-icon" :size="20" @click="settingsVisible = true"><Setting/></el-icon>
        </el-tooltip>
      </el-header>
      <el-main style="background:#f2f3f5;overflow:auto">
        <router-view></router-view>
      </el-main>
    </el-container>
    <SettingsDrawer v-model="settingsVisible" @logout="handleLogout"/>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import SettingsDrawer from '@/components/SettingsDrawer.vue'
import { useUserStore } from '@/stores/useUserStore'
import { usePermissionStore } from '@/stores/usePermissionStore'
import { useRouter } from 'vue-router'
import { resetDynamicRoutes } from '@/router'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const permStore = usePermissionStore()
const router = useRouter()
const settingsVisible = ref(false)

const handleLogout = async () => {
  // 先吊销服务端 token，再清本地状态和动态路由
  const revoked = await userStore.logoutAndRevoke()
  permStore.reset()
  resetDynamicRoutes()
  if (revoked) {
    ElMessage.success('已退出')
  } else {
    ElMessage.warning('服务端注销失败，已在本地退出')
  }
  router.replace('/login')
}
</script>

<style scoped>
.setting-icon {
  cursor: pointer;
  color: #606266;
  transition: color .2s;
}

.setting-icon:hover {
  color: #409eff;
  transform: rotate(30deg);
}
</style>
