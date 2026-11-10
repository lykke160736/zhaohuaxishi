<template>
  <div>
    <div class="row">
      <div class="stack" style="gap: 2px">
        <span class="kicker">ORDERS</span>
        <h1 class="display" style="font-size: 2.2rem; margin-top: 6px">订单管理</h1>
      </div>
      <button class="btn btn-ghost btn-sm" title="导出全部订单为 CSV" @click="exportCsv">导出 CSV</button>
    </div>

    <div class="card" style="margin-top: 22px; padding: 6px 20px">
      <div v-for="o in store.merchantOrders" :key="o.id" style="padding: 15px 0; border-bottom: 1px solid var(--line)">
        <div class="row">
          <div class="stack">
            <span class="mono" style="font-size: 0.85rem">{{ o.no }}</span>
            <span class="dim" style="font-size: 0.8rem">
              {{ fmtDT(o.createdAt) }} 下单 · {{ o.snap.category }}盲盒 · 自提 {{ fmtTime(o.snap.pickupStart) }}–{{ fmtTime(o.snap.pickupEnd) }}
            </span>
            <span v-if="o.cancelReason" class="dim" style="font-size: 0.8rem">取消原因：{{ o.cancelReason }}</span>
          </div>
          <div class="row" style="gap: 12px">
            <span class="price">¥{{ fenInt(o.amount) }}</span>
            <StatusPill :status="o.status" />
          </div>
        </div>
        <div v-if="o.status === 'to_pickup'" class="row" style="margin-top: 10px; justify-content: flex-end; gap: 8px">
          <button class="btn btn-ghost btn-sm btn-danger" @click="cancelFor = o; reason = ''">无法履约，取消并退款</button>
        </div>
        <div v-if="refundOf(o)" class="row" style="margin-top: 10px">
          <span class="tag" :class="refundOf(o).status === 'success' ? 'tag-green' : 'tag-accent'">
            退款 {{ refundOf(o).status === 'success' ? '已到账' : '渠道处理中' }}
          </span>
          <span v-if="refundOf(o).status === 'processing'" class="dim" style="font-size: 0.78rem">
            到账结果由支付渠道回调确认，无需手动操作
          </span>
        </div>
      </div>
    </div>

    <div v-if="cancelFor" class="dialog-mask" @click.self="cancelFor = null">
      <div class="dialog">
        <h3 class="display" style="font-size: 1.3rem">取消无法履约订单</h3>
        <p class="dim" style="font-size: 0.85rem; margin-top: 8px">
          将先暂停受影响批次售卖，再对订单 {{ cancelFor.no }} 发起全额退款，资金结果持续追踪。
        </p>
        <div class="field" style="margin-top: 16px">
          <label>取消原因</label>
          <input class="input" v-model="reason" placeholder="例如：设备故障，剩余食品无法交付" />
        </div>
        <div class="row" style="gap: 10px">
          <button class="btn btn-ghost" style="flex: 1" @click="cancelFor = null">返回</button>
          <button class="btn btn-danger btn-ghost" style="flex: 1" @click="doCancel">确认取消并退款</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useStore } from '../../store'
import { fmtTime, fmtDT, fenInt } from '../../utils'
import StatusPill from '../../components/StatusPill.vue'

const store = useStore()
const cancelFor = ref(null)
const reason = ref('')

const refundOf = (o) => store.orderRefund(o.id)

// 导出订单 CSV：加 BOM 让 Excel 正确识别中文
const STATUS_TEXT = {
  pending_payment: '待支付', to_pickup: '待取货', picked: '已领取',
  closed: '已关闭', refunding: '退款中', refunded: '已退款', no_show: '超时未取'
}

function exportCsv() {
  const rows = [['订单号', '下单时间', '品类', '金额(元)', '状态', '支付时间', '取货码']]
  for (const o of store.merchantOrders) {
    rows.push([
      o.no,
      fmtDT(o.createdAt),
      o.snap.category,
      (o.amount / 100).toFixed(2),
      STATUS_TEXT[o.status] || o.status,
      o.paidAt ? fmtDT(o.paidAt) : '',
      o.pickupCode || ''
    ])
  }
  const csv = '﻿' + rows.map((r) => r.map((c) => `"${String(c).replaceAll('"', '""')}"`).join(',')).join('\r\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = `订单导出_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

async function doCancel() {
  if (!reason.value.trim()) return
  const r = await store.merchantCancel(cancelFor.value.id, reason.value.trim())
  if (!r.ok) alert(r.msg)
  cancelFor.value = null
}
</script>
