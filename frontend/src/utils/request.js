import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/stores/useUserStore'

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
      const userStore = useUserStore()
      userStore.logout()
      ElMessage.error('登录已过期，请重新登录')
      router.replace('/login')
    } else if (status === 403) {
      ElMessage.error(err.response?.data?.msg || '没有操作权限')
    } else {
      ElMessage.error(err.response?.data?.msg || '接口请求失败')
    }
    return Promise.reject(err)
  }
)

export default service
