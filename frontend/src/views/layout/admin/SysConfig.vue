<template>
<el-card>
  <template #header>系统参数配置</template>
  <el-form label-width="180px">
    <el-form-item label="大模型运行模式">
      <el-radio-group v-model="modelMode">
        <el-radio label="cloud">云端大模型接口调用</el-radio>
        <el-radio label="local">本地私有化部署大模型</el-radio>
      </el-radio-group>
    </el-form-item>
    <el-form-item label="云端API密钥">
      <el-input v-model="apiKey" placeholder="填写大模型api key"></el-input>
    </el-form-item>
    <el-form-item label="单文件最大上传大小(MB)">
      <el-input-number v-model="maxFileSize" :min="1"></el-input-number>
    </el-form-item>
    <el-form-item label="允许上传文件格式">
      <el-checkbox-group v-model="allowSuffix">
        <el-checkbox label="pdf"/>
        <el-checkbox label="doc"/>
        <el-checkbox label="docx"/>
        <el-checkbox label="jpg"/>
        <el-checkbox label="png"/>
      </el-checkbox-group>
    </el-form-item>
    <el-form-item>
      <el-button type="primary">保存系统配置</el-button>
    </el-form-item>
  </el-form>
  <el-divider>系统操作日志查询</el-divider>
  <el-table :data="logList" border>
    <el-table-column prop="operUser" label="操作人"/>
    <el-table-column prop="operTime" label="操作时间"/>
    <el-table-column prop="content" label="操作内容"/>
  </el-table>
</el-card>
</template>
<script setup>
import {ref} from 'vue'
const modelMode = ref('cloud')
const apiKey = ref('')
const maxFileSize = ref(10)
const allowSuffix = ref(['pdf','doc','docx','jpg','png'])
const logList = ref([
  {operUser:"admin",operTime:"2026‑10‑09 10:20:11",content:"修改大模型配置参数"}
])
</script>