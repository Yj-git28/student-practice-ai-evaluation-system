import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(import.meta.dirname, 'src') // 配置@指向src文件夹
    }
  },
  server: {
    host: true,
    // 允许通过 Cloudflare 隧道域名访问（trycloudflare.com 子域名）
    allowedHosts: ['.trycloudflare.com'],
    proxy: {
      // 前端 /api/** 转发到后端 8080，避开跨域
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
