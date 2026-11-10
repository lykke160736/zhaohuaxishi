<template>
  <div class="phone" v-if="b">
    <div class="page page-narrow">
      <PageHead :title="`${s.name} · ${b.category}盲盒`" :kicker="`批次 ${b.no}`" />

      <div class="card" style="margin-top: 14px; text-align: center; padding: 0; overflow: hidden">
        <img class="food-img" :src="BOX_IMG" alt="盲盒：商家搭配好的当日食物" style="height: 210px" loading="lazy" />
        <div style="padding: 20px 20px 26px">
          <span class="kicker">盲盒价格</span>
          <div class="price" style="font-size: 3.2rem; line-height: 1.1; margin-top: 6px">¥{{ fenInt(b.price) }}</div>
          <p class="dim" style="font-size: 0.85rem; margin-top: 10px">
            内容由商家按价格搭配，购买前不获知具体组合
          </p>
        </div>
      </div>

      <div class="card">
        <div class="row" style="padding: 6px 0">
          <span class="tone-2" style="font-size: 0.9rem">自提时间</span>
          <strong class="mono">{{ today }} {{ fmtTime(b.pickupStart) }} – {{ fmtTime(b.pickupEnd) }}</strong>
        </div>
        <hr class="hr" />
        <div class="row" style="padding: 6px 0">
          <span class="tone-2" style="font-size: 0.9rem">自提地点</span>
          <strong style="font-size: 0.9rem; text-align: right; max-width: 60%">{{ s.address }}</strong>
        </div>
        <hr class="hr" />
        <div class="row" style="padding: 6px 0">
          <span class="tone-2" style="font-size: 0.9rem">剩余数量</span>
          <strong class="mono">{{ b.available }} / {{ b.total }} 份</strong>
        </div>
      </div>

      <div class="notice" style="margin-top: 14px">
        下单后请在 <strong class="mono">3 分钟</strong> 内完成支付，超时订单自动关闭并释放库存。本平台不提供配送，请结合门店位置与取货时间自行安排到店。
      </div>

      <div v-if="msg" class="notice notice-warn" style="margin-top: 12px">{{ msg }}</div>

      <div style="margin-top: 22px">
        <button
          v-if="b.status === 'on_sale'"
          class="btn btn-accent btn-block"
          style="padding: 16px"
          @click="buy"
        >
          立即下单 · ¥{{ fenInt(b.price) }}
        </button>
        <button v-else class="btn btn-block" disabled style="padding: 16px; background: var(--paper-2)">
          {{ { sold_out: '已售罄', stopped: '已停售', suspended: '门店暂停经营', ended: '已结束' }[b.status] }}
        </button>
      </div>
    </div>
    <BottomNav />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from '../../store'
import { fmtTime, fmtDate, fenInt } from '../../utils'
import { BOX_IMG } from '../../food-images'
import PageHead from '../../components/PageHead.vue'
import BottomNav from '../../components/BottomNav.vue'

const route = useRoute()
const router = useRouter()
const store = useStore()
const msg = ref('')

const b = computed(() => store.batchView(store.batches.find((x) => x.id === route.params.id))).value
const s = store.stores.find((x) => x.id === b.storeId)
const today = fmtDate(b.pickupStart)

async function buy() {
  const r = await store.placeOrder(b.id)
  if (!r.ok) {
    msg.value = r.msg
    return
  }
  router.push(`/order/${r.order.id}`)
}
</script>
