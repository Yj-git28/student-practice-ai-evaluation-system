import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截
service.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if(token) config.headers.Authorization = `Bearer ${token}`
  return config
})

//响应拦截
service.interceptors.response.use(
  res => {
    return res.data
  },
  err => {
    ElMessage.error('接口请求失败：' + err.message)
    return Promise.reject(err)
  }
)
export default service