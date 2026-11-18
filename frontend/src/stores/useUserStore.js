import { defineStore } from 'pinia'
import request from '@/utils/request'

export const useUserStore = defineStore('user', {
  state() {
    return {
      token: localStorage.getItem('token') || '',
      userId: null,
      username: '',
      realName: '',
      email: '',
      roleId: null,
      roleCode: '',
      roleName: '',
      classId: null,
      permissions: [],
      roles: [],
      dataScope: ''
    }
  },
  actions: {
    setToken(val) {
      this.token = val
      localStorage.setItem('token', val)
    },

    async login(form) {
      const res = await request.post('/auth/login', form)
      if (res.code !== 200) throw new Error(res.msg)
      this.setToken(res.data.token)
    },

    async sendEmailCode(email) {
      const res = await request.post('/auth/email/send', { email })
      if (res.code !== 200) throw new Error(res.msg)
    },

    async loginByEmail({ email, code }) {
      const res = await request.post('/auth/login/email', { email, code })
      if (res.code !== 200) throw new Error(res.msg)
      this.setToken(res.data.token)
    },

    async sendRegisterCode(email) {
      const res = await request.post('/auth/register/email/send', { email })
      if (res.code !== 200) throw new Error(res.msg)
    },

    async register({ username, email, password, code, roleId, inviteCode }) {
      const res = await request.post('/auth/register', { username, email, password, code, roleId, inviteCode })
      if (res.code !== 200) throw new Error(res.msg)
      this.setToken(res.data.token)
    },

    async resetPassword({ email, code, password }) {
      const res = await request.post('/auth/forgot/reset', { email, code, password })
      if (res.code !== 200) throw new Error(res.msg)
    },

    async getInfo() {
      const res = await request.get('/auth/getInfo')
      if (res.code !== 200) throw new Error(res.msg)
      const d = res.data
      this.userId = d.userId
      this.username = d.username
      this.realName = d.realName
      this.email = d.email
      this.roleId = d.roleId
      this.roleCode = d.roleCode
      this.roleName = d.roleName
      this.classId = d.classId
      this.roles = d.roles || []
      this.permissions = d.permissions || []
      this.dataScope = d.dataScope
      return d
    },

    hasPerm(code) {
      return this.permissions.includes(code)
    },

    logout() {
      this.token = ''
      this.userId = null
      this.username = ''
      this.realName = ''
      this.email = ''
      this.roleId = null
      this.roleCode = ''
      this.roleName = ''
      this.classId = null
      this.permissions = []
      this.roles = []
      this.dataScope = ''
      localStorage.removeItem('token')
    },

    /**
     * 主动退出：先让后端把当前 token 加入黑名单，再清本地状态。
     * 服务端失败也要保证本地退出，返回是否成功吊销。
     */
    async logoutAndRevoke() {
      try {
        if (this.token) {
          await request.post('/auth/logout', null, { skipAuthHandler: true })
        }
        return true
      } catch (e) {
        return false
      } finally {
        this.logout()
      }
    }
  }
})