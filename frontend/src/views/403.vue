<template>
  <div style="text-align:center;padding-top:120px">
    <h1>403 无访问权限</h1>
    <p>当前账号角色不能访问该页面</p>
    <el-button @click="$router.push('/login')">返回登录页</el-button>
  </div>
</template>
