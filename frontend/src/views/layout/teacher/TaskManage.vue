<template>
  <el-card>
    <template #header>
      <div class="header">
        <span>实训任务管理</span>
        <el-button type="primary" @click="openCreate">新增任务</el-button>
      </div>
    </template>

    <el-form :inline="true" class="filter-bar">
      <el-form-item label="关键字">
        <el-input v-model="filters.keyword" placeholder="任务名称" clearable style="width:200px"
                  @keyup.enter="handleSearch"/>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" style="width:130px">
          <el-option label="全部" value=""/>
          <el-option label="草稿" :value="0"/>
          <el-option label="已发布" :value="1"/>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="tableData" border v-loading="loading" empty-text="暂无实训任务">
      <el-table-column prop="trainingName" label="任务名称" min-width="160"/>
      <el-table-column prop="description" label="实训目标" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '-' }}</template>
      </el-table-column>
      <el-table-column label="发布范围" width="140">
        <template #default="{ row }">
          <el-tag :type="scopeTagType(row.scopeType)" size="small">{{ scopeLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" width="150">
        <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="截止时间" width="150">
        <template #default="{ row }">{{ formatTime(row.endTime) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="resultCount" label="已提交数" width="100">
        <template #default="{ row }">{{ row.resultCount ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status !== 1" v-has-perm="'training:publish'" link type="success"
                     @click="publishTask(row)">发布</el-button>
          <el-button link type="danger" @click="removeTask(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.trainingId ? '编辑任务' : '新增任务'" width="580px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="任务名称" prop="trainingName">
          <el-input v-model="form.trainingName" maxlength="100" show-word-limit placeholder="请输入任务名称"/>
        </el-form-item>

        <!-- 发布范围：仅管理员可见（教师创建默认归属自己） -->
        <el-form-item v-if="isAdmin" label="发布范围" prop="scopeType">
          <el-select v-model="form.scopeType" style="width:100%" @change="onScopeTypeChange">
            <el-option label="仅所属教师可见" :value="1"/>
            <el-option label="年级公共发布" :value="2"/>
            <el-option label="全校公共发布" :value="3"/>
          </el-select>
        </el-form-item>
        <el-form-item v-if="isAdmin && form.scopeType === 2" label="指定年级" prop="scopeValue">
          <el-select v-model="form.scopeValue" placeholder="请选择年级" style="width:100%">
            <el-option v-for="g in grades" :key="g" :label="g" :value="g"/>
          </el-select>
        </el-form-item>

        <el-form-item label="实训目标" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="1000" show-word-limit
                    placeholder="请输入实训目标与要求"/>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="请选择开始时间" style="width:100%"/>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="请选择截止时间" style="width:100%"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/useUserStore'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.roleCode === 'ADMIN')

const tableData = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const grades = ref([])

const filters = reactive({ keyword: '', status: '' })

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({
  trainingId: null,
  trainingName: '',
  description: '',
  startTime: '',
  endTime: '',
  scopeType: 1,
  scopeValue: null
})

const rules = {
  trainingName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入实训目标', trigger: 'blur' }],
  scopeValue: [{ required: true, message: '请选择年级', trigger: 'change' }]
}

function scopeLabel(row) {
  const map = { 1: '教师专属', 2: row.scopeValue ? `${row.scopeValue}公共` : '年级公共', 3: '全校公共' }
  return map[row.scopeType] || '教师专属'
}
function scopeTagType(scopeType) {
  const map = { 1: 'info', 2: 'warning', 3: 'success' }
  return map[scopeType] || 'info'
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}
function toPickerTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}

async function loadList() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (filters.keyword && filters.keyword.trim()) params.keyword = filters.keyword.trim()
    if (filters.status !== '') params.status = filters.status
    const res = await request.get('/training/page', { params })
    if (res.code === 200 && res.data) {
      tableData.value = res.data.list || []
      total.value = res.data.total || 0
    } else {
      tableData.value = []
      total.value = 0
      ElMessage.error(res.msg || '加载实训任务失败')
    }
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadGrades() {
  if (!isAdmin.value) return
  try {
    const res = await request.get('/training/grades')
    if (res.code === 200) grades.value = res.data || []
  } catch (e) {
    grades.value = []
  }
}

function handleSearch() { page.value = 1; loadList() }
function handleSizeChange() { page.value = 1; loadList() }
function resetFilters() { filters.keyword = ''; filters.status = ''; page.value = 1; loadList() }

function openCreate() {
  Object.assign(form, {
    trainingId: null, trainingName: '', description: '', startTime: '', endTime: '',
    scopeType: isAdmin.value ? 1 : 1,
    scopeValue: null
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
  loadGrades()
}

function openEdit(row) {
  Object.assign(form, {
    trainingId: row.trainingId,
    trainingName: row.trainingName || '',
    description: row.description || '',
    startTime: toPickerTime(row.startTime),
    endTime: toPickerTime(row.endTime),
    scopeType: row.scopeType || 1,
    scopeValue: row.scopeValue || null
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
  loadGrades()
}

function onScopeTypeChange() {
  if (form.scopeType !== 2) {
    form.scopeValue = null
    formRef.value?.clearValidate(['scopeValue'])
  }
}

async function saveTask() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  if (form.startTime && form.endTime && form.endTime < form.startTime) {
    ElMessage.warning('截止时间不能早于开始时间')
    return
  }
  saving.value = true
  try {
    const payload = {
      trainingName: form.trainingName.trim(),
      description: form.description.trim(),
      startTime: form.startTime || null,
      endTime: form.endTime || null
    }
    if (isAdmin.value) {
      payload.scopeType = form.scopeType
      payload.scopeValue = form.scopeType === 2 ? form.scopeValue : null
    }

    const res = form.trainingId
        ? await request.put(`/training/${form.trainingId}`, payload)
        : await request.post('/training', payload)
    if (res.code === 200) {
      ElMessage.success(form.trainingId ? '保存成功' : '新增成功')
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

async function publishTask(row) {
  try {
    await ElMessageBox.confirm(`确定发布实训任务「${row.trainingName}」吗？发布后学生可见。`, '发布任务', {
      type: 'warning'
    })
  } catch (e) { return }
  try {
    const res = await request.post(`/training/${row.trainingId}/publish`)
    if (res.code === 200) {
      ElMessage.success('已发布')
      loadList()
    } else {
      ElMessage.error(res.msg || '发布失败')
    }
  } catch (e) { /* 拦截器已处理 */ }
}

async function removeTask(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除实训任务「${row.trainingName}」吗？其下学生提交的成果也会一并删除。`,
      '删除任务', { type: 'warning' })
  } catch (e) { return }
  try {
    const res = await request.delete(`/training/${row.trainingId}`)
    if (res.code === 200) {
      ElMessage.success('已删除')
      if (tableData.value.length === 1 && page.value > 1) page.value -= 1
      loadList()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (e) { /* 拦截器已处理 */ }
}

onMounted(() => { loadList(); loadGrades() })
</script>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; }
.filter-bar { margin-bottom: 6px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
