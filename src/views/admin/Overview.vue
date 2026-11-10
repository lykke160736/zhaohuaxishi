<template>
  <div>
    <span class="kicker">PLATFORM OVERVIEW</span>
    <h1 class="display" style="font-size: clamp(2.2rem, 5vw, 3.2rem); margin: 8px 0 4px">
      平台业务总览，<br /><span class="dim">钱、货、单都对得上。</span>
    </h1>

    <div class="stat-grid" style="margin-top: 30px">
      <div class="stat stat-clickable" @click="open('stores')">
        <span class="kicker">在营门店</span>
        <div class="v">{{ store.stores.filter(s => s.operationStatus !== 'suspended').length }}</div>
        <span class="kicker dim" style="margin-top: 8px">点击查看门店详情</span>
      </div>
      <div class="stat stat-clickable" @click="open('orders')">
        <span class="kicker">今日订单</span>
        <div class="v">{{ store.orders.length }}</div>
        <span class="kicker dim" style="margin-top: 8px">点击查看订单列表</span>
      </div>
      <div class="stat stat-clickable" @click="open('gmv')">
        <span class="kicker">成交金额</span>
        <div class="v" style="color: var(--accent)">¥{{ fenInt(gmv) }}</div>
        <span class="kicker dim" style="margin-top: 8px">点击查看成交明细</span>
      </div>
      <div class="stat stat-clickable" @click="open('refunds')">
        <span class="kicker">退款中 / 已退</span>
        <div class="v">{{ refunding }} / {{ refunded }}</div>
        <span class="kicker dim" style="margin-top: 8px">点击查看退款追踪</span>
      </div>
      <div class="stat stat-clickable" @click="$router.push('/admin/audit')">
        <span class="kicker">待审核入驻</span>
        <div class="v">{{ store.pendingApplications.length }}</div>
        <span class="kicker dim" style="margin-top: 8px">进入审核页</span>
      </div>
      <div class="stat stat-clickable" @click="$router.push('/admin/intervention')">
        <span class="kicker">平台介入队列</span>
        <div class="v">{{ store.platformQueue.length }}</div>
        <span class="kicker dim" style="margin-top: 8px">进入介入处理</span>
      </div>
    </div>

    <!-- 平台图表 -->
    <div class="chart-grid" style="margin-top: 22px">
      <div class="card">
        <div class="row">
          <span class="kicker">近 7 日订单与成交</span>
          <span class="kicker dim">柱：订单量 · 线：成交额(元)</span>
        </div>
        <Chart :option="trendOpt" height="260px" />
      </div>
      <div class="card">
        <div class="row">
          <span class="kicker">品类成交分布</span>
          <span class="kicker dim">按成交金额</span>
        </div>
        <Chart :option="catOpt" height="260px" />
      </div>
    </div>

    <h2 class="kicker" style="margin-top: 34px">资金追踪（退款以渠道确认为准）</h2>
    <div class="card" style="margin-top: 12px; padding: 6px 20px">
      <div v-for="r in store.refunds" :key="r.id" class="row" style="padding: 13px 0; border-bottom: 1px solid var(--line)">
        <div class="stack">
          <span class="mono" style="font-size: 0.85rem">{{ r.id }} · {{ { normal: '普通退款', merchant_cancel: '商家取消', aftersale: '特殊售后', platform: '平台裁决' }[r.type] }}</span>
          <span class="dim" style="font-size: 0.8rem">{{ r.reason }} · {{ fmtDT(r.createdAt) }}</span>
        </div>
        <div class="row" style="gap: 14px">
          <span class="price">¥{{ fenInt(r.amount) }}</span>
          <StatusPill :status="r.status" />
        </div>
      </div>
    </div>

    <!-- 售卖时段管理：显式操作，刷新页面不会自动改变业务时间 -->
    <h2 class="kicker" style="margin-top: 30px">售卖时段管理</h2>
    <div class="card" style="margin-top: 12px; padding: 16px 20px">
      <div class="row" style="gap: 16px">
        <div class="stack" style="flex: 1">
          <strong style="font-size: 0.95rem">重开今日售卖时段</strong>
          <span class="dim" style="font-size: 0.8rem; line-height: 1.6">
            把已过期的在售批次按当前时间重新开盘，保留原售卖时长与库存。调整后不改变历史订单的取货时间快照。
          </span>
        </div>
        <button class="btn btn-ink btn-sm" style="flex: none" @click="refreshWindow">顺延过期批次</button>
      </div>
      <p v-if="demoMsg" class="notice notice-warn" style="margin-top: 12px">{{ demoMsg }}</p>
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

        <template v-if="drawer === 'stores'">
          <div
            v-for="s in store.stores"
            :key="s.id"
            class="card"
            style="margin-top: 14px; padding: 14px 16px; cursor: pointer"
            @click="toggleStore(s.id)"
          >
            <div class="row">
              <div class="stack">
                <strong>{{ s.name }}</strong>
                <span class="dim mono" style="font-size: 0.78rem">{{ s.category }} · {{ s.address }}</span>
              </div>
              <div class="row" style="gap: 10px">
                <StatusPill :status="s.auditStatus" />
                <span class="dim" style="font-size: 0.76rem; white-space: nowrap">{{ expandedStore === s.id ? '收起 ▲' : '营业详情 ▼' }}</span>
              </div>
            </div>
            <div class="row" style="margin-top: 6px">
              <span class="mono dim" style="font-size: 0.75rem">📞 {{ s.phone }}</span>
              <span class="mono dim" style="font-size: 0.75rem">{{ s.lat.toFixed(4) }}, {{ s.lng.toFixed(4) }}</span>
            </div>

            <!-- 展开：营业情况 + 详细信息 -->
            <template v-if="expandedStore === s.id">
              <hr class="hr" />
              <span class="kicker">营业情况</span>
              <div class="stat-grid" style="margin-top: 8px; gap: 10px">
                <div class="stat" style="padding: 12px 14px">
                  <span class="kicker" style="font-size: 0.64rem">在售批次</span>
                  <div class="mono" style="font-size: 1.3rem">{{ storeStat(s.id).onSale }} / {{ storeStat(s.id).batchCount }}</div>
                </div>
                <div class="stat" style="padding: 12px 14px">
                  <span class="kicker" style="font-size: 0.64rem">累计订单</span>
                  <div class="mono" style="font-size: 1.3rem">{{ storeStat(s.id).orderCount }}</div>
                </div>
                <div class="stat" style="padding: 12px 14px">
                  <span class="kicker" style="font-size: 0.64rem">成交金额</span>
                  <div class="mono" style="font-size: 1.3rem; color: var(--accent)">¥{{ fenInt(storeStat(s.id).gmv) }}</div>
                </div>
                <div class="stat" style="padding: 12px 14px">
                  <span class="kicker" style="font-size: 0.64rem">顾客评分</span>
                  <div class="mono" style="font-size: 1.3rem">
                    {{ storeStat(s.id).rating ? `★ ${storeStat(s.id).rating.avg.toFixed(1)}` : '暂无' }}
                    <span class="dim" style="font-size: 0.72rem">{{ storeStat(s.id).rating ? `(${storeStat(s.id).rating.count})` : '' }}</span>
                  </div>
                </div>
              </div>

              <span class="kicker" style="display: block; margin-top: 16px">门店信息</span>
              <div class="stack" style="gap: 7px; font-size: 0.86rem; margin-top: 8px">
                <div class="row"><span class="tone-2">门店名称</span><span>{{ s.name }}</span></div>
                <div class="row"><span class="tone-2">经营品类</span><span>{{ s.category }}</span></div>
                <div class="row"><span class="tone-2">地址</span><span style="text-align: right; max-width: 66%">{{ s.address }}</span></div>
                <div class="row"><span class="tone-2">联系电话</span><span class="mono">{{ s.phone }}</span></div>
                <div class="row"><span class="tone-2">坐标 (GCJ-02)</span><span class="mono dim">{{ s.lat.toFixed(4) }}, {{ s.lng.toFixed(4) }}</span></div>
                <div class="row"><span class="tone-2">门口告示</span><span style="text-align: right; max-width: 66%">{{ s.notice || '—' }}</span></div>
                <div class="row"><span class="tone-2">负责人</span><span>{{ ownerOf(s.ownerId)?.name || '—' }}（{{ ownerOf(s.ownerId)?.account || '—' }}）</span></div>
                <div class="row">
                  <span class="tone-2">账号状态</span>
                  <StatusPill :status="ownerOf(s.ownerId)?.status || 'active'" />
                </div>
              </div>

              <span class="kicker" style="display: block; margin-top: 16px">批次列表</span>
              <div class="stack" style="gap: 6px; margin-top: 8px">
                <div
                  v-for="b in store.storeBatches(s.id)"
                  :key="b.id"
                  class="row"
                  style="font-size: 0.82rem; padding: 7px 0; border-bottom: 1px dashed var(--line)"
                >
                  <span class="mono">{{ b.no }} · {{ b.category }} · ¥{{ fenInt(b.price) }}</span>
                  <span class="row" style="gap: 8px">
                    <span class="dim mono">余 {{ b.available }}/{{ b.total }}</span>
                    <StatusPill :status="b.status" />
                  </span>
                </div>
                <p v-if="!store.storeBatches(s.id).length" class="dim" style="font-size: 0.82rem">暂无批次</p>
              </div>
            </template>
          </div>
        </template>

        <template v-if="drawer === 'orders'">
          <div
            v-for="o in store.orders"
            :key="o.id"
            class="card"
            style="margin-top: 10px; padding: 12px 16px; cursor: pointer"
            @click="toggleOrder(o.id)"
          >
            <div class="row">
              <span class="mono" style="font-size: 0.85rem">{{ o.no }}</span>
              <div class="row" style="gap: 10px">
                <StatusPill :status="o.status" />
                <span class="dim" style="font-size: 0.72rem">{{ expandedOrder === o.id ? '收起 ▲' : '详情 ▼' }}</span>
              </div>
            </div>
            <p class="dim" style="font-size: 0.78rem; margin-top: 4px">
              {{ o.snap.storeName }} · {{ o.snap.category }}盲盒 · ¥{{ fenInt(o.amount) }} · {{ fmtDT(o.createdAt) }}
            </p>

            <template v-if="expandedOrder === o.id">
              <hr class="hr" />
              <div class="stack" style="gap: 7px; font-size: 0.84rem">
                <div class="row"><span class="tone-2">订单号</span><span class="mono">{{ o.no }}</span></div>
                <div class="row"><span class="tone-2">订单状态</span><StatusPill :status="o.status" /></div>
                <div class="row"><span class="tone-2">门店</span><span>{{ o.snap.storeName }}</span></div>
                <div class="row"><span class="tone-2">品类</span><span>{{ o.snap.category }}</span></div>
                <div class="row"><span class="tone-2">金额</span><span class="price">¥{{ fenInt(o.amount) }}</span></div>
                <div class="row"><span class="tone-2">下单时间</span><span class="mono dim">{{ fmtDT(o.createdAt) }}</span></div>
                <div class="row"><span class="tone-2">支付截止</span><span class="mono dim">{{ fmtDT(o.payDeadline) }}</span></div>
                <div class="row"><span class="tone-2">支付时间</span><span class="mono dim">{{ o.paidAt ? fmtDT(o.paidAt) : '—' }}</span></div>
                <div class="row"><span class="tone-2">领取时间</span><span class="mono dim">{{ o.pickedAt ? fmtDT(o.pickedAt) : '—' }}</span></div>
                <div class="row"><span class="tone-2">取货码</span><span class="mono">{{ o.pickupCode || '—' }}</span></div>
                <div class="row"><span class="tone-2">自提时段</span><span class="mono dim">{{ fmtDT(o.snap.pickupStart) }} – {{ fmtDT(o.snap.pickupEnd) }}</span></div>
                <div v-if="o.operator" class="row"><span class="tone-2">核销门店</span><span>{{ o.operator }}</span></div>
                <template v-if="refundOf(o.id)">
                  <hr class="hr" />
                  <div class="row"><span class="tone-2">退款单</span><span class="mono">{{ refundOf(o.id).id }}</span></div>
                  <div class="row"><span class="tone-2">退款状态</span><StatusPill :status="refundOf(o.id).status" /></div>
                  <div class="row"><span class="tone-2">退款金额</span><span class="price">¥{{ fenInt(refundOf(o.id).amount) }}</span></div>
                  <div class="row"><span class="tone-2">退款原因</span><span style="text-align: right; max-width: 66%">{{ refundOf(o.id).reason }}</span></div>
                </template>
                <template v-if="aftersaleOf(o.id)">
                  <hr class="hr" />
                  <div class="row"><span class="tone-2">特殊售后</span><StatusPill :status="aftersaleOf(o.id).status" /></div>
                  <div class="row"><span class="tone-2">售后原因</span><span style="text-align: right; max-width: 66%">{{ aftersaleOf(o.id).reason }}</span></div>
                </template>
              </div>
            </template>
          </div>
          <p v-if="!store.orders.length" class="dim" style="padding: 18px; text-align: center; font-size: 0.88rem">暂无订单</p>
        </template>

        <template v-if="drawer === 'gmv'">
          <div class="card" style="margin-top: 14px; padding: 16px">
            <div class="row">
              <span class="kicker">平台成交总额</span>
              <span class="price" style="font-size: 1.6rem">¥{{ fenInt(gmv) }}</span>
            </div>
            <hr class="hr" />
            <div class="row"><span class="tone-2" style="font-size: 0.88rem">成功成交订单</span><span class="mono">{{ paidCount }}</span></div>
            <div class="row"><span class="tone-2" style="font-size: 0.88rem">退款已到账</span><span class="mono" style="color: var(--red)">¥{{ fenInt(refundedTotal) }}</span></div>
          </div>
        </template>

        <template v-if="drawer === 'refunds'">
          <div v-for="r in store.refunds" :key="r.id" class="card" style="margin-top: 10px; padding: 12px 16px">
            <div class="row">
              <span class="mono" style="font-size: 0.85rem">{{ r.id }}</span>
              <StatusPill :status="r.status" />
            </div>
            <p class="dim" style="font-size: 0.78rem; margin-top: 4px">
              {{ r.reason }} · {{ fmtDT(r.createdAt) }} · 金额 ¥{{ fenInt(r.amount) }}
            </p>
          </div>
          <p v-if="!store.refunds.length" class="dim" style="padding: 18px; text-align: center; font-size: 0.88rem">暂无退款记录</p>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useStore } from '../../store'
