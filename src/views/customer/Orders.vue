<template>
  <div class="phone">
    <div class="page">
      <div style="padding: 26px 0 4px">
        <span class="kicker">MY ORDERS</span>
        <h1 class="display" style="font-size: 2rem; margin-top: 6px">我的订单</h1>
      </div>

      <div class="chips" style="margin: 14px 0 6px">
        <button v-for="t in tabs" :key="t.k" class="chip" :class="{ on: tab === t.k }" @click="tab = t.k">
          {{ t.label }}
        </button>
      </div>

      <div class="grid-cards" style="margin-top: 10px">
        <div v-for="o in list" :key="o.id" class="card" style="cursor: pointer" @click="$router.push(`/order/${o.id}`)">
          <div class="row">
            <span class="kicker mono">{{ o.no }}</span>
            <StatusPill :status="o.status" />
          </div>
          <div class="row" style="margin-top: 10px; align-items: flex-end">
            <div class="stack">
              <strong class="display" style="font-size: 1.15rem">{{ o.snap.storeName }}</strong>
              <span class="dim" style="font-size: 0.82rem">
                {{ o.snap.category }}盲盒 · {{ fmtTime(o.snap.pickupStart) }}–{{ fmtTime(o.snap.pickupEnd) }} 自提
              </span>
            </div>
            <span class="price" style="font-size: 1.15rem">¥{{ fenInt(o.amount) }}</span>
          </div>
          <template v-if="o.status === 'pending_payment'">
            <hr class="hr" />
            <div class="row">
              <span class="dim" style="font-size: 0.82rem">支付剩余时间</span>
              <span class="mono" style="color: var(--accent); font-weight: 600">{{ left(new Date(o.payDeadline) - store.nowMs) }}</span>
            </div>
          </template>
        </div>
      </div>

      <EmptyState v-if="!list.length" glyph="单" text="这个状态下还没有订单" />
    </div>
    <BottomNav />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useStore } from '../../store'
import { fmtTime, fenInt, left } from '../../utils'
import StatusPill from '../../components/StatusPill.vue'
import EmptyState from '../../components/EmptyState.vue'
import BottomNav from '../../components/BottomNav.vue'

const store = useStore()
const tab = ref('all')
const tabs = [
  { k: 'all', label: '全部' },
  { k: 'pending_payment', label: '待支付' },
  { k: 'to_pickup', label: '待取货' },
  { k: 'picked', label: '已领取' },
  { k: 'after', label: '退款/售后' }
]

const list = computed(() => {
  if (tab.value === 'all') return store.myOrders
  if (tab.value === 'after')
    return store.myOrders.filter((o) => ['refunding', 'refunded'].includes(o.status))
  return store.myOrders.filter((o) => o.status === tab.value)
})
</script>
