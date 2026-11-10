<template>
  <div class="bell-wrap">
    <button ref="trigger" class="bell-btn" :title="`消息通知${store.unreadCount ? `（${store.unreadCount} 条未读）` : ''}`" @click="toggle">
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9">
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 8-3 8h18s-3-1-3-8" stroke-linecap="round" stroke-linejoin="round" />
        <path d="M13.7 21a2 2 0 0 1-3.4 0" stroke-linecap="round" />
      </svg>
      <span class="bell-name">消息</span>
      <span v-if="store.unreadCount" class="bell-badge">{{ store.unreadCount > 99 ? '99+' : store.unreadCount }}</span>
    </button>

    <Teleport to="body">
    <div v-if="open" class="bell-mask" @click="open = false"></div>
    <div v-if="open" class="bell-panel" :style="panelStyle">
      <div class="row" style="padding: 12px 14px; border-bottom: 1px solid var(--line)">
        <span class="kicker">消息通知</span>
        <button
          v-if="store.unreadCount"
          class="kicker"
          style="background: none; border: none; color: var(--accent); cursor: pointer"
          @click="store.markAllNoticeRead()"
        >全部已读</button>
      </div>
      <div class="bell-list">
        <div
          v-for="n in store.myNotices"
          :key="n.id"
          class="bell-item"
          :class="{ unread: !n.read }"
          @click="openNotice(n)"
        >
          <span class="bell-dot" :class="`t-${n.type}`"></span>
          <div class="stack" style="gap: 3px; min-width: 0">
            <div class="row">
              <strong style="font-size: 0.88rem">{{ n.title }}</strong>
              <span class="mono dim" style="font-size: 0.7rem">{{ fmtDT(n.createdAt) }}</span>
            </div>
            <span class="tone-2" style="font-size: 0.82rem; line-height: 1.5">{{ n.body }}</span>
          </div>
        </div>
        <p v-if="!store.myNotices.length" class="dim" style="padding: 24px; text-align: center; font-size: 0.86rem">
          暂无消息
        </p>
      </div>
    </div>
    </Teleport>
  </div>
</template>

<script setup>
import { onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from '../store'
import { fmtDT } from '../utils'

const store = useStore()
const router = useRouter()
const open = ref(false)
const trigger = ref(null)
const panelStyle = ref({})

function positionPanel() {
  if (!open.value || !trigger.value) return
  const rect = trigger.value.getBoundingClientRect()
  const gap = 10
  const margin = 12
  const width = Math.min(340, window.innerWidth - margin * 2)
  const below = window.innerHeight - rect.bottom - gap - margin
  const above = rect.top - gap - margin
  const upward = above > below
  const height = Math.min(440, Math.max(0, upward ? above : below))
  const left = trigger.value.closest('.side') ? rect.left : rect.right - width
  panelStyle.value = {
    left: `${Math.max(margin, Math.min(left, window.innerWidth - width - margin))}px`,
    right: 'auto',
    top: upward ? 'auto' : `${rect.bottom + gap}px`,
    bottom: upward ? `${window.innerHeight - rect.top + gap}px` : 'auto',
    width: `${width}px`,
    maxHeight: `${height}px`
  }
}
function toggle() {
  open.value = !open.value
  positionPanel()
}
window.addEventListener('resize', positionPanel)
onUnmounted(() => window.removeEventListener('resize', positionPanel))

async function openNotice(n) {
  open.value = false
  if (n.link) router.push(n.link)
  await store.markNoticeRead(n.id)
}
</script>
