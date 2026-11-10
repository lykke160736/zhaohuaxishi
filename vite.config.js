import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: true,
    // 开发环境把接口请求转发到本地 Java 服务（npm run server）
    proxy: {
      '/api': {
        target: 'http://localhost:8787',
        changeOrigin: true
      },
      // 售后证据等上传图片由后端静态目录提供
      '/uploads': {
        target: 'http://localhost:8787',
        changeOrigin: true
      }
    }
  }
})
