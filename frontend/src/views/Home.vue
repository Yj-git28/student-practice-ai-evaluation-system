<template>
  <div style="padding:20px">
    <h2>首页测试</h2>
    <el-button type="primary">ElementPlus按钮测试</el-button>
    <!-- 这个就是测试按钮 -->
    <el-button type="success" @click="testApi">测试后端接口</el-button>
    <p>用户名：{{ userStore.userName }}</p>
    <p>接口返回：{{ msg }}</p>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useUserStore } from '@/stores/user'
const userStore = useUserStore()
userStore.userName = "测试用户"
const msg = ref("")

// 请求后端接口
const testApi = async () => {
  const res = await fetch("/api/test")
  const data = await res.text()
  msg.value = data
}
</script>