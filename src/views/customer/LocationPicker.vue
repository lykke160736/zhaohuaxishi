<template>
  <div class="phone">
    <div class="page page-narrow">
      <PageHead title="选择浏览位置" kicker="真实地图 · 拖拽与选点" />

      <!-- 真实地图：高德瓦片，GCJ-02 与门店坐标一致 -->
      <div style="position: relative; margin-top: 14px">
        <div ref="mapEl" class="map-box"></div>
        <button class="map-locate" :disabled="locating" @click="useDeviceLocation">
          <svg v-if="!locating" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="3.2" />
            <path d="M12 2v3M12 19v3M2 12h3M19 12h3" stroke-linecap="round" />
          </svg>
          <span v-else class="spinner"></span>
          {{ locating ? '定位中' : '我的位置' }}
        </button>
      </div>

      <!-- 已选点确认 -->
      <div v-if="picked" class="card" style="margin-top: 12px">
        <div class="row">
          <div class="stack" style="min-width: 0">
            <span class="kicker">已选位置</span>
            <strong style="font-size: 0.92rem">{{ picked.name }}</strong>
            <span class="mono dim" style="font-size: 0.72rem">
              {{ picked.city }} · {{ picked.lat.toFixed(5) }}, {{ picked.lng.toFixed(5) }}
            </span>
          </div>
          <button class="btn btn-ink btn-sm" :disabled="resolving" @click="confirmPicked">
            {{ resolving ? '解析地址…' : '使用此位置' }}
          </button>
        </div>
      </div>

      <div v-if="err" class="notice notice-warn" style="margin-top: 12px">{{ err }}</div>

      <!-- 真实地名搜索 -->
      <div class="field" style="margin-top: 18px">
        <label>搜索地名（城市、区县、乡镇、地标）</label>
        <div class="row" style="gap: 8px">
          <input class="input" v-model="kw" placeholder="例如：厦门 / 思明区 / 中山路" @keyup.enter="doSearch" />
          <button class="btn btn-ink btn-sm" style="flex-shrink: 0" :disabled="searching" @click="doSearch">
            {{ searching ? '搜索中' : '搜索' }}
          </button>
        </div>
      </div>

      <div class="card" style="margin-top: 10px; padding: 6px 20px" v-if="results.length">
        <button
          v-for="(p, i) in results"
          :key="i"
          class="row"
          style="width: 100%; padding: 13px 0; border-bottom: 1px solid var(--line); text-align: left"
          @click="pickFromSearch(p)"
        >
          <div class="stack" style="min-width: 0">
            <strong style="font-size: 0.92rem">{{ p.name }}</strong>
            <span class="dim mono" style="font-size: 0.71rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 300px">
              {{ p.full }}
            </span>
            <span v-if="p.fuzzy" class="dim" style="font-size: 0.68rem; color: var(--ink-3)">
              关键词「{{ p.fuzzy }}」精确结果未找到，匹配到相关区域
            </span>
          </div>
          <span class="tag">在地图查看</span>
        </button>
      </div>

      <!-- 当前浏览中心 -->
      <div style="margin-top: 22px">
        <span class="kicker">当前浏览中心</span>
        <div class="card" style="margin-top: 10px">
          <div class="row">
            <div class="stack" style="min-width: 0">
              <strong style="font-size: 0.95rem">{{ store.browseCenter.name }}</strong>
              <span class="dim mono" style="font-size: 0.74rem">
                {{ store.browseCenter.city }} · {{ store.browseCenter.lat.toFixed(5) }}, {{ store.browseCenter.lng.toFixed(5) }}
              </span>
            </div>
            <span class="tag" :class="store.browseCenter.source === 'gps' ? 'tag-green' : 'tag-accent'">
              {{ store.browseCenter.source === 'gps' ? '设备定位' : store.browseCenter.source === 'manual' ? '手动选定' : '默认点' }}
            </span>
          </div>
        </div>
      </div>

      <div style="margin-top: 20px">
        <button class="btn btn-ghost btn-block" @click="$router.back()">返回</button>
      </div>
    </div>
    <BottomNav />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useStore } from '../../store'
import { GAODE_TILE, gcj02ToWgs84, locateByDevice, reverseGeocode, searchPlaces, wgs84ToGcj02 } from '../../utils'
import PageHead from '../../components/PageHead.vue'
import BottomNav from '../../components/BottomNav.vue'

const store = useStore()
const router = useRouter()

const mapEl = ref(null)
const kw = ref('')
const results = ref([])
const searching = ref(false)
const locating = ref(false)
const resolving = ref(false)
const err = ref('')
const picked = ref(null)

let map = null
let pickedMarker = null
let storeMarkers = []

