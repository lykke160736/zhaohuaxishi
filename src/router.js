import { createRouter, createWebHistory } from 'vue-router'
import { useStore } from './store'

const routes = [
  // 认证（公开）
  { path: '/login', name: 'login', component: () => import('./views/auth/Auth.vue'), meta: { public: true } },
  { path: '/register', name: 'register', component: () => import('./views/auth/Auth.vue'), meta: { public: true } },

  // 顾客端：仅顾客身份可访问
  { path: '/', name: 'home', component: () => import('./views/customer/Home.vue'), meta: { role: 'customer' } },
  { path: '/location', name: 'location', component: () => import('./views/customer/LocationPicker.vue'), meta: { role: 'customer' } },
  { path: '/store/:id', name: 'store', component: () => import('./views/customer/StoreDetail.vue'), meta: { role: 'customer' } },
  { path: '/batch/:id', name: 'batch', component: () => import('./views/customer/BatchDetail.vue'), meta: { role: 'customer' } },
  { path: '/orders', name: 'orders', component: () => import('./views/customer/Orders.vue'), meta: { role: 'customer' } },
  { path: '/order/:id', name: 'order', component: () => import('./views/customer/OrderDetail.vue'), meta: { role: 'customer' } },
  { path: '/pickup/:id', name: 'pickup', component: () => import('./views/customer/PickupPass.vue'), meta: { role: 'customer' } },
  { path: '/pay/:id', name: 'pay', component: () => import('./views/customer/PayMethod.vue'), meta: { role: 'customer' } },
  { path: '/aftersale/:orderId', name: 'aftersale', component: () => import('./views/customer/AfterSale.vue'), meta: { role: 'customer' } },
  { path: '/me', name: 'me', component: () => import('./views/customer/Me.vue'), meta: { role: 'customer' } },
  { path: '/wallet', name: 'wallet', component: () => import('./views/customer/Wallet.vue'), meta: { role: 'customer' } },

  // 商家端：仅商家身份可访问
  {
    path: '/merchant',
    component: () => import('./views/merchant/Layout.vue'),
    meta: { role: 'merchant' },
    children: [
      { path: '', name: 'm-dashboard', component: () => import('./views/merchant/Dashboard.vue') },
      { path: 'batches', name: 'm-batches', component: () => import('./views/merchant/Batches.vue') },
      { path: 'verify', name: 'm-verify', component: () => import('./views/merchant/Verify.vue') },
      { path: 'orders', name: 'm-orders', component: () => import('./views/merchant/MerchantOrders.vue') },
      { path: 'aftersale', name: 'm-aftersale', component: () => import('./views/merchant/MerchantAfterSale.vue') },
      { path: 'profile', name: 'm-profile', component: () => import('./views/merchant/Profile.vue') }
    ]
  },

  // 平台管理端：仅管理员身份可访问
  {
    path: '/admin',
    component: () => import('./views/admin/Layout.vue'),
    meta: { role: 'admin' },
    children: [
      { path: '', name: 'a-overview', component: () => import('./views/admin/Overview.vue') },
      { path: 'stores', name: 'a-stores', component: () => import('./views/admin/StoreManagement.vue') },
      { path: 'batches', name: 'a-batches', component: () => import('./views/admin/BatchModeration.vue') },
      { path: 'audit', name: 'a-audit', component: () => import('./views/admin/MerchantAudit.vue') },
      { path: 'accounts', name: 'a-accounts', component: () => import('./views/admin/Accounts.vue') },
      { path: 'intervention', name: 'a-intervention', component: () => import('./views/admin/Intervention.vue') },
      { path: 'logs', name: 'a-logs', component: () => import('./views/admin/AuditLogs.vue') }
    ]
  },

  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

// 三端身份隔离：未登录跳登录；身份与目标端不符时回到本端首页，不允许随意跨端切换
router.beforeEach((to) => {
  const store = useStore()
  const logged = store.session.loggedIn

  if (to.meta.public) {
    return logged ? store.homePath : true
  }
  if (!logged) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 子路由的 meta 合并到 to.meta，父路由声明的 role 同样生效
  const need = to.meta.role
  if (need && need !== store.session.role) return store.homePath
  return true
})

export default router
