// 通用工具：时间、金额、距离格式化
export const pad = (n) => String(n).padStart(2, '0')

export function fmtTime(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function fmtDT(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  return `${d.getMonth() + 1}月${d.getDate()}日 ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function fmtDate(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

// 服务端金额以分为最小单位，展示层转换为元
export function fen(v) {
  return `¥${(v / 100).toFixed(2)}`
}

export function fenInt(v) {
  const y = v / 100
  return Number.isInteger(y) ? String(y) : y.toFixed(2)
}

// 距离（km），门店坐标与浏览中心均为 GCJ-02 口径，按地理直线距离
export function haversineKm(a, b) {
  const R = 6371
  const dLat = ((b.lat - a.lat) * Math.PI) / 180
  const dLng = ((b.lng - a.lng) * Math.PI) / 180
  const s =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((a.lat * Math.PI) / 180) * Math.cos((b.lat * Math.PI) / 180) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(s))
}

export function fmtKm(km) {
  return km < 1 ? `${Math.round(km * 1000)} m` : `${km.toFixed(1)} km`
}

// 剩余毫秒 → mm:ss / hh:mm:ss
export function left(ms) {
  if (ms <= 0) return '00:00'
  const s = Math.floor(ms / 1000)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const ss = s % 60
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(ss)}` : `${pad(m)}:${pad(ss)}`
}

export function randCode(len = 8) {
  let s = ''
  while (s.length < len) s += Math.floor(Math.random() * 10)
  return s
}

export function randToken(len = 24) {
  const chars = 'abcdef0123456789'
  let s = ''
  while (s.length < len) s += chars[Math.floor(Math.random() * chars.length)]
  return s
}

// 售后证据：兼容历史纯文本，新数据为 { text, images } JSON 字符串
export function parseEvidence(raw) {
  if (!raw) return { text: '未上传', images: [] }
  const s = String(raw).trim()
  if (s.startsWith('{')) {
    try {
      const o = JSON.parse(s)
      return {
        text: o.text || '',
        images: Array.isArray(o.images) ? o.images.filter(Boolean) : []
      }
    } catch {
      // 解析失败按纯文本展示
    }
  }
  return { text: s, images: [] }
}

// ---------------- 真实地理位置服务 ----------------
// 数据源：Open-Meteo 地名库（GeoNames）+ BigDataCloud 逆地理编码
// 均为真实地理数据，国内网络可达；失败时上层提示重试，不使用未知坐标查询

function withTimeout(promise, ms = 8000) {
  return Promise.race([
    promise,
    new Promise((_, reject) => setTimeout(() => reject(new Error('地图服务超时')), ms))
  ])
}

// 浏览器实时定位（需用户授权；拒绝时上层走手动搜索替代路径）
// 错误信息区分具体原因，便于用户采取对应操作
export function locateByDevice() {
  return new Promise((resolve, reject) => {
    if (!('geolocation' in navigator)) {
      reject(new Error('当前浏览器不支持定位，请换 Chrome / Edge，或手动搜索地点'))
      return
    }
    if (!window.isSecureContext) {
      reject(new Error('定位需要 HTTPS 或 localhost 环境。请把地址栏里的 127.0.0.1 或局域网 IP 改成 localhost 试试，或手动搜索地点'))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude, accuracy: pos.coords.accuracy }),
      (err) => {
        const map = {
          1: '浏览器定位权限被拒绝。请点击地址栏左侧的权限图标 → 允许定位，或手动搜索地点',
          2: '设备无法获取 GPS 信号，请检查系统定位开关，或手动搜索地点',
          3: '定位超时，请点按钮重试，或手动搜索地点'
        }
        reject(new Error(map[err.code] || '定位失败，请手动搜索地点'))
      },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 30000 }
    )
  })
}

// ---------------- WGS-84 → GCJ-02 ----------------
// 高德等国内地图使用 GCJ-02；GPS 原始坐标为 WGS-84，需转换后地图标记才准确
const GCJ_A = 6378245.0
const GCJ_EE = 0.00669342162296594323

