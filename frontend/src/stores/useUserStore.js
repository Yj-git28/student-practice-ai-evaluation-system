import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state() {
    return {
      token: '',
      userInfo: {},
      role: '' // 角色：student / teacher / admin
    }
  },
  actions: {
    setToken(val) {
      this.token = val
      localStorage.setItem('token', val)
    },
    setUserInfo(info) {
      this.userInfo = info
      this.role = info.role
    },
    logout() {
      this.token = ''
      this.userInfo = {}
      this.role = ''
      localStorage.removeItem('token')
    }
  }
})
