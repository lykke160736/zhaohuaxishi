<template>
  <div>
    <span class="kicker">UC-0104 · 审核通过后开放商家权限</span>
    <h1 class="display" style="font-size: 2.2rem; margin-top: 6px">商家入驻审核</h1>

    <h2 class="kicker" style="margin-top: 24px">待审核（{{ pending.length }}）</h2>
    <div v-for="a in pending" :key="a.id" class="card" style="margin-top: 12px">
      <div
        class="row"
        style="cursor: pointer"
        @click="detail = a"
      >
        <div class="stack">
          <strong class="display" style="font-size: 1.2rem">{{ a.storeName }}</strong>
          <span class="dim mono" style="font-size: 0.78rem">
            {{ a.category }} · 申请人 {{ a.applicant }} · 提交于 {{ fmtDT(a.submittedAt) }}
          </span>
          <span class="dim" style="font-size: 0.76rem">点击查看商家完整资料 ▲</span>
        </div>
        <StatusPill :status="a.status" />
      </div>
      <hr class="hr" />
      <div class="stack" style="gap: 6px; font-size: 0.9rem">
        <div class="row"><span class="tone-2">地址</span><span>{{ a.address }}</span></div>
        <div class="row"><span class="tone-2">电话</span><span class="mono">{{ a.phone }}</span></div>
        <div class="row" style="cursor: pointer" @click.stop="openMaterials(a)">
          <span class="tone-2">材料</span>
          <span style="text-align: right; max-width: 60%; border-bottom: 1px dashed var(--line-2)">{{ a.materials }} · 点击查看</span>
        </div>
      </div>
      <div class="field" style="margin-top: 14px; margin-bottom: 0">
        <input class="input" v-model="notes[a.id]" placeholder="审核意见（通过原因或退回说明）" />
      </div>
      <div class="row" style="margin-top: 12px; gap: 10px; justify-content: flex-end">
        <button class="btn btn-ghost btn-sm" @click="detail = a">查看详情</button>
        <button class="btn btn-ghost btn-sm btn-danger" @click="audit(a, false)">退回补充</button>
        <button class="btn btn-ink btn-sm" @click="audit(a, true)">审核通过</button>
      </div>
    </div>
    <div v-if="!pending.length" class="notice" style="margin-top: 12px">当前没有待审核的入驻申请。</div>

    <h2 class="kicker" style="margin-top: 30px">审核记录</h2>
    <div class="card" style="margin-top: 12px; padding: 6px 20px">
      <div
        v-for="a in done"
        :key="a.id"
        class="row"
        style="padding: 13px 0; border-bottom: 1px solid var(--line); cursor: pointer"
        @click="detail = a"
      >
        <div class="stack">
          <strong style="font-size: 0.95rem">{{ a.storeName }}</strong>
          <span class="dim" style="font-size: 0.8rem">{{ a.note }} · {{ fmtDT(a.auditedAt) }}</span>
        </div>
        <StatusPill :status="a.status" />
      </div>
    </div>

    <!-- 商家详情抽屉 -->
    <div v-if="detail" class="drawer-mask" @click.self="detail = null">
      <div class="drawer">
        <button class="close" @click="detail = null">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" />
          </svg>
        </button>
        <div class="row" style="align-items: flex-start">
          <div class="stack">
            <span class="kicker">商家入驻资料</span>
            <h3 class="display" style="margin-top: 4px">{{ detail.storeName }}</h3>
          </div>
          <StatusPill :status="detail.status" />
        </div>

        <span class="kicker" style="display: block; margin-top: 20px">申请信息</span>
        <div class="stack" style="gap: 8px; font-size: 0.88rem; margin-top: 8px">
          <div class="row"><span class="tone-2">申请人</span><span>{{ detail.applicant }}</span></div>
          <div class="row"><span class="tone-2">申请账号</span><span class="mono">{{ accountOf(detail.accountId)?.account || '—' }}</span></div>
          <div class="row"><span class="tone-2">账号手机号</span><span class="mono dim">{{ accountOf(detail.accountId)?.phone || '—' }}</span></div>
          <div class="row">
            <span class="tone-2">账号状态</span>
            <StatusPill :status="accountOf(detail.accountId)?.status || 'active'" />
          </div>
          <div class="row"><span class="tone-2">提交时间</span><span class="mono dim">{{ fmtDT(detail.submittedAt) }}</span></div>
          <div class="row"><span class="tone-2">审核时间</span><span class="mono dim">{{ detail.auditedAt ? fmtDT(detail.auditedAt) : '—' }}</span></div>
        </div>

        <span class="kicker" style="display: block; margin-top: 20px">门店信息</span>
        <div class="stack" style="gap: 8px; font-size: 0.88rem; margin-top: 8px">
          <div class="row"><span class="tone-2">经营品类</span><span>{{ detail.category }}</span></div>
          <div class="row"><span class="tone-2">门店地址</span><span style="text-align: right; max-width: 66%">{{ detail.address }}</span></div>
          <div class="row"><span class="tone-2">门店电话</span><span class="mono">{{ detail.phone }}</span></div>
          <div class="row">
            <span class="tone-2">坐标 (GCJ-02)</span>
            <span class="mono dim">{{ coordText(detail) }}</span>
          </div>
        </div>

        <span class="kicker" style="display: block; margin-top: 20px">审核材料</span>
        <div
          class="card"
          style="margin-top: 8px; padding: 14px 16px; font-size: 0.86rem; cursor: pointer"
          @click="openMaterials(detail)"
        >
          <div class="row">
            <span>{{ detail.materials }}</span>
            <span class="dim" style="font-size: 0.76rem; white-space: nowrap">查看材料 ↗</span>
          </div>
        </div>

        <template v-if="detail.note">
          <span class="kicker" style="display: block; margin-top: 20px">审核意见</span>
          <p class="tone-2" style="font-size: 0.86rem; margin-top: 8px">{{ detail.note }}</p>
        </template>

        <!-- 待审核时可直接在抽屉里处理 -->
        <template v-if="detail.status === 'pending'">
          <div class="field" style="margin-top: 20px; margin-bottom: 0">
            <input class="input" v-model="notes[detail.id]" placeholder="审核意见（通过原因或退回说明）" />
          </div>
          <div class="row" style="margin-top: 14px; gap: 10px; justify-content: flex-end">
            <button class="btn btn-ghost btn-sm btn-danger" @click="audit(detail, false)">退回补充</button>
            <button class="btn btn-ink btn-sm" @click="audit(detail, true)">审核通过</button>
          </div>
        </template>
      </div>
    </div>

    <!-- 审核材料查看 -->
    <div v-if="matApp" class="drawer-mask" @click.self="matApp = null">
      <div class="drawer">
        <button class="close" @click="matApp = null">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" />
          </svg>
        </button>
        <span class="kicker">审核材料</span>
        <h3 class="display" style="margin-top: 4px">{{ matApp.storeName }}</h3>
        <p class="dim" style="font-size: 0.8rem; margin-top: 4px">
          申请人 {{ matApp.applicant }} · 提交于 {{ fmtDT(matApp.submittedAt) }}
        </p>

        <div class="stack" style="gap: 8px; margin-top: 18px">
          <button
            v-for="(m, i) in materialItems"
            :key="i"
            class="card"
            style="padding: 12px 14px; text-align: left; cursor: pointer; border-color: var(--line)"
            :style="activeMat === i ? 'border-color: var(--ink)' : ''"
            @click="activeMat = i"
          >
            <div class="row">
              <span style="font-size: 0.88rem">{{ m }}</span>
              <span class="tag tag-green">已上传</span>
            </div>
          </button>
          <p v-if="!materialItems.length" class="dim" style="font-size: 0.86rem">暂无材料</p>
        </div>

        <!-- 材料预览 -->
        <template v-if="materialItems[activeMat]">
          <span class="kicker" style="display: block; margin-top: 22px">材料预览</span>
          <div class="material-sheet">
            <div class="row">
              <span class="mono dim" style="font-size: 0.72rem">归档编号 {{ matApp.id }}-{{ activeMat + 1 }}</span>
              <span class="tag">扫描件</span>
            </div>
            <h4 class="display" style="font-size: 1.15rem; margin-top: 14px; text-align: center">{{ materialItems[activeMat] }}</h4>
            <hr class="hr" />
            <div class="stack" style="gap: 8px; font-size: 0.82rem">
              <div class="row"><span class="tone-2">门店名称</span><span>{{ matApp.storeName }}</span></div>
              <div class="row"><span class="tone-2">申请人</span><span>{{ matApp.applicant }}</span></div>
              <div class="row"><span class="tone-2">经营品类</span><span>{{ matApp.category }}</span></div>
              <div class="row"><span class="tone-2">经营地址</span><span style="text-align: right; max-width: 66%">{{ matApp.address }}</span></div>
              <div class="row"><span class="tone-2">联系电话</span><span class="mono">{{ matApp.phone }}</span></div>
              <div class="row"><span class="tone-2">上传时间</span><span class="mono dim">{{ fmtDT(matApp.submittedAt) }}</span></div>
            </div>
            <p class="dim mono" style="font-size: 0.72rem; margin-top: 16px; text-align: center">
              材料影像已归档，与申请信息一致，可作为审核依据
            </p>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useStore } from '../../store'
import { fmtDT } from '../../utils'
import StatusPill from '../../components/StatusPill.vue'

const store = useStore()
const notes = ref({})
const detail = ref(null)

// 审核材料查看
const matApp = ref(null)
const activeMat = ref(0)
const materialItems = computed(() =>
  matApp.value
    ? String(matApp.value.materials || '')
        .split(/[、,，]/)
        .map((x) => x.trim())
        .filter(Boolean)
    : []
)

function openMaterials(a) {
  matApp.value = a
  activeMat.value = 0
}

const pending = computed(() => store.pendingApplications)
const done = computed(() => store.applications.filter((a) => a.status !== 'pending'))

const accountOf = (accountId) => store.accounts.find((x) => x.id === accountId)
const coordText = (a) =>
  a.lat != null && a.lng != null ? `${a.lat.toFixed(4)}, ${a.lng.toFixed(4)}` : '未标注'

async function audit(a, pass) {
  const note = (notes.value[a.id] || '').trim()
  if (!note) {
    alert('请填写审核意见')
    return
  }
  const r = await store.auditApplication(a.id, pass, note)
  if (!r.ok) {
    alert(r.msg)
    return
  }
  detail.value = null
}
</script>
