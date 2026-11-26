<template>
  <el-card>
    <template #header>
      <span>权限管理（角色授权 &amp; 权限项维护）</span>
    </template>

    <el-tabs v-model="activeTab">
      <!-- ============ 角色授权 ============ -->
      <el-tab-pane label="角色授权" name="role">
        <el-row :gutter="16">
          <el-col :span="7">
            <el-table :data="roles" border highlight-current-row @row-click="selectRole"
                      v-loading="roleLoading">
              <el-table-column prop="roleName" label="角色" min-width="100"/>
              <el-table-column prop="roleCode" label="角色标识" min-width="110"/>
              <el-table-column label="已授权" width="80">
                <template #default="{ row }">{{ row.permIds?.length || 0 }}</template>
              </el-table-column>
            </el-table>
          </el-col>

          <el-col :span="17">
            <div class="perm-tree-box">
              <div class="tree-toolbar">
                <span>当前角色：<b>{{ currentRole?.roleName || '请选择左侧角色' }}</b></span>
                <div>
                  <el-button size="small" @click="expandAll = !expandAll"
                             :type="expandAll ? 'primary' : 'default'">
                    {{ expandAll ? '折叠' : '展开' }}
                  </el-button>
                  <el-button size="small" @click="checkAll">全选</el-button>
                  <el-button size="small" @click="clearCheck">清空</el-button>
                  <el-button size="small" type="primary" :loading="granting"
                             :disabled="!currentRole" @click="saveGrant">保存授权</el-button>
                </div>
              </div>
              <el-tree
                ref="treeRef"
                :data="permTree"
                show-checkbox
                node-key="permissionId"
                :props="{ label: 'permName', children: 'children' }"
                :default-expand-all="expandAll"
                class="perm-tree"
              >
                <template #default="{ data }">
                  <span class="tree-node">
                    <span>{{ data.permName }}</span>
                    <el-tag size="small" :type="typeTag(data.permType)" class="type-tag">
                      {{ typeLabel(data.permType) }}
                    </el-tag>
                    <span class="perm-code">{{ data.permCode }}</span>
                  </span>
                </template>
              </el-tree>
            </div>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- ============ 权限项维护 ============ -->
      <el-tab-pane label="权限项维护" name="perm">
        <div class="perm-toolbar">
          <el-button type="primary" @click="openCreate">新增权限</el-button>
          <el-button @click="loadAll">刷新</el-button>
        </div>
        <el-table :data="permFlat" border v-loading="permLoading">
          <el-table-column prop="permissionId" label="ID" width="70"/>
          <el-table-column prop="permName" label="名称" min-width="120"/>
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="typeTag(row.permType)">{{ typeLabel(row.permType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="permCode" label="权限标识" min-width="160"/>
          <el-table-column prop="permPath" label="路由路径" min-width="150">
            <template #default="{ row }">{{ row.permPath || '-' }}</template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" width="70"/>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="removePerm(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增 / 编辑权限项 -->
    <el-dialog v-model="formVisible" :title="form.permissionId ? '编辑权限' : '新增权限'" width="500px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="权限名称" required>
          <el-input v-model="form.permName" placeholder="如：实训管理"/>
        </el-form-item>
        <el-form-item label="权限类型" required>
          <el-select v-model="form.permType" style="width:100%">
            <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="上级权限">
          <el-select v-model="form.parentId" clearable placeholder="顶级菜单" style="width:100%">
            <el-option label="（顶级）" :value="0"/>
            <el-option v-for="p in parentOptions" :key="p.permissionId"
                       :label="`${p.permName}（${p.permCode}）`" :value="p.permissionId"/>
          </el-select>
        </el-form-item>
        <el-form-item label="权限标识" required>
          <el-input v-model="form.permCode" placeholder="如 training:list"/>
        </el-form-item>
        <el-form-item label="路由路径">
          <el-input v-model="form.permPath" placeholder="菜单类型填写，如 /training"/>
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="可选"/>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sort" :min="0" :max="999"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePerm">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const TYPE_OPTIONS = [
  { label: '菜单', value: 1 },
  { label: '按钮', value: 2 },
  { label: '接口', value: 3 }
]
const typeLabel = t => TYPE_OPTIONS.find(o => o.value === t)?.label || '-'
const typeTag = t => t === 1 ? '' : t === 2 ? 'warning' : 'info'

const activeTab = ref('role')

/* ---------- 角色授权 ---------- */
const roles = ref([])
const roleLoading = ref(false)
const currentRole = ref(null)
const treeRef = ref(null)
const permTree = ref([])
const expandAll = ref(true)
const granting = ref(false)

async function loadRoles() {
  roleLoading.value = true
  try {
    const res = await request.get('/system/permission/roles')
    if (res.code === 200) roles.value = res.data || []
  } catch (e) {
    ElMessage.error('角色列表加载失败')
  } finally {
    roleLoading.value = false
  }
}

async function loadTree() {
  try {
    const res = await request.get('/system/permission/tree')
    if (res.code === 200) permTree.value = res.data || []
  } catch (e) {
    ElMessage.error('权限树加载失败')
  }
}

function selectRole(row) {
  currentRole.value = row
  //  nextTick 后回显勾选
  setTimeout(() => {
    treeRef.value?.setCheckedKeys(row.permIds || [])
  }, 0)
}

function checkAll() {
  const all = flatten(permTree.value).map(p => p.permissionId)
  treeRef.value?.setCheckedKeys(all)
}

function clearCheck() {
  treeRef.value?.setCheckedKeys([])
}

async function saveGrant() {
  if (!currentRole.value) return
  const checked = treeRef.value?.getCheckedKeys() || []
  const half = treeRef.value?.getHalfCheckedKeys() || []
  const permissionIds = [...checked, ...half]
  granting.value = true
  try {
    const res = await request.put(`/system/permission/roles/${currentRole.value.roleId}`, { permissionIds })
    if (res.code === 200) {
      ElMessage.success('授权已保存')
      await loadRoles()
      currentRole.value.permIds = permissionIds
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    granting.value = false
  }
}

/* ---------- 权限项维护 ---------- */
const permFlat = ref([])
const permLoading = ref(false)
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ permissionId: null, parentId: 0, permName: '', permType: 1, permCode: '', permPath: '', icon: '', sort: 0 })

const parentOptions = computed(() => permFlat.value.filter(p => p.permType === 1))

function flatten(nodes) {
  const out = []
  const walk = list => list.forEach(n => {
    out.push(n)
    if (n.children?.length) walk(n.children)
  })
  walk(nodes)
  return out
}

async function loadAll() {
  permLoading.value = true
  try {
    const res = await request.get('/system/permission/tree')
    if (res.code === 200) {
      permTree.value = res.data || []
      permFlat.value = flatten(permTree.value)
    }
  } catch (e) {
    ElMessage.error('权限列表加载失败')
  } finally {
    permLoading.value = false
  }
}

function openCreate() {
  Object.assign(form, { permissionId: null, parentId: 0, permName: '', permType: 1, permCode: '', permPath: '', icon: '', sort: 0 })
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    permissionId: row.permissionId,
    parentId: row.parentId || 0,
    permName: row.permName,
    permType: row.permType,
    permCode: row.permCode,
    permPath: row.permPath || '',
    icon: row.icon || '',
    sort: row.sort || 0
  })
  formVisible.value = true
}

