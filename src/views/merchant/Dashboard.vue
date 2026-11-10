<template>
  <div v-if="s">
    <span class="kicker">MERCHANT CONSOLE</span>
    <h1 class="display" style="font-size: clamp(2.2rem, 5vw, 3.2rem); margin: 8px 0 4px">
      {{ s.name }}，<br /><span class="dim">今天也少浪费一点。</span>
    </h1>

    <!-- 4 个可点击统计卡片 → 点击弹抽屉显示详情 -->
    <div class="stat-grid" style="margin-top: 30px">
      <div class="stat stat-clickable" @click="open('batches')">
        <span class="kicker">在售批次</span>
        <div class="v">{{ live.length }}</div>
        <span class="kicker dim" style="margin-top: 8px">点击查看批次详情</span>
      </div>
      <div class="stat stat-clickable" @click="open('sold')">
        <span class="kicker">今日已售（份）</span>
        <div class="v">{{ soldToday }}</div>
        <span class="kicker dim" style="margin-top: 8px">点击查看已售订单</span>
      </div>
      <div class="stat stat-clickable" @click="open('toPickup')">
        <span class="kicker">待核销订单</span>
        <div class="v">{{ toPickup }}</div>
        <span class="kicker dim" style="margin-top: 8px">点击查看待取列表</span>
      </div>
      <div class="stat stat-clickable" @click="open('gmv')">
        <span class="kicker">今日成交</span>
        <div class="v" style="color: var(--accent)">¥{{ fenInt(gmv) }}</div>
        <span class="kicker dim" style="margin-top: 8px">点击查看成交明细</span>
      </div>
    </div>

    <!-- 经营预警 -->
    <div class="card" style="margin-top: 22px">
      <div class="row">
        <span class="kicker" :style="hasWarn ? 'color: var(--red)' : ''">经营预警</span>
        <span class="kicker dim">{{ hasWarn ? '建议尽快处理' : '库存与售卖时段正常' }}</span>
      </div>
      <div v-if="hasWarn" class="stack" style="gap: 10px; margin-top: 12px">
        <div v-for="w in warnings.lowStock" :key="'ls' + w.id" class="warn-row">
          <span class="tag tag-warn">库存告急</span>
          <span style="font-size: 0.86rem">{{ w.no }} · {{ w.category }} · 仅剩 {{ w.available }} 份</span>
          <router-link to="/merchant/batches" class="warn-link">去补货</router-link>
        </div>
        <div v-for="w in warnings.endingSoon" :key="'es' + w.id" class="warn-row">
          <span class="tag tag-warn">即将截止</span>
          <span style="font-size: 0.86rem">{{ w.no }} · {{ fmtTime(w.endAt) }} 结束，余 {{ w.available }} 份</span>
          <router-link to="/merchant/batches" class="warn-link">去看看</router-link>
        </div>
      </div>
      <p v-else class="dim" style="font-size: 0.86rem; margin-top: 10px">
        所有在售批次库存充足、售卖时段充裕，无需调整。
      </p>
    </div>

    <div class="card" style="margin-top: 22px" v-if="store.pendingAftersales.length">
      <div class="row">
        <div class="stack">
          <strong>有 {{ store.pendingAftersales.length }} 条售后申请待处理</strong>
          <span class="dim" style="font-size: 0.85rem">商家处理期限为 24 小时，逾期顾客可申请平台介入</span>
        </div>
        <router-link to="/merchant/aftersale" class="btn btn-ink btn-sm">去处理</router-link>
      </div>
    </div>

    <!-- 经营图表 -->
    <div class="chart-grid" style="margin-top: 22px">
      <div class="card">
        <div class="row">
          <span class="kicker">近 7 日订单趋势</span>
          <span class="kicker dim">按下单时间</span>
        </div>
        <Chart :option="trendOpt" height="240px" />
      </div>
      <div class="card">
        <div class="row">
          <span class="kicker">订单状态分布</span>
        </div>
        <Chart :option="statusOpt" height="240px" />
      </div>
    </div>

    <template v-if="reviews.length">
      <h2 class="kicker" style="margin-top: 34px">顾客评价</h2>
      <div class="card" style="margin-top: 12px; padding: 6px 20px">
        <div v-for="r in reviews" :key="r.id" style="padding: 13px 0; border-bottom: 1px solid var(--line)">
          <div class="row">
            <strong style="font-size: 0.9rem">{{ r.userName }}</strong>
            <span class="stars">{{ '★'.repeat(r.rating) }}<span>{{ '★'.repeat(5 - r.rating) }}</span></span>
          </div>
          <p v-if="r.text" class="tone-2" style="font-size: 0.85rem; margin-top: 4px">{{ r.text }}</p>
          <p class="mono dim" style="font-size: 0.72rem; margin-top: 4px">{{ fmtDT(r.createdAt) }}</p>
        </div>
      </div>
    </template>

    <h2 class="kicker" style="margin-top: 34px">最近订单</h2>
    <div class="card" style="margin-top: 12px; padding: 6px 20px">
      <div v-for="o in recent" :key="o.id" class="row" style="padding: 13px 0; border-bottom: 1px solid var(--line)">
        <div class="stack">
          <span class="mono" style="font-size: 0.85rem">{{ o.no }}</span>
          <span class="dim" style="font-size: 0.8rem">{{ fmtDT(o.createdAt) }} · {{ o.snap.category }}盲盒</span>
        </div>
        <div class="row" style="gap: 14px">
          <span class="price">¥{{ fenInt(o.amount) }}</span>
          <StatusPill :status="o.status" />
        </div>
      </div>
    </div>

    <!-- 钻取详情抽屉 -->
    <div v-if="drawer" class="drawer-mask" @click.self="drawer = null">
      <div class="drawer">
        <button class="close" @click="drawer = null">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" />
          </svg>
        </button>

        <h3 class="display">{{ drawerTitle }}</h3>
        <p class="dim" style="font-size: 0.8rem; margin-top: 4px">{{ drawerDesc }}</p>

        <!-- 在售批次详情 -->
        <template v-if="drawer === 'batches'">
          <div v-for="b in live" :key="b.id" class="card" style="margin-top: 14px">
            <div class="row">
              <div class="stack">
                <span class="kicker mono">{{ b.no }} · {{ b.category }}</span>
                <strong style="font-size: 0.92rem">{{ fmtTime(b.pickupStart) }}–{{ fmtTime(b.pickupEnd) }} 自提 · ¥{{ fenInt(b.price) }}</strong>
              </div>
              <StatusPill :status="b.status" />
            </div>
            <div class="dots" style="margin-top: 10px">
              <span v-for="i in Math.min(b.total, 24)" :key="i" class="dot" :class="{ on: i <= b.sold }"></span>
            </div>
            <span class="mono dim" style="font-size: 0.75rem; margin-top: 8px">
              总量 {{ b.total }} · 已售 {{ b.sold }} · 预留 {{ b.reserved }} · 可售 {{ b.available }}
            </span>
          </div>
          <p v-if="!live.length" class="dim" style="padding: 24px; text-align: center; font-size: 0.88rem">当前没有在售批次</p>
        </template>

        <!-- 已售订单明细 -->
        <template v-if="drawer === 'sold'">
          <div v-for="o in soldOrders" :key="o.id" class="card" style="margin-top: 10px; padding: 12px 16px">
            <div class="row">
              <span class="mono" style="font-size: 0.85rem">{{ o.no }}</span>
              <StatusPill :status="o.status" />
            </div>
            <p class="dim" style="font-size: 0.78rem; margin-top: 4px">{{ fmtDT(o.createdAt) }} · {{ o.snap.category }}盲盒 · ¥{{ fenInt(o.amount) }}</p>
          </div>
          <p v-if="!soldOrders.length" class="dim" style="padding: 24px; text-align: center; font-size: 0.88rem">今日暂无已成交订单</p>
        </template>

        <!-- 待核销列表 -->
        <template v-if="drawer === 'toPickup'">
          <div v-for="o in toPickupList" :key="o.id" class="card" style="margin-top: 10px; padding: 12px 16px">
            <div class="row">
              <span class="mono" style="font-size: 0.85rem">{{ o.no }}</span>
              <span class="mono dim" style="font-size: 0.8rem; font-weight: 600; color: var(--accent)">{{ o.pickupCode }}</span>
            </div>
            <p class="dim" style="font-size: 0.78rem; margin-top: 4px">
              {{ o.snap.category }}盲盒 · 取货截止 {{ fmtTime(o.snap.pickupEnd) }} · ¥{{ fenInt(o.amount) }}
            </p>
            <router-link :to="'/merchant/verify'" class="btn btn-ghost btn-sm" style="margin-top: 8px">去核销</router-link>
          </div>
          <p v-if="!toPickupList.length" class="dim" style="padding: 24px; text-align: center; font-size: 0.88rem">没有待核销的订单</p>
        </template>

        <!-- 成交明细 -->
        <template v-if="drawer === 'gmv'">
          <div class="card" style="margin-top: 14px; padding: 16px">
            <div class="row">
              <span class="kicker">成交总额</span>
              <span class="price" style="font-size: 1.4rem">¥{{ fenInt(gmv) }}</span>
            </div>
            <hr class="hr" />
            <div class="row">
              <span class="tone-2" style="font-size: 0.88rem">成功成交订单数</span>
              <span class="mono">{{ paidOrders.length }}</span>
            </div>
            <div class="row">
              <span class="tone-2" style="font-size: 0.88rem">退款已到账</span>
              <span class="mono">¥{{ fenInt(refundedTotal) }}</span>
            </div>
          </div>
          <div v-for="o in paidOrders" :key="o.id" class="card" style="margin-top: 10px; padding: 12px 16px">
            <div class="row">
              <span class="mono" style="font-size: 0.85rem">{{ o.no }}</span>
              <span class="price">¥{{ fenInt(o.amount) }}</span>
            </div>
            <div class="row" style="margin-top: 4px">
              <span class="dim" style="font-size: 0.78rem">{{ fmtDT(o.paidAt) }} 支付</span>
              <StatusPill :status="o.status" />
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useStore } from '../../store'
import { fmtDT, fmtTime, fenInt } from '../../utils'
import StatusPill from '../../components/StatusPill.vue'
import Chart from '../../components/Chart.vue'

