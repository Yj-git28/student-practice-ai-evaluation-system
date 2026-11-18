<template>
  <el-card>
    <template #header>
      <div class="header">
        <span>师生账号 &amp; 班级管理</span>
        <div>
          <el-button type="primary" @click="openCreate">新增账号</el-button>
          <el-button @click="openImport">批量导入</el-button>
          <el-button @click="exportUsers">批量导出</el-button>
          <el-button @click="openClassManage">班级管理</el-button>
          <el-button @click="loadList">刷新</el-button>
        </div>
      </div>
    </template>

    <el-form :inline="true" class="filter-bar">
      <el-form-item label="年级">
        <el-select v-model="filters.grade" placeholder="全部年级" clearable style="width:130px"
                   @change="onGradeChange">
          <el-option v-for="g in grades" :key="g" :label="g" :value="g"/>
        </el-select>
      </el-form-item>
      <el-form-item label="班级">
        <el-select v-model="filters.classId" placeholder="全部班级" clearable filterable style="width:180px">
          <el-option v-for="c in classOptions" :key="c.classId" :label="c.className" :value="c.classId"/>
        </el-select>
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="filters.roleId" placeholder="全部角色" clearable style="width:120px">
          <el-option v-for="r in ROLE_OPTIONS" :key="r.value" :label="r.label" :value="r.value"/>
        </el-select>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="filters.keyword" placeholder="账号 / 姓名 / 邮箱" clearable style="width:200px"
                  @keyup.enter="loadList"/>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadList">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="userTable" border v-loading="loading">
      <el-table-column prop="userId" label="ID" width="70"/>
      <el-table-column prop="username" label="账号" min-width="110"/>
      <el-table-column prop="realName" label="姓名" min-width="100"/>
      <el-table-column prop="email" label="邮箱" min-width="180">
        <template #default="{ row }">{{ row.email || '-' }}</template>
      </el-table-column>
      <el-table-column label="角色" width="90">
        <template #default="{ row }">
          <el-tag :type="roleTagType(row.roleId)" size="small">{{ row.roleName || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="grade" label="年级" width="100">
        <template #default="{ row }">{{ row.grade || '-' }}</template>
      </el-table-column>
      <el-table-column prop="className" label="班级" min-width="150">
        <template #default="{ row }">{{ row.className || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="resetPwd(row)">重置密码</el-button>
          <el-button link type="danger" @click="removeUser(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="total-tip">共 {{ userTable.length }} 个账号</div>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="formVisible" :title="form.userId ? '编辑账号' : '新增账号'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="账号" required>
          <el-input v-model="form.username" :disabled="!!form.userId"
                    placeholder="3-32 位字母、数字或下划线"/>
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="form.realName" placeholder="真实姓名"/>
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="可选，用于登录 / 找回密码"/>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleId" style="width:100%">
            <el-option v-for="r in ROLE_OPTIONS" :key="r.value" :label="r.label" :value="r.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="班级">
          <el-select v-model="form.classId" clearable filterable placeholder="教师/管理员可不选"
                     style="width:100%">
            <el-option v-for="c in classes" :key="c.classId"
                       :label="c.grade ? `${c.grade} · ${c.className}` : c.className" :value="c.classId"/>
          </el-select>
        </el-form-item>
        <el-form-item v-if="!form.userId" label="初始密码">
          <el-input v-model="form.password" placeholder="留空则为 123456"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveUser">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog v-model="importVisible" title="批量导入账号" width="660px">
      <el-alert type="info" :closable="false" show-icon
                title="CSV 格式：账号,姓名,邮箱,角色,班级,初始密码（首行可为表头）"/>
      <div class="import-tools">
        <el-button size="small" @click="downloadTemplate">下载 CSV 模板</el-button>
        <el-button size="small" @click="triggerFile">选择 CSV 文件</el-button>
        <input ref="fileInput" type="file" accept=".csv,.txt" hidden @change="onFileChange"/>
        <span class="tip">角色填 学生 / 教师 / 管理员；班级需与系统班级名称一致；密码留空为 123456</span>
      </div>
      <el-input v-model="importText" type="textarea" :rows="9"
                placeholder="也可以直接粘贴 CSV 内容，例如：&#10;账号,姓名,邮箱,角色,班级,初始密码&#10;stu1001,张三,stu1001@example.com,学生,软件工程2401班,123456"/>
      <div v-if="importResult" class="import-result">
        <el-alert :type="importResult.failed.length ? 'warning' : 'success'" :closable="false" show-icon
                  :title="`共 ${importResult.total} 条，成功 ${importResult.success} 条，失败 ${importResult.failed.length} 条`"/>
        <el-table v-if="importResult.failed.length" :data="importResult.failed" size="small" max-height="200">
          <el-table-column prop="row" label="行号" width="70"/>
          <el-table-column prop="username" label="账号" width="140"/>
          <el-table-column prop="reason" label="失败原因"/>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">开始导入</el-button>
      </template>
    </el-dialog>

    <!-- 班级管理 -->
    <el-dialog v-model="classVisible" title="班级管理" width="640px">
      <el-form :inline="true" class="class-form">
        <el-form-item label="年级">
          <el-select v-model="classForm.grade" filterable allow-create default-first-option
                     placeholder="如 2024级" style="width:140px">
            <el-option v-for="g in grades" :key="g" :label="g" :value="g"/>
          </el-select>
        </el-form-item>
        <el-form-item label="班级名称">
          <el-input v-model="classForm.className" placeholder="如 软件工程2402班" style="width:190px"/>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="classSaving" @click="saveClass">
            {{ classForm.classId ? '保存修改' : '添加班级' }}
          </el-button>
          <el-button v-if="classForm.classId" @click="resetClassForm">取消</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="classes" size="small" border max-height="320">
        <el-table-column label="年级" width="120">
          <template #default="{ row }">{{ row.grade || '-' }}</template>
        </el-table-column>
        <el-table-column prop="className" label="班级名称"/>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button link type="primary" @click="editClass(row)">编辑</el-button>
            <el-button link type="danger" @click="removeClass(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="total-tip">共 {{ classes.length }} 个班级；删除前需先移除班级下的账号</div>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const ROLE_OPTIONS = [
  { label: '教师', value: 1 },
  { label: '学生', value: 2 },
  { label: '管理员', value: 3 }
]
const ROLE_MAP = {
  '教师': 1, 'TEACHER': 1, '1': 1,
  '学生': 2, 'STUDENT': 2, '2': 2,
  '管理员': 3, 'ADMIN': 3, '3': 3
}

const userTable = ref([])
const classes = ref([])
const loading = ref(false)

const filters = reactive({ grade: '', classId: null, roleId: null, keyword: '' })

const grades = computed(() => [...new Set(classes.value.map(c => c.grade).filter(Boolean))])
const classOptions = computed(() =>
  filters.grade ? classes.value.filter(c => c.grade === filters.grade) : classes.value
)

function roleTagType(roleId) {
  if (roleId === 3) return 'danger'
  if (roleId === 1) return 'warning'
  return 'success'
}

/** 兼容后端返回 camelCase / snake_case 两种字段命名 */
function normalizeUser(row) {
  return {
    userId: row.userId ?? row.user_id,
    username: row.username,
    realName: row.realName ?? row.real_name,
    email: row.email,
    roleId: row.roleId ?? row.role_id,
    roleName: row.roleName ?? row.role_name,
    classId: row.classId ?? row.class_id ?? null,
    className: row.className ?? row.class_name ?? '',
    grade: row.grade ?? ''
  }
}

function normalizeClass(row) {
  return {
    classId: row.classId ?? row.class_id,
    className: row.className ?? row.class_name,
    grade: row.grade ?? ''
  }
}

async function loadClasses() {
  try {
    const res = await request.get('/system/classes')
    if (res.code === 200) classes.value = (res.data || []).map(normalizeClass)
  } catch (e) {
    // 下拉框加载失败不阻塞列表
  }
}

async function loadList() {
  loading.value = true
  try {
    const params = {}
    if (filters.grade) params.grade = filters.grade
    if (filters.classId) params.classId = filters.classId
    if (filters.roleId) params.roleId = filters.roleId
    if (filters.keyword && filters.keyword.trim()) params.keyword = filters.keyword.trim()
    const res = await request.get('/system/users', { params })
    if (res.code === 200) {
      userTable.value = (res.data || []).map(normalizeUser)
    } else {
      ElMessage.error(res.msg || '加载失败')
    }
  } catch (e) {
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

function onGradeChange() {
  if (filters.classId) {
    const stillValid = classes.value.some(c => c.classId === filters.classId && (!filters.grade || c.grade === filters.grade))
    if (!stillValid) filters.classId = null
  }
}

function resetFilters() {
  filters.grade = ''
  filters.classId = null
  filters.roleId = null
  filters.keyword = ''
  loadList()
}

/* ---------- 新增 / 编辑 ---------- */
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ userId: null, username: '', realName: '', email: '', password: '', roleId: 2, classId: null })

function openCreate() {
  Object.assign(form, { userId: null, username: '', realName: '', email: '', password: '', roleId: 2, classId: null })
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    userId: row.userId,
    username: row.username,
    realName: row.realName,
    email: row.email || '',
    password: '',
    roleId: row.roleId,
    classId: row.classId || null
  })
  formVisible.value = true
}

