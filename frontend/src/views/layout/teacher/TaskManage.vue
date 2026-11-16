<template>
  <div class="task-manage">
    <div class="header">
      <h3>实训任务管理</h3>
      <div class="btn-group">
        <!-- 新增任务：权限码 training:add -->
        <el-button type="primary" v-has-perm="'training:add'">新增任务</el-button>
      </div>
    </div>

    <el-table :data="tableData" border style="width:100%">
      <el-table-column prop="taskName" label="任务名称" />
      <el-table-column prop="target" label="实训目标" />
      <el-table-column prop="deadline" label="截止时间" />
      <el-table-column prop="status" label="状态" />
      <el-table-column label="操作">
        <template #default="scope">
          <!-- 编辑：training:edit -->
          <el-button size="small" type="warning" v-has-perm="'training:edit'">编辑</el-button>
          <!-- 删除：training:delete -->
          <el-button size="small" type="danger" v-has-perm="'training:delete'">删除</el-button>
          <!-- 发布实训：training:publish -->
          <el-button size="small" type="success" v-has-perm="'training:publish'">发布实训</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗，这里只是模板，你可以自己补表单 -->
    <el-dialog v-model="dialogVisible" title="任务编辑">
      <el-form label-width="100px">
        <el-form-item label="任务名称">
          <el-input v-model="form.taskName" />
        </el-form-item>
        <el-form-item label="实训目标">
          <el-input v-model="form.target" type="textarea" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="form.deadline" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" v-has-perm="'training:edit'">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const tableData = ref([
  {
    taskName: '软件综合实训项目',
    target: '完成系统开发、提交报告、截图成果',
    deadline: '2026-12-30',
    status: '进行中'
  }
])

const dialogVisible = ref(false)
const form = ref({
  taskName: '',
  target: '',
  deadline: ''
})
</script>

<style scoped>
.task-manage{padding:16px;}
.header{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;}
</style>
