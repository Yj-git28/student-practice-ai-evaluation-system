<template>
  <el-container style="height:100vh">
    <el-aside width="220px" style="background:#304156">
      <div style="height:60px;line-height:60px;color:#fff;text-align:center;font-size:16px">AI实训评价系统</div>
      <el-menu
        :default-active="$route.path"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        router
      >
        <!--学生菜单-->
        <template v-if="userStore.role === 'student'">
          <el-menu-item index="/student/task">📋 实训任务列表</el-menu-item>
          <el-menu-item index="/student/submit">📤 提交实训成果</el-menu-item>
        </template>
        <!--教师菜单-->
        <template v-if="userStore.role === 'teacher'">
          <el-menu-item index="/teacher/taskManage">✏️ 实训任务管理</el-menu-item>
          <el-menu-item index="/teacher/review">🔍 AI成果复核</el-menu-item>
          <el-menu-item index="/teacher/stat">📊 数据统计报表</el-menu-item>
        </template>
        <!--管理员菜单-->
        <template v-if="userStore.role === 'admin'">
          <el-menu-item index="/admin/user">👥 账号班级管理</el-menu-item>
          <el-menu-item index="/admin/config">⚙️系统参数配置</el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background:#fff;border-bottom:1px solid #eee;display:flex;justify-content:flex-end;align-items:center;padding-right:20px">
        <span>欢迎：{{userStore.username}} 【{{roleText}}】</span>
        <el-button type="text" @click="handleLogout">退出登录</el-button>
      </el-header>
      <el-main style="background:#f2f3f5;overflow:auto">
        <router-view></router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useUserStore } from '@/stores/useUserStore'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
const userStore = useUserStore()
const router = useRouter()

const roleText = {
  student:'学生',
  teacher:'教师',
  admin:'系统管理员'
}[userStore.role]

const handleLogout = ()=>{
  userStore.logout()
  ElMessage.success('已退出')
  router.push('/login')
}
</script>
