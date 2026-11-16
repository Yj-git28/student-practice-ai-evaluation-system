import { createApp } from 'vue'
import App from './App.vue'
import { createPinia } from 'pinia'
import router from './router'
import ElementPlus from 'element-plus'
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

app.mount('#app')
