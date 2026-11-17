<template>
  <div style="display:flex;justify-content:center;align-items:center;height:100vh;background:#f5f5f5;">
    <el-card style="width:420px;padding:20px;">
      <h2 style="text-align:center;margin-bottom:24px;">软件实训AI检查评价系统</h2>
      <el-form :model="form" @keyup.enter="handleLogin">
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="teacher01 / stu01 / admin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width:100%" :loading="loading" @click="handleLogin">
            登录系统
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/useUserStore'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)
const form = ref({ username: '', password: '' })

const handleLogin = async () => {
  if (!form.value.username || !form.value.password) {
    return ElMessage.warning('请输入账号和密码')
  }
  loading.value = true
  try {
    await userStore.login(form.value)
    const redirect = route.query.redirect || '/'
    await router.replace(redirect)
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>