function outOfChina(lat, lng) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function transformLat(x, y) {
  let r = -100 + 2 * x + 3 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  r += ((20 * Math.sin(6 * x * Math.PI) + 20 * Math.sin(2 * x * Math.PI)) * 2) / 3
  r += ((20 * Math.sin(y * Math.PI) + 40 * Math.sin((y / 3) * Math.PI)) * 2) / 3
  r += ((160 * Math.sin((y / 12) * Math.PI) + 320 * Math.sin((y * Math.PI) / 30)) * 2) / 3
  return r
}

function transformLng(x, y) {
  let r = 300 + x + 2 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  r += ((20 * Math.sin(6 * x * Math.PI) + 20 * Math.sin(2 * x * Math.PI)) * 2) / 3
  r += ((20 * Math.sin(x * Math.PI) + 40 * Math.sin((x / 3) * Math.PI)) * 2) / 3
  r += ((150 * Math.sin((x / 12) * Math.PI) + 300 * Math.sin((x / 30) * Math.PI)) * 2) / 3
  return r
}

export function wgs84ToGcj02(lat, lng) {
  if (outOfChina(lat, lng)) return { lat, lng }
  let dLat = transformLat(lng - 105, lat - 35)
  let dLng = transformLng(lng - 105, lat - 35)
  const radLat = (lat / 180) * Math.PI
  let magic = Math.sin(radLat)
  magic = 1 - GCJ_EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180) / (((GCJ_A * (1 - GCJ_EE)) / (magic * sqrtMagic)) * Math.PI)
  dLng = (dLng * 180) / ((GCJ_A / sqrtMagic) * Math.cos(radLat) * Math.PI)
  return { lat: lat + dLat, lng: lng + dLng }
}

// GCJ-02 → WGS-84（三次迭代反解，误差约 1 米内）
export function gcj02ToWgs84(glat, glng) {
  let lat = glat
  let lng = glng
  for (let i = 0; i < 3; i++) {
    const t = wgs84ToGcj02(lat, lng)
    lat -= t.lat - glat
    lng -= t.lng - glng
  }
  return { lat, lng }
}

// 高德地图瓦片（国内可达、中文标注，坐标系 GCJ-02 与本平台一致）
export const GAODE_TILE = 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}'

// 高德地图 URI：传起点=当前浏览中心，避免高德网页版弹层让用户自己填起点
// mode: car 驾车 / walk 步行（高德 URI 也支持 bus 公交）
// callnative=1 在手机上调起高德 APP，桌面浏览器打开高德网页版路线规划
export function amapNavUrl({ lng, lat, name = '朝花惜食合作门店', fromLng, fromLat, mode = 'car' }) {
  const from = fromLng != null && fromLat != null ? `&from=${fromLng},${fromLat},当前位置` : ''
  return `https://uri.amap.com/navigation?to=${lng},${lat},${encodeURIComponent(name)}${from}&mode=${mode}&policy=1&src=zhaohuaxishi&coordinate=gaode&callnative=1`
}

// 腾讯地图 URI：routeplan 路径规划（腾讯官方文档要求 type=drive/walk，不是 car）
// fromcoord 格式 lat,lng（腾讯文档示例 fromcoord=39.907380,116.388501）
export function tencentNavUrl({ lng, lat, name = '朝花惜食合作门店', fromLng, fromLat, mode = 'car' }) {
  // 腾讯官方 type：驾车=drive、步行=walk
  const type = mode === 'walk' ? 'walk' : 'drive'
  const fromPart = fromLng != null && fromLat != null
    ? `&fromcoord=${fromLat},${fromLng}`
    : ''
  return `https://apis.map.qq.com/uri/v1/routeplan?type=${type}&to=${encodeURIComponent(name)}&tocoord=${lat},${lng}${fromPart}&policy=0&referer=zhaohuaxishi`
}