async function saveUser() {
  if (!form.userId && !/^[A-Za-z0-9_]{3,32}$/.test(form.username)) {
    ElMessage.warning('账号需为 3-32 位字母、数字或下划线')
    return
  }
  if (!form.realName || !form.realName.trim()) {
    ElMessage.warning('请填写姓名')
    return
  }
  saving.value = true
  try {
    const payload = {
      username: form.username.trim(),
      realName: form.realName.trim(),
      email: form.email.trim(),
      password: form.password,
      roleId: form.roleId,
      classId: form.classId
    }
    const res = form.userId
        ? await request.put(`/system/users/${form.userId}`, payload)
        : await request.post('/system/users', payload)
    if (res.code === 200) {
      ElMessage.success(form.userId ? '保存成功' : '新增成功')
      formVisible.value = false
      loadList()
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function resetPwd(row) {
  try {
    await ElMessageBox.confirm(`确定将 ${row.realName}（${row.username}）的密码重置为 123456 吗？`, '重置密码', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    const res = await request.post(`/system/users/${row.userId}/reset-password`)
    if (res.code === 200) {
      ElMessage.success(`已重置为 ${res.data?.password || '123456'}，该账号需重新登录`)
    } else {
      ElMessage.error(res.msg || '重置失败')
    }
  } catch (e) {
    ElMessage.error('重置失败')
  }
}

async function removeUser(row) {
  try {
    await ElMessageBox.confirm(`确定删除账号 ${row.username} 吗？删除后该账号无法登录。`, '删除账号', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    const res = await request.delete(`/system/users/${row.userId}`)
    if (res.code === 200) {
      ElMessage.success('已删除')
      loadList()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

/* ---------- 批量导入 ---------- */
const importVisible = ref(false)
const importing = ref(false)
const importText = ref('')
const importResult = ref(null)
const fileInput = ref(null)

function openImport() {
  importText.value = ''
  importResult.value = null
  importVisible.value = true
}

function triggerFile() {
  fileInput.value?.click()
}

function onFileChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    importText.value = String(reader.result || '')
  }
  reader.readAsText(file, 'UTF-8')
  event.target.value = ''
}

function downloadTemplate() {
  const content = '账号,姓名,邮箱,角色,班级,初始密码\n'
      + 'stu1001,张三,stu1001@example.com,学生,软件工程2401班,123456\n'
      + 'teacher02,李老师,teacher02@example.com,教师,,123456\n'
  const blob = new Blob(['\ufeff' + content], { type: 'text/csv;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = '账号导入模板.csv'
  link.click()
  URL.revokeObjectURL(link.href)
}

/** 导出当前筛选结果（不受分页影响），CSV 带 BOM，Excel 直接打开不乱码 */
function exportUsers() {
  if (!userTable.value.length) {
    ElMessage.warning('当前没有可导出的账号')
    return
  }
  const header = ['账号', '姓名', '邮箱', '角色', '年级', '班级']
  const rows = userTable.value.map(row => [
    row.username, row.realName, row.email, row.roleName, row.grade, row.className
  ])
  const csv = [header, ...rows]
      .map(cols => cols.map(value => {
        const text = value === null || value === undefined ? '' : String(value)
        return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
      }).join(','))
      .join('\r\n')

  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`
      + `_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`

  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `账号列表_${stamp}.csv`
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${userTable.value.length} 条账号`)
}

/** 自动识别分隔符：英文逗号 / 中文逗号 / 制表符（Excel 直接复制常见） */
function detectDelimiter(line) {
  if (line.includes('\t')) return '\t'
  if (line.includes('，')) return '，'
  return ','
}

function parseCsv(text) {
  const rows = []
  const failures = []
  const lineNoMap = []
  const seenUsernames = new Set()
  const lines = String(text).replace(/^\uFEFF/, '').split(/\r?\n/)
  let firstLineHandled = false

  lines.forEach((raw, index) => {
    const line = raw.trim()
    if (!line) return
    const cells = line.split(detectDelimiter(line)).map(c => c.trim().replace(/^"|"$/g, ''))
    const lineNo = index + 1

    // 首个非空行如果是表头则跳过
    if (!firstLineHandled) {
      firstLineHandled = true
      if ((cells[0] || '').includes('账号')) return
    }

    const [username, realName, email, roleText, className, password] = cells
    if (!username) {
      failures.push({ row: lineNo, username: '-', reason: '账号不能为空' })
      return
    }
    if (!/^[A-Za-z0-9_]{3,32}$/.test(username)) {
      failures.push({ row: lineNo, username, reason: '账号需为 3-32 位字母、数字或下划线' })
      return
    }
    if (seenUsernames.has(username)) {
      failures.push({ row: lineNo, username, reason: '文件内账号重复' })
      return
    }
    seenUsernames.add(username)

    const roleKey = (roleText || '学生').trim()
    const roleId = ROLE_MAP[roleKey.toUpperCase()] || ROLE_MAP[roleKey]
    if (!roleId) {
      failures.push({ row: lineNo, username, reason: `角色无法识别：${roleText}` })
      return
    }
    if (email && !/^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(email)) {
      failures.push({ row: lineNo, username, reason: `邮箱格式不正确：${email}` })
      return
    }
    if (password && password.length < 6) {
      failures.push({ row: lineNo, username, reason: '初始密码至少 6 位' })
      return
    }
    let classId = null
    if (className) {
      const matched = classes.value.find(c => c.className === className)
      if (!matched) {
        failures.push({ row: lineNo, username, reason: `班级不存在：${className}` })
        return
      }
      classId = matched.classId
    }
    rows.push({
      username,
      realName: realName || username,
      email: email || '',
      roleId,
      classId,
      password: password || ''
    })
    lineNoMap.push(lineNo)
  })
  return { rows, failures, lineNoMap }
}

async function submitImport() {
  if (!importText.value.trim()) {
    ElMessage.warning('请先选择文件或粘贴 CSV 内容')
    return
  }
  const { rows, failures, lineNoMap } = parseCsv(importText.value)
  if (!rows.length) {
    importResult.value = { total: failures.length, success: 0, failed: failures }
    return
  }
  importing.value = true
  try {
    const res = await request.post('/system/users/import', rows)
    if (res.code === 200) {
      const data = res.data || { total: 0, success: 0, failed: [] }
      const backendFailed = (data.failed || []).map(f => ({
        row: lineNoMap[f.row - 1] || f.row,
        username: f.username,
        reason: f.reason
      }))
      importResult.value = {
        total: data.total + failures.length,
        success: data.success,
        failed: [...failures, ...backendFailed].sort((a, b) => a.row - b.row)
      }
      ElMessage.success(`导入完成：成功 ${data.success} 条`)
      loadList()
    } else {
      ElMessage.error(res.msg || '导入失败')
    }
  } catch (e) {
    ElMessage.error('导入失败')
  } finally {
    importing.value = false
  }
}

/* ---------- 班级管理 ---------- */
const classVisible = ref(false)
const classSaving = ref(false)
const classForm = reactive({ classId: null, className: '', grade: '' })

function openClassManage() {
  resetClassForm()
  classVisible.value = true
  loadClasses()
}

function resetClassForm() {
  classForm.classId = null
  classForm.className = ''
  classForm.grade = filters.grade || ''
}

function editClass(row) {
  Object.assign(classForm, { classId: row.classId, className: row.className, grade: row.grade || '' })
}

async function saveClass() {
  if (!classForm.className.trim()) {
    ElMessage.warning('请填写班级名称')
    return
  }
  classSaving.value = true
  try {
    const payload = { className: classForm.className.trim(), grade: classForm.grade || '' }
    const res = classForm.classId
        ? await request.put(`/system/classes/${classForm.classId}`, payload)
        : await request.post('/system/classes', payload)
    if (res.code === 200) {
      ElMessage.success(classForm.classId ? '修改成功' : '添加成功')
      resetClassForm()
      await loadClasses()
      loadList()
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    classSaving.value = false
  }
}

async function removeClass(row) {
  try {
    await ElMessageBox.confirm(`确定删除班级「${row.className}」吗？`, '删除班级', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    const res = await request.delete(`/system/classes/${row.classId}`)
    if (res.code === 200) {
      ElMessage.success('已删除')
      await loadClasses()
      loadList()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadClasses()
  loadList()
})
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

.total-tip {
  margin-top: 10px;
  color: #909399;
  font-size: 13px;
}

.import-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0;
}

.import-tools .tip {
  color: #909399;
  font-size: 12px;
}

.import-result {
  margin-top: 12px;
}

.class-form {
  margin-bottom: 4px;
}
</style>
