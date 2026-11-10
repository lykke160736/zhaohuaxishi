<template>
  <div>
    <span class="kicker">全平台批次与违规处置</span>
    <h1 class="display" style="font-size: 2.2rem; margin-top: 6px">盲盒监管</h1>
    <p class="dim" style="margin-top: 10px">平台下架后不再接受新订单，库存及历史订单保留。无法继续履约时，应通过订单和售后流程处理。</p>
    <div class="row" style="margin-top: 22px; gap: 12px; flex-wrap: wrap">
      <input v-model="keyword" class="input" style="max-width: 300px" placeholder="搜索门店名称、批次号" />
      <select v-model="status" class="input" style="max-width: 160px"><option value="">全部状态</option><option value="on_sale">在售</option><option value="sold_out">已售罄</option><option value="stopped">已停售</option><option value="suspended">门店暂停经营</option><option value="ended">已结束</option></select>
      <span class="dim">共 {{ batches.length }} 个批次</span>
    </div>
    <p v-if="message" class="notice" style="margin-top: 16px" role="status">{{ message }}</p>
    <div v-for="b in batches" :key="b.id" class="card" style="margin-top: 16px">
      <div class="row" style="gap: 12px; flex-wrap: wrap"><strong>{{ nameOf(b) }} · {{ b.no }}</strong><StatusPill :status="b.status" /></div>
      <p style="margin-top: 12px">{{ b.category }}盲盒 · ¥{{ fenInt(b.price) }} · 总量 {{ b.total }} / 已售 {{ b.sold }} / 预留 {{ b.reserved }} / 可售 {{ b.available }}</p>
      <p class="dim" style="margin-top: 8px">取货：{{ fmtDT(b.pickupStart) }} — {{ fmtDT(b.pickupEnd) }}</p>
      <p v-if="b.stopReason" style="margin-top: 8px">停售原因：{{ b.stopReason }} · {{ fmtDT(b.stoppedAt) }}</p>
      <button v-if="canStop(b)" class="btn btn-ghost btn-sm btn-danger" style="margin-top: 14px" @click="select(b)">平台下架</button>
    </div>
    <p v-if="!batches.length" class="notice" style="margin-top: 16px">没有符合条件的批次。</p>
    <div v-if="selected" class="drawer-mask" @click.self="!busy && (selected = null)">
      <form class="drawer" @submit.prevent="submit">
        <h2 class="display">平台下架 · {{ selected.no }}</h2>
        <p class="dim" style="margin-top: 16px">{{ nameOf(selected) }}。下架后停止新增订单，不会取消已有订单或自动发起退款；恢复门店经营也不会重新上架此批次。</p>
        <label class="field" style="margin-top: 20px">下架原因<textarea v-model="reason" class="textarea" required maxlength="500" placeholder="填写具体违规情况，将通知商家并写入操作审计"></textarea></label>
        <p v-if="error" class="notice notice-warn" role="alert">{{ error }}</p>
        <div class="row" style="margin-top: 16px; justify-content: flex-end; gap: 12px"><button type="button" class="btn btn-ghost" :disabled="busy" @click="selected = null">取消</button><button class="btn btn-accent" :disabled="busy || !reason.trim()">{{ busy ? '提交中…' : '确认下架' }}</button></div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useStore } from '../../store'
import { fmtDT, fenInt } from '../../utils'
import StatusPill from '../../components/StatusPill.vue'
const store = useStore()
const keyword = ref('')
const status = ref('')
const selected = ref(null)
const reason = ref('')
const busy = ref(false)
const error = ref('')
const message = ref('')
const nameOf = b => store.stores.find(s => s.id === b.storeId)?.name || '—'
const canStop = b => store.batches.find(row => row.id === b.id)?.status === 'on_sale' && store.nowMs < new Date(b.pickupEnd).getTime()
const batches = computed(() => store.batches.map(b => store.batchView(b)).filter(b => (!status.value || b.status === status.value) && `${nameOf(b)} ${b.no}`.includes(keyword.value.trim())))
function select(b) { selected.value = b; reason.value = ''; error.value = ''; message.value = '' }
async function submit() {
  if (busy.value || !reason.value.trim()) return
  busy.value = true
  try {
    const r = await store.adminStopBatch(selected.value.id, reason.value.trim())
    if (!r.ok) { error.value = r.msg; return }
    message.value = '批次已下架，已通知商家并记录操作日志。'
    selected.value = null
  } finally { busy.value = false }
}
</script>
