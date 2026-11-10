<template>
  <div>
    <span class="kicker">UC-0504 · 处理期限 24 小时</span>
    <h1 class="display" style="font-size: 2.2rem; margin-top: 6px">售后处理</h1>

    <h2 class="kicker" style="margin-top: 24px">待处理（{{ list.length }}）</h2>
    <div v-for="a in list" :key="a.id" class="card" style="margin-top: 12px">
      <div class="row">
        <span class="kicker mono">售后 {{ a.id }} · 订单 {{ orderOf(a)?.no }}</span>
        <span class="tag tag-accent">剩余 {{ left(new Date(a.deadline) - store.nowMs) }}</span>
      </div>
      <p style="margin-top: 10px; font-size: 0.95rem">{{ a.reason }}</p>
      <p class="dim" style="font-size: 0.82rem; margin-top: 6px">证据：{{ ev(a).text }}</p>
      <div v-if="ev(a).images.length" class="evidence-imgs">
        <a v-for="(src, i) in ev(a).images" :key="i" :href="src" target="_blank" rel="noopener">
          <img class="evidence-img" :src="src" alt="售后证据图" />
        </a>
      </div>
      <div class="field" style="margin-top: 14px; margin-bottom: 0">
        <textarea class="textarea" v-model="replies[a.id]" style="min-height: 60px" placeholder="处理意见（同意退款或说明拒绝理由）"></textarea>
      </div>
      <div class="row" style="margin-top: 12px; gap: 10px; justify-content: flex-end">
        <button class="btn btn-ghost btn-sm btn-danger" @click="handle(a, false)">拒绝</button>
        <button class="btn btn-ink btn-sm" @click="handle(a, true)">同意退款</button>
      </div>
    </div>
    <div v-if="!list.length" class="notice" style="margin-top: 12px">没有待处理的售后申请。</div>

    <h2 class="kicker" style="margin-top: 30px">历史处理记录</h2>
    <div class="card" style="margin-top: 12px; padding: 6px 20px">
      <div v-for="a in history" :key="a.id" style="padding: 14px 0; border-bottom: 1px solid var(--line)">
        <div class="row">
          <span class="mono" style="font-size: 0.82rem">{{ a.id }} · 订单 {{ orderOf(a)?.no }}</span>
          <StatusPill :status="a.status" />
        </div>
        <p style="font-size: 0.88rem; margin-top: 8px">{{ a.reason }}</p>
        <div v-if="ev(a).images.length" class="evidence-imgs">
          <a v-for="(src, i) in ev(a).images" :key="i" :href="src" target="_blank" rel="noopener">
            <img class="evidence-img" :src="src" alt="售后证据图" />
          </a>
        </div>
        <p v-if="a.reply" class="dim" style="font-size: 0.8rem; margin-top: 4px">我的回复：{{ a.reply }}</p>
        <p v-if="a.platformNote" class="dim" style="font-size: 0.8rem; margin-top: 4px">平台裁决：{{ a.platformNote }}</p>
      </div>
      <p v-if="!history.length" class="dim" style="padding: 16px 0; text-align: center; font-size: 0.88rem">暂无记录</p>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useStore } from '../../store'
import { left, parseEvidence } from '../../utils'
import StatusPill from '../../components/StatusPill.vue'

const store = useStore()
const replies = ref({})

const list = computed(() => store.pendingAftersales)
const history = computed(() => {
  const sid = store.merchantStore.id
  return store.aftersales.filter((a) => {
    const o = store.orders.find((x) => x.id === a.orderId)
    return o && o.storeId === sid && a.status !== 'merchant_review'
  })
})

const orderOf = (a) => store.orders.find((x) => x.id === a.orderId)
const ev = (a) => parseEvidence(a.evidence)

async function handle(a, agree) {
  const reply = (replies.value[a.id] || '').trim()
  if (!reply) {
    alert('请填写处理意见')
    return
  }
  const r = await store.handleAftersale(a.id, agree, reply)
  if (!r.ok) alert(r.msg)
}
</script>