const STATUS_TEXT = {
  pending_payment: '待支付', to_pickup: '待取货', picked: '已领取',
  closed: '已关闭', refunding: '退款中', refunded: '已退款', no_show: '超时未取'
}

const store = useStore()
const s = computed(() => store.merchantStore).value

// 近 7 天日期标签（M/D）
const dayKeys = computed(() => {
  const arr = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date(store.nowMs - i * 86400000)
    arr.push(`${d.getMonth() + 1}/${d.getDate()}`)
  }
  return arr
})

const trendOpt = computed(() => {
  const counts = dayKeys.value.map(() => 0)
  for (const o of store.merchantOrders) {
    const d = new Date(o.createdAt)
    const i = dayKeys.value.indexOf(`${d.getMonth() + 1}/${d.getDate()}`)
    if (i >= 0) counts[i]++
  }
  return {
    grid: { left: 34, right: 14, top: 24, bottom: 26 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: dayKeys.value, axisLine: { lineStyle: { color: '#d8d0c0' } } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#ece5d8' } } },
    series: [{
      type: 'line', data: counts, smooth: true, symbolSize: 7,
      lineStyle: { width: 3, color: '#c25e2e' }, itemStyle: { color: '#c25e2e' },
      areaStyle: { color: 'rgba(194,94,46,0.12)' }
    }]
  }
})

const statusOpt = computed(() => {
  const c = {}
  for (const o of store.merchantOrders) c[o.status] = (c[o.status] || 0) + 1
  const data = Object.entries(c).map(([k, v]) => ({ name: STATUS_TEXT[k] || k, value: v }))
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'], data,
      label: { show: false }, itemStyle: { borderColor: '#fff', borderWidth: 2 }
    }]
  }
})

