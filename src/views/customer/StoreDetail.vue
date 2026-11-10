<template>
  <div class="phone" v-if="s">
    <div class="page page-narrow">
      <PageHead :title="s.name" :kicker="`${s.category} · ${fmtKm(km)} · ${s.phone}`" />

      <div class="row" style="margin-top: 12px">
        <span v-if="rating" class="tag">★ {{ rating.avg.toFixed(1) }} · {{ rating.count }} 条评价</span>
        <button class="btn btn-ghost btn-sm" @click="store.toggleFavorite(s.id)">
          {{ store.isFavorite(s.id) ? '★ 已收藏' : '☆ 收藏门店' }}
        </button>
      </div>

      <div class="hero-band" style="margin-top: 14px">
        <img class="food-img" :src="storeImg(s)" :alt="s.name" loading="lazy" />
        <div class="fade"></div>
        <div class="cap">
          <p style="font-size: 1.02rem; font-weight: 600">{{ s.notice }}</p>
          <p class="mono dim" style="font-size: 0.74rem; margin-top: 2px">{{ s.address }}</p>
        </div>
      </div>

      <!-- 真实导航：两个按钮并排，同窗口跳转 -->
      <div class="nav-row" style="margin-top: 14px">
        <button class="nav-btn primary" @click="go('amap')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 21s-7-5.3-7-11a7 7 0 0 1 14 0c0 5.7-7 11-7 11Z" />
            <circle cx="12" cy="10" r="2.6" />
          </svg>
          高德导航
        </button>
        <button class="nav-btn" @click="go('tencent')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M4 4h7v7H4zM13 13h7v7h-7z" stroke-linejoin="round" />
            <path d="M11 7l2 2M17 15l2 2" stroke-linecap="round" />
          </svg>
          腾讯导航
        </button>
      </div>

      <div style="margin-top: 22px" class="row">
        <span class="kicker">盲盒取货批次</span>
        <span class="kicker dim">{{ batches.length }} 个</span>
      </div>

      <div style="margin-top: 12px">
        <div v-for="b in batches" :key="b.id" class="card" style="cursor: pointer" @click="open(b)">
          <div class="row">
            <div class="stack">
              <span class="kicker mono">{{ b.no }} · {{ b.category }}</span>
              <strong class="display" style="font-size: 1.15rem">
                {{ fmtDate(b.pickupStart) }} {{ fmtTime(b.pickupStart) }} – {{ fmtTime(b.pickupEnd) }} 自提
              </strong>
            </div>
            <StatusPill :status="b.status" />
          </div>
          <hr class="hr" />
          <div class="row">
            <span class="mono dim" style="font-size: 0.78rem">
              共 {{ b.total }} 份 · 已售 {{ b.sold }} · 余 {{ b.available }}
            </span>
            <span class="price" style="font-size: 1.25rem">¥{{ fenInt(b.price) }}</span>
          </div>
        </div>
      </div>

      <EmptyState v-if="!batches.length" glyph="歇" text="今天没有盲盒上架" />

      <template v-if="reviews.length">
        <div class="row" style="margin-top: 22px">
          <span class="kicker">顾客评价</span>
          <span class="kicker dim">{{ reviews.length }} 条</span>
        </div>
        <div class="card" style="margin-top: 12px; padding: 6px 20px">
          <div v-for="r in reviews" :key="r.id" style="padding: 12px 0; border-bottom: 1px solid var(--line)">
            <div class="row">
              <strong style="font-size: 0.88rem">{{ r.userName }}</strong>
              <span class="stars">{{ '★'.repeat(r.rating) }}<span>{{ '★'.repeat(5 - r.rating) }}</span></span>
            </div>
            <p v-if="r.text" style="font-size: 0.85rem; margin-top: 4px">{{ r.text }}</p>
            <p class="mono dim" style="font-size: 0.72rem; margin-top: 4px">{{ fmtDT(r.createdAt) }}</p>
          </div>
        </div>
      </template>
    </div>
    <BottomNav />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from '../../store'
import { amapNavUrl, tencentNavUrl, fmtKm, fmtTime, fmtDate, fmtDT, fenInt, haversineKm, wgs84ToGcj02 } from '../../utils'
import { storeImg } from '../../food-images'
import PageHead from '../../components/PageHead.vue'
import StatusPill from '../../components/StatusPill.vue'
import EmptyState from '../../components/EmptyState.vue'
import BottomNav from '../../components/BottomNav.vue'

const route = useRoute()
const router = useRouter()
const store = useStore()

const s = computed(() => store.stores.find((x) => x.id === route.params.id)).value
const km = haversineKm(store.browseCenter, s)
const batches = computed(() => store.storeBatches(s.id))
const rating = computed(() => store.storeRating(s.id))
const reviews = computed(() => store.storeReviews(s.id))

function open(b) {
  router.push(`/batch/${b.id}`)
}

// 导航：起点=当前浏览中心（GCJ-02），终点=门店（GCJ-02），默认驾车模式
// 同窗口跳转（window.location.href），绕开浏览器弹窗拦截
const sGcj = computed(() => wgs84ToGcj02(s.lat, s.lng))
const fromGcj = computed(() => wgs84ToGcj02(store.browseCenter.lat, store.browseCenter.lng))

function go(brand) {
  const fn = brand === 'amap' ? amapNavUrl : tencentNavUrl
  const url = fn({
    lng: sGcj.value.lng,
    lat: sGcj.value.lat,
    name: s.name,
    fromLng: fromGcj.value.lng,
    fromLat: fromGcj.value.lat,
    mode: 'car'
  })
  window.location.href = url
}
</script>
