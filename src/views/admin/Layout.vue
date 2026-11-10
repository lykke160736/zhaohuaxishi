<template>
  <div class="desk">
    <aside class="side">
      <div class="side-brand">朝花惜食<span style="color: var(--accent)">·</span>平台</div>
      <router-link v-for="l in links" :key="l.to" :to="l.to" class="side-link" :class="{ on: $route.path === l.to }">
        {{ l.label }}
        <span class="n" v-if="l.n">{{ l.n() }}</span>
      </router-link>
      <div style="margin-top: auto; padding-top: 18px; border-top: 1px solid var(--line)">
        <NoticeBell />
        <div class="who">
          <span class="kicker">平台管理员</span>
          <strong>{{ store.session.name }}</strong>
        </div>
        <button class="side-link" style="width: 100%" @click="signOut">退出登录</button>
      </div>
    </aside>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from '../../store'
import NoticeBell from '../../components/NoticeBell.vue'

const store = useStore()
const router = useRouter()

const audits = computed(() => store.pendingApplications.length)
const queue = computed(() => store.platformQueue.length)

const links = [
  { to: '/admin', label: '业务总览' },
  { to: '/admin/stores', label: '门店管理' },
  { to: '/admin/batches', label: '盲盒监管' },
  { to: '/admin/audit', label: '入驻审核', n: () => audits.value || '' },
  { to: '/admin/intervention', label: '售后介入', n: () => queue.value || '' },
  { to: '/admin/accounts', label: '账号与权限' },
  { to: '/admin/logs', label: '操作审计' }
]

async function signOut() {
  await store.logout()
  router.replace('/login')
}
</script>
