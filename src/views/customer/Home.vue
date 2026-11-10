<template>
  <div class="phone">
    <div class="page">
      <!-- 品牌区 -->
      <div style="padding: 26px 0 4px" class="row">
        <span class="kicker">ZHAOHUAXISHI · 余量盲盒</span>
        <!-- 真实可点击的城市入口：显示当前定位状态 -->
        <button class="city-btn" @click="$router.push('/location')" :title="'更换浏览位置'">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1">
            <path d="M12 21s-7-5.3-7-11a7 7 0 0 1 14 0c0 5.7-7 11-7 11Z" />
            <circle cx="12" cy="10" r="2.6" />
          </svg>
          <span>{{ store.browseCenter.city }}</span>
          <span v-if="store.browseCenter.source === 'gps'" class="city-dot" title="已使用设备真实定位"></span>
          <span v-else class="city-dot" style="background: var(--ink-3)" title="默认浏览点，点击定位"></span>
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" style="margin-left: -2px">
            <path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </div>

      <!-- 主视觉：食物平铺 + 渐隐 -->
      <div class="hero-band" style="margin-top: 14px">
        <img class="food-img" :src="HERO_IMG" alt="新鲜出炉的面包与点心" loading="lazy" />
        <div class="fade"></div>
        <div class="cap">
          <h1 class="display" style="font-size: clamp(2.3rem, 8.5vw, 3.1rem)">
            今天的食物，<br /><span class="dim">今天吃完。</span>
          </h1>
        </div>
      </div>
      <p class="tone-2" style="font-size: 0.92rem; max-width: 30em; margin-top: 12px">
        附近门店当天未售完的食品，按盲盒低价上架。店里装好什么就是什么，到店自提，不配送。
      </p>

      <!-- 今日概览 -->
      <div class="hero-stats">
        <div class="hero-stat">
          <span class="kicker">在售门店</span>
          <strong>{{ store.storesNearby.length }}<span class="unit">家</span></strong>
        </div>
        <div class="hero-stat">
          <span class="kicker">今日余量</span>
          <strong>{{ totalLeft }}<span class="unit">份</span></strong>
        </div>
        <div class="hero-stat">
          <span class="kicker">最低价</span>
          <strong class="accent">¥{{ fenInt(minPrice) }}</strong>
        </div>
      </div>

      <!-- 浏览位置 -->
      <button class="card row" style="width: 100%; margin-top: 20px; text-align: left" @click="$router.push('/location')">
        <div class="row" style="gap: 10px; min-width: 0">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="1.8" style="flex-shrink: 0">
            <path d="M12 21s-7-5.3-7-11a7 7 0 0 1 14 0c0 5.7-7 11-7 11Z" />
            <circle cx="12" cy="10" r="2.6" />
          </svg>
          <div class="stack" style="min-width: 0">
            <span class="kicker">
              {{ store.browseCenter.source === 'gps' ? '设备真实定位' : store.browseCenter.source === 'manual' ? '手动选定位置' : '默认浏览点' }}
            </span>
            <strong style="font-size: 0.95rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
              {{ store.browseCenter.name }}
            </strong>
            <span class="mono dim" style="font-size: 0.68rem">
              {{ store.browseCenter.lat.toFixed(4) }}, {{ store.browseCenter.lng.toFixed(4) }}
            </span>
          </div>
        </div>
        <span class="tag">{{ store.browseCenter.source === 'gps' ? '更换' : '去定位' }}</span>
      </button>

      <!-- 搜索 + 推荐 -->
      <div class="search-row" style="margin-top: 18px">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--ink-3)" stroke-width="2">
          <circle cx="11" cy="11" r="7" />
          <path d="M20 20l-3.5-3.5" stroke-linecap="round" />
        </svg>
        <input v-model="q" class="search-input" placeholder="搜索门店、品类或地址，如「面包」「沙坡尾」" />
        <button v-if="q" class="search-clear" @click="q = ''">✕</button>
      </div>

      <template v-if="recommended.length">
        <div class="row" style="margin-top: 22px">
          <span class="kicker">为你推荐</span>
          <span class="kicker dim">按你的常买品类与收藏门店匹配</span>
        </div>
        <div class="rec-row">
          <button
            v-for="r in recommended"
            :key="r.batch.id"
            class="rec-card"
            @click="$router.push(`/batch/${r.batch.id}`)"
          >
            <img class="rec-img" :src="storeImg(r.store)" :alt="r.store.name" loading="lazy" />
            <div class="stack" style="gap: 3px; min-width: 0; text-align: left">
              <strong style="font-size: 0.92rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">{{ r.store.name }}</strong>
              <span class="mono dim" style="font-size: 0.72rem">{{ r.batch.category }} · 余 {{ r.batch.available }} · {{ fmtKm(r.store.km) }}</span>
              <span class="price" style="font-size: 0.95rem">¥{{ fenInt(r.batch.price) }}</span>
            </div>
          </button>
        </div>
      </template>

      <!-- 品类 -->
      <div class="chips" style="margin-top: 18px">
        <button
          v-for="c in store.categories"
          :key="c"
          class="chip"
          :class="{ on: cat === c }"
          @click="cat = c"
        >
          {{ c }}
        </button>
        <button class="chip" :class="{ on: onlyFav }" @click="onlyFav = !onlyFav">★ 收藏</button>
      </div>

      <!-- 门店列表 -->
      <div style="margin-top: 18px" class="row">
        <span class="kicker">15 KM 内 · {{ filtered.length }} 家门店 · 有货优先</span>
        <span class="kicker dim">直线距离</span>
      </div>

      <div class="grid-cards" style="margin-top: 12px">
        <div v-for="s in filtered" :key="s.id" class="store-card" @click="$router.push(`/store/${s.id}`)">
          <!-- 门店封面 -->
          <div class="store-cover">
            <img :src="storeImg(s)" :alt="s.name" loading="lazy" />
            <div class="cover-shade"></div>
            <div class="cover-top">
              <span class="cover-cat">{{ s.category }}</span>
              <button
                class="fav-btn cover-fav"
                :class="{ on: store.isFavorite(s.id) }"
                :title="store.isFavorite(s.id) ? '取消收藏' : '收藏门店'"
                @click.stop="store.toggleFavorite(s.id)"
              >★</button>
            </div>
            <div class="cover-bottom">
              <strong class="display">{{ s.name }}</strong>
              <span class="mono cover-meta">
                {{ fmtKm(s.km) }}<template v-if="ratingOf(s.id)"> · ★{{ ratingOf(s.id).avg.toFixed(1) }}</template>
              </span>
            </div>
          </div>

          <!-- 门店信息 -->
          <div class="store-body">
            <span class="dim" style="font-size: 0.78rem; line-height: 1.5">{{ s.notice }}</span>

            <template v-if="liveBatches(s.id).length">
              <div
                v-for="b in visibleBatches(s.id)"
                :key="b.id"
                class="batch-row"
                @click.stop="$router.push(`/batch/${b.id}`)"
              >
                <div class="stack" style="gap: 5px; min-width: 0; flex: 1">
                  <span class="tone-2" style="font-size: 0.86rem">
                    {{ b.category }}盲盒 · {{ fmtDate(b.pickupStart) }} {{ fmtTime(b.pickupStart) }}–{{ fmtTime(b.pickupEnd) }} 自提
                  </span>
                  <span class="stock-bar">
                    <i :style="{ width: Math.max(6, Math.round((b.available / b.total) * 100)) + '%' }"></i>
                  </span>
                  <span class="mono dim" style="font-size: 0.7rem">余 {{ b.available }} / {{ b.total }}</span>
                </div>
                <div class="stack" style="align-items: flex-end; gap: 2px; flex-shrink: 0">
                  <span class="price">¥{{ fenInt(b.price) }}</span>
                  <span class="kicker" style="font-size: 0.6rem">立即抢</span>
                </div>
              </div>
            </template>
            <span v-else class="soldout">{{ availabilityText(s) }}</span>
            <button
              v-if="liveBatches(s.id).length > 2"
              class="more-batches"
              :aria-expanded="!!expanded[s.id]"
              @click.stop="expanded[s.id] = !expanded[s.id]"
            >{{ expanded[s.id] ? '收起盲盒⌃' : '更多盲盒⌄' }}</button>
          </div>
        </div>
      </div>

      <EmptyState v-if="!filtered.length" glyph="远" text="这个范围内还没有符合条件的门店，换个位置看看">
        <button class="btn btn-ghost btn-sm" style="margin-top: 14px" @click="$router.push('/location')">更换浏览位置</button>
      </EmptyState>

      <p class="kicker" style="text-align: center; margin-top: 34px">
        售完即止 · 盲盒内容由商家按价格搭配 · 购买前不获知具体组合
      </p>
    </div>
    <BottomNav />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useStore } from '../../store'
