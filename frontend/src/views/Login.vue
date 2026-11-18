<template>
  <div style="display:flex;justify-content:center;align-items:center;height:100vh;background:#f5f5f5;">
    <el-card style="width:420px;padding:20px;">
      <h2 style="text-align:center;margin-bottom:24px;">软件实训AI检查评价系统</h2>

      <el-tabs v-model="tab">
        <!-- 邮箱密码登录 -->
        <el-tab-pane label="密码登录" name="pwd">
          <el-form :model="form" label-width="72px" label-position="left" @keyup.enter="handleLogin">
            <el-form-item label="账号">
              <el-input v-model="form.username" placeholder="请输入用户名或邮箱" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="form.password" type="password" show-password />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 邮箱验证码登录 -->
        <el-tab-pane label="验证码登录" name="email">
          <el-form :model="emailForm" label-width="72px" label-position="left" @keyup.enter="handleEmailLogin">
            <el-form-item label="邮箱">
              <el-input v-model="emailForm.email" placeholder="请输入邮箱" />
            </el-form-item>
            <el-form-item label="验证码">
              <div style="display:flex;gap:8px;width:100%">
                <el-input v-model="emailForm.code" placeholder="6 位验证码" />
                <el-button :disabled="countdown > 0" @click="handleSendCode">
                  {{ countdown > 0 ? countdown + 's' : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 注册 -->
        <el-tab-pane label="注册" name="register">
          <el-form :model="regForm" label-width="72px" label-position="left" @keyup.enter="handleRegister">
            <el-form-item label="用户名">
              <el-input v-model="regForm.username" placeholder="请输入用户名" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="regForm.email" placeholder="请输入邮箱" />
            </el-form-item>
            <el-form-item label="身份">
              <el-radio-group v-model="regForm.roleId">
                <el-radio :value="2">学生</el-radio>
                <el-radio :value="1">教师</el-radio>
                <el-radio :value="3">管理员</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="regForm.roleId === 3" label="邀请码">
              <el-input v-model="regForm.inviteCode" placeholder="请输入管理员邀请码" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="regForm.password" type="password" show-password placeholder="至少 6 位" />
            </el-form-item>
            <el-form-item label="验证码">
              <div style="display:flex;gap:8px;width:100%">
                <el-input v-model="regForm.code" placeholder="6 位验证码" />
                <el-button :disabled="regCountdown > 0" @click="handleSendRegCode">
                  {{ regCountdown > 0 ? regCountdown + 's' : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <el-button type="primary" style="width:100%;margin-top:8px" :loading="loading"
                 @click="onSubmit">
        {{ tab === 'register' ? '注册并登录' : '登录系统' }}
      </el-button>

      <div v-if="tab !== 'register'" style="text-align:right;margin-top:12px;">
        <el-link type="primary" :underline="false" @click="showForgot = true">忘记密码？</el-link>
      </div>
    </el-card>

    <!-- 忘记密码弹窗 -->
    <el-dialog v-model="showForgot" title="重置密码" width="400px" @close="resetForgotForm">
      <el-form :model="forgotForm" label-width="72px" label-position="left">
        <el-form-item label="邮箱">
          <el-input v-model="forgotForm.email" placeholder="请输入注册邮箱" />
        </el-form-item>
        <el-form-item label="验证码">
          <div style="display:flex;gap:8px;width:100%">
            <el-input v-model="forgotForm.code" placeholder="6 位验证码" />
            <el-button :disabled="forgotCountdown > 0" @click="handleSendForgotCode">
              {{ forgotCountdown > 0 ? forgotCountdown + 's' : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="forgotForm.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForgot = false">取消</el-button>
        <el-button type="primary" :loading="forgotLoading" @click="handleResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/useUserStore'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const tab = ref('pwd')
const loading = ref(false)
const countdown = ref(0)
const regCountdown = ref(0)
const form = ref({ username: '', password: '' })
const emailForm = ref({ email: '', code: '' })
const regForm = ref({ username: '', email: '', password: '', code: '', roleId: 2, inviteCode: '' })

const EMAIL_RE = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/

async function afterLogin() {
  const redirect = route.query.redirect || '/'
  await router.replace(redirect)
}

const handleLogin = async () => {
  if (!form.value.username || !form.value.password) {
    return ElMessage.warning('请输入邮箱和密码')
  }
  loading.value = true
  try {
    await userStore.login(form.value)
    await afterLogin()
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}

const handleSendCode = async () => {
  if (!EMAIL_RE.test(emailForm.value.email)) {
    return ElMessage.warning('请输入正确的邮箱')
  }
  try {
    await userStore.sendEmailCode(emailForm.value.email)
    ElMessage.success('验证码已发送，请查收邮箱')
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    ElMessage.error(e.message || '发送失败')
  }
}

const handleEmailLogin = async () => {
  if (!emailForm.value.email || !emailForm.value.code) {
    return ElMessage.warning('请输入邮箱和验证码')
  }
  loading.value = true
  try {
    await userStore.loginByEmail(emailForm.value)
    await afterLogin()
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}

const handleSendRegCode = async () => {
  if (!EMAIL_RE.test(regForm.value.email)) {
    return ElMessage.warning('请输入正确的邮箱')
  }
  try {
    await userStore.sendRegisterCode(regForm.value.email)
    ElMessage.success('验证码已发送，请查收邮箱')
    regCountdown.value = 60
    const timer = setInterval(() => {
      regCountdown.value--
      if (regCountdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    ElMessage.error(e.message || '发送失败')
  }
}

const handleRegister = async () => {
  if (!regForm.value.username || regForm.value.username.length < 2) {
    return ElMessage.warning('请输入用户名（至少 2 位）')
  }
  if (!EMAIL_RE.test(regForm.value.email)) {
    return ElMessage.warning('请输入正确的邮箱')
  }
  if (!regForm.value.password || regForm.value.password.length < 6) {
    return ElMessage.warning('密码至少 6 位')
  }
  if (!regForm.value.code) {
    return ElMessage.warning('请输入验证码')
  }
  if (regForm.value.roleId === 3 && !regForm.value.inviteCode) {
    return ElMessage.warning('请输入管理员邀请码')
  }
  loading.value = true
  try {
    await userStore.register(regForm.value)
    ElMessage.success('注册成功，已自动登录')
    await afterLogin()
  } catch (e) {
    ElMessage.error(e.message || '注册失败')
  } finally {
    loading.value = false
  }
}

const onSubmit = () => {
  if (tab.value === 'pwd') handleLogin()
  else if (tab.value === 'email') handleEmailLogin()
  else handleRegister()
}

// ===== 忘记密码 =====
const showForgot = ref(false)
const forgotLoading = ref(false)
const forgotCountdown = ref(0)
const forgotForm = ref({ email: '', code: '', password: '' })

const resetForgotForm = () => {
  forgotForm.value = { email: '', code: '', password: '' }
  forgotCountdown.value = 0
}

const handleSendForgotCode = async () => {
  if (!EMAIL_RE.test(forgotForm.value.email)) {
    return ElMessage.warning('请输入正确的邮箱')
  }
  try {
    await userStore.sendEmailCode(forgotForm.value.email)
    ElMessage.success('验证码已发送，请查收邮箱')
    forgotCountdown.value = 60
    const timer = setInterval(() => {
      forgotCountdown.value--
      if (forgotCountdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    ElMessage.error(e.message || '发送失败')
  }
}

const handleResetPassword = async () => {
  if (!EMAIL_RE.test(forgotForm.value.email)) {
    return ElMessage.warning('请输入正确的邮箱')
  }
  if (!forgotForm.value.code) return ElMessage.warning('请输入验证码')
  if (!forgotForm.value.password || forgotForm.value.password.length < 6) {
    return ElMessage.warning('新密码至少 6 位')
  }
  forgotLoading.value = true
  try {
    await userStore.resetPassword(forgotForm.value)
    ElMessage.success('密码重置成功，请使用新密码登录')
    showForgot.value = false
    tab.value = 'pwd'
  } catch (e) {
    ElMessage.error(e.message || '重置失败')
  } finally {
    forgotLoading.value = false
  }
}
</script>