// 厦门本地标志性建筑 / 商圈 / 地标（WGS-84 坐标）。
// 作用：①搜索兜底；②逆地理编码就近匹配为精确名，保证中文且不依赖网络
const XIAMEN_PLACES = [
  { name: '厦门市', full: '福建省 厦门市', city: '厦门市', lat: 24.4798, lng: 118.0894 },
  { name: '思明区', full: '福建省 厦门市 思明区', city: '厦门市', lat: 24.4639, lng: 118.0821 },
  { name: '湖里区', full: '福建省 厦门市 湖里区', city: '厦门市', lat: 24.5121, lng: 118.1463 },
  { name: '集美区', full: '福建省 厦门市 集美区', city: '厦门市', lat: 24.5758, lng: 118.0975 },
  { name: '海沧区', full: '福建省 厦门市 海沧区', city: '厦门市', lat: 24.4848, lng: 118.0327 },
  { name: '同安区', full: '福建省 厦门市 同安区', city: '厦门市', lat: 24.7230, lng: 118.1520 },
  { name: '翔安区', full: '福建省 厦门市 翔安区', city: '厦门市', lat: 24.6180, lng: 118.2430 },
  // —— 思明区 ——
  { name: '中山路步行街', full: '福建省 厦门市 思明区 中山路', city: '厦门市', lat: 24.4585, lng: 118.0848 },
  { name: '鼓浪屿', full: '福建省 厦门市 思明区 鼓浪屿', city: '厦门市', lat: 24.4472, lng: 118.0673 },
  { name: '沙坡尾', full: '福建省 厦门市 思明区 沙坡尾', city: '厦门市', lat: 24.4465, lng: 118.0863 },
  { name: '厦门大学', full: '福建省 厦门市 思明区 思明南路 422 号', city: '厦门市', lat: 24.4395, lng: 118.0967 },
  { name: '南普陀寺', full: '福建省 厦门市 思明区 思明南路 515 号', city: '厦门市', lat: 24.4421, lng: 118.0950 },
  { name: '曾厝垵', full: '福建省 厦门市 思明区 曾厝垵', city: '厦门市', lat: 24.4368, lng: 118.1153 },
  { name: '环岛路', full: '福建省 厦门市 思明区 环岛路', city: '厦门市', lat: 24.4308, lng: 118.1278 },
  { name: '胡里山炮台', full: '福建省 厦门市 思明区 曾厝垵路 2 号', city: '厦门市', lat: 24.4332, lng: 118.1118 },
  { name: '椰风寨', full: '福建省 厦门市 思明区 环岛路', city: '厦门市', lat: 24.4278, lng: 118.1200 },
  { name: '世茂双子塔', full: '福建省 厦门市 思明区 演武西路 180 号', city: '厦门市', lat: 24.4390, lng: 118.0836 },
  { name: '万象城', full: '福建省 厦门市 思明区 湖滨东路 99 号', city: '厦门市', lat: 24.4803, lng: 118.1167 },
  { name: '八市', full: '福建省 厦门市 思明区 开禾路', city: '厦门市', lat: 24.4572, lng: 118.0840 },
  { name: '海湾公园', full: '福建省 厦门市 思明区 湖滨西路', city: '厦门市', lat: 24.4799, lng: 118.0705 },
  { name: '瑞景商业广场', full: '福建省 厦门市 思明区 莲前西路 209 号', city: '厦门市', lat: 24.4780, lng: 118.1450 },
  { name: '宝龙一城', full: '福建省 厦门市 思明区 金山路', city: '厦门市', lat: 24.4853, lng: 118.1505 },
  { name: '软件园二期', full: '福建省 厦门市 思明区 观日路', city: '厦门市', lat: 24.4896, lng: 118.1782 },
  { name: '观音山', full: '福建省 厦门市 思明区 环岛东路', city: '厦门市', lat: 24.4901, lng: 118.1752 },
  { name: '东渡', full: '福建省 厦门市 思明区 东渡路', city: '厦门市', lat: 24.4853, lng: 118.0702 },
  // —— 湖里区 ——
  { name: 'SM城市广场', full: '福建省 厦门市 湖里区 嘉禾路 468 号', city: '厦门市', lat: 24.5049, lng: 118.1284 },
  { name: '高崎国际机场', full: '福建省 厦门市 湖里区 高崎', city: '厦门市', lat: 24.5440, lng: 118.1278 },
  { name: '五缘湾', full: '福建省 厦门市 湖里区 五缘湾', city: '厦门市', lat: 24.5250, lng: 118.1605 },
  { name: '蔡塘广场', full: '福建省 厦门市 湖里区 吕岭路', city: '厦门市', lat: 24.4810, lng: 118.1308 },
  { name: '万达广场', full: '福建省 厦门市 湖里区 仙岳路 4668 号', city: '厦门市', lat: 24.5012, lng: 118.1402 },
  { name: '联发东南天地', full: '福建省 厦门市 湖里区 台湾街 128 号', city: '厦门市', lat: 24.4990, lng: 118.1090 },
  // —— 集美区 ——
  { name: '厦门北站', full: '福建省 厦门市 集美区 后溪镇', city: '厦门市', lat: 24.6356, lng: 118.0115 },
  { name: '集美大学', full: '福建省 厦门市 集美区 银江路 185 号', city: '厦门市', lat: 24.5751, lng: 118.0932 },
  { name: '集美万达广场', full: '福建省 厦门市 集美区 银江路 113 号', city: '厦门市', lat: 24.5712, lng: 118.0966 },
  { name: '集美龙舟池', full: '福建省 厦门市 集美区 鳌园路', city: '厦门市', lat: 24.5670, lng: 118.1020 },
  { name: '鳌园', full: '福建省 厦门市 集美区 鳌园路 3 号', city: '厦门市', lat: 24.5640, lng: 118.1040 },
  { name: '陈嘉庚纪念馆', full: '福建省 厦门市 集美区 鳌园路 3 号', city: '厦门市', lat: 24.5652, lng: 118.1030 },
  { name: '园博苑', full: '福建省 厦门市 集美区 杏锦路', city: '厦门市', lat: 24.5700, lng: 118.0600 },
  { name: '杏林湾商务营运中心', full: '福建省 厦门市 集美区 杏林湾路', city: '厦门市', lat: 24.5762, lng: 118.0780 },
  { name: '集美市民广场', full: '福建省 厦门市 集美区 杏林湾路', city: '厦门市', lat: 24.5780, lng: 118.0880 },
  { name: '灵玲马戏城', full: '福建省 厦门市 集美区 杏林湾路', city: '厦门市', lat: 24.5850, lng: 118.0780 },
  { name: '老院子景区', full: '福建省 厦门市 集美区 华夏路 9 号', city: '厦门市', lat: 24.5720, lng: 118.0700 },
  { name: '杏南中学', full: '福建省 厦门市 集美区 杏林南路', city: '厦门市', lat: 24.5630, lng: 118.0350 },
  { name: '杏林南路', full: '福建省 厦门市 集美区 杏林南路', city: '厦门市', lat: 24.5620, lng: 118.0360 },
  { name: '杏林大桥', full: '福建省 厦门市 集美区 杏林大桥', city: '厦门市', lat: 24.5400, lng: 118.0500 },
  { name: '诚毅学院', full: '福建省 厦门市 集美区 集美大道 199 号', city: '厦门市', lat: 24.5750, lng: 118.0950 },
  { name: '集美学村', full: '福建省 厦门市 集美区 银江路', city: '厦门市', lat: 24.5680, lng: 118.1000 },
  // —— 海沧区 ——
  { name: '阿罗海城市广场', full: '福建省 厦门市 海沧区 海沧大道 899 号', city: '厦门市', lat: 24.4900, lng: 118.0200 },
  { name: '海沧大桥', full: '福建省 厦门市 海沧区 海沧大桥', city: '厦门市', lat: 24.4950, lng: 118.0500 },
  { name: '天竺山森林公园', full: '福建省 厦门市 海沧区 东孚街道', city: '厦门市', lat: 24.5700, lng: 117.9500 },
  // —— 同安区 ——
  { name: '同安影视城', full: '福建省 厦门市 同安区 五显镇', city: '厦门市', lat: 24.7220, lng: 118.1520 },
  { name: '梵天寺', full: '福建省 厦门市 同安区 轮山路', city: '厦门市', lat: 24.7280, lng: 118.1570 },
  // —— 翔安区 ——
  { name: '翔安隧道', full: '福建省 厦门市 翔安区 翔安隧道', city: '厦门市', lat: 24.5700, lng: 118.2200 }
]

