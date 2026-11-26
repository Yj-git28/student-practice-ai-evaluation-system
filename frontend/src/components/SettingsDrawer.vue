<template>
  <el-drawer :model-value="modelValue" title="设置" size="380px"
             @update:model-value="$emit('update:modelValue', $event)" @open="onOpen" @closed="stopTicker">
    <div class="section-title">账号信息</div>
    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="姓名">{{ userStore.realName }}</el-descriptions-item>
      <el-descriptions-item label="账号">{{ userStore.username }}</el-descriptions-item>
      <el-descriptions-item label="角色">{{ userStore.roleName }}</el-descriptions-item>
      <el-descriptions-item label="邮箱">{{ userStore.email || '未绑定' }}</el-descriptions-item>
    </el-descriptions>

    <div class="section-title">邮箱换绑</div>
    <el-form label-width="70px" size="default">
      <el-form-item label="新邮箱">
        <el-input v-model="newEmail" placeholder="输入要绑定的新邮箱" clearable/>
      </el-form-item>
      <el-form-item label="验证码">
        <div class="code-row">
          <el-input v-model="code" placeholder="6 位验证码" maxlength="6"/>
          <el-button :disabled="countdown > 0" @click="sendCode">
            {{ countdown > 0 ? countdown + 's' : '发送验证码' }}
          </el-button>
        </div>
      </el-form-item>
      <el-form-item label="人机验证">
        <CaptchaBox ref="captchaBox" v-model="captcha"/>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="binding" @click="submitBind">确认换绑</el-button>
      </el-form-item>
    </el-form>

    <template v-if="isAdmin">
      <div class="section-title">管理员邀请码</div>
      <div class="invite-row">
        <span class="invite-code">{{ inviteCode || '加载中…' }}</span>
        <el-button link type="primary" @click="copyCode">复制</el-button>
        <el-button link type="danger" @click="resetCode">重置</el-button>
      </div>
      <div class="hint">一次性邀请码：有效 2 小时（{{ remainingText }}），注册成功即作废并换新；学生、教师注册不需要</div>
    </template>

    <el-divider/>
    <el-button type="danger" plain style="width:100%" @click="$emit('logout')">退出登录</el-button>
  </el-drawer>
</template>

<script setup>
import { computed, ref, onBeforeUnmount } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/useUserStore'
import CaptchaBox from '@/components/CaptchaBox.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
defineEmits(['update:modelValue', 'logout'])

const userStore = useUserStore()
const isAdmin = computed(() => userStore.roleCode === 'ADMIN')

const newEmail = ref('')
const code = ref('')
const binding = ref(false)
const countdown = ref(0)
/** 人机验证：{ captchaId, answer } */
const captcha = ref({ captchaId: '', answer: '' })
const captchaBox = ref(null)
let timer = null

const inviteCode = ref('')
const remaining = ref(0)
let tickTimer = null

const remainingText = computed(() => {
  if (remaining.value <= 0) return '已过期'
  const h = Math.floor(remaining.value / 3600)
  const m = Math.floor((remaining.value % 3600) / 60)
  const s = remaining.value % 60
  return h > 0 ? `剩余 ${h} 小时 ${m} 分` : `剩余 ${m} 分 ${s} 秒`
})

function startTicker() {
  stopTicker()
  tickTimer = setInterval(() => {
    remaining.value--
    if (remaining.value <= 0) {
      stopTicker()
      loadInviteCode() // 过期后服务端会自动下发新码
    }
  }, 1000)
}

function stopTicker() {
  clearInterval(tickTimer)
  tickTimer = null
}

function onOpen() {
  newEmail.value = ''
  code.value = ''
  stopTicker()
  if (isAdmin.value) loadInviteCode()
}

async function sendCode() {
  if (!/^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(newEmail.value)) {
    ElMessage.warning('请输入正确的新邮箱')
    return
  }
  if (!captcha.value.captchaId || !captcha.value.answer) {
    ElMessage.warning('请先填写右侧图形验证码')
    return
  }
  try {
    const res = await request.post('/auth/bind-email/send', {
      email: newEmail.value,
      captchaId: captcha.value.captchaId,
      captchaAnswer: captcha.value.answer
    })
    if (res.code !== 200) {
      ElMessage.error(res.msg || '发送失败')
      captchaBox.value?.refresh()
      return
    }
    ElMessage.success('验证码已发送到新邮箱')
    startCountdown()
  } catch (e) {
    ElMessage.error('发送失败')
    captchaBox.value?.refresh()
  }
}

function startCountdown() {
  countdown.value = 60
  clearInterval(timer)
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}

async function submitBind() {
  if (!newEmail.value || !code.value) {
    ElMessage.warning('请填写新邮箱和验证码')
    return
  }
  binding.value = true
  try {
    const res = await request.post('/auth/bind-email', { email: newEmail.value, code: code.value })
    if (res.code !== 200) {
      ElMessage.error(res.msg || '换绑失败')
      return
    }
    ElMessage.success('邮箱换绑成功')
    await userStore.getInfo()
    newEmail.value = ''
    code.value = ''
  } catch (e) {
    ElMessage.error('换绑失败')
  } finally {
    binding.value = false
  }
}

async function loadInviteCode() {
  try {
    const res = await request.get('/system/admin-invite-code')
    if (res.code === 200) {
      inviteCode.value = res.data?.inviteCode || ''
      remaining.value = Number(res.data?.remainingSeconds || 0)
      startTicker()
    }
  } catch (e) {
    // 非管理员或接口异常时静默
  }
}

async function copyCode() {
  try {
    await navigator.clipboard.writeText(inviteCode.value)
    ElMessage.success('邀请码已复制')
  } catch (e) {
    ElMessage.warning('复制失败，请手动选中复制')
  }
}

async function resetCode() {
  try {
    await ElMessageBox.confirm('重置后旧邀请码立即失效，确定重置？', '重置邀请码', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    const res = await request.post('/system/admin-invite-code/reset')
    if (res.code === 200) {
      inviteCode.value = res.data?.inviteCode || ''
      remaining.value = Number(res.data?.remainingSeconds || 0)
      startTicker()
      ElMessage.success('邀请码已重置，重新计时 2 小时')
    } else {
      ElMessage.error(res.msg || '重置失败')
    }
  } catch (e) {
    ElMessage.error('重置失败')
  }
}

onBeforeUnmount(() => {
  clearInterval(timer)
  stopTicker()
})
</script>

<style scoped>
.section-title {
  font-weight: 600;
  margin: 18px 0 10px;
  color: #303133;
}

.section-title:first-child {
  margin-top: 0;
}

.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.invite-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.invite-code {
  font-weight: 600;
  letter-spacing: 1px;
  font-size: 15px;
}

.hint {
  color: #909399;
  font-size: 12px;
  margin-top: 6px;
}
</style>
