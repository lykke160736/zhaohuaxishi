<template>
  <div>
    <span class="kicker">经营资格与处置记录</span>
    <h1 class="display" style="font-size: 2.2rem; margin-top: 6px">门店管理</h1>
    <p class="dim" style="margin-top: 10px">暂停经营只限制新增交易和供货，商家仍可登录处理已有订单、核销和售后。恢复经营不会重新上架已停售批次。</p>
    <div class="row" style="margin-top: 22px; gap: 12px; flex-wrap: wrap">
      <input v-model="keyword" class="input" style="max-width: 320px" placeholder="搜索门店名称、地址" />
      <select v-model="status" class="input" style="max-width: 180px"><option value="">全部经营状态</option><option value="active">正常经营</option><option value="suspended">暂停经营</option></select>
      <span class="dim">共 {{ stores.length }} 家</span>
    </div>
    <p v-if="message" class="notice" style="margin-top: 16px" role="status">{{ message }}</p>
    <div v-for="s in stores" :key="s.id" class="card" style="margin-top: 16px">
      <div class="row" style="gap: 12px; flex-wrap: wrap">
        <div class="stack"><strong>{{ s.name }}</strong><span class="dim">{{ s.category }} · {{ s.address }}</span></div>
        <StatusPill :status="s.operationStatus" />
      </div>
      <p class="dim" style="margin-top: 12px">联系电话：{{ s.phone }} · 商家：{{ store.accounts.find(a => a.id === s.ownerId)?.name || '—' }}</p>
      <p v-if="s.operationAt" style="margin-top: 10px">最近处置：{{ s.operationReason }} · {{ fmtDT(s.operationAt) }}</p>
      <button class="btn btn-ghost btn-sm" style="margin-top: 14px" :class="{ 'btn-danger': s.operationStatus === 'active' }" @click="select(s)">{{ s.operationStatus === 'suspended' ? '恢复经营' : '暂停经营' }}</button>
    </div>
    <p v-if="!stores.length" class="notice" style="margin-top: 16px">没有符合条件的门店。</p>
    <div v-if="selected" class="drawer-mask" @click.self="!busy && (selected = null)">
      <form class="drawer" @submit.prevent="submit">
        <h2 class="display">{{ target === 'suspended' ? '暂停经营' : '恢复经营' }} · {{ selected.name }}</h2>
        <p class="dim" style="margin-top: 16px">{{ target === 'suspended' ? '确认后停止新增订单、发布批次和追加库存。已有订单继续履约；食品安全等无法履约情况需另行处理订单和售后。' : '恢复后，未过期且未停售的批次可继续售卖；已下架批次不会自动恢复。' }}</p>
        <label class="field" style="margin-top: 20px">处理原因<textarea v-model="reason" class="textarea" required maxlength="500" placeholder="填写具体原因，将通知商家并写入操作审计"></textarea></label>
        <p v-if="error" class="notice notice-warn" role="alert">{{ error }}</p>
        <div class="row" style="margin-top: 16px; justify-content: flex-end; gap: 12px">
          <button type="button" class="btn btn-ghost" :disabled="busy" @click="selected = null">取消</button>
          <button class="btn btn-ink" :disabled="busy || !reason.trim()">{{ busy ? '提交中…' : '确认处理' }}</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useStore } from '../../store'
import { fmtDT } from '../../utils'
import StatusPill from '../../components/StatusPill.vue'
const store = useStore()
const keyword = ref('')
const status = ref('')
const selected = ref(null)
const target = ref('')
const reason = ref('')
const busy = ref(false)
const error = ref('')
const message = ref('')
const stores = computed(() => store.stores.filter(s => (!status.value || s.operationStatus === status.value) && `${s.name} ${s.address}`.includes(keyword.value.trim())))
function select(s) {
  selected.value = s
  target.value = s.operationStatus === 'suspended' ? 'active' : 'suspended'
  reason.value = ''; error.value = ''; message.value = ''
}
async function submit() {
  if (busy.value || !reason.value.trim()) return
  busy.value = true
  try {
    const r = await store.setStoreOperation(selected.value.id, target.value, reason.value.trim())
    if (!r.ok) { error.value = r.msg; return }
    message.value = '经营状态已更新，已通知商家并记录操作日志。'
    selected.value = null
  } finally { busy.value = false }
}
</script>
