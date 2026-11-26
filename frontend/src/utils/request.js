import axios from 'axios'
import { ElMessage } from 'element-plus'
import router, { resetDynamicRoutes } from '@/router'
import { useUserStore } from '@/stores/useUserStore'
import { usePermissionStore } from '@/stores/usePermissionStore'

const service = axios.create({ baseURL: '/api', timeout: 30000 })

service.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

service.interceptors.response.use(
  res => res.data,
  err => {
    const status = err.response?.status
    if (status === 401) {
      // 主动注销等场景不需要再提示“登录已过期”，由调用方处理
      if (err.config?.skipAuthHandler) return Promise.reject(err)
      const userStore = useUserStore()
      const permStore = usePermissionStore()
      userStore.logout()
      permStore.reset()
      resetDynamicRoutes()
      ElMessage.error('登录已过期，请重新登录')
      router.replace('/login')
    } else if (status === 403) {
      ElMessage.error(err.response?.data?.msg || '没有操作权限')
    } else if (status === 500) {
      // 服务端错误：展示后端返回的具体 msg，而不是笼统当成登录过期
      ElMessage.error(err.response?.data?.msg || '服务器开小差了，请稍后重试')
    } else {
      ElMessage.error(err.response?.data?.msg || '接口请求失败，请稍后重试')
    }
    return Promise.reject(err)
  }
)

export default service
