import { defineStore } from 'pinia'
import request from '@/utils/request'

export const useUserStore = defineStore('user', {
  state() {
    return {
      token: localStorage.getItem('token') || '',
      userId: null,
      username: '',
      realName: '',
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

    async getInfo() {
      const res = await request.get('/auth/getInfo')
      if (res.code !== 200) throw new Error(res.msg)
      const d = res.data
      this.userId = d.userId
      this.username = d.username
      this.realName = d.realName
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
      this.roleId = null
      this.roleCode = ''
      this.roleName = ''
      this.classId = null
      this.permissions = []
      this.roles = []
      this.dataScope = ''
      localStorage.removeItem('token')
    }
  }
})