import { fmtDT, fenInt } from '../../utils'
import StatusPill from '../../components/StatusPill.vue'
import Chart from '../../components/Chart.vue'

const store = useStore()

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
  const amounts = dayKeys.value.map(() => 0)
  for (const o of store.orders) {
    const d = new Date(o.createdAt)
    const i = dayKeys.value.indexOf(`${d.getMonth() + 1}/${d.getDate()}`)
    if (i < 0) continue
    counts[i]++
    if (!['pending_payment', 'closed', 'refunded'].includes(o.status)) amounts[i] += o.amount / 100
  }
  return {
    grid: { left: 40, right: 44, top: 30, bottom: 26 },
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { fontSize: 11 } },
    xAxis: { type: 'category', data: dayKeys.value, axisLine: { lineStyle: { color: '#d8d0c0' } } },
    yAxis: [
      { type: 'value', name: '单', minInterval: 1, splitLine: { lineStyle: { color: '#ece5d8' } } },
      { type: 'value', name: '元', splitLine: { show: false } }
    ],
    series: [
      { name: '订单量', type: 'bar', data: counts, barWidth: 16, itemStyle: { color: '#181410', borderRadius: [4, 4, 0, 0] } },
      { name: '成交额', type: 'line', yAxisIndex: 1, data: amounts.map((v) => v.toFixed(2)), smooth: true, symbolSize: 6, lineStyle: { width: 3, color: '#c25e2e' }, itemStyle: { color: '#c25e2e' } }
    ]
  }
})

