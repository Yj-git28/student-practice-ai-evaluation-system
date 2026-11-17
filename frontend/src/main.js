import { createApp } from 'vue'
import App from './App.vue'
import { createPinia } from 'pinia'
import router from './router'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import hasPerm from '@/directives/hasPerm'

const app = createApp(App)
const pinia = createPinia()

// 必须先注册pinia，再注册router
app.use(pinia)
app.use(router)
app.use(ElementPlus)
// 自定义权限指令必须在挂载前注册，否则首屏渲染解析不到
app.directive('has-perm', hasPerm)

// 全局错误边界：统一捕获组件渲染/生命周期/事件里的前端异常，避免白屏
app.config.errorHandler = (err, instance, info) => {
  console.error('[全局异常]', info, err)
  ElMessage.error('页面出现异常，请刷新重试')
}

app.mount('#app')
