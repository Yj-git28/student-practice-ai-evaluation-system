<template>
  <el-card>
    <template #header><span>我的实训任务列表</span></template>
    <el-table :data="taskList" border>
      <el-table-column prop="taskName" label="实训任务名称"/>
      <el-table-column prop="target" label="实训目标"/>
      <el-table-column prop="endTime" label="提交截止时间"/>
      <el-table-column prop="status" label="任务状态"/>
      <el-table-column label="操作">
        <template #default="scope">
          <el-button type="primary" link @click="$router.push('/student/submit')">提交成果</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>
<script setup>
import {ref} from 'vue'
const taskList = ref([
  {
    taskName:"软件综合实训项目",
    target:"完成系统开发，提交报告、截图成果",
    endTime:"2026‑12‑30",
    status:"进行中"
  }
])
</script>