import { fmtKm, fmtTime, fmtDate, fenInt } from '../../utils'
import { HERO_IMG, storeImg } from '../../food-images'
import BottomNav from '../../components/BottomNav.vue'
import EmptyState from '../../components/EmptyState.vue'

const store = useStore()
const cat = ref('全部')
const onlyFav = ref(false)
const q = ref('')
const expanded = ref({})
let refreshTimer
onMounted(() => { refreshTimer = setInterval(() => store.bootstrap(), 60000) })
onUnmounted(() => clearInterval(refreshTimer))

const filtered = computed(() => {
  let list =
    cat.value === '全部'
      ? store.storesNearby
      : store.storesNearby.filter((s) => s.category === cat.value)
  if (onlyFav.value) list = list.filter((s) => store.isFavorite(s.id))
  const kw = q.value.trim().toLowerCase()
  if (kw) {
    list = list.filter(
      (s) =>
        s.name.toLowerCase().includes(kw) ||
        s.category.includes(kw) ||
        s.address.toLowerCase().includes(kw) ||
        (s.notice || '').includes(kw)
    )
  }
  return list.slice().sort((a, b) => Number(liveBatches(b.id).length > 0) - Number(liveBatches(a.id).length > 0) || a.km - b.km)
})

// 个性化推荐：按历史常买品类 + 收藏门店加权，只推在售且有余量的批次
const recommended = computed(() => {
  const pickedCats = {}
  for (const o of store.myOrders) {
    if (o.status === 'picked') pickedCats[o.snap.category] = (pickedCats[o.snap.category] || 0) + 1
  }
  const favs = store.myFavorites
  if (!Object.keys(pickedCats).length && !favs.length) return []

  const cands = []
  for (const s of store.storesNearby) {
    for (const b of store.storeBatches(s.id)) {
      if (b.status !== 'on_sale' || b.available <= 0) continue
      let score = 0
      if (favs.includes(s.id)) score += 5
      score += (pickedCats[b.category] || 0) * 2
      // 同分时更近、更便宜优先
      score -= s.km / 15
      score -= b.price / 100000
      cands.push({ store: s, batch: b, score })
    }
  }
  return cands
    .filter((x) => x.score > 0.1)
    .sort((a, b) => b.score - a.score)
    .slice(0, 3)
})

