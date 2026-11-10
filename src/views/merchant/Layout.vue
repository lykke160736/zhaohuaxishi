<template>
  <div class="desk">
    <aside class="side">
      <div class="side-brand">朝花惜食<span style="color: var(--accent)">·</span>商家</div>
      <router-link v-for="l in links" :key="l.to" :to="l.to" class="side-link" :class="{ on: $route.path === l.to }">
        {{ l.label }}
        <span class="n" v-if="l.n">{{ l.n() }}</span>
      </router-link>
      <div style="margin-top: auto; padding-top: 18px; border-top: 1px solid var(--line)">
        <NoticeBell />
        <div class="who">
          <span class="kicker">当前商家</span>
          <strong>{{ store.session.name }}</strong>
        </div>
        <button class="side-link" style="width: 100%" @click="signOut">退出登录</button>
      </div>
    </aside>
    <main class="main">
      <!-- 未通过审核的商家不进入业务功能，避免越权操作 -->
      <div v-if="!s" class="card" style="max-width: 520px; margin-top: 40px">
        <span class="kicker">入驻审核中</span>
        <h2 class="display" style="font-size: 1.6rem; margin: 8px 0">门店尚未开通</h2>
        <p class="tone-2" style="font-size: 0.9rem">
          你的入驻申请已提交，平台审核通过后将自动开放盲盒发布、核销与订单管理权限。审核期间无法进行任何售卖操作。
        </p>
        <div class="notice" style="margin-top: 16px">
          申请门店：{{ myApplication?.storeName || '—' }} · 状态：
          {{ myApplication?.status === 'rejected' ? '已退回，请按审核意见补充材料' : '等待平台审核' }}
        </div>
        <button class="btn btn-ghost btn-sm" style="margin-top: 16px" @click="signOut">退出登录</button>
      </div>
      <template v-else>
        <div v-if="s.operationStatus === 'suspended'" class="notice notice-warn" style="margin-bottom: 20px">
          <strong>门店已暂停经营</strong>：{{ s.operationReason }}。暂不可新增交易、发布批次或追加库存；已有订单仍需履约，退款及售后可正常处理。
        </div>
        <router-view />
      </template>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from '../../store'
import NoticeBell from '../../components/NoticeBell.vue'

const store = useStore()
const router = useRouter()

const s = computed(() => store.merchantStore)
const pendingCount = computed(() => store.pendingAftersales.length)
const toPickup = computed(() => store.merchantOrders.filter((o) => o.status === 'to_pickup').length)
const myApplication = computed(() =>
  store.applications.find((a) => a.accountId === store.session.userId)
)

const links = [
  { to: '/merchant', label: '工作台' },
  { to: '/merchant/batches', label: '盲盒批次' },
  { to: '/merchant/verify', label: '到店核销', n: () => toPickup.value },
  { to: '/merchant/orders', label: '订单管理' },
  { to: '/merchant/aftersale', label: '售后处理', n: () => pendingCount.value || '' },
  { to: '/merchant/profile', label: '门店资料' }
]

async function signOut() {
  await store.logout()
  router.replace('/login')
}
</script>
