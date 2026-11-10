<template>
  <div class="phone">
    <div class="page page-narrow">
      <div style="padding: 26px 0 4px">
        <span class="kicker">REWARD · 惜食积分</span>
        <h1 class="display" style="font-size: 2rem; margin-top: 6px">积分与优惠券</h1>
      </div>

      <!-- 余额卡 -->
      <div class="card-dark" style="margin-top: 14px">
        <div class="row" style="align-items: flex-end">
          <div class="stack" style="gap: 4px">
            <span class="kicker" style="color: rgba(242,237,226,.55)">可用积分</span>
            <div style="font-family: var(--mono); font-size: 2.9rem; font-weight: 600; line-height: 1">
              {{ wallet.points }}
            </div>
          </div>
          <div class="stack" style="gap: 4px; align-items: flex-end">
            <span class="kicker" style="color: rgba(242,237,226,.55)">可用优惠券</span>
            <div style="font-family: var(--mono); font-size: 1.7rem; font-weight: 600; line-height: 1">{{ usable.length }} 张</div>
          </div>
        </div>
        <p style="font-size: 0.8rem; color: rgba(242,237,226,.6); margin-top: 14px">
          每次到店领取盲盒，按成交金额累积积分（1 元 = 1 分）。积分可兑换无门槛优惠券，下单支付时直接抵扣。
        </p>
      </div>

      <!-- 兑换 -->
      <div class="card" style="margin-top: 14px">
        <div class="row">
          <div class="stack" style="gap: 3px">
            <strong style="font-size: 0.98rem">5 元无门槛券</strong>
            <span class="dim" style="font-size: 0.82rem">兑换需 100 积分 · 支付时可用，不与其他券叠加</span>
          </div>
          <button class="btn btn-ink btn-sm" :disabled="wallet.points < 100" @click="redeem">立即兑换</button>
        </div>
        <p v-if="msg" class="notice" :class="ok ? '' : 'notice-warn'" style="margin-top: 12px">{{ msg }}</p>
      </div>

      <!-- 优惠券 -->
      <h2 class="kicker" style="margin-top: 26px">我的优惠券</h2>
      <div class="grid-cards" style="margin-top: 12px">
        <div v-for="c in allCoupons" :key="c.id" class="coupon" :class="{ used: c.used }">
          <div class="coupon-amt"><span>¥</span>{{ fenInt(c.amount) }}</div>
          <div class="stack" style="gap: 3px; min-width: 0; flex: 1">
            <strong style="font-size: 0.9rem">{{ c.minSpend ? `满 ¥${fenInt(c.minSpend)} 可用` : '无门槛' }}</strong>
            <span class="mono dim" style="font-size: 0.72rem">兑换于 {{ fmtDT(c.createdAt) }}</span>
            <span v-if="c.used" class="mono dim" style="font-size: 0.72rem">已用于订单 {{ orderNo(c.orderId) }}</span>
          </div>
          <span class="tag" :class="c.used ? '' : 'tag-green'">{{ c.used ? '已使用' : '可用' }}</span>
        </div>
      </div>
      <p v-if="!allCoupons.length" class="dim" style="font-size: 0.86rem; margin-top: 12px">
        还没有优惠券，攒够 100 积分即可兑换。
      </p>

      <!-- 积分明细 -->
      <h2 class="kicker" style="margin-top: 26px">积分获取记录</h2>
      <div class="card" style="margin-top: 12px; padding: 6px 20px">
        <div
          v-for="o in pickedOrders"
          :key="o.id"
          class="row"
          style="padding: 12px 0; border-bottom: 1px solid var(--line); font-size: 0.86rem"
        >
          <div class="stack" style="gap: 2px">
            <strong>{{ o.snap.storeName }}</strong>
            <span class="mono dim" style="font-size: 0.74rem">{{ o.no }} · {{ fmtDT(o.pickedAt) }}</span>
          </div>
          <span class="mono" style="color: var(--accent)">+{{ Math.floor(o.amount / 100) }}</span>
        </div>
        <p v-if="!pickedOrders.length" class="dim" style="padding: 16px 0; font-size: 0.86rem">暂无积分记录</p>
      </div>

      <p class="kicker" style="text-align: center; margin-top: 26px">
        积分与优惠券仅用于本账号，退款订单对应积分将不再计入
      </p>
    </div>
    <BottomNav />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useStore } from '../../store'
import { fmtDT, fenInt } from '../../utils'
import BottomNav from '../../components/BottomNav.vue'

const store = useStore()
const msg = ref('')
const ok = ref(true)

const wallet = computed(() => store.myWallet)
const usable = computed(() => store.myCoupons)
const allCoupons = computed(() => wallet.value.coupons || [])
const pickedOrders = computed(() =>
  store.myOrders.filter((o) => o.status === 'picked').slice(0, 8)
)

const orderNo = (id) => store.orders.find((o) => o.id === id)?.no || '—'

async function redeem() {
  const r = await store.redeemCoupon()
  ok.value = r.ok
  msg.value = r.ok ? '兑换成功，已加入我的优惠券' : r.msg
}
</script>
