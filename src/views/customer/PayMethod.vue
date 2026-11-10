<template>
  <div class="phone" v-if="o">
    <div class="page page-narrow">
      <PageHead title="支付盲盒" :kicker="`订单 ${o.no}`">
        <span class="mono dim" style="font-size: 0.78rem">
          剩余 {{ left(new Date(o.payDeadline) - store.nowMs) }}
        </span>
      </PageHead>

      <!-- 支付金额 + 商品摘要 -->
      <div class="card-dark" style="margin-top: 14px; text-align: center">
        <span class="kicker" style="color: rgba(242, 237, 226, 0.55)">应付金额</span>
        <div style="font-family: var(--mono); font-size: 3.2rem; font-weight: 600; margin: 4px 0">
          ¥{{ fenInt(payable) }}
        </div>
        <p v-if="coupon" style="font-size: 0.78rem; color: rgba(242, 237, 226, 0.6)">
          原价 ¥{{ fenInt(o.amount) }} · 优惠券 -¥{{ fenInt(coupon.amount) }}
        </p>
        <p style="font-size: 0.85rem; color: rgba(242, 237, 226, 0.75)">
          {{ o.snap.storeName }} · {{ o.snap.category }}盲盒 × 1
        </p>
      </div>

      <!-- 优惠券 -->
      <div v-if="selectable.length" class="card" style="margin-top: 14px; padding: 14px 16px">
        <div class="row">
          <span class="kicker">优惠券</span>
          <span class="kicker dim">{{ selectable.length }} 张可用</span>
        </div>
        <div class="stack" style="gap: 8px; margin-top: 10px">
          <button
            class="coupon-pick"
            :class="{ on: !couponId }"
            @click="couponId = null"
          >
            <span style="font-size: 0.86rem">不使用优惠券</span>
            <span class="mono" style="font-size: 0.85rem">¥{{ fenInt(o.amount) }}</span>
          </button>
          <button
            v-for="c in selectable"
            :key="c.id"
            class="coupon-pick"
            :class="{ on: couponId === c.id }"
            @click="couponId = c.id"
          >
            <span style="font-size: 0.86rem">
              无门槛券 · 减 ¥{{ fenInt(c.amount) }}
            </span>
            <span class="mono" style="font-size: 0.85rem; color: var(--accent)">
              ¥{{ fenInt(o.amount - c.amount) }}
            </span>
          </button>
        </div>
      </div>

      <!-- 支付方式选项卡 -->
      <div class="seg" style="margin-top: 22px">
        <button :class="{ on: method === 'wechat' }" @click="method = 'wechat'">
          <span class="wx-glyph">微</span> 微信支付
        </button>
        <button :class="{ on: method === 'alipay' }" @click="method = 'alipay'">
          <span class="ali-glyph">支</span> 支付宝
        </button>
      </div>

      <!-- 商家收款码 -->
      <div class="card" style="margin-top: 14px; text-align: center; padding: 22px 20px">
        <img v-if="qrUrl" :src="qrUrl" style="width: 200px; height: 200px; margin: 0 auto; display: block" />
        <div v-else class="qr-skeleton"><span class="spinner"></span></div>
        <p class="kicker" style="margin-top: 14px">
          {{ method === 'wechat' ? '请用微信扫描上方二维码' : '请用支付宝扫描上方二维码' }}
        </p>
        <p class="dim" style="font-size: 0.78rem; margin-top: 4px">
          收款账户：{{ o.snap.storeName }}
        </p>
      </div>

      <div style="margin-top: 18px" class="stack">
        <button class="btn btn-accent btn-block" style="padding: 15px" :disabled="busy" @click="confirm">
          {{ busy ? '支付处理中…' : '我已完成支付' }}
        </button>
        <button class="btn btn-ghost btn-block" @click="$router.back()">返回订单</button>
      </div>

      <div v-if="msg" class="notice notice-warn" style="margin-top: 12px">{{ msg }}</div>

      <!-- 确认支付弹层 -->
      <div v-if="showConfirm" class="dialog-mask" @click.self="showConfirm = false">
        <div class="dialog" style="max-width: 320px">
          <h3 class="display" style="font-size: 1.15rem">确认支付</h3>
          <div class="card-dark" style="margin-top: 16px; text-align: center">
            <p style="font-size: 0.82rem; color: rgba(242,237,226,.72)">本次将通过 {{ method === 'wechat' ? '微信支付' : '支付宝' }} 付款</p>
            <div style="font-family: var(--mono); font-size: 1.8rem; font-weight: 600; margin: 6px 0">
              ¥{{ fenInt(payable) }}
            </div>
            <p style="font-size: 0.75rem; color: rgba(242,237,226,.55)">{{ o.snap.storeName }}</p>
          </div>
          <div class="row" style="gap: 10px; margin-top: 16px">
            <button class="btn btn-ghost" style="flex: 1" @click="showConfirm = false">取消</button>
            <button class="btn btn-accent" style="flex: 1" @click="doPay">确认付款</button>
          </div>
        </div>
      </div>
    </div>
    <BottomNav />
  </div>

  <div v-else class="phone">
    <div class="page page-narrow">
      <div class="card" style="margin-top: 36px; text-align: center; padding: 44px 22px">
        <span class="kicker">无法支付</span>
        <h2 class="display" style="font-size: 1.5rem; margin-top: 10px">订单不存在或无权访问</h2>
        <p class="tone-2" style="font-size: 0.88rem; margin-top: 10px; line-height: 1.7">
          请确认该订单属于当前登录账号，或返回订单列表重新进入。
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
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import QRCode from 'qrcode'
import { useStore } from '../../store'
import { fenInt, left } from '../../utils'
import PageHead from '../../components/PageHead.vue'
import BottomNav from '../../components/BottomNav.vue'