// ---------------- 坐标 → 真实地址（逆地理编码）----------------
// 三个数据源并行，把信息拆成两个维度后再组装，避免出现拼音后缀：
//   - 精确名（标志性建筑 / 小区 / 道路）：取 Nominatim 或 Photon
//   - 行政区（市 / 街道办）：优先 BigDataCloud（稳定返回中文），其次 Nominatim，其次 Photon
// 数据源：
//   1. OSM Nominatim —— POI/楼栋级，国内网络常被阻断
//   2. Photon(komoot) —— 同为 OSM 数据，可到 标志性建筑/小区 级，国内可达但偏慢；其 city/district 字段常为拼音
//   3. BigDataCloud —— 国内可达、速度快，最细到街道办且为中文，作为行政区的权威源
const hasCjk = (s) => typeof s === 'string' && /[\u4e00-\u9fa5]/.test(s)

// 厦门本地标志性建筑库就近匹配：不依赖网络、必定中文、必定是真实地标
// 排除厦门市/各区这类行政区泛名，只取具体建筑/商圈/景点
function reverseViaLocal(lat, lng) {
  const landmarks = XIAMEN_PLACES.filter(
    (p) => p.name !== '厦门市' && !/[市区]$/.test(p.name)
  )
  let best = null
  for (const p of landmarks) {
    const km = haversineKm({ lat, lng }, { lat: p.lat, lng: p.lng })
    if (!best || km < best.km) best = { ...p, km }
  }
  if (!best || best.km > 3) return null // 超过 3 公里认为不相关
  return { precise: best.name, city: best.city, street: '', km: best.km, lat, lng }
}

