<template>
  <div style="display:flex;justify-content:center;align-items:center;height:100vh;background:#f5f5f5;">
    <el-card style="width:420px;padding:20px;">
      <h2 style="text-align:center;margin-bottom:24px;">软件实训AI检查评价系统</h2>
      <el-form :model="loginForm">
        <el-form-item label="账号">
          <el-input v-model="loginForm.username"></el-input>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="loginForm.password" type="password"></el-input>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="loginForm.role">
            <el-option label="学生" value="student"></el-option>
            <el-option label="教师" value="teacher"></el-option>
            <el-option label="管理员" value="admin"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width:100%" @click="handleLogin">登录系统</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/useUserStore'

const router = useRouter()
const userStore = useUserStore()

const loginForm = ref({
  username: '',
  password: '',
  role: 'student'
})

const handleLogin = () => {
  const userList = [
    { username: 'student', password: '123456', role: 'student' },
    { username: 'teacher', password: '123456', role: 'teacher' },
    { username: 'admin', password: '123456', role: 'admin' }
  ]
  const findUser = userList.find(item => {
    return item.username === loginForm.value.username
      && item.password === loginForm.value.password
      && item.role === loginForm.value.role
  })

  if (findUser) {
    userStore.setUserInfo(findUser)
    userStore.setToken('mock-token-123456')
    // ========== 根据角色跳转到对应首页 ==========
    if (findUser.role === 'student') {
      router.push('/student/task')
    } else if (findUser.role === 'teacher') {
      router.push('/teacher/taskManage')
    } else if (findUser.role === 'admin') {
      router.push('/admin/user')
    }
  } else {
    alert('账号、密码或角色选择错误！')
  }
}
</script>
