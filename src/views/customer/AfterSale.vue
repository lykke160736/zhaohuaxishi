<template>
  <div class="phone" v-if="o">
    <div class="page page-narrow">
      <PageHead title="特殊售后" :kicker="`订单 ${o.no}`" />

      <div class="notice" style="margin-top: 14px">
        特殊售后用于食品质量、到店无法履约等商家责任场景，不受普通退款「15 分钟 / 2 小时」限制。提交后商家须在 24 小时内处理。
      </div>

      <div class="card" style="margin-top: 16px">
        <div class="field">
          <label>问题描述</label>
          <textarea class="textarea" v-model="reason" placeholder="请描述遇到的问题，例如：食品变质、到店后商家无法交付…"></textarea>
        </div>
        <div class="field">
          <label>证据照片（最多 6 张）</label>
          <div class="evidence-imgs" style="margin-top: 0">
            <div v-for="(p, i) in photos" :key="p.url" class="evidence-item">
              <img class="evidence-img" :src="p.url" alt="证据照片" />
              <button class="evidence-del" type="button" @click="removePhoto(i)">×</button>
            </div>
            <label v-if="photos.length < 6" class="evidence-add">
              <input type="file" accept="image/*" multiple hidden @change="onPick" />
              <span>{{ uploading ? '上传中…' : '＋ 照片' }}</span>
            </label>
          </div>
        </div>
        <div class="field" style="margin-bottom: 0">
          <label>证据说明（选填）</label>
          <textarea class="textarea" v-model="evidenceNote" style="min-height: 64px" placeholder="例如：吐司底部整片焦黑、与店员沟通未果…"></textarea>
        </div>
      </div>

      <div v-if="msg" class="notice notice-warn" style="margin-top: 12px">{{ msg }}</div>

      <button class="btn btn-ink btn-block" style="margin-top: 18px; padding: 15px" @click="submit">
        提交售后申请
      </button>
    </div>
    <BottomNav />
  </div>

  <div v-else class="phone">
    <div class="page page-narrow">
      <div class="card" style="margin-top: 36px; text-align: center; padding: 44px 22px">
        <span class="kicker">无法申请售后</span>
        <h2 class="display" style="font-size: 1.5rem; margin-top: 10px">订单不存在或无权访问</h2>
        <p class="tone-2" style="font-size: 0.88rem; margin-top: 10px; line-height: 1.7">
          售后申请仅限订单所属顾客提交，请切换到对应账号后再试。
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
import { useRoute, useRouter } from 'vue-router'
import { useStore } from '../../store'
import { api } from '../../api'
import PageHead from '../../components/PageHead.vue'
import BottomNav from '../../components/BottomNav.vue'

const route = useRoute()
const router = useRouter()
const store = useStore()

// 保留响应式查询：切换订单 ID 时自动重新选中，非本人订单视为无权访问
const o = computed(() => store.myOrder(route.params.orderId))
const reason = ref('')
const photos = ref([])
const evidenceNote = ref('')
const uploading = ref(false)
const msg = ref('')

async function onPick(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length) return
  const room = 6 - photos.value.length
  if (room <= 0) {
    msg.value = '最多上传 6 张照片'
    return
  }
  uploading.value = true
  for (const f of files.slice(0, room)) {
    if (!f.type.startsWith('image/')) {
      msg.value = '仅支持图片文件'
      continue
    }
    const r = await api.upload(f)
    if (!r.ok) {
      msg.value = r.msg || '照片上传失败，请重试'
      continue
    }
    photos.value.push({ url: r.url })
  }
  uploading.value = false
}

const removePhoto = (i) => photos.value.splice(i, 1)

async function submit() {
  if (!o.value) {
    msg.value = '订单不存在或无权访问'
    return
  }
  if (!reason.value.trim()) {
    msg.value = '请填写问题描述'
    return
  }
  if (!photos.value.length) {
    msg.value = '请至少上传 1 张证据照片'
    return
  }
  const text = evidenceNote.value.trim() || `现场照片 ${photos.value.length} 张`
  const evidence = JSON.stringify({ text, images: photos.value.map((p) => p.url) })
  const r = await store.submitAftersale(o.value.id, reason.value.trim(), evidence)
  if (!r.ok) {
    msg.value = r.msg
    return
  }
  router.replace(`/order/${o.value.id}`)
}
</script>
