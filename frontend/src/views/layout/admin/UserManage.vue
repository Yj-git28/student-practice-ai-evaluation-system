<template>
  <el-card>
    <template #header>
      <span>师生账号&班级管理</span>
      <div>
        <el-button>新增账号</el-button>
        <el-button type="primary">批量导入账号</el-button>
      </div>
    </template>
    <el-table :data="userTable" border>
      <el-table-column prop="username" label="账号"/>
      <el-table-column prop="realName" label="姓名"/>
      <el-table-column prop="role" label="角色"/>
      <el-table-column prop="className" label="所属班级"/>
      <el-table-column label="操作">
        <template #default>
          <el-button link>编辑</el-button>
          <el-button link>重置密码</el-button>
          <el-button link type="danger">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>
<script setup>
import {ref} from 'vue'
const userTable = ref([
  {username:"stu001",realName:"张三",role:"student",className:"软工24‑1"},
  {username:"tea01",realName:"李老师",role:"teacher",className:"软工24‑1"}
])
</script>