onMounted(() => {
  const c = wgs84ToGcj02(store.browseCenter.lat, store.browseCenter.lng)
  map = L.map(mapEl.value, { zoomControl: true, attributionControl: true }).setView([c.lat, c.lng], 14)

  L.tileLayer(GAODE_TILE, {
    subdomains: '1234',
    maxZoom: 18,
    attribution: '地图数据 © 高德地图'
  }).addTo(map)

  // 当前浏览中心
  L.circleMarker([c.lat, c.lng], {
    radius: 8,
    color: '#f6f2e9',
    weight: 3,
    fillColor: '#d4500a',
    fillOpacity: 1
  })
    .addTo(map)
    .bindTooltip(`浏览中心：${store.browseCenter.name}`, { direction: 'top', offset: [0, -8] })

  // 附近真实门店点位
  for (const s of store.stores) {
    const g = wgs84ToGcj02(s.lat, s.lng)
    const m = L.circleMarker([g.lat, g.lng], {
      radius: 6,
      color: '#181410',
      weight: 2,
      fillColor: '#fcfaf4',
      fillOpacity: 1
    })
      .addTo(map)
      .bindTooltip(`${s.name} · ${s.category}`, { direction: 'top', offset: [0, -6] })
    storeMarkers.push(m)
  }

  // 点击地图选点（GCJ-02），存回 WGS-84
  map.on('click', (e) => {
    err.value = ''
    const w = gcj02ToWgs84(e.latlng.lat, e.latlng.lng)
    picked.value = {
      name: '地图选点',
      city: store.browseCenter.city,
      lat: w.lat,
      lng: w.lng
    }
    if (pickedMarker) map.removeLayer(pickedMarker)
    pickedMarker = L.circleMarker([e.latlng.lat, e.latlng.lng], {
      radius: 7,
      color: '#d4500a',
      weight: 2,
      dashArray: '3',
      fillColor: '#d4500a',
      fillOpacity: 0.35
    }).addTo(map)
  })

  // 地图上的初始浏览中心 marker（不是 GPS，是默认/手动设定点）
  // 用户主动点右下角"我的位置"按钮时才触发 GPS 定位，避免 onMounted 静默尝试卡住加载态
})

onBeforeUnmount(() => {
  if (map) {
    map.remove()
    map = null
  }
})

function setPin(glat, glng) {
  if (pickedMarker) map.removeLayer(pickedMarker)
  pickedMarker = L.circleMarker([glat, glng], {
    radius: 7,
    color: '#d4500a',
    weight: 2,
    dashArray: '3',
    fillColor: '#d4500a',
    fillOpacity: 0.35
  }).addTo(map)
  map.flyTo([glat, glng], 16, { duration: 0.8 })
}

// 用浏览器真实定位：
// - 先同步做硬错误检查（isSecureContext、geolocation 存在性、权限状态），
//   这三类错误立刻返回，不走 Promise，避免被 15 秒超时兜底吞掉
// - 通过检查后才走异步 GPS 获取坐标 + 立刻飞地图 + 异步补地名
async function useDeviceLocation() {
  locating.value = true
  err.value = ''

  // ---- 同步硬错误检查（优先级最高，立刻返回，不进 Promise） ----
  if (!('geolocation' in navigator)) {
    err.value = '当前浏览器不支持定位。请换 Chrome / Edge，或在下方地图直接点击选点。'
    locating.value = false
    return
  }
  if (!window.isSecureContext) {
    err.value = '定位需要 HTTPS 或 localhost 环境。请把地址栏里的 127.0.0.1 或局域网 IP 改成 localhost，或在下方地图直接点击选点。'
    locating.value = false
    return
  }
  // 同步查询当前权限状态：granted 可直接定位；denied 立刻报权限错误；prompt 则让用户点击后弹授权
  if (navigator.permissions && navigator.permissions.query) {
    try {
      const state = await navigator.permissions.query({ name: 'geolocation' })
      if (state.state === 'denied') {
        err.value = '浏览器已拒绝本页面定位权限。请点地址栏左侧的权限图标 → 允许定位，或在下方地图直接点击选点。'
        locating.value = false
        return
      }
    } catch { /* 某些浏览器不支持 permissions.query，跳过 */ }
  }

  // ---- 检查通过，开始异步 GPS ----
  let settled = false
  const settle = () => { settled = true; locating.value = false }
  const timer = setTimeout(() => {
    if (!settled) {
      err.value = '定位超时。请确认系统定位开关已打开，或在下方地图直接点击选点。'
      settle()
    }
  }, 15000)

  try {
    const { lat, lng } = await locateByDevice()
    if (settled) return
    const g = wgs84ToGcj02(lat, lng)
    picked.value = { name: '当前位置', city: store.browseCenter.city, lat, lng }
    setPin(g.lat, g.lng)
    reverseGeocode(lat, lng).then((place) => {
      store.setBrowseCenter(place, 'gps')
      picked.value = { name: place.name, city: place.city, lat, lng }
    }).catch(() => {
      store.setBrowseCenter({ name: '当前位置', city: store.browseCenter.city, lat, lng }, 'gps')
    })
  } catch (e) {
    if (!settled) err.value = e.message || '定位失败'
  } finally {
    clearTimeout(timer)
    settle()
  }
}

async function doSearch() {
  const q = kw.value.trim()
  if (!q) return
  searching.value = true
  err.value = ''
  try {
    results.value = await searchPlaces(q)
    if (!results.value.length) err.value = '没搜到这个地点。可以简化关键词试试，或直接在下方地图上点击选点'
  } catch (e) {
    err.value = '搜索服务暂时不可用，直接在下方地图上点击选点吧'
    results.value = []
  } finally {
    searching.value = false
  }
}

function pickFromSearch(p) {
  const g = wgs84ToGcj02(p.lat, p.lng)
  picked.value = { name: p.name, city: p.city, lat: p.lat, lng: p.lng }
  setPin(g.lat, g.lng)
}

async function confirmPicked() {
  if (!picked.value) return
  resolving.value = true
  err.value = ''
  try {
    // 用真实逆地理编码补全地址，避免把"地图选点"这类占位名称写进浏览位置
    const place = await reverseGeocode(picked.value.lat, picked.value.lng)
    await store.setBrowseCenter({ ...place, name: picked.value.name === '地图选点' ? place.name : picked.value.name }, 'manual')
    router.back()
  } catch (e) {
    await store.setBrowseCenter(
      { name: picked.value.name, city: picked.value.city || '未知', lat: picked.value.lat, lng: picked.value.lng },
      'manual'
    )
    router.back()
  } finally {
    resolving.value = false
  }
}
</script>
