<template>
<el-card>
  <template #header>
    <div style="display:flex;justify-content:space-between">
      <span>实训任务管理</span>
      <el-button type="primary" @click="openDialog=true">新建实训任务</el-button>
    </div>
  </template>
  <el-table :data="taskTable" border>
    <el-table-column prop="taskName" label="任务名称"/>
    <el-table-column prop="deadline" label="提交截止"/>
    <el-table-column prop="state" label="状态"/>
    <el-table-column label="操作">
      <template #default>
        <el-button link>编辑任务与评价指标</el-button>
        <el-button link type="danger">关闭任务</el-button>
      </template>
    </el-table-column>
  </el-table>
</el-card>

<el-dialog v-model="openDialog" title="新建实训任务">
  <el-form :model="form">
    <el-form-item label="实训任务名称"><el-input v-model="form.taskName"/></el-form-item>
    <el-form-item label="实训目标"><el-input v-model="form.target" type="textarea"/></el-form-item>
    <el-form-item label="实训要求"><el-input v-model="form.require" type="textarea"/></el-form-item>
    <el-form-item label="提交截止时间"><el-date-picker v-model="form.deadline" type="date"/></el-form-item>
  </el-form>
  <el-divider>自定义AI评价指标（权重）</el-divider>
  <el-table :data="indicatorList">
    <el-table-column prop="name" label="指标名称"/>
    <el-table-column prop="weight" label="权重(%)"/>
    <el-table-column label="操作">
      <template #default><el-button link>删除</el-button></template>
    </el-table-column>
  </el-table>
  <el-button type="text">新增评价指标</el-button>
  <template #footer>
    <el-button @click="openDialog=false">取消</el-button>
    <el-button type="primary">保存发布任务</el-button>
  </template>
</el-dialog>
</template>

<script setup>
import {ref} from 'vue'
const openDialog = ref(false)
const taskTable = ref([{taskName:"软件综合实训",deadline:"2026‑12‑30",state:"启用"}])
const form = ref({taskName:'',target:'',require:'',deadline:''})
const indicatorList = ref([
  {name:"代码质量",weight:30},
  {name:"文档规范性",weight:25},
  {name:"功能实现度",weight:30},
  {name:"实训步骤完整性",weight:15}
])
</script>
