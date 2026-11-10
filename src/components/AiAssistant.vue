<template>
  <button v-if="!open" class="ai-fab" title="AI 助手" @click="open = true">
    <span class="ai-fab-glyph">✦</span> AI
  </button>

  <div v-else class="ai-panel">
    <div class="ai-head">
      <div class="stack" style="gap: 3px">
        <strong class="display" style="font-size: 1.05rem">小惜 · AI 助手</strong>
        <span class="ai-status" :class="{ on: configured }">{{ statusText }}</span>
      </div>
      <button class="backbtn" style="width: 32px; height: 32px" title="收起" @click="open = false">✕</button>
    </div>

    <div ref="msgBox" class="ai-msgs">
      <div v-if="!msgs.length" class="ai-empty">
        <p class="dim" style="font-size: 0.86rem">你好，我是小惜。{{ hintText }}</p>
        <div class="ai-chips" style="margin-top: 10px">
          <button v-for="s in suggestions" :key="s" class="chip" @click="ask(s)">{{ s }}</button>
        </div>
      </div>
      <div v-for="(m, i) in msgs" :key="i" class="ai-msg" :class="m.role">
        <div class="ai-bubble">{{ m.text }}</div>
        <div v-if="m.queries?.length" class="dim" style="font-size: 0.72rem; margin-top: 6px">
          已查询：{{ [...new Set(m.queries.map(q => q.name))].join('、') }} · {{ new Date(m.queries.at(-1).queriedAt).toLocaleTimeString('zh-CN') }}
        </div>

      </div>
      <div v-if="busy" class="ai-msg assistant">
        <div class="ai-bubble dim">正在理解问题并查询业务数据…</div>
      </div>
    </div>

    <div class="ai-inputrow">
      <input v-model="text" class="input" maxlength="4000" placeholder="查询盲盒、订单或经营数据…" @keyup.enter="send" />
      <button class="btn btn-accent" style="padding: 10px 18px" :disabled="busy || !text.trim()" @click="send">发送</button>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useStore } from '../store'
import { aiChat, aiStatus } from '../ai'

const store = useStore()
const open = ref(false)
const text = ref('')
const busy = ref(false)
const msgs = ref([])
const msgBox = ref(null)
const configured = ref(false)
const statusText = ref('正在检查连接')
let generation = 0
const role = computed(() => store.session.role)
const suggestions = computed(() => ({
  customer: ['附近20元以内有面包盲盒吗？', '我的订单什么时候取？', '我的订单还能普通退款吗？', '积分兑换规则是什么？'],
  merchant: ['今天的经营情况怎么样？', '有哪些待取货订单？', '哪些批次快截止了还有库存？', '有哪些售后待处理？'],
  admin: ['待审核入驻有几条？', '平台介入队列情况如何？', '平台订单各状态有多少？']
}[role.value] || []))
const hintText = computed(() => ({
  customer: '可以按预算找盲盒、查询本人订单和退款资格。',
  merchant: '可以查询本店库存、待取订单和经营情况。',
  admin: '可以查询入驻审核、售后介入和平台统计。'
}[role.value] || ''))

watch(() => store.session.userId, () => {
  generation++
  msgs.value = []
  text.value = ''
  busy.value = false
  open.value = false
})
watch(open, async (value) => {
  if (!value) return
  const current = generation
  const status = await aiStatus()
  if (current !== generation) return
  configured.value = status.configured
  statusText.value = status.message
})

function ask(q) { text.value = q; send() }
async function send() {
  const q = text.value.trim()
  if (!q || busy.value) return
  const current = generation
  const history = msgs.value.filter(m => !m.error).slice(-8).map(m => ({ role: m.role, content: m.text }))
  msgs.value.push({ role: 'user', text: q })
  text.value = ''
  busy.value = true
  scroll()
  try {
    const reply = await aiChat([...history, { role: 'user', content: q }])
    if (current !== generation) return
    msgs.value.push({ role: 'assistant', ...reply })
    configured.value = true
    statusText.value = '智能服务已连接'
  } catch (e) {
    if (current !== generation) return
    msgs.value.push({ role: 'assistant', text: e.message, error: true })
    configured.value = false
    statusText.value = '智能服务暂不可用'
  } finally {
    if (current === generation) { busy.value = false; scroll() }
  }
}
function scroll() {
  nextTick(() => { if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight })
}
</script>
