<template>
  <div class="grain" aria-hidden="true"></div>

  <!-- 服务端异常提示：无法连接后端或会话失效时给出可见反馈与恢复入口 -->
  <div
    v-if="store.storageError"
    role="alert"
    style="
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      z-index: 500;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      padding: 10px 18px;
      background: #fdf1e7;
      border-bottom: 1px solid #e8c9a8;
      color: #7a3b12;
      font-size: 0.8rem;
      line-height: 1.5;
    "
  >
    <div>
      <strong>服务端连接异常</strong>
      <span style="margin-left: 8px">{{ store.storageError }}</span>
    </div>
    <button
      style="
        flex: none;
        padding: 6px 12px;
        border: 1px solid #c98a4f;
        border-radius: 6px;
        background: transparent;
        color: #7a3b12;
        font-size: 0.76rem;
        cursor: pointer;
      "
      @click="retry"
    >
      重新连接并恢复会话
    </button>
  </div>

  <router-view />
  <AiAssistant v-if="store.session.loggedIn" />
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useStore } from './store'
import AiAssistant from './components/AiAssistant.vue'

const store = useStore()
let timer
onMounted(() => {
  // 每秒推进展示用时钟；支付超时、取货超时等业务判定由服务端后台任务完成
  timer = setInterval(() => store.tick(), 1000)
})
onUnmounted(() => clearInterval(timer))

async function retry() {
  await store.bootstrap()
}
</script>