async function reverseViaNominatim(lat, lng) {
  const r = await withTimeout(
    fetch(
      `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1&namedetails=1&accept-language=zh-CN`,
      { headers: { Accept: 'application/json' } }
    ),
    4500
  )
  if (!r.ok) throw new Error('Nominatim 解析失败')
  const d = await r.json()
  const a = d.address || {}
  const city = a.city || a.town || a.county || a.state || '未知城市'
  const street = a.suburb || a.quarter || a.city_district || '' // 街道办/镇
  const micro = a.neighbourhood || a.residential || a.village || '' // 小区/村
  const road = a.road || '' // 道路名
  // POI/标志性建筑名：Nominatim 在楼栋级命中时 name 即为建筑名，排除与城市/街道同名的泛化结果
  const poi = d.name && d.name !== city && d.name !== street ? d.name : ''
  const precise = poi || micro || (road ? `${road}${a.house_number ? ` ${a.house_number} 号` : ''}` : '')
  if (!precise && !street) throw new Error('Nominatim 结果过粗')
  return { precise, city, street, lat, lng }
}

async function reverseViaPhoton(lat, lng) {
  // Photon 偶发限流返回 HTML，做一次重试；两次都失败才放弃
  let props = []
  for (let attempt = 0; attempt < 2 && !props.length; attempt++) {
    try {
      const r = await withTimeout(
        fetch(`https://photon.komoot.io/reverse?lat=${lat}&lon=${lng}&limit=15&lang=zh`, {
          headers: { Accept: 'application/json' }
        }),
        6000
      )
      if (r.ok) {
        const text = await r.text()
        const json = text.startsWith('{') ? JSON.parse(text) : null
        props = ((json?.features) || []).map((f) => f.properties || {})
      }
    } catch { /* 本次失败，重试 */ }
    if (!props.length) await new Promise((r) => setTimeout(r, 400))
  }
  if (!props.length) throw new Error('Photon 无结果')
  const city = (props.find((p) => p.city) || {}).city || '未知城市'
  const named = (type) => props.find((p) => p.name && p.type === type && p.name !== city && hasCjk(p.name))
  const poiF = named('house') // 标志性建筑：学校/商场/医院等楼栋级 POI
  const microF = named('locality') // 小区/村/片区
  const streetF = named('street') // 道路
  const districtF = props.find((p) => p.type === 'district' && p.name && hasCjk(p.name))
  const street = poiF?.district || microF?.district || streetF?.district || districtF?.name || ''
  // 精确名优先标志性建筑，其次小区，再次道路；都没有时退到街道办名
  const precise = poiF?.name || microF?.name || streetF?.name || districtF?.name || ''
  if (!precise && !street) throw new Error('Photon 结果过粗')
  return { precise, city, street, lat, lng }
}

