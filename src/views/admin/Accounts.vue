<template>
  <div>
    <span class="kicker">UC-0704 / UC-0602</span>
    <h1 class="display" style="font-size: 2.2rem; margin-top: 6px">账号与权限</h1>
    <p class="dim" style="font-size: 0.85rem; margin-top: 8px">
      停用账号仅限制访问，历史交易与追溯记录完整保留；注册不自动获得商家或平台权限。
    </p>

    <div class="card" style="margin-top: 22px; padding: 0">
      <table class="table">
        <thead>
          <tr>
            <th>姓名</th>
            <th>登录账号</th>
            <th>手机号</th>
            <th>角色</th>
            <th>状态</th>
            <th>注册时间</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in store.accounts" :key="a.id">
            <td><strong>{{ a.name }}</strong></td>
            <td class="mono">{{ a.account }}</td>
            <td class="mono dim">{{ a.phone }}</td>
            <td><StatusPill :status="a.role" /></td>
            <td><StatusPill :status="a.status" /></td>
            <td class="mono dim">{{ fmtDT(a.createdAt) }}</td>
            <td>
              <span v-if="a.role === 'admin'" class="dim" style="font-size: 0.8rem">不可停用</span>
              <button
                v-else
                class="btn btn-ghost btn-sm"
                :class="{ 'btn-danger': a.status === 'active' }"
                @click="toggle(a)"
              >
                {{ a.status === 'active' ? '停用' : '启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { useStore } from '../../store'
import { fmtDT } from '../../utils'
import StatusPill from '../../components/StatusPill.vue'

const store = useStore()

async function toggle(a) {
  const r = await store.toggleAccount(a.id)
  if (!r.ok) alert(r.msg)
}
</script>
