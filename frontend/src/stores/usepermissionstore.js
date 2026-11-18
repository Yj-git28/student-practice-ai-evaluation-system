import { defineStore } from 'pinia'

// 收集 views 下所有页面组件，key 形如 /src/views/layout/teacher/TaskManage.vue
const viewModules = import.meta.glob('@/views/**/*.vue')

// permPath → 组件本地映射；后端下发 component 字段前作为兜底
const componentMap = {
  '/training':        () => import('@/views/layout/teacher/TaskManage.vue'),
  '/training/list':   () => import('@/views/layout/student/TaskList.vue'),
  '/training/submit': () => import('@/views/layout/student/SubmitWork.vue'),
  '/ai-check':        () => import('@/views/layout/teacher/WorkReview.vue'),
  '/evaluation':      () => import('@/views/layout/teacher/DataStat.vue'),
  '/system/user':     () => import('@/views/layout/admin/UserManage.vue'),
  '/system/config':   () => import('@/views/layout/admin/SysConfig.vue')
}

const Layout = () => import('@/components/LayoutMain.vue')

/**
 * 解析菜单对应组件，优先级：
 * 1. 后端下发的 component（F8 后端驱动，预留字段）
 * 2. 本地 permPath 映射 componentMap
 * 3. 都匹配不到 → 403 兜底页
 */
function resolveComponent(menu) {
  if (menu.component) {
    const hit = Object.keys(viewModules).find(k =>
      k.endsWith(menu.component + '.vue') || k.endsWith(menu.component)
    )
    if (hit) return viewModules[hit]
  }
  if (componentMap[menu.permPath]) return componentMap[menu.permPath]
  console.warn('[permission]未配置组件映射：', menu.permPath, menu.permName)
  return () => import('@/views/403.vue')
}

export const usePermissionStore = defineStore('permission', {
  state() {
    return {
      routesLoaded: false,
      menus: [],
      dynamicRoutes: []
    }
  },
  actions: {
    generateRoutes(menus) {
      this.menus = menus || []
      const leafRoutes = []

      // 递归把所有带 permPath 的节点都收进路由，避免叶子菜单掉 403
      const walk = (nodes) => {
        nodes.forEach(m => {
          if (m.permPath) leafRoutes.push(this.toRoute(m))
          if (m.children && m.children.length) walk(m.children)
        })
      }
      walk(this.menus)

      const routes = [{
        path: '/',
        name: 'LayoutRoot',
        component: Layout,
        redirect: leafRoutes.length ? leafRoutes[0].path : '/403',
        meta: { dynamic: true },
        children: leafRoutes
      }]
      this.dynamicRoutes = routes
      return routes
    },

    toRoute(menu) {
      return {
        path: menu.permPath,
        name: menu.permCode || ('menu-' + menu.permissionId),
        component: resolveComponent(menu),
        meta: {
          title: menu.permName,
          icon: menu.icon,
          permission: menu.permCode,
          dynamic: true
        }
      }
    },

    reset() {
      this.routesLoaded = false
      this.menus = []
      this.dynamicRoutes = []
    }
  }
})
