import { defineStore } from 'pinia'
import { haversineKm } from './utils.js'
import { api, clearToken, getToken, setToken } from './api.js'

// 前端只保存服务端下发的业务快照与本地时钟；订单、库存、积分等一律以服务端为准
const GUEST_SESSION = { loggedIn: false, role: null, userId: null, name: '', phone: '' }
const DEFAULT_CENTER = {
  name: '衡中教育综合体',
  city: '厦门市',
  lat: 24.551,
  lng: 118.054,
  source: 'default'
}

export const useStore = defineStore('main', {
  state: () => ({
    clock: Date.now(),
    session: { ...GUEST_SESSION },
    merchant: { accountId: 'm1' },
    browseCenter: { ...DEFAULT_CENTER },
    locationDenied: false,
    categories: ['全部', '面包', '炸鸡', '自助餐', '糕点', '便当', '轻食', '水果', '饮品'],
    stores: [],
    batches: [],
    orders: [],
    reviews: [],
    favorites: {},
    notices: [],
    wallet: {},
    refunds: [],
    aftersales: [],
    drafts: [],
    applications: [],
    accounts: [],
    logs: [],
    storageError: null,
    cacheCorrupted: false
  }),

  getters: {
    // 业务时钟（每秒由页面推进），倒计时仅用于展示，超时判定在服务端完成
    nowMs: (s) => s.clock,

    // 门店附带与浏览中心的地理直线距离，仅返回 15 km 服务范围内门店
    storesNearby(s) {
      return s.stores
        .filter((st) => st.auditStatus === 'approved' && st.operationStatus !== 'suspended')
        .map((st) => ({ ...st, km: haversineKm(s.browseCenter, st) }))
        .filter((st) => st.km <= 15)
        .sort((a, b) => a.km - b.km)
    },

    // 批次实时状态推导：截止优先于停售展示
    batchView(s) {
      return (b) => {
        const ended = s.clock >= new Date(b.pickupEnd).getTime()
        const available = b.total - b.sold - b.reserved
        let status = b.status
        if (ended) status = 'ended'
        else if (b.status === 'on_sale' && s.stores.find(st => st.id === b.storeId)?.operationStatus === 'suspended') status = 'suspended'
        else if (status === 'on_sale' && available <= 0) status = 'sold_out'
        return { ...b, available, status }
      }
    },

    storeBatches(s) {
      return (storeId) =>
        s.batches.filter((b) => b.storeId === storeId).map((b) => this.batchView(b))
    },

    myOrders(s) {
      return s.orders
        .filter((o) => o.userId === s.session.userId)
        .slice()
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    },

    // 订单归属校验：顾客只能访问自己名下的订单，非本人或不存在时返回 undefined
    myOrder(s) {
      return (id) => s.orders.find((o) => o.id === id && o.userId === s.session.userId)
    },

    orderRefund: (s) => (orderId) => s.refunds.find((r) => r.orderId === orderId),
    orderAftersale: (s) => (orderId) =>
      s.aftersales.slice().reverse().find((a) => a.orderId === orderId),

    // 收藏与评价
    myFavorites: (s) => s.favorites[s.session.userId] || [],
    isFavorite: (s) => (storeId) => (s.favorites[s.session.userId] || []).includes(storeId),
    storeReviews: (s) => (storeId) =>
      s.reviews
        .filter((r) => r.storeId === storeId)
        .slice()
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    storeRating: (s) => (storeId) => {
      const rs = s.reviews.filter((r) => r.storeId === storeId)
      return rs.length ? { avg: rs.reduce((n, r) => n + r.rating, 0) / rs.length, count: rs.length } : null
    },

    // 消息通知（按当前账号投递，倒序）
    myNotices: (s) =>
      s.notices
        .filter((n) => n.to === s.session.userId)
        .slice()
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    unreadCount: (s) => s.notices.filter((n) => n.to === s.session.userId && !n.read).length,

    // 惜食积分与优惠券（按账号隔离）
    myWallet: (s) => s.wallet[s.session.userId] || { points: 0, coupons: [] },
    myCoupons: (s) => (s.wallet[s.session.userId] || { coupons: [] }).coupons.filter((c) => !c.used),

    merchantStore(s) {
      return s.stores.find((st) => st.ownerId === s.merchant.accountId)
    },

    merchantOrders(s) {
      const st = s.stores.find((x) => x.ownerId === s.merchant.accountId)
      if (!st) return []
      return s.orders
        .filter((o) => o.storeId === st.id)
        .slice()
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    },

    pendingAftersales(s) {
      const st = s.stores.find((x) => x.ownerId === s.merchant.accountId)
      if (!st) return []
      return s.aftersales.filter((a) => {
        const o = s.orders.find((x) => x.id === a.orderId)
        return o && o.storeId === st.id && a.status === 'merchant_review'
      })
    },

    pendingApplications: (s) => s.applications.filter((a) => a.status === 'pending'),
    platformQueue: (s) => s.aftersales.filter((a) => a.status === 'platform_review'),

    // 三端各自的落地首页，路由守卫与登录跳转共用
    homePath: (s) =>
      ({ customer: '/', merchant: '/merchant', admin: '/admin' }[s.session.role] || '/login')
  },

  actions: {
    // 服务端下发的完整快照直接覆盖本地镜像
    _apply(state) {
      if (!state) return
      for (const key of Object.keys(state)) this[key] = state[key]
    },

    _after(r) {
      if (r?.state) this._apply(r.state)
      return r
    },

    // 启动时恢复会话：会话有效性由服务端校验，本地只有令牌
    async bootstrap() {
      this.clock = Date.now()
      let r = await api.get('/bootstrap')
      // 令牌已失效（被清空）后，再取一次访客快照，避免停留在未初始化状态
      if (!r.ok && r.status === 401) r = await api.get('/bootstrap')
      if (r.ok) {
        this.storageError = null
        this._apply(r.state)
      } else {
        this.storageError = r.msg || '无法获取服务端数据'
      }
      return r.ok
    },

    tick() {
      // 仅推进展示用时钟；支付超时、取货超时等由服务端后台任务处理
      this.clock = Date.now()
    },

    // 缓存/令牌异常时的恢复入口：清掉令牌后重新加载
    resetLocalCache() {
      clearToken()
      location.reload()
    },

    // ---------------- 认证 ----------------
    async login({ account, password, role }) {
      const r = await api.post('/auth/login', { account, password, role })
      if (r.ok) {
        setToken(r.token)
        this._apply(r.state)
      }
      return r
    },

    async register(payload) {
      const r = await api.post('/auth/register', payload)
      if (r.ok) {
        setToken(r.token)
        this._apply(r.state)
      }
      return r
    },

    async logout() {
      await api.post('/auth/logout')
      clearToken()
      await this.bootstrap()
    },

    // ---------------- 顾客：选址 ----------------
    async setBrowseCenter(place, source = 'manual') {
      const r = await api.post('/location', { place, source })
      return this._after(r)
    },

    // ---------------- 顾客：下单与支付 ----------------
    async placeOrder(batchId) {
      const r = await api.post('/orders', { batchId })
      return this._after(r)
    },

    async cancelUnpaid(orderId) {
      const r = await api.post(`/orders/${orderId}/cancel`)
      return this._after(r)
    },

    // 只创建支付意图，真实到账由服务端渠道回调确认
    async payOrder(orderId, couponId = null, channel = 'wechat') {
      const r = await api.post(`/orders/${orderId}/pay`, { couponId, channel })
      return this._after(r)
    },

    // 轮询支付结果；服务端在渠道回调完成后把订单置为待取货
    async waitPayment(paymentId, timeoutMs = 30000) {
      const deadline = Date.now() + timeoutMs
      while (Date.now() < deadline) {
        const r = await api.get(`/payments/${paymentId}`)
        if (!r.ok) return { status: 'failed', failureReason: r.msg }
        this._apply(r.state)
        if (r.payment.status !== 'pending') return r.payment
        await new Promise((resolve) => setTimeout(resolve, 900))
      }
      return { status: 'pending' }
    },

    // ---------------- 顾客：收藏 / 通知 / 积分 ----------------
    async toggleFavorite(storeId) {
      const r = await api.post(`/favorites/${storeId}`)
      return this._after(r)
    },

    async markNoticeRead(id) {
      const r = await api.post(`/notices/${id}/read`)
      return this._after(r)
    },

    async markAllNoticeRead() {
      const r = await api.post('/notices/read-all')
      return this._after(r)
    },

    async redeemCoupon() {
      const r = await api.post('/wallet/redeem')
      return this._after(r)
    },

    async addReview(orderId, { rating, text } = {}) {
      const r = await api.post(`/orders/${orderId}/review`, { rating, text })
      return this._after(r)
    },

    // ---------------- 顾客：退款与售后 ----------------
    async applyRefund(orderId) {
      const r = await api.post(`/orders/${orderId}/refund`)
      return this._after(r)
    },

    async submitAftersale(orderId, reason, evidence) {
      const r = await api.post(`/orders/${orderId}/aftersale`, { reason, evidence })
      return this._after(r)
    },

    async escalate(aftersaleId) {
      const r = await api.post(`/aftersales/${aftersaleId}/escalate`)
      return this._after(r)
    },

    // ---------------- 商家 ----------------
    async saveDraft(draft) {
      const r = await api.post('/drafts', draft)
      return this._after(r)
    },

    async publishDraft(draftId) {
      const r = await api.post(`/drafts/${draftId}/publish`)
      return this._after(r)
    },

    async publishBatch(batch) {
      return this._after(await api.post('/batches', batch))
    },

    async addStock(batchId, n) {
      const r = await api.post(`/batches/${batchId}/stock`, { n })
      return this._after(r)
    },

    async stopBatch(batchId, reason) {
      const r = await api.post(`/batches/${batchId}/stop`, { reason })
      return this._after(r)
    },

    async verifyCode(code) {
      const r = await api.post('/verify', { code })
      return this._after(r)
    },

    async merchantCancel(orderId, reason) {
      const r = await api.post(`/merchant/orders/${orderId}/cancel`, { reason })
      return this._after(r)
    },

    async handleAftersale(aftersaleId, agree, reply) {
      const r = await api.post(`/aftersales/${aftersaleId}/handle`, { agree, reply })
      return this._after(r)
    },

    async updateProfile(data) {
      const r = await api.post('/merchant/profile', data)
      return this._after(r)
    },

    // ---------------- 平台 ----------------
    async resolveAftersale(aftersaleId, refund, note) {
      const r = await api.post(`/aftersales/${aftersaleId}/resolve`, { refund, note })
      return this._after(r)
    },

    async auditApplication(appId, pass, note) {
      const r = await api.post(`/applications/${appId}/audit`, { pass, note })
      return this._after(r)
    },

    async setStoreOperation(id, status, reason) {
      return this._after(await api.post(`/admin/stores/${id}/operation`, { status, reason }))
    },
    async adminStopBatch(id, reason) {
      return this._after(await api.post(`/admin/batches/${id}/stop`, { reason }))
    },
    async toggleAccount(accId) {
      const r = await api.post(`/accounts/${accId}/toggle`)
      return this._after(r)
    },

    async refreshDemoWindow() {
      const r = await api.post('/demo/refresh-window')
      return this._after(r)
    },

    // 供调试：确认本地是否持有令牌
    hasToken() {
      return !!getToken()
    }
  }
})
