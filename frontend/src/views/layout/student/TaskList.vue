<template>
  <el-card>
    <template #header>
      <div class="header">
        <span>我的实训任务列表</span>
        <el-button size="small" @click="loadList">刷新</el-button>
      </div>
    </template>

    <el-table :data="taskList" border v-loading="loading" empty-text="暂无已发布的实训任务">
      <el-table-column prop="trainingName" label="实训任务名称" min-width="160"/>
      <el-table-column prop="description" label="实训目标" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '-' }}</template>
      </el-table-column>
      <el-table-column label="提交截止时间" width="170">
        <template #default="{ row }">{{ formatTime(row.endTime) }}</template>
      </el-table-column>
      <el-table-column label="任务状态" width="100">
        <template #default="{ row }">
          <el-tag :type="isExpired(row) ? 'info' : 'success'" size="small">
            {{ isExpired(row) ? '已截止' : '进行中' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="isExpired(row)" @click="goSubmit(row)">
            提交成果
          </el-button>
          <span v-if="isExpired(row)" class="expired-tip">已过截止时间</span>
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
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'

const router = useRouter()
const taskList = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function isExpired(row) {
  if (!row.endTime) return false
  const end = new Date(String(row.endTime).replace(' ', 'T'))
  if (Number.isNaN(end.getTime())) return false
  return end.getTime() < Date.now()
}

async function loadList() {
  loading.value = true
  try {
    const res = await request.get('/training/page', { params: { page: page.value, size: size.value } })
    if (res.code === 200 && res.data) {
      taskList.value = res.data.list || []
      total.value = res.data.total || 0
    } else {
      taskList.value = []
      total.value = 0
    }
  } catch (e) {
    taskList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  page.value = 1
  loadList()
}

function goSubmit(row) {
  router.push({ path: '/training/submit', query: { trainingId: row.trainingId } })
}

onMounted(loadList)
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.expired-tip {
  margin-left: 6px;
  font-size: 12px;
  color: #909399;
}
</style>
