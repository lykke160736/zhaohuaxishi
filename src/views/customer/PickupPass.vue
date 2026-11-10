<template>
  <div class="phone" v-if="o">
    <div class="page page-narrow">
      <PageHead title="取货凭证" :kicker="`订单 ${o.no}`" />

      <div v-if="valid" class="card" style="margin-top: 16px; text-align: center; padding: 30px 22px">
        <span class="kicker">到店出示 · 一人一码</span>
        <img v-if="qr" :src="qr" alt="取货二维码" style="width: 200px; height: 200px; margin: 18px auto 8px; display: block" />
        <div class="pickup-code">{{ codePretty }}</div>
        <p class="dim" style="font-size: 0.8rem; margin-top: 12px">二维码不可识别时，可向店员报出上方数字取货码</p>
      </div>

      <div v-else class="card" style="margin-top: 16px; text-align: center; padding: 40px 22px">
        <span class="stamp" v-if="o.status === 'picked'">已核销</span>
        <p class="tone-2" style="margin-top: 16px">{{ invalidWhy }}</p>
      </div>

      <div class="card" style="margin-top: 14px">
        <div class="row" style="padding: 5px 0">
          <span class="tone-2" style="font-size: 0.9rem">门店</span>
          <strong>{{ o.snap.storeName }}</strong>
        </div>
        <hr class="hr" />
        <div class="row" style="padding: 5px 0">
          <span class="tone-2" style="font-size: 0.9rem">取货时间</span>
          <strong class="mono" style="font-size: 0.88rem">{{ fmtDT(o.snap.pickupStart) }} – {{ fmtTime(o.snap.pickupEnd) }}</strong>
        </div>
        <hr class="hr" />
        <div class="row" style="padding: 5px 0; align-items: flex-start">
          <span class="tone-2" style="font-size: 0.9rem">地址</span>
          <strong style="font-size: 0.88rem; text-align: right; max-width: 60%">{{ o.snap.address }}</strong>
        </div>
      </div>

      <div v-if="valid && nearEnd" class="notice notice-warn" style="margin-top: 14px">
        距取货截止不足 30 分钟，请尽快到店。超时未取视为放弃，订单将关闭且不支持普通退款。
      </div>

      <div class="notice" style="margin-top: 14px" v-if="valid">
        凭证仅本人可见；退款受理、取消或核销成功后自动失效，重复扫码不会重复交付。
      </div>
    </div>
    <BottomNav />
  </div>

  <div v-else class="phone">
    <div class="page page-narrow">
      <div class="card" style="margin-top: 36px; text-align: center; padding: 44px 22px">
        <span class="kicker">凭证不可查看</span>
        <h2 class="display" style="font-size: 1.5rem; margin-top: 10px">订单不存在或无权访问</h2>
        <p class="tone-2" style="font-size: 0.88rem; margin-top: 10px; line-height: 1.7">
          取货凭证仅订单所属顾客可见，请切换到对应账号后再试。
        </p>
        <button class="btn btn-ink btn-block" style="margin-top: 20px" @click="$router.replace('/orders')">
          返回我的订单
        </button>
      </div>
    </div>
    <BottomNav />
  </div>
</template>

<script setup>
import { computed, ref, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import QRCode from 'qrcode'
import { useStore } from '../../store'
import { fmtTime, fmtDT } from '../../utils'
import PageHead from '../../components/PageHead.vue'
import BottomNav from '../../components/BottomNav.vue'

const route = useRoute()
const store = useStore()
const qr = ref('')

// 保留响应式查询：同一组件内切换订单 ID 时会重新选中订单，非本人订单视为无权访问
const o = computed(() => store.myOrder(route.params.id))

const valid = computed(() => o.value?.status === 'to_pickup')
const invalidWhy = computed(() => {
  const m = {
    picked: '该凭证已完成核销，感谢你的珍惜。',
    refunding: '退款受理中，取货凭证已失效。',
    refunded: '该订单已退款，凭证失效。',
    closed: '订单已关闭，未生成有效凭证。',
    no_show: '已超过取货截止时间，凭证失效。'
  }
  return m[o.value?.status] || '凭证不可用'
})

const codePretty = computed(() =>
  o.value?.pickupCode ? o.value.pickupCode.replace(/(\d{4})(\d{4})/, '$1 $2') : ''
)

const nearEnd = computed(
  () => valid.value && new Date(o.value.snap.pickupEnd).getTime() - store.nowMs < 30 * 60000
)

// 二维码承载安全随机令牌，不含可猜测的订单序号；订单变化时重新生成
watchEffect(async () => {
  const order = o.value
  if (!order || order.status !== 'to_pickup' || !order.qrToken) {
    qr.value = ''
    return
  }
  qr.value = await QRCode.toDataURL(`zhx://pickup/${order.qrToken}`, {
    width: 400,
    margin: 1,
    color: { dark: '#181410', light: '#fcfaf4' }
  })
})
</script>