async function reverseViaBdc(lat, lng) {
  const r = await withTimeout(
    fetch(`https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${lat}&longitude=${lng}&localityLanguage=zh`),
    8000
  )
  if (!r.ok) throw new Error('BigDataCloud 解析失败')
  const d = await r.json()
  const city = d.city || d.principalSubdivision || '未知城市'
  // administrative 数组按 adminLevel 从大到小（国家→省→市→区→街道）排列，取最细一级的非区名作为街道办
  const admins = d.localityInfo?.administrative || []
  const streetEntry = [...admins]
    .filter((x) => (x.adminLevel || 0) >= 7 && x.name && x.name !== city && x.name !== d.locality)
    .sort((a, b) => (b.adminLevel || 0) - (a.adminLevel || 0))[0]
  const street = streetEntry?.name || ''
  const precise = '' // BDC 只到街道办级，无 POI/小区名
  return { precise, city, street, lat, lng }
}

// 并行竞速：精确源（Nominatim / Photon）任一命中即取其精确名；
// 行政区（市 / 街道）优先采用 BigDataCloud 的中文结果，拼音字段一律丢弃；
// 精确源整体最多等 8 秒，超时后退回到街道办级结果
export async function reverseGeocode(lat, lng) {
  const nomP = reverseViaNominatim(lat, lng).catch(() => null)
  const phoP = reverseViaPhoton(lat, lng).catch(() => null)
  const bdcP = reverseViaBdc(lat, lng).catch(() => null)

  const preciseP = Promise.any([nomP, phoP]).catch(() => null)
  const precise = await Promise.race([preciseP, new Promise((r) => setTimeout(() => r(null), 10000))])
  const bdc = await bdcP
  const nom = await nomP
  const local = reverseViaLocal(lat, lng) // 本地地标，同步、零网络

  // 行政区中文：BDC > Nominatim > Photon，且只接受含汉字的值（排除 Xiamen / Xingbin 这类拼音）
  const admin = bdc || nom || precise
  const street = [admin?.street, precise?.street].find((s) => hasCjk(s)) || ''
  const city = [admin?.city, precise?.city].find((s) => hasCjk(s)) || '厦门市'

  // 精确名（标志性建筑）优先级：网络 POI > 本地地标（2km 内）
  const netPrecise = precise?.precise || ''
  const localPrecise = local && local.km <= 2 ? local.precise : ''
  const preciseName = netPrecise || localPrecise

  if (preciseName) {
    const suffix = street || city
    const name = suffix ? `${preciseName} · ${suffix}` : preciseName
    return { name, city, street, lat, lng }
  }
  if (local) {
    const suffix = street || city
    return { name: `${local.precise} · ${suffix}`, city, street, lat, lng }
  }
  if (bdc?.street) return { name: `${bdc.street} · ${city}`, city, street: bdc.street, lat, lng }
  if (precise?.street) return { name: `${precise.street} · ${city}`, city, street: precise.street, lat, lng }
  return { name: `当前位置（${city}）`, city, street: '', lat, lng }
}

