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

// 统一清理动态路由：根路由删除时子路由一并移除，再删兜底路由
export function resetDynamicRoutes() {
  if (router.hasRoute('CatchAll')) router.removeRoute('CatchAll')
  if (router.hasRoute('LayoutRoot')) router.removeRoute('LayoutRoot')
  // 兼容历史遗留的无名动态路由
  router.getRoutes().forEach(r => {
    if (r.meta?.dynamic && r.name) router.removeRoute(r.name)
  })
}

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

  // 3.有token，但是动态路由还没加载（刷新页面/第一次登录）
  if (!permStore.routesLoaded) {
    try {
      const info = await userStore.getInfo()
      const routes = permStore.generateRoutes(info.menus)
      routes.forEach(r => router.addRoute(r))
      // 404兜底，必须addRoute之后添加
      router.addRoute({ path: '/:pathMatch(.*)*', name: 'CatchAll', redirect: '/403' })
      permStore.routesLoaded = true

      // 关键修复：登录后或停留在 /、/403 时，直接去第一个有权限的菜单
      // 否则 next({ ...to }) 会把 /403 原样重放，形成"进 403 就出不来"的死循环
      const firstPath = permStore.menus.length ? permStore.menus[0].permPath : ''
      if (to.path === '/' || to.path === '/403') {
        return next({ path: firstPath || '/403', replace: true })
      }

      // 其它地址：replace:true 重新匹配一次，防止白屏
      return next({ ...to, replace: true })
    } catch (e) {
      userStore.logout()
      permStore.reset()
      resetDynamicRoutes()
      return next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    }
  }

  next()
})

export default router