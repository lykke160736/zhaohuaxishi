<template>
  <div class="phone">
    <div class="page page-narrow">
      <div style="padding: 26px 0 4px">
        <span class="kicker">MY ACCOUNT</span>
        <h1 class="display" style="font-size: 2rem; margin-top: 6px">{{ store.session.name }}</h1>
        <div class="row" style="margin-top: 8px">
          <span class="mono dim" style="font-size: 0.8rem">{{ store.session.phone }}</span>
          <span class="tag tag-accent">顾客</span>
        </div>
      </div>

      <!-- 个人资料 -->
      <div class="card" style="margin-top: 18px">
        <span class="kicker">个人资料</span>
        <div class="stack" style="gap: 9px; margin-top: 12px; font-size: 0.9rem">
          <div class="row"><span class="tone-2">昵称</span><span>{{ store.session.name }}</span></div>
          <div class="row"><span class="tone-2">登录账号</span><span class="mono">{{ me?.account || store.session.phone }}</span></div>
          <div class="row"><span class="tone-2">手机号</span><span class="mono">{{ me?.mobile || store.session.phone }}</span></div>
          <div class="row"><span class="tone-2">账号状态</span><StatusPill :status="me?.status || 'active'" /></div>
          <div class="row"><span class="tone-2">注册时间</span><span class="mono dim">{{ me ? fmtDT(me.createdAt) : '—' }}</span></div>
        </div>
      </div>

      <!-- 惜食足迹 -->
      <div class="card" style="margin-top: 14px">
        <span class="kicker">惜食足迹</span>
        <div class="stat-grid" style="margin-top: 12px; gap: 10px">
          <div class="stat" style="padding: 12px 14px">
            <span class="kicker" style="font-size: 0.64rem">累计订单</span>
            <div class="mono" style="font-size: 1.3rem">{{ store.myOrders.length }}</div>
          </div>
          <div class="stat" style="padding: 12px 14px">
            <span class="kicker" style="font-size: 0.64rem">已领取</span>
            <div class="mono" style="font-size: 1.3rem">{{ pickedCount }} <span class="dim" style="font-size: 0.72rem">份</span></div>
          </div>
          <div class="stat" style="padding: 12px 14px">
            <span class="kicker" style="font-size: 0.64rem">累计消费</span>
            <div class="mono" style="font-size: 1.3rem; color: var(--accent)">¥{{ fenInt(spent) }}</div>
          </div>
          <div class="stat" style="padding: 12px 14px">
            <span class="kicker" style="font-size: 0.64rem">待取货</span>
            <div class="mono" style="font-size: 1.3rem">{{ toPickup }} <span class="dim" style="font-size: 0.72rem">笔</span></div>
          </div>
        </div>
        <hr class="hr" />
        <div class="row">
          <span class="tone-2" style="font-size: 0.88rem">收藏门店</span>
          <span class="mono">{{ store.myFavorites.length }} 家</span>
        </div>
        <hr class="hr" />
        <div class="row">
          <span class="tone-2" style="font-size: 0.88rem">我的评价</span>
          <span class="mono">{{ myReviewCount }} 条</span>
        </div>
      </div>

      <!-- 常用入口 -->
      <div class="card" style="margin-top: 14px; padding: 6px 20px">
        <div class="row" style="padding: 13px 0; border-bottom: 1px solid var(--line); cursor: pointer" @click="$router.push('/orders')">
          <span style="font-size: 0.9rem">我的订单</span>
          <span class="row" style="gap: 6px">
            <span class="mono dim" style="font-size: 0.8rem">{{ store.myOrders.length }} 笔</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--ink-3)" stroke-width="2">
              <path d="M9 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </span>
        </div>
        <div class="row" style="padding: 13px 0; border-bottom: 1px solid var(--line); cursor: pointer" @click="$router.push('/wallet')">
          <span style="font-size: 0.9rem">积分与优惠券</span>
          <span class="row" style="gap: 6px">
            <span class="mono dim" style="font-size: 0.8rem">{{ store.myWallet.points }} 分 · {{ store.myCoupons.length }} 张券</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--ink-3)" stroke-width="2">
              <path d="M9 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </span>
        </div>
        <div class="row" style="padding: 13px 0; border-bottom: 1px solid var(--line); cursor: pointer" @click="$router.push('/')">
          <span style="font-size: 0.9rem">收藏门店</span>
          <span class="row" style="gap: 6px">
            <span class="mono dim" style="font-size: 0.8rem">{{ store.myFavorites.length }} 家</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--ink-3)" stroke-width="2">
              <path d="M9 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </span>
        </div>
        <div class="row" style="padding: 13px 0">
          <span style="font-size: 0.9rem">浏览位置</span>
          <span style="font-size: 0.85rem; text-align: right; max-width: 60%">{{ store.browseCenter.name }}</span>
        </div>
      </div>

      <button class="btn btn-ghost btn-block" style="margin-top: 18px; color: var(--red); border-color: rgba(176,49,31,.3)" @click="signOut">
        退出登录
      </button>
    </div>
    <BottomNav />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from '../../store'
import { fmtDT, fenInt } from '../../utils'
import BottomNav from '../../components/BottomNav.vue'
import StatusPill from '../../components/StatusPill.vue'

const store = useStore()
const router = useRouter()

const me = computed(() => store.accounts.find((a) => a.id === store.session.userId))
const pickedCount = computed(() => store.myOrders.filter((o) => o.status === 'picked').length)
const toPickup = computed(() => store.myOrders.filter((o) => o.status === 'to_pickup').length)
const spent = computed(() =>
  store.myOrders
    .filter((o) => !['pending_payment', 'closed', 'refunded'].includes(o.status))
    .reduce((n, o) => n + o.amount, 0)
)
const myReviewCount = computed(() => store.reviews.filter((r) => r.userId === store.session.userId).length)

async function signOut() {
  await store.logout()
  router.replace('/login')
}
</script>
