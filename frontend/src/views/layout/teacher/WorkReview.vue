<template>
  <el-card>
    <template #header>
      <div class="header">
        <span>学生实训成果AI复核打分</span>
        <el-button size="small" @click="loadList">刷新</el-button>
      </div>
    </template>

    <el-form :inline="true" class="filter-bar">
      <el-form-item label="关键字">
        <el-input v-model="filters.keyword" placeholder="学生姓名 / 实训任务" clearable style="width:220px"/>
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="filters.onlyUnreviewed">仅看未复核</el-checkbox>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="displayList" border v-loading="loading" empty-text="暂无学生提交记录">
      <el-table-column prop="stuName" label="学生姓名" width="110">
        <template #default="{ row }">{{ row.stuName || row.stuUsername || '-' }}</template>
      </el-table-column>
      <el-table-column prop="className" label="班级" width="150">
        <template #default="{ row }">{{ row.className || '-' }}</template>
      </el-table-column>
      <el-table-column prop="trainingName" label="实训任务" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.trainingName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="fileName" label="提交文件" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">{{ row.fileName || '-' }}</template>
      </el-table-column>
      <el-table-column label="提交时间" width="150">
        <template #default="{ row }">{{ formatTime(row.submitTime) }}</template>
      </el-table-column>
      <el-table-column prop="aiScore" label="AI得分" width="85">
        <template #default="{ row }">{{ row.aiScore ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="aiComment" label="AI评语" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.aiComment || '-' }}</template>
      </el-table-column>
      <el-table-column label="最终分数" width="100">
        <template #default="{ row }">
          <span v-if="hasFinal(row)">{{ row.finalScore }}</span>
          <el-tag v-else type="warning" size="small">未复核</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-has-perm="'evaluation:review'" @click="openReview(row)">人工复核</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pager"
      @current-change="loadList"
      @size-change="handleSizeChange"/>

    <el-dialog v-model="dialogVisible" title="人工复核（修改最终成绩）" width="560px">
      <el-descriptions border :column="2" class="review-info">
        <el-descriptions-item label="学生">{{ currentRow.stuName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ currentRow.className || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实训任务" :span="2">{{ currentRow.trainingName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="AI得分">{{ currentRow.aiScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交文件">{{ currentRow.fileName || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        :title="'AI 评语：' + (currentRow.aiComment || '暂无评语')"
        type="info"
        :closable="false"
        class="review-ai-comment"/>

      <el-form :model="editForm" label-width="100px" class="review-form">
        <el-form-item label="最终总分">
          <el-input-number v-model="editForm.finalScore" :min="0" :max="100" :precision="0"
                           :step="1" controls-position="right"/>
          <span class="score-tip">0 - 100 分</span>
        </el-form-item>
        <el-form-item label="教师补充评语">
          <el-input v-model="editForm.teacherComment" type="textarea" :rows="4" maxlength="500"
                    show-word-limit placeholder="请输入复核意见"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" v-has-perm="'evaluation:review'" @click="saveReview">确认保存最终成绩</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

const filters = reactive({ keyword: '', onlyUnreviewed: false })

const dialogVisible = ref(false)
const saving = ref(false)
const currentRow = ref({})
const editForm = reactive({ resultId: null, finalScore: 0, teacherComment: '' })

const displayList = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return list.value.filter(row => {
    if (filters.onlyUnreviewed && hasFinal(row)) return false
    if (!keyword) return true
    const name = String(row.stuName || row.stuUsername || '').toLowerCase()
    const task = String(row.trainingName || '').toLowerCase()
    return name.includes(keyword) || task.includes(keyword)
  })
})

function hasFinal(row) {
  return row.finalScore !== null && row.finalScore !== undefined
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function loadList() {
  loading.value = true
  try {
    const res = await request.get('/result/page', { params: { page: page.value, size: size.value } })
    if (res.code === 200 && res.data) {
      list.value = res.data.list || []
      total.value = res.data.total || 0
    } else {
      list.value = []
      total.value = 0
      ElMessage.error(res.msg || '加载提交记录失败')
    }
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadList()
}

function handleSizeChange() {
  page.value = 1
  loadList()
}

function resetFilters() {
  filters.keyword = ''
  filters.onlyUnreviewed = false
  page.value = 1
  loadList()
}

function openReview(row) {
  currentRow.value = row || {}
  editForm.resultId = row.resultId
  editForm.finalScore = hasFinal(row)
      ? Number(row.finalScore)
      : (row.aiScore !== null && row.aiScore !== undefined ? Math.round(Number(row.aiScore)) : 0)
  editForm.teacherComment = row.teacherComment || ''
  dialogVisible.value = true
}

async function saveReview() {
  if (editForm.finalScore === null || editForm.finalScore === undefined) {
    ElMessage.warning('请输入最终分数')
    return
  }
  if (editForm.finalScore < 0 || editForm.finalScore > 100) {
    ElMessage.warning('最终分数需在 0 - 100 之间')
    return
  }
  saving.value = true
  try {
    const res = await request.post(`/result/${editForm.resultId}/review`, {
      finalScore: editForm.finalScore,
      teacherComment: editForm.teacherComment
    })
    if (res.code === 200) {
      ElMessage.success('复核结果已保存')
      dialogVisible.value = false
      loadList()
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (e) {
    // 错误提示已由 request 拦截器统一处理
  } finally {
    saving.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  margin-bottom: 6px;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.review-info {
  margin-bottom: 10px;
}

.review-ai-comment {
  margin-bottom: 14px;
}

.review-form {
  margin-top: 8px;
}

.score-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
