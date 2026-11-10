<template>
  <div class="phone" v-if="o">
    <div class="page page-narrow">
      <PageHead :title="o.snap.storeName" :kicker="`订单 ${o.no}`">
        <StatusPill :status="o.status" />
      </PageHead>

      <!-- 已领取印章 -->
      <div v-if="o.status === 'picked'" style="text-align: center; margin: 18px 0 4px">
        <span class="stamp">已领取</span>
        <p class="dim mono" style="font-size: 0.75rem; margin-top: 10px">
          {{ fmtDT(o.pickedAt) }} · {{ o.operator || '门店核销' }}
        </p>
      </div>

      <!-- 待支付倒计时 -->
      <div v-if="o.status === 'pending_payment'" class="card-dark" style="margin-top: 14px; text-align: center">
        <span class="kicker" style="color: rgba(242, 237, 226, 0.55)">支付剩余时间</span>
        <div class="mono" style="font-size: 2.6rem; font-weight: 600; margin: 8px 0; color: #ffb387">
          {{ left(new Date(o.payDeadline) - store.nowMs) }}
        </div>
        <p style="font-size: 0.82rem; color: rgba(242, 237, 226, 0.6)">
          超时订单自动关闭，库存立即释放
        </p>
      </div>

      <!-- 交易快照 -->
      <div class="card" style="margin-top: 14px">
        <div class="row" style="padding: 5px 0">
          <span class="tone-2" style="font-size: 0.9rem">商品</span>
          <strong>{{ o.snap.category }}盲盒 × 1</strong>
        </div>
        <hr class="hr" />
        <div class="row" style="padding: 5px 0">
          <span class="tone-2" style="font-size: 0.9rem">成交金额</span>
          <span class="price" style="font-size: 1.2rem">¥{{ fenInt(o.amount) }}</span>
        </div>
        <hr class="hr" />
        <div class="row" style="padding: 5px 0">
          <span class="tone-2" style="font-size: 0.9rem">自提时间</span>
          <strong class="mono" style="font-size: 0.9rem">{{ fmtDT(o.snap.pickupStart) }} – {{ fmtTime(o.snap.pickupEnd) }}</strong>
        </div>
        <hr class="hr" />
        <div class="row" style="padding: 5px 0; align-items: flex-start">
          <span class="tone-2" style="font-size: 0.9rem">自提地点</span>
          <strong style="font-size: 0.88rem; text-align: right; max-width: 58%">{{ o.snap.address }}</strong>
        </div>
        <template v-if="o.channelTx">
          <hr class="hr" />
          <div class="row" style="padding: 5px 0">
            <span class="tone-2" style="font-size: 0.9rem">渠道交易号</span>
            <span class="mono dim" style="font-size: 0.78rem">{{ o.channelTx }}</span>
          </div>
        </template>
      </div>

      <!-- 退款进度 -->
      <div v-if="refund" class="card" style="margin-top: 14px">
        <div class="row">
          <span class="kicker">退款进度</span>
          <StatusPill :status="refund.status" />
        </div>
        <p style="font-size: 0.9rem; margin-top: 10px">{{ refund.reason }}</p>
        <p class="dim" style="font-size: 0.8rem; margin-top: 4px">
          申请 ≠ 到账，资金结果以渠道回调确认为准（服务端自动处理，无需手动确认）
          <template v-if="refund.confirmedAt"> · {{ fmtDT(refund.confirmedAt) }} 已原路退回</template>
        </p>
      </div>

      <!-- 售后进度 -->
      <div v-if="as" class="card" style="margin-top: 14px">
        <div class="row">
          <span class="kicker">特殊售后</span>
          <StatusPill :status="as.status" />
        </div>
        <p style="font-size: 0.9rem; margin-top: 10px">{{ as.reason }}</p>
        <p v-if="asEvidence.text" class="dim" style="font-size: 0.8rem; margin-top: 4px">证据：{{ asEvidence.text }}</p>
        <div v-if="asEvidence.images.length" class="evidence-imgs">
          <a v-for="(src, i) in asEvidence.images" :key="i" :href="src" target="_blank" rel="noopener">
            <img class="evidence-img" :src="src" alt="售后证据图" />
          </a>
        </div>
        <p v-if="as.reply" class="tone-2" style="font-size: 0.85rem; margin-top: 8px; border-left: 2px solid var(--line-2); padding-left: 10px">
          商家回复：{{ as.reply }}
        </p>
        <p v-if="as.platformNote" class="tone-2" style="font-size: 0.85rem; margin-top: 8px; border-left: 2px solid var(--accent); padding-left: 10px">
          平台裁决：{{ as.platformNote }}
        </p>
        <button v-if="as.status === 'rejected'" class="btn btn-ghost btn-sm" style="margin-top: 12px" @click="doEscalate">
          不认可？申请平台介入
        </button>
      </div>

      <!-- 评价（已领取订单） -->
      <div v-if="o.status === 'picked'" class="card" style="margin-top: 14px">
        <div class="row">
          <span class="kicker">评价本次盲盒</span>
          <span v-if="myReview" class="tag tag-green">已评价</span>
        </div>
        <template v-if="!myReview">
          <div class="rate-row" style="margin-top: 10px">
            <button
              v-for="i in 5"
              :key="i"
              class="rate-star"
              :class="{ on: i <= rating }"
              @click="rating = i"
            >★</button>
          </div>
          <div class="field" style="margin-top: 10px">
            <textarea v-model="reviewText" class="input" rows="2" placeholder="说说口味、分量、取货体验（可不填）"></textarea>
          </div>
          <button class="btn btn-ink btn-sm" style="margin-top: 10px" @click="submitReview">提交评价</button>
        </template>
        <template v-else>
          <p style="font-size: 0.9rem; margin-top: 10px">
            <span class="stars">{{ '★'.repeat(myReview.rating) }}<span>{{ '★'.repeat(5 - myReview.rating) }}</span></span>
          </p>
          <p v-if="myReview.text" class="tone-2" style="font-size: 0.85rem; margin-top: 6px">{{ myReview.text }}</p>
        </template>
      </div>

      <!-- 操作区 -->
      <div style="margin-top: 20px" class="stack">
        <template v-if="o.status === 'pending_payment'">
          <button class="btn btn-accent btn-block" style="padding: 15px" @click="$router.push(`/pay/${o.id}`)">
            去支付 ¥{{ fenInt(o.amount) }}
          </button>
          <button class="btn btn-ghost btn-block" @click="cancel">取消订单</button>
        </template>

        <template v-if="o.status === 'to_pickup'">
          <div class="row" style="gap: 10px; margin-bottom: 10px">
            <button class="btn btn-ink" style="flex: 1; padding: 15px" @click="$router.push(`/pickup/${o.id}`)">
              查看取货凭证
            </button>
            <button class="btn btn-ghost" style="flex: 1" @click="$router.push(`/store/${o.storeId}`)">
              导航到店
            </button>
          </div>
          <div class="row" style="gap: 10px">
            <button class="btn btn-ghost" style="flex: 1" @click="applyRefund">申请普通退款</button>
            <button class="btn btn-ghost" style="flex: 1" @click="$router.push(`/aftersale/${o.id}`)">特殊售后</button>
          </div>
          <p class="dim" style="font-size: 0.78rem; text-align: center">
            普通退款条件：付款 15 分钟内，且不晚于取货截止前 2 小时
          </p>
        </template>

        <template v-if="['picked', 'no_show'].includes(o.status) && !as">
          <button class="btn btn-ghost btn-block" @click="$router.push(`/aftersale/${o.id}`)">申请特殊售后</button>
        </template>
      </div>

      <div v-if="msg" class="notice notice-warn" style="margin-top: 14px">{{ msg }}</div>
    </div>
    <BottomNav />
  </div>

  <!-- 订单不存在，或不属于当前登录顾客 -->
  <div v-else class="phone">
    <div class="page page-narrow">
      <div class="card" style="margin-top: 36px; text-align: center; padding: 44px 22px">
        <span class="kicker">无法查看订单</span>
        <h2 class="display" style="font-size: 1.5rem; margin-top: 10px">订单不存在或无权访问</h2>
        <p class="tone-2" style="font-size: 0.88rem; margin-top: 10px; line-height: 1.7">
          该订单可能已被删除，或不属于当前登录账号。<br />切换到订单所属账号后再试。
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
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useStore } from '../../store'
import { fmtTime, fmtDT, fenInt, left, parseEvidence } from '../../utils'
import PageHead from '../../components/PageHead.vue'
import StatusPill from '../../components/StatusPill.vue'
import BottomNav from '../../components/BottomNav.vue'

const route = useRoute()
const store = useStore()
const msg = ref('')

const o = computed(() => store.myOrder(route.params.id))
const refund = computed(() => store.orderRefund(o.value?.id))
const as = computed(() => store.orderAftersale(o.value?.id))
const asEvidence = computed(() => parseEvidence(as.value?.evidence))

// 评价：每单一次，提交后展示只读结果
const rating = ref(5)
const reviewText = ref('')
const myReview = computed(() => store.reviews.find((r) => r.orderId === o.value?.id))

async function submitReview() {
  const r = await store.addReview(o.value.id, { rating: rating.value, text: reviewText.value })
  msg.value = r.ok ? '' : r.msg
}

async function cancel() {
  const r = await store.cancelUnpaid(o.value.id)
  if (!r.ok) msg.value = r.msg
}

async function applyRefund() {
  const r = await store.applyRefund(o.value.id)
  msg.value = r.ok ? '' : r.msg
}

async function doEscalate() {
  const r = await store.escalate(as.value.id)
  if (!r.ok) msg.value = r.msg
}
</script>
