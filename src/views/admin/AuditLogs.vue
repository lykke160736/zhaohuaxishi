<template>
  <div>
    <span class="kicker">UC-0603 · 只读追溯，不可篡改</span>
    <h1 class="display" style="font-size: 2.2rem; margin-top: 6px">操作审计</h1>

    <div class="chips" style="margin-top: 18px">
      <button v-for="t in actors" :key="t" class="chip" :class="{ on: actor === t }" @click="actor = t">{{ t }}</button>
    </div>

    <div class="card" style="margin-top: 16px; padding: 0; overflow: hidden">
      <table class="table log-table">
        <thead>
          <tr>
            <th style="width: 120px">角色</th>
            <th style="width: 160px" class="col-center">操作名</th>
            <th>说明</th>
            <th style="width: 150px" class="col-right">时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in list" :key="l.id">
            <td><span class="tag">{{ l.actor }}</span></td>
            <td class="col-center"><strong style="font-size: 0.92rem">{{ l.action }}</strong></td>
            <td class="dim" style="font-size: 0.84rem">{{ l.detail }}</td>
            <td class="col-right mono dim" style="font-size: 0.75rem; white-space: nowrap">{{ fmtDT(l.time) }}</td>
          </tr>
          <tr v-if="!list.length">
            <td colspan="4" class="dim" style="text-align: center; padding: 18px; font-size: 0.88rem">该操作者暂无记录</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useStore } from '../../store'
import { fmtDT } from '../../utils'

const store = useStore()
const actor = ref('全部')
const actors = ['全部', '顾客', '商家', '平台管理员', '支付渠道', '系统']

const list = computed(() =>
  actor.value === '全部' ? store.logs : store.logs.filter((l) => l.actor === actor.value)
)
</script>
