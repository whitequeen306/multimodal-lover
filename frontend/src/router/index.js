import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/pages/LoginPage.vue')
  },
  {
    path: '/characters',
    name: 'Characters',
    component: () => import('@/pages/CharacterListPage.vue'),
    meta: { auth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/pages/ProfilePage.vue'),
    meta: { auth: true }
  },
  {
    path: '/chat/:id',
    name: 'Chat',
    component: () => import('@/pages/ChatPage.vue'),
    meta: { auth: true }
  },
  {
    path: '/',
    redirect: '/characters'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('vlover-token')
  if (to.meta.auth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/characters')
  } else {
    next()
  }
})

export default router
