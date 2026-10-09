<template>
  <div>
    <!-- 绑定点击事件 -->
    <el-button type="primary" @click="getTestApi">ElementPlus测试按钮</el-button>
    <p>用户名：{{ userStore.userName }}</p>
    <p>后端返回信息：{{ msg }}</p>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useUserStore } from '@/stores/user'
const userStore = useUserStore()
const msg = ref("等待请求后端...")

// 点击按钮触发，走vite代理 /api
const getTestApi = async ()=>{
  const res = await fetch('/api/test')
  const data = await res.text()
  msg.value = data
  // 可以把后端返回的值存入pinia
  userStore.userName = data
}
</script>