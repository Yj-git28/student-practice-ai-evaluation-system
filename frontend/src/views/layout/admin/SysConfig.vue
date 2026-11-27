<template>
  <el-card>
    <template #header>系统参数配置</template>
    <el-form label-width="180px" v-loading="configLoading">
      <el-form-item label="大模型运行模式">
        <el-radio-group v-model="modelMode">
          <el-radio label="cloud">云端大模型接口调用</el-radio>
          <el-radio label="local">本地私有化部署大模型</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="云端API密钥">
        <el-input v-model="apiKey" type="password" show-password placeholder="填写大模型api key"/>
      </el-form-item>
      <el-form-item label="单文件最大上传大小(MB)">
        <el-input-number v-model="maxFileSize" :min="1" :max="200"/>
      </el-form-item>
      <el-form-item label="允许上传文件格式">
        <el-checkbox-group v-model="allowSuffix">
          <el-checkbox label="pdf"/>
          <el-checkbox label="doc"/>
          <el-checkbox label="docx"/>
          <el-checkbox label="jpg"/>
          <el-checkbox label="jpeg"/>
          <el-checkbox label="png"/>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" v-has-perm="'config:update'" @click="saveConfig">保存系统配置</el-button>
      </el-form-item>
    </el-form>
    <el-divider>系统操作日志查询</el-divider>
    <div class="log-tools">
      <el-button size="small" @click="loadLogs">刷新</el-button>
      <span class="tip">展示最近 100 条操作记录</span>
    </div>
    <el-table :data="logList" border v-loading="logLoading" max-height="420">
      <el-table-column prop="operUser" label="操作人" width="140"/>
      <el-table-column label="操作时间" width="180">
        <template #default="{ row }">{{ formatTime(row.operTime) }}</template>
      </el-table-column>
      <el-table-column prop="content" label="操作内容"/>
    </el-table>
  </el-card>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const modelMode = ref('cloud')
const apiKey = ref('')
const maxFileSize = ref(10)
const allowSuffix = ref(['pdf', 'doc', 'docx', 'jpg', 'png'])

const configLoading = ref(false)
const saving = ref(false)

const logList = ref([])
const logLoading = ref(false)

async function loadConfig() {
  configLoading.value = true
  try {
    const res = await request.get('/system/config')
    if (res.code === 200 && res.data) {
      const data = res.data
      modelMode.value = data.modelMode || 'cloud'
      apiKey.value = data.apiKey || ''
      maxFileSize.value = Number(data.maxFileSize) || 10
      allowSuffix.value = String(data.allowSuffix || '')
          .split(',')
          .map(item => item.trim())
          .filter(Boolean)
    }
  } catch (e) {
    // 读取失败时保留默认值，不阻塞页面
  } finally {
    configLoading.value = false
  }
}

async function saveConfig() {
  if (!allowSuffix.value.length) {
    ElMessage.warning('请至少选择一种允许上传的文件格式')
    return
  }
  saving.value = true
  try {
    const res = await request.post('/system/config', {
      modelMode: modelMode.value,
      apiKey: apiKey.value,
      maxFileSize: maxFileSize.value,
      allowSuffix: allowSuffix.value.join(',')
    })
    if (res.code === 200) {
      ElMessage.success('系统配置已保存')
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (e) {
    // 错误提示已由 request 拦截器统一处理
  } finally {
    saving.value = false
  }
}

async function loadLogs() {
  logLoading.value = true
  try {
    const res = await request.get('/system/logs', { params: { limit: 100 } })
    if (res.code === 200) logList.value = res.data || []
  } catch (e) {
    logList.value = []
  } finally {
    logLoading.value = false
  }
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(() => {
  loadConfig()
  loadLogs()
})
</script>
<style scoped>
.log-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.log-tools .tip {
  color: #909399;
  font-size: 12px;
}
</style>
