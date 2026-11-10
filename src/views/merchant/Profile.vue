<template>
  <div v-if="s">
    <span class="kicker">UC-0103 · 变更可追溯</span>
    <h1 class="display" style="font-size: 2.2rem; margin-top: 6px">门店资料</h1>

    <div class="notice" style="margin-top: 18px">
      已成立订单保留交易时的取货信息快照，修改门店资料不会改变已有订单的约定。
    </div>

    <!-- 营业情况 -->
    <span class="kicker" style="display: block; margin-top: 26px">营业情况</span>
    <div class="stat-grid" style="margin-top: 10px">
      <div class="stat">
        <span class="kicker">在售批次</span>
        <div class="v">{{ stat.onSale }} / {{ stat.batchCount }}</div>
      </div>
      <div class="stat">
        <span class="kicker">累计订单</span>
        <div class="v">{{ stat.orderCount }}</div>
      </div>
      <div class="stat">
        <span class="kicker">成交金额</span>
        <div class="v" style="color: var(--accent)">¥{{ fenInt(stat.gmv) }}</div>
      </div>
      <div class="stat">
        <span class="kicker">顾客评分</span>
        <div class="v">
          {{ stat.rating ? `★ ${stat.rating.avg.toFixed(1)}` : '暂无' }}
          <span class="dim" style="font-size: 0.8rem">{{ stat.rating ? `(${stat.rating.count})` : '' }}</span>
        </div>
      </div>
    </div>

    <div class="chart-grid" style="margin-top: 18px; align-items: start">
      <!-- 门店信息 -->
      <div class="card" style="max-width: 560px">
        <span class="kicker">门店信息</span>
        <div class="stack" style="gap: 9px; margin-top: 12px; font-size: 0.9rem">
          <div class="row"><span class="tone-2">门店名称</span><span>{{ s.name }}</span></div>
          <div class="row"><span class="tone-2">经营品类</span><span>{{ s.category }}</span></div>
          <div class="row"><span class="tone-2">地址</span><span style="text-align: right; max-width: 66%">{{ s.address }}</span></div>
          <div class="row"><span class="tone-2">联系电话</span><span class="mono">{{ s.phone }}</span></div>
          <div class="row"><span class="tone-2">坐标 (GCJ-02)</span><span class="mono dim">{{ s.lat.toFixed(4) }}, {{ s.lng.toFixed(4) }}</span></div>
          <div class="row"><span class="tone-2">门口告示</span><span style="text-align: right; max-width: 66%">{{ s.notice || '—' }}</span></div>
          <div class="row"><span class="tone-2">负责人</span><span>{{ account?.name || store.session.name }}（{{ account?.account || '—' }}）</span></div>
          <div class="row"><span class="tone-2">账号状态</span><StatusPill :status="account?.status || 'active'" /></div>
          <div class="row"><span class="tone-2">入驻审核</span><StatusPill :status="s.auditStatus" /></div>
          <div class="row"><span class="tone-2">入驻时间</span><span class="mono dim">{{ account ? fmtDT(account.createdAt) : '—' }}</span></div>
        </div>
      </div>

      <!-- 编辑资料 -->
      <div class="card" style="max-width: 560px">
        <span class="kicker">修改门店资料</span>
        <div class="field" style="margin-top: 12px">
          <label>门店名称</label>
          <input class="input" v-model="form.name" />
        </div>
        <div class="field">
          <label>食品类别</label>
          <select class="select" v-model="form.category">
            <option v-for="c in store.categories.slice(1)" :key="c">{{ c }}</option>
          </select>
        </div>
        <div class="field">
          <label>门店地址</label>
          <input class="input" v-model="form.address" />
        </div>
        <div class="field">
          <label>联系电话</label>
          <input class="input" v-model="form.phone" />
        </div>
        <div class="field">
          <label>门店告示</label>
          <input class="input" v-model="form.notice" />
        </div>
        <div class="row" style="gap: 10px">
          <button class="btn btn-ink" @click="save">保存变更</button>
          <span v-if="saved" class="tag tag-green" style="align-self: center">已保存并记录操作人</span>
          <span v-if="msg" class="tag tag-accent" style="align-self: center">{{ msg }}</span>
        </div>
      </div>
    </div>

    <!-- 批次列表 -->
    <span class="kicker" style="display: block; margin-top: 26px">批次列表</span>
    <div class="card" style="margin-top: 10px; padding: 6px 20px">
      <div
        v-for="b in batches"
        :key="b.id"
        class="row"
        style="padding: 12px 0; border-bottom: 1px solid var(--line); font-size: 0.88rem"
      >
        <span class="mono">{{ b.no }} · {{ b.category }} · ¥{{ fenInt(b.price) }}</span>
        <span class="row" style="gap: 10px">
          <span class="dim mono">余 {{ b.available }}/{{ b.total }}</span>
          <StatusPill :status="b.status" />
        </span>
      </div>
      <p v-if="!batches.length" class="dim" style="padding: 16px 0; font-size: 0.86rem">暂无批次</p>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useStore } from '../../store'
import { fmtDT, fenInt } from '../../utils'
import StatusPill from '../../components/StatusPill.vue'

const store = useStore()
const s = computed(() => store.merchantStore)
const form = reactive({ name: '', category: '', address: '', phone: '', notice: '' })
const saved = ref(false)
const msg = ref('')

watch(s, (v) => {
  if (v) Object.assign(form, { name: v.name, category: v.category, address: v.address, phone: v.phone, notice: v.notice })
}, { immediate: true })

const account = computed(() => store.accounts.find((a) => a.id === s.value?.ownerId))
const batches = computed(() => (s.value ? store.storeBatches(s.value.id) : []))

// 营业情况：在售批次、订单量、成交额、评分
const stat = computed(() => {
  const bs = batches.value
  const os = store.merchantOrders
  const gmv = os
    .filter((o) => !['pending_payment', 'closed'].includes(o.status))
    .reduce((n, o) => n + o.amount, 0)
  return {
    onSale: bs.filter((b) => b.status === 'on_sale').length,
    batchCount: bs.length,
    orderCount: os.length,
    gmv,
    rating: s.value ? store.storeRating(s.value.id) : null
  }
})

async function save() {
  const r = await store.updateProfile({ ...form })
  msg.value = r.ok ? '' : r.msg
  if (!r.ok) return
  saved.value = true
  setTimeout(() => (saved.value = false), 2500)
}
</script>
