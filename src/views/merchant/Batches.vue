<template>
  <div>
    <div class="row">
      <div>
        <span class="kicker">UC-0201 ~ 0204</span>
        <h1 class="display" style="font-size: 2.2rem; margin-top: 6px">盲盒批次</h1>
      </div>
      <button class="btn btn-ink" @click="openForm()">+ 发布盲盒</button>
    </div>

    <p class="dim" style="font-size: 0.85rem; margin-top: 10px; max-width: 56em">
      发布后价格、自提时段等交易信息即固定，不可修改；需要变更时请停止旧批次并新建。追加库存只增加同一约定下的数量。
    </p>

    <!-- 草稿箱 -->
    <h2 class="kicker" style="margin-top: 26px">草稿箱（未发布，不对顾客售卖）</h2>
    <div class="card" style="margin-top: 12px; padding: 6px 20px" v-if="drafts.length">
      <div v-for="d in drafts" :key="d.id" class="row" style="padding: 14px 0; border-bottom: 1px solid var(--line)">
        <div class="stack">
          <strong>{{ d.category }}盲盒 · ¥{{ fenInt(d.price) }} × {{ d.qty }} 份</strong>
          <span class="dim mono" style="font-size: 0.78rem">
            自提 {{ fmtDT(d.pickupStart) }} – {{ fmtTime(d.pickupEnd) }} · 更新于 {{ fmtDT(d.updatedAt) }}
          </span>
        </div>
        <div class="row" style="gap: 8px">
          <button class="btn btn-ghost btn-sm" @click="openForm(d)">编辑</button>
          <button class="btn btn-accent btn-sm" @click="publish(d)">发布</button>
        </div>
      </div>
    </div>
    <div v-else class="notice" style="margin-top: 12px">草稿箱为空。按今天实际余量起草，不预填自提时段。</div>

    <!-- 批次列表 -->
    <h2 class="kicker" style="margin-top: 28px">已发布批次</h2>
    <div class="card" style="margin-top: 12px; padding: 6px 20px">
      <div v-for="b in batches" :key="b.id" style="padding: 16px 0; border-bottom: 1px solid var(--line)">
        <div class="row">
          <div class="stack">
            <span class="kicker mono">{{ b.no }} · {{ b.category }} · 发布于 {{ fmtDT(b.publishedAt) }}</span>
            <strong style="font-size: 1.05rem">
              ¥{{ fenInt(b.price) }} · {{ fmtTime(b.pickupStart) }}–{{ fmtTime(b.pickupEnd) }} 自提
            </strong>
            <span v-if="b.stopReason" class="dim" style="font-size: 0.8rem">停售原因：{{ b.stopReason }}</span>
          </div>
          <StatusPill :status="b.status" />
        </div>
        <div class="row" style="margin-top: 10px">
          <div class="dots">
            <span
              v-for="i in Math.min(b.total, 24)"
              :key="i"
              class="dot"
              :class="{ on: i <= b.sold }"
            ></span>
          </div>
          <span class="mono dim" style="font-size: 0.78rem">
            总量 {{ b.total }} · 已售 {{ b.sold }} · 预留 {{ b.reserved }} · 可售 {{ b.available }}
          </span>
        </div>
        <div class="row" style="margin-top: 12px; gap: 8px" v-if="b.status === 'on_sale'">
          <button class="btn btn-ghost btn-sm" @click="addStockFor = b; addN = 1">追加库存</button>
          <button class="btn btn-ghost btn-sm btn-danger" @click="stopFor = b; stopReason = ''">停止售卖</button>
          <button class="btn btn-ghost btn-sm" style="margin-left: auto" @click="logFor = b">
            库存流水（{{ b.stockLogs.length }}）
          </button>
        </div>
      </div>
    </div>

    <!-- 库存流水详情抽屉 -->
    <div v-if="logFor" class="drawer-mask" @click.self="logFor = null">
      <div class="drawer">
        <button class="close" @click="logFor = null">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" />
          </svg>
        </button>
        <h3 class="display">库存流水 · {{ logFor.no }}</h3>
        <p class="dim" style="font-size: 0.8rem; margin-top: 4px">{{ logFor.category }}盲盒 · ¥{{ fenInt(logFor.price) }}</p>

        <div class="card" style="margin-top: 14px; padding: 14px 16px">
          <div class="row">
            <span class="tone-2" style="font-size: 0.88rem">当前总量</span>
            <span class="mono">{{ logFor.total }}</span>
          </div>
          <hr class="hr" />
          <div class="row">
            <span class="tone-2" style="font-size: 0.88rem">已售</span>
            <span class="mono">{{ logFor.sold }}</span>
          </div>
          <hr class="hr" />
          <div class="row">
            <span class="tone-2" style="font-size: 0.88rem">预留（锁定中）</span>
            <span class="mono">{{ logFor.reserved }}</span>
          </div>
          <hr class="hr" />
          <div class="row">
            <span class="tone-2" style="font-size: 0.88rem">可售</span>
            <span class="mono">{{ store.batchView(logFor).available }}</span>
          </div>
        </div>

        <h4 class="kicker" style="margin-top: 22px">变更明细（{{ logFor.stockLogs.length }} 条）</h4>
        <div v-for="(l, i) in logFor.stockLogs" :key="i" class="card" style="margin-top: 8px; padding: 12px 16px">
          <div class="row">
            <span :style="{ color: l.delta > 0 ? 'var(--green)' : 'var(--red)' }" class="mono" style="font-weight: 600">
              {{ l.delta > 0 ? '+' : '' }}{{ l.delta }} 份
            </span>
            <span class="mono dim" style="font-size: 0.78rem">{{ fmtDT(l.at) }}</span>
          </div>
          <div class="row" style="margin-top: 6px">
            <span class="tone-2" style="font-size: 0.85rem">{{ l.by }}</span>
            <span class="mono dim" style="font-size: 0.75rem">变更后总量 {{ l.after }}</span>
          </div>
        </div>
        <p v-if="!logFor.stockLogs.length" class="dim" style="padding: 18px; text-align: center; font-size: 0.88rem">暂无流水</p>
      </div>
    </div>

    <!-- 新建/编辑草稿弹层 -->
    <div v-if="form" class="dialog-mask" @click.self="!formBusy && (form = null)">
      <div class="dialog">
        <h3 class="display" style="font-size: 1.3rem">{{ form.id ? '编辑草稿' : '发布盲盒' }}</h3>
        <div class="field" style="margin-top: 18px">
          <label>食品类别</label>
          <input class="input" v-model="form.category" type="text" placeholder="如：面包、炸鸡、便当等，按实际售卖品类填写" />
        </div>
        <div class="row" style="gap: 12px">
          <div class="field" style="flex: 1">
            <label>盲盒价格（元）</label>
            <input class="input" v-model="form.price" type="number" min="0.01" step="0.01" placeholder="如 12.90" />
          </div>
          <div class="field" style="flex: 1">
            <label>数量（份）</label>
            <input class="input" v-model="form.qty" type="number" min="1" step="1" placeholder="按实际余量" />
          </div>
        </div>
        <div class="row" style="gap: 12px">
          <div class="field" style="flex: 1">
            <label>自提开始</label>
            <input class="input" v-model="form.start" type="time" />
          </div>
          <div class="field" style="flex: 1">
            <label>自提截止</label>
            <input class="input" v-model="form.end" type="time" />
          </div>
        </div>
        <div v-if="formMsg" class="notice notice-warn" style="margin-bottom: 12px">{{ formMsg }}</div>
        <div class="row" style="gap: 10px">
          <button class="btn btn-ghost" :disabled="formBusy" @click="form = null">取消</button>
          <button class="btn btn-ghost" style="flex: 1" :disabled="formBusy" @click="save(false)">保存草稿</button>
          <button class="btn btn-ink" style="flex: 1" :disabled="formBusy" @click="save(true)">{{ formBusy ? '提交中…' : '直接发布' }}</button>
        </div>
      </div>
    </div>

    <!-- 追加库存 -->
    <div v-if="addStockFor" class="dialog-mask" @click.self="addStockFor = null">
      <div class="dialog">
        <h3 class="display" style="font-size: 1.3rem">追加库存 · {{ addStockFor.no }}</h3>
        <p class="dim" style="font-size: 0.85rem; margin-top: 6px">只能增加数量，不能修改价格或取货时间。</p>
        <div class="field" style="margin-top: 16px">
          <label>本次追加数量</label>
          <input class="input" v-model="addN" type="number" min="1" step="1" />
        </div>
        <div class="row" style="gap: 10px">
          <button class="btn btn-ghost" style="flex: 1" @click="addStockFor = null">取消</button>
          <button class="btn btn-ink" style="flex: 1" @click="doAddStock">确认追加</button>
        </div>
      </div>
    </div>

    <!-- 停售 -->
    <div v-if="stopFor" class="dialog-mask" @click.self="stopFor = null">
      <div class="dialog">
        <h3 class="display" style="font-size: 1.3rem">停止售卖 · {{ stopFor.no }}</h3>
        <p class="dim" style="font-size: 0.85rem; margin-top: 6px">
          停售后新下单被阻止；已付款订单继续履约，交易记录保留。
        </p>
        <div class="field" style="margin-top: 16px">
          <label>停售原因</label>
          <input class="input" v-model="stopReason" placeholder="例如：剩余食品已作其他处置" />
        </div>
        <div class="row" style="gap: 10px">
          <button class="btn btn-ghost" style="flex: 1" @click="stopFor = null">再想想</button>
          <button class="btn btn-danger btn-ghost" style="flex: 1" @click="doStop">确认停售</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useStore } from '../../store'
