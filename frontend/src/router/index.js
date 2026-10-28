import { createRouter, createWebHistory } from 'vue-router'

const constantRoutes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/403',
    name: 'NoPermission',
    component: () => import('@/views/403.vue')
  }
]

//异步路由：按角色控制页面访问 meta.roles允许哪些角色进入
const asyncRoutes = [
  {
    path: '/',
    component: () => import('@/components/LayoutMain.vue'),
    children: [
      //学生页面
      {
        path: 'student/task',
        name: 'StuTaskList',
        component: () => import('@/views/layout/student/TaskList.vue'),
        meta: { roles: ['student'] }
      },
      {
        path: 'student/submit',
        name: 'StuSubmit',
        component: () => import('@/views/layout/student/SubmitWork.vue'),
        meta: { roles: ['student'] }
      },
      //教师页面
      {
        path: 'teacher/taskManage',
        name: 'TeaTaskManage',
        component: () => import('@/views/layout/teacher/TaskManage.vue'),
        meta: { roles: ['teacher'] }
      },
      {
        path: 'teacher/review',
        name: 'TeaReview',
        component: () => import('@/views/layout/teacher/WorkReview.vue'),
        meta: { roles: ['teacher'] }
      },
      {
        path: 'teacher/stat',
        name: 'TeaStat',
        component: () => import('@/views/layout/teacher/DataStat.vue'),
        meta: { roles: ['teacher'] }
      },
      //管理员页面
      {
        path: 'admin/user',
        name: 'AdminUser',
        component: () => import('@/views/layout/admin/UserManage.vue'),
        meta: { roles: ['admin'] }
      },
      {
        path: 'admin/config',
        name: 'AdminConfig',
        component: () => import('@/views/layout/admin/SysConfig.vue'),
        meta: { roles: ['admin'] }
      }
    ]
  }
]

//合并静态路由 + 业务路由
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [...constantRoutes, ...asyncRoutes]
})

import { useUserStore } from '@/stores/useUserStore'
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const token = userStore.token
  const role = userStore.role

  //放行登录页
  if (to.path === '/login') {
    return next()
  }
  //没有token强制跳登录
  if (!token) {
    return next('/login')
  }

  //校验角色权限
  if (to.meta?.roles) {
    if (to.meta.roles.includes(role)) {
      next()
    } else {
      next('/403')
    }
  } else {
    next()
  }
})

export default router
