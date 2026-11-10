<template>
  <div>
    <span class="kicker">UC-0402 · 每单仅核销一次</span>
    <h1 class="display" style="font-size: 2.2rem; margin-top: 6px">到店核销</h1>

    <div class="card" style="margin-top: 24px; max-width: 520px; text-align: center; padding: 30px">
      <span class="kicker">输入 8 位数字取货码</span>
      <input
        class="input mono"
        v-model="code"
        maxlength="8"
        inputmode="numeric"
        placeholder="0000 0000"
        style="text-align: center; font-size: 1.6rem; letter-spacing: 0.3em; margin-top: 14px"
        @keyup.enter="verify"
      />
      <button class="btn btn-ink btn-block" style="margin-top: 16px; padding: 14px" @click="verify">核验并交付</button>
      <p class="dim" style="font-size: 0.78rem; margin-top: 12px">
        摄像头被授权拒绝时，数字码输入为正常替代路径；连续失败将被限制并记录
      </p>
    </div>

    <!-- 核销结果 -->
    <div v-if="result" class="card" style="margin-top: 18px; max-width: 520px" :style="result.ok ? 'border-color: rgba(61,122,82,.4)' : 'border-color: rgba(176,49,31,.4)'">
      <template v-if="result.ok">
        <div class="row">
          <strong style="color: var(--green); font-size: 1.1rem">核销成功，可以交付</strong>
          <span class="stamp" style="font-size: 0.9rem; padding: 4px 10px">已核销</span>
        </div>
        <hr class="hr" />
        <div class="row" style="padding: 4px 0"><span class="tone-2">订单号</span><span class="mono">{{ result.order.no }}</span></div>
        <div class="row" style="padding: 4px 0"><span class="tone-2">内容</span><span>{{ result.order.snap.category }}盲盒 × 1</span></div>
        <div class="row" style="padding: 4px 0"><span class="tone-2">金额</span><span class="price">¥{{ fenInt(result.order.amount) }}</span></div>
      </template>
      <template v-else>
        <strong style="color: var(--red)">核销被拒绝</strong>
        <p class="tone-2" style="font-size: 0.9rem; margin-top: 8px">{{ result.msg }}</p>
      </template>
    </div>

    <!-- 今日待取 -->
    <h2 class="kicker" style="margin-top: 32px">待取货订单（{{ waiting.length }}）</h2>
    <div class="card" style="margin-top: 12px; padding: 6px 20px">
      <div v-for="o in waiting" :key="o.id" class="row" style="padding: 13px 0; border-bottom: 1px solid var(--line)">
        <div class="stack">
          <span class="mono" style="font-size: 0.85rem">{{ o.no }}</span>
          <span class="dim" style="font-size: 0.8rem">{{ o.snap.category }}盲盒 · 截止 {{ fmtTime(o.snap.pickupEnd) }}</span>
        </div>
        <button class="btn btn-ghost btn-sm" @click="code = o.pickupCode; verify()">代客核销</button>
      </div>
      <p v-if="!waiting.length" class="dim" style="padding: 18px 0; text-align: center; font-size: 0.88rem">
        当前没有等待取货的订单
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useStore } from '../../store'
import { fmtTime, fenInt } from '../../utils'

const store = useStore()
const code = ref('')
const result = ref(null)

const waiting = computed(() => store.merchantOrders.filter((o) => o.status === 'to_pickup'))

async function verify() {
  if (!/^\d{8}$/.test(code.value.trim())) {
    result.value = { ok: false, msg: '请输入完整的 8 位数字取货码' }
    return
  }
  result.value = await store.verifyCode(code.value)
  if (result.value.ok) code.value = ''
}
</script>
