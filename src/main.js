import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useStore } from './store'
import './styles.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

const store = useStore(pinia)

// 启动时先向服务端恢复会话（令牌由服务端校验），再挂载路由，避免身份未就绪触发守卫误判。
// 服务端不可达时也要挂载：页面会显示连接异常提示与重试入口
store.bootstrap().finally(() => {
  app.use(router)
  app.mount('#app')
})
