<template>
  <span class="tag" :class="cls">{{ text }}</span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ status: String })

const MAP = {
  pending_payment: ['待支付', 'tag-accent'],
  to_pickup: ['待取货', 'tag-solid'],
  picked: ['已领取', 'tag-green'],
  no_show: ['超时未取', ''],
  refunding: ['退款处理中', 'tag-accent'],
  refunded: ['已退款', ''],
  closed: ['已关闭', ''],
  on_sale: ['在售', 'tag-green'],
  sold_out: ['已售罄', ''],
  stopped: ['已停售', 'tag-red'],
  ended: ['已结束', ''],
  merchant_review: ['商家处理中', 'tag-accent'],
  agreed: ['商家已同意', 'tag-green'],
  rejected: ['商家已拒绝', 'tag-red'],
  platform_review: ['平台介入中', 'tag-accent'],
  resolved: ['平台已裁决', 'tag-solid'],
  processing: ['退款中', 'tag-accent'],
  success: ['已到账', 'tag-green'],
  pending: ['待审核', 'tag-accent'],
  approved: ['已通过', 'tag-green'],
  active: ['正常', 'tag-green'],
  disabled: ['已停用', 'tag-red'],
  suspended: ['暂停经营', 'tag-red'],
  customer: ['顾客', ''],
  merchant: ['商家', 'tag-solid'],
  admin: ['平台管理员', 'tag-accent']
}

const text = computed(() => (MAP[props.status] || [props.status, ''])[0])
const cls = computed(() => (MAP[props.status] || ['', ''])[1])
</script>