const catOpt = computed(() => {
  const c = {}
  for (const o of store.orders) {
    if (['pending_payment', 'closed'].includes(o.status)) continue
    c[o.snap.category] = (c[o.snap.category] || 0) + o.amount / 100
  }
  const data = Object.entries(c).map(([k, v]) => ({ name: k, value: Number(v.toFixed(2)) }))
  return {
    tooltip: { trigger: 'item', valueFormatter: (v) => `¥${v}` },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'], data,
      label: { show: false }, itemStyle: { borderColor: '#fff', borderWidth: 2 }
    }]
  }
})

const gmv = computed(() => store.orders.filter((o) => !['pending_payment', 'closed'].includes(o.status)).reduce((n, o) => n + o.amount, 0))
const paidCount = computed(() => store.orders.filter((o) => !['pending_payment', 'closed', 'refunded'].includes(o.status)).length)
const refunding = computed(() => store.refunds.filter((r) => r.status === 'processing').length)
const refunded = computed(() => store.refunds.filter((r) => r.status === 'success').length)
const refundedTotal = computed(() => store.refunds.filter((r) => r.status === 'success').reduce((n, r) => n + r.amount, 0))

const drawer = ref(null)
const expandedStore = ref(null)
const expandedOrder = ref(null)
const demoMsg = ref('')

