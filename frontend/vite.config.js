import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  base: '/QianDuan/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src') // 配置@指向src文件夹
    }
  }
})
