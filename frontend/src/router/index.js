import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/useUserStore'
import { usePermissionStore } from '@/stores/usePermissionStore'

// 只放不需要登录的静态路由
const constantRoutes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
  { path: '/403',  name: 'NoPermission', component: () => import('@/views/403.vue') }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: constantRoutes
})

const WHITE_LIST = ['/login', '/403']

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const permStore = usePermissionStore()
  const token = userStore.token

  // 1.没有token，跳登录
  if (!token) {
    if (WHITE_LIST.includes(to.path)) return next()
    return next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
  }

  // 2.已经登录访问登录页，跳首页
  if (to.path === '/login') return next('/')

  //3.有token，但是动态路由还没加载（刷新页面/第一次登录）
  if (!permStore.routesLoaded) {
    try {
      const info = await userStore.getInfo()
      const routes = permStore.generateRoutes(info.menus)
      routes.forEach(r => router.addRoute(r))
      // 404兜底，必须addRoute之后添加
      router.addRoute({ path: '/:pathMatch(.*)*', redirect: '/403' })
      permStore.routesLoaded = true
      // 关键：replace:true重新触发一次路由匹配，防止白屏
      return next({ ...to, replace: true })
    } catch (e) {
      userStore.logout()
      permStore.reset()
      return next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    }
  }
  next()
})

export default router