// 显式演示操作：只在点击时顺延过期批次，提示实际处理的数量
async function refreshWindow() {
  const r = await store.refreshDemoWindow()
  if (!r.ok) {
    demoMsg.value = r.msg
    return
  }
  demoMsg.value = r.changed ? `已顺延 ${r.changed} 个过期批次，可回到门店页重新选购` : '当前没有被截止的在售批次，无需顺延'
}

function toggleStore(id) {
  expandedStore.value = expandedStore.value === id ? null : id
}

function toggleOrder(id) {
  expandedOrder.value = expandedOrder.value === id ? null : id
}

const refundOf = (orderId) => store.orderRefund(orderId)
const aftersaleOf = (orderId) => store.orderAftersale(orderId)

// 门店营业情况：在售批次、订单量、成交额、评分
function storeStat(id) {
  const bs = store.storeBatches(id)
  const os = store.orders.filter((o) => o.storeId === id)
  const gmv = os
    .filter((o) => !['pending_payment', 'closed'].includes(o.status))
    .reduce((n, o) => n + o.amount, 0)
  return {
    onSale: bs.filter((b) => b.status === 'on_sale').length,
    batchCount: bs.length,
    orderCount: os.length,
    gmv,
    rating: store.storeRating(id)
  }
}

const ownerOf = (accountId) => store.accounts.find((a) => a.id === accountId)
const drawerTitle = computed(() => ({
  stores: '在营门店详情',
  orders: '全部订单列表',
  gmv: '成交总额与退款追踪',
  refunds: '退款进度'
})[drawer.value] || '')
const drawerDesc = computed(() => ({
  stores: '所有已审核通过可对外售卖的门店',
  orders: '今日所有订单，含状态与成交金额',
  gmv: '成功成交总额、退款到账金额、订单明细',
  refunds: '普通退款、商家取消、特殊售后、平台裁决四类退款的追踪'
})[drawer.value] || '')

function open(which) { drawer.value = which }
</script>