import { fmtTime, fmtDT, fenInt } from '../../utils'
import StatusPill from '../../components/StatusPill.vue'

const store = useStore()
const sid = store.merchantStore.id

const batches = computed(() => store.storeBatches(sid).slice().reverse())
const drafts = computed(() => store.drafts.filter((d) => d.storeId === sid && !d.published))

const form = ref(null)
const formMsg = ref('')
const formBusy = ref(false)
const addStockFor = ref(null)
const addN = ref(1)
const stopFor = ref(null)
const stopReason = ref('')
const logFor = ref(null)

function toTimeInput(iso) {
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function openForm(d) {
  formMsg.value = ''
  form.value = d
    ? {
        id: d.id,
        category: d.category,
        price: (d.price / 100).toFixed(2),
        qty: d.qty,
        start: toTimeInput(d.pickupStart),
        end: toTimeInput(d.pickupEnd)
      }
    : { id: null, category: '', price: '', qty: '', start: '', end: '' }
}

async function save(publishNow = false) {
  if (formBusy.value) return
  const f = form.value
  if (!f.category || !f.category.trim()) {
    formMsg.value = '请填写食品类别'
    return
  }
  if (!f.start || !f.end) {
    formMsg.value = '请填写自提起止时间'
    return
  }
  const date = new Date()
  const today = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  formBusy.value = true
  try {
    const payload = {
      id: f.id,
      category: f.category,
      price: f.price,
      qty: f.qty,
      pickupStart: new Date(`${today}T${f.start}:00`).toISOString(),
      pickupEnd: new Date(`${today}T${f.end}:00`).toISOString()
    }
    const r = publishNow ? await store.publishBatch(payload) : await store.saveDraft(payload)
    if (!r.ok) {
      formMsg.value = r.msg
      return
    }
    form.value = null
  } finally {
    formBusy.value = false
  }
}

async function publish(d) {
  const r = await store.publishDraft(d.id)
  if (!r.ok) alert(r.msg)
}

async function doAddStock() {
  const r = await store.addStock(addStockFor.value.id, addN.value)
  if (!r.ok) alert(r.msg)
  addStockFor.value = null
}

async function doStop() {
  const r = await store.stopBatch(stopFor.value.id, stopReason.value || '商家停止售卖')
  if (!r.ok) alert(r.msg)
  stopFor.value = null
}
</script>