// 关键词 → 真实候选地点
// 三层搜索顺序：厦门本地地标（保证快速出结果）→ Open-Meteo GeoNames（全球地名）→ Photon（OSM POI/门店级）
// 全部失败时至少保留本地厦门地标，不会空列表
export async function searchPlaces(kw) {
  const q = String(kw || '').trim()
  if (!q) return []

  // 第一层：厦门本地地标
  const local = XIAMEN_PLACES.filter((p) => p.name.includes(q) || p.full.includes(q)).map((p) => ({ ...p, cn: true }))

  // 第二层：Open-Meteo GeoNames（全球城市/行政区）
  let remote = []
  try {
    const r = await withTimeout(
      fetch(
        `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(q)}&count=10&language=zh&format=json`
      ),
      5000
    )
    if (r.ok) {
      const d = await r.json()
      remote = (d.results || []).map((p) => ({
        name: p.name,
        full: [p.name, p.admin2, p.admin1, p.country].filter(Boolean).join('，'),
        city: p.admin2 || p.admin1 || p.country || '',
        lat: p.latitude,
        lng: p.longitude,
        cn: p.country_code === 'CN'
      }))
    }
  } catch { /* 网络超时：跳过 */ }

  // 第三层：Photon（OSM 数据，可到 POI 品牌/门店级，如"集美区美宜佳"；Nominatim 国内不可达故不用）
  let poi = []
  try {
    const r = await withTimeout(
      fetch(
        `https://photon.komoot.io/api/?q=${encodeURIComponent(q + ' 厦门')}&limit=6&lat=24.48&lon=118.09&lang=zh`,
        { headers: { Accept: 'application/json' } }
      ),
      6000
    )
    if (r.ok) {
      const feats = (await r.json()).features || []
      poi = feats
        .map((f) => {
          const p = f.properties || {}
          const [lng, la] = f.geometry?.coordinates || []
          return {
            name: p.name || p.street || p.district || '',
            full: [p.name, p.street, p.district, p.city, p.state].filter(Boolean).join('，'),
            city: p.city || p.state || '',
            lat: la,
            lng,
            cn: p.countrycode === 'CN'
          }
        })
        .filter((p) => p.name && Number.isFinite(p.lat) && Number.isFinite(p.lng))
    }
  } catch { /* Photon 偶发超时：跳过 */ }

  const seen = new Set(local.map((p) => `${p.lat.toFixed(3)},${p.lng.toFixed(3)}`))
  const merged = [...local, ...poi, ...remote.filter((p) => !seen.has(`${p.lat.toFixed(3)},${p.lng.toFixed(3)}`))]
  // 去重：同名同经纬度
  const dedup = []
  const seenKeys = new Set()
  for (const p of merged) {
    const k = `${p.lat.toFixed(3)},${p.lng.toFixed(3)}`
    if (!seenKeys.has(k)) { seenKeys.add(k); dedup.push(p) }
  }

  // 兜底：三层全空时，从关键词里切出可能的地名片段，
  // 在本地厦门地标里模糊匹配，至少返回相关行政区让用户有得选
  if (!dedup.length) {
    const tokens = new Set()
    // 先按标点/空格切
    for (const t of q.replace(/[，,。.、·\-／\s]+/g, ' ').split(' ')) {
      if (t.length >= 2) tokens.add(t)
      // 对每个切分片段，再滑窗切 2~6 字的子串（覆盖"集美区美宜佳"这类无空格输入）
      for (let i = 0; i < t.length; i++) {
        for (let j = i + 2; j <= Math.min(i + 6, t.length); j++) {
          tokens.add(t.slice(i, j))
        }
      }
    }
    const fuzzy = new Set()
    for (const t of tokens) {
      for (const p of XIAMEN_PLACES) {
        if ((p.name.includes(t) || p.full.includes(t)) && !fuzzy.has(`${p.lat},${p.lng}`)) {
          fuzzy.add(`${p.lat},${p.lng}`)
          dedup.push({ ...p, cn: true, fuzzy: t })
        }
      }
    }
  }

  return dedup.sort((a, b) => Number(b.cn) - Number(a.cn)).slice(0, 10)
}
