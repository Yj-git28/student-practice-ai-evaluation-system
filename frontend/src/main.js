import { createApp } from 'vue'
import App from './App.vue'
import { createPinia } from 'pinia'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

const app = createApp(App)
const pinia = createPinia()

// 必须先注册pinia，再注册router
app.use(pinia)
app.use(router)
app.use(ElementPlus)

app.mount('#app')
import hasPerm from '@/directives/hasPerm'
app.directive('has-perm', hasPerm)
