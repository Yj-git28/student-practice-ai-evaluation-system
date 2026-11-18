<template>
  <el-card>
    <template #header>
      <div class="header">
        <span>实训成果提交 &amp; AI评价查看</span>
        <el-button size="small" @click="loadMyList">刷新记录</el-button>
      </div>
    </template>

    <el-alert
      v-if="!trainingId"
      type="warning"
      :closable="false"
      show-icon
      title="未指定实训任务，请先在下拉框中选择要提交的任务"/>

    <el-form label-width="100px" class="submit-form">
      <el-form-item label="实训任务">
        <el-select
          v-if="!trainingId"
          v-model="selectedTaskId"
          placeholder="请选择实训任务"
          filterable
          style="width:100%"
          @change="onTaskChange">
          <el-option
            v-for="item in taskOptions"
            :key="item.trainingId"
            :label="item.trainingName"
            :value="item.trainingId"/>
        </el-select>
        <div v-else class="task-info">
          <span class="task-name">{{ taskInfo.trainingName || '加载中...' }}</span>
          <span class="task-deadline">截止时间：{{ formatTime(taskInfo.endTime) }}</span>
        </div>
      </el-form-item>

      <el-form-item label="成果文件">
        <div class="upload-box">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :show-file-list="false"
            accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
            :on-change="handleFileChange">
            <el-button type="primary">选择实训报告/截图文件</el-button>
          </el-upload>
          <span class="upload-tip">支持 pdf / doc / docx / jpg / jpeg / png，单个文件不超过 20MB</span>
          <div v-if="selectedFile" class="file-picked">
            已选择：{{ selectedFile.name }}（{{ formatSize(selectedFile.size) }}）
          </div>
        </div>
      </el-form-item>

      <el-form-item>
        <el-button type="success" :loading="submitting" :disabled="!trainingId" @click="submitWork">
          确认提交成果
        </el-button>
      </el-form-item>
    </el-form>

    <template v-if="aiResult">
      <el-divider content-position="left">AI 初步核查评价结果</el-divider>
      <el-descriptions border :column="2">
        <el-descriptions-item label="AI 综合得分">{{ aiResult.aiScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交记录ID">{{ aiResult.resultId ?? '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        :title="'AI 评语：' + (aiResult.aiComment || '暂无评语')"
        type="info"
        :closable="false"
        class="ai-comment"/>
      <el-descriptions v-if="latestFinal" border :column="2" class="final-box">
        <el-descriptions-item label="教师最终分数">{{ latestFinal.finalScore }}</el-descriptions-item>
        <el-descriptions-item label="教师评语">{{ latestFinal.teacherComment || '-' }}</el-descriptions-item>
        <el-descriptions-item label="复核时间">{{ formatTime(latestFinal.reviewTime) }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-else
        title="教师尚未复核，当前展示的是 AI 初评得分"
        type="warning"
        :closable="false"
        class="ai-comment"/>
    </template>

    <el-divider content-position="left">我的提交记录</el-divider>
    <el-table :data="myList" border v-loading="myLoading" empty-text="暂无提交记录">
      <el-table-column label="提交时间" width="160">
        <template #default="{ row }">{{ formatTime(row.submitTime) }}</template>
      </el-table-column>
      <el-table-column prop="fileName" label="提交文件" min-width="180" show-overflow-tooltip/>
      <el-table-column prop="aiScore" label="AI 得分" width="90">
        <template #default="{ row }">{{ row.aiScore ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="aiComment" label="AI 评语" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.aiComment || '-' }}</template>
      </el-table-column>
      <el-table-column label="最终分数" width="100">
        <template #default="{ row }">
          <span v-if="row.finalScore !== null && row.finalScore !== undefined">{{ row.finalScore }}</span>
          <el-tag v-else type="info" size="small">待复核</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="teacherComment" label="教师评语" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.teacherComment || '-' }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const ALLOW_SUFFIX = ['pdf', 'doc', 'docx', 'jpg', 'jpeg', 'png']
const MAX_SIZE = 20 * 1024 * 1024

const route = useRoute()
const router = useRouter()

const trainingId = ref(route.query.trainingId ? Number(route.query.trainingId) : null)
const selectedTaskId = ref(null)
const taskOptions = ref([])
const taskInfo = ref({})

const uploadRef = ref(null)
const selectedFile = ref(null)
const submitting = ref(false)
const aiResult = ref(null)

const myList = ref([])
const myLoading = ref(false)

const latestFinal = computed(() => {
  if (!aiResult.value) return null
  const hit = myList.value.find(item =>
    String(item.resultId) === String(aiResult.value.resultId)
  )
  return hit && hit.finalScore !== null && hit.finalScore !== undefined ? hit : null
})

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatSize(bytes) {
  if (!bytes && bytes !== 0) return '-'
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

async function loadTaskOptions() {
  try {
    const res = await request.get('/training/page', { params: { page: 1, size: 100 } })
    if (res.code === 200 && res.data) taskOptions.value = res.data.list || []
  } catch (e) {
    taskOptions.value = []
  }
}

async function loadTaskInfo() {
  if (!trainingId.value) return
  try {
    const res = await request.get(`/training/${trainingId.value}`)
    if (res.code === 200 && res.data) taskInfo.value = res.data
  } catch (e) {
    taskInfo.value = {}
  }
}

async function loadMyList() {
  if (!trainingId.value) {
    myList.value = []
    return
  }
  myLoading.value = true
  try {
    const res = await request.get('/result/my', { params: { trainingId: trainingId.value } })
    myList.value = res.code === 200 ? (res.data || []) : []
  } catch (e) {
    myList.value = []
  } finally {
    myLoading.value = false
  }
}

function onTaskChange(value) {
  trainingId.value = value
  aiResult.value = null
  router.replace({ path: route.path, query: { trainingId: value } })
  loadTaskInfo()
  loadMyList()
}

function handleFileChange(uploadFile) {
  const raw = uploadFile?.raw
  if (!raw) return
  const ext = String(raw.name).split('.').pop().toLowerCase()
  if (!ALLOW_SUFFIX.includes(ext)) {
    ElMessage.error('文件格式不支持，仅允许 pdf / doc / docx / jpg / jpeg / png')
    uploadRef.value?.clearFiles()
    selectedFile.value = null
    return
  }
  if (raw.size > MAX_SIZE) {
    ElMessage.error('文件大小不能超过 20MB')
    uploadRef.value?.clearFiles()
    selectedFile.value = null
    return
  }
  selectedFile.value = raw
}

async function submitWork() {
  if (!trainingId.value) {
    ElMessage.warning('请先选择实训任务')
    return
  }
  if (!selectedFile.value) {
    ElMessage.warning('请选择要提交的成果文件')
    return
  }
  const formData = new FormData()
  formData.append('trainingId', trainingId.value)
  formData.append('file', selectedFile.value, selectedFile.value.name)

  submitting.value = true
  try {
    // AI 推理耗时较长，单独放宽超时时间；FormData 交由浏览器自动补 boundary
    const res = await request.post('/result/submit', formData, { timeout: 180000 })
    if (res.code === 200) {
      aiResult.value = res.data || {}
      ElMessage.success('提交成功，已完成 AI 初评')
      selectedFile.value = null
      uploadRef.value?.clearFiles()
      loadMyList()
    } else {
      ElMessage.error(res.msg || '提交失败')
    }
  } catch (e) {
    // 错误提示已由 request 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadTaskOptions()
  if (trainingId.value) {
    loadTaskInfo()
    loadMyList()
  }
})
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.submit-form {
  margin-top: 14px;
}

.task-info {
  display: flex;
  flex-direction: column;
  line-height: 1.6;
}

.task-name {
  font-weight: 600;
  color: #303133;
}

.task-deadline {
  font-size: 12px;
  color: #909399;
}

.upload-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
}

.file-picked {
  font-size: 13px;
  color: #67c23a;
}

.ai-comment {
  margin-top: 10px;
}

.final-box {
  margin-top: 10px;
}
</style>
