<template>
  <div>
    <span class="kicker">UC-0505 · 可追溯的裁决与资金关联</span>
    <h1 class="display" style="font-size: 2.2rem; margin-top: 6px">售后平台介入</h1>

    <div v-for="a in queue" :key="a.id" class="card" style="margin-top: 20px">
      <div class="row">
        <span class="kicker mono">售后 {{ a.id }} · 订单 {{ orderOf(a)?.no }}</span>
        <StatusPill :status="a.status" />
      </div>
      <hr class="hr" />
      <div class="stack" style="gap: 8px; font-size: 0.92rem">
        <p><strong>顾客诉求：</strong>{{ a.reason }}</p>
        <div>
          <p class="tone-2"><strong>证据：</strong>{{ ev(a).text }}</p>
          <div v-if="ev(a).images.length" class="evidence-imgs">
            <a v-for="(src, i) in ev(a).images" :key="i" :href="src" target="_blank" rel="noopener">
              <img class="evidence-img" :src="src" alt="售后证据图" />
            </a>
          </div>
        </div>
        <p class="tone-2" style="border-left: 2px solid var(--line-2); padding-left: 10px">
          <strong>商家结论：</strong>{{ a.reply }}
        </p>
      </div>
      <div class="field" style="margin-top: 14px; margin-bottom: 0">
        <textarea class="textarea" v-model="notes[a.id]" style="min-height: 60px" placeholder="平台裁决意见（将与资金执行记录关联留档）"></textarea>
      </div>
      <div class="row" style="margin-top: 12px; gap: 10px; justify-content: flex-end">
        <button class="btn btn-ghost btn-sm" @click="resolve(a, false)">维持商家结论</button>
        <button class="btn btn-accent btn-sm" @click="resolve(a, true)">裁决退款 ¥{{ fenInt(orderOf(a)?.amount || 0) }}</button>
      </div>
    </div>

    <div v-if="!queue.length" class="notice" style="margin-top: 20px">介入队列为空。顾客不认可商家结论时可申请平台介入。</div>

    <h2 class="kicker" style="margin-top: 30px">已裁决</h2>
    <div class="card" style="margin-top: 12px; padding: 6px 20px">
      <div v-for="a in resolved" :key="a.id" style="padding: 13px 0; border-bottom: 1px solid var(--line)">
        <div class="row">
          <span class="mono" style="font-size: 0.82rem">{{ a.id }}</span>
          <StatusPill :status="a.status" />
        </div>
        <p class="dim" style="font-size: 0.82rem; margin-top: 6px">{{ a.platformNote }}</p>
      </div>
      <p v-if="!resolved.length" class="dim" style="padding: 16px 0; text-align: center; font-size: 0.88rem">暂无裁决记录</p>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useStore } from '../../store'
import { fenInt, parseEvidence } from '../../utils'
import StatusPill from '../../components/StatusPill.vue'

const store = useStore()
const notes = ref({})

const queue = computed(() => store.platformQueue)
const resolved = computed(() => store.aftersales.filter((a) => a.status === 'resolved'))
const orderOf = (a) => store.orders.find((x) => x.id === a.orderId)
const ev = (a) => parseEvidence(a.evidence)

async function resolve(a, refund) {
  const note = (notes.value[a.id] || '').trim()
  if (!note) {
    alert('请填写裁决意见')
    return
  }
  const r = await store.resolveAftersale(a.id, refund, note)
  if (!r.ok) alert(r.msg)
}
</script>