const ratingOf = (id) => store.storeRating(id)

// 今日概览：余量份数、最低价
const allLive = computed(() =>
  store.storesNearby.flatMap((s) => store.storeBatches(s.id).filter((b) => b.status === 'on_sale'))
)
const totalLeft = computed(() => allLive.value.reduce((n, b) => n + b.available, 0))
const minPrice = computed(() =>
  allLive.value.length ? Math.min(...allLive.value.map((b) => b.price)) : 0
)

const liveBatches = (storeId) =>
  store.storeBatches(storeId).filter((b) => b.status === 'on_sale' && b.available > 0).sort((a, b) => Date.parse(a.pickupEnd) - Date.parse(b.pickupEnd))

const visibleBatches = id => expanded.value[id] ? liveBatches(id) : liveBatches(id).slice(0, 2)
function availabilityText(s) {
  const day = new Date(store.nowMs + 28800000).toISOString().slice(0, 10)
  const today = store.storeBatches(s.id).filter(b => new Date(Date.parse(b.pickupStart) + 28800000).toISOString().slice(0, 10) === day)
  if (today.some(b => b.status === 'sold_out')) return '本批已售罄'
  const plan = s.dailyPlan
  if (plan && store.nowMs < Date.parse(`${day}T${plan.publishTime}:00+08:00`)) return `暂无在售盲盒 · 预计今日${plan.publishTime}上架，以实际发布为准`
  if (today.some(b => b.status === 'ended') || (plan && store.nowMs >= Date.parse(`${day}T${plan.pickupEnd}:00+08:00`))) return '今日售卖已结束'
  return '暂无在售盲盒'
}
</script>