const route = useRoute()
const router = useRouter()
const store = useStore()
const o = computed(() => store.myOrder(route.params.id))

const method = ref('wechat')
const msg = ref('')
const qrUrl = ref('')
const showConfirm = ref(false)
const busy = ref(false)

// 可用优惠券：本人、未使用、金额小于订单金额
const couponId = ref(null)
const selectable = computed(() =>
  o.value ? store.myCoupons.filter((c) => c.amount < o.value.amount) : []
)
const coupon = computed(() => selectable.value.find((c) => c.id === couponId.value) || null)
const payable = computed(() => (o.value ? o.value.amount - (coupon.value?.amount || 0) : 0))

// 真实商家收款码（演示 URI，真实扫码需商家后台配置）
const URIS = {
  wechat: 'weixin://wxpay/bizpayurl?pr=ZHX000REAL',
  alipay: 'alipays://platformapi/startapp?appId=20000056&q=https%3A%2F%2Fqr.alipay.com%2FZHX000REAL'
}

async function renderQr() {
  qrUrl.value = await QRCode.toDataURL(URIS[method.value], { width: 400, margin: 1, errorCorrectionLevel: 'M' })
}
onMounted(renderQr)

function confirm() {
  if (!o.value) return
  if (o.value.status !== 'pending_payment') {
    msg.value = '订单当前状态不可支付'
    return
  }
  msg.value = ''
  showConfirm.value = true
}

async function doPay() {
  showConfirm.value = false
  busy.value = true
  try {
    // 第一步：服务端创建支付单（此时订单仍是待支付，库存仍为预留）
    const r = await store.payOrder(o.value.id, couponId.value, method.value)
    if (!r.ok) {
      msg.value = r.msg
      return
    }
    msg.value = '支付渠道处理中，请稍候…'
    // 第二步：轮询渠道回调结果，只有回调确认后订单才会变为待取货
    const result = await store.waitPayment(r.payment.id, 25000)
    if (result.status === 'paid') {
      router.replace(`/order/${o.value.id}`)
      return
    }
    if (result.status === 'failed') {
      msg.value = `支付未成功：${result.failureReason || '渠道返回失败'}`
      return
    }
    msg.value = '渠道尚未返回结果，请稍后在「我的订单」查看支付状态，勿重复付款'
  } finally {
    busy.value = false
  }
}
</script>
