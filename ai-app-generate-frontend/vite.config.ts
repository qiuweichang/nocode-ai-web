import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// 本地联调时允许为独立实例指定后端端口，避免占用开发者正在运行的服务。
const devBackendOrigin = process.env.VITE_DEV_BACKEND_ORIGIN || 'http://localhost:8124'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server: {
    // 生成页面必须与工作台同源，iframe 元素选择和预览实时刷新才能正常工作。
    proxy: {
      '/api': {
        target: devBackendOrigin,
        changeOrigin: true,
      },
    },
  },
})