// 经营预警：库存 ≤3 的在售批次 + 3 小时内截止的在售批次
const warnings = computed(() => {
  const nowMs = store.nowMs
  const onSale = store.storeBatches(s.id).filter((b) => b.status === 'on_sale')
  return {
    lowStock: onSale.filter((b) => b.available > 0 && b.available <= 3),
    endingSoon: onSale.filter((b) => {
      const left = new Date(b.endAt).getTime() - nowMs
      return left > 0 && left < 3 * 60 * 60 * 1000
    })
  }
})

const hasWarn = computed(() => warnings.value.lowStock.length + warnings.value.endingSoon.length > 0)

const reviews = computed(() => store.storeReviews(s.id).slice(0, 5))

const live = computed(() => store.storeBatches(s.id).filter((b) => b.status === 'on_sale'))
const toPickup = computed(() => store.merchantOrders.filter((o) => o.status === 'to_pickup').length)
const toPickupList = computed(() => store.merchantOrders.filter((o) => o.status === 'to_pickup'))
const recent = computed(() => store.merchantOrders.slice(0, 6))
// 今日已售：今日创建且已完成支付的订单（每单1份），与详情列表口径一致
const isToday = (iso) => {
  const d = new Date(iso)
  const now = new Date(store.nowMs)
  return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate()
}
const paidStatuses = ['to_pickup', 'picked', 'no_show', 'refunding']
const soldToday = computed(() => store.merchantOrders.filter((o) => isToday(o.createdAt) && paidStatuses.includes(o.status)).length)
const soldOrders = computed(() => store.merchantOrders.filter((o) => isToday(o.createdAt) && paidStatuses.includes(o.status)))
const paidOrders = computed(() => store.merchantOrders.filter((o) => !['pending_payment', 'closed', 'refunded'].includes(o.status)))
const gmv = computed(() => paidOrders.value.reduce((n, o) => n + o.amount, 0))
const refundedTotal = computed(() => store.refunds.filter((r) => r.status === 'success' && store.merchantOrders.some((o) => o.id === r.orderId)).reduce((n, r) => n + r.amount, 0))

const drawer = ref(null)

const drawerTitle = computed(() => ({
  batches: '在售批次详情',
  sold: '今日已售订单',
  toPickup: '待核销订单',
  gmv: '今日成交明细'
})[drawer.value] || '')

const drawerDesc = computed(() => ({
  batches: '当前所有在售批次的数量、价格、可售余量',
  sold: '今日已完成的销售订单（含已取货与超时未取）',
  toPickup: '已付款待核销的订单，含取货码和截止时间',
  gmv: '成功成交总额、退款到账金额、订单明细'
})[drawer.value] || '')

function open(which) { drawer.value = which }
</script>