async function savePerm() {
  if (!form.permName?.trim()) return ElMessage.warning('请填写权限名称')
  if (!form.permCode?.trim()) return ElMessage.warning('请填写权限标识')
  saving.value = true
  try {
    const payload = { ...form, permName: form.permName.trim(), permCode: form.permCode.trim() }
    const res = form.permissionId
      ? await request.put(`/system/permission/${form.permissionId}`, payload)
      : await request.post('/system/permission', payload)
    if (res.code === 200) {
      ElMessage.success(form.permissionId ? '修改成功' : '新增成功')
      formVisible.value = false
      await Promise.all([loadAll(), loadTree()])
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function removePerm(row) {
  try {
    await ElMessageBox.confirm(`确定删除权限「${row.permName}（${row.permCode}）」吗？`, '删除权限', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    const res = await request.delete(`/system/permission/${row.permissionId}`)
    if (res.code === 200) {
      ElMessage.success('已删除')
      await Promise.all([loadAll(), loadTree()])
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadRoles()
  loadTree()
  loadAll()
})
</script>

<style scoped>
.perm-tree-box {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
}

.tree-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.perm-tree {
  max-height: 460px;
  overflow: auto;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
}

.type-tag {
  flex-shrink: 0;
}

.perm-code {
  color: #909399;
  font-size: 12px;
}

.perm-toolbar {
  margin-bottom: 12px;
}
</style>
