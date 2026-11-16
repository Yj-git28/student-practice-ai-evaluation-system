import { defineStore } from 'pinia'

// 后端perm_path映射本地vue页面组件，新增菜单必须在这里添加映射
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
      const children = this.menus.map(m => this.toRoute(m))
      const routes = [{
        path: '/',
        component: Layout,
        redirect: children.length ? children[0].path : '/403',
        meta: { dynamic: true },
        children
      }]
      this.dynamicRoutes = routes
      return routes
    },

    toRoute(menu) {
      const comp = componentMap[menu.permPath]
      if (!comp) {
        console.warn('[permission]未配置组件映射：', menu.permPath, menu.permName)
      }
      return {
        path: menu.permPath,
        name: menu.permCode || ('menu-' + menu.permissionId),
        component: comp || (() => import('@/views/403.vue')),
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
