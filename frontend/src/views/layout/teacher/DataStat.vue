<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="header">
        <span>班级实训数据统计</span>
        <el-button type="primary" v-has-perm:all="['training:list','evaluation:list']" @click="exportCsv">
          导出CSV报表
        </el-button>
      </div>
    </template>
    <el-row :gutter="20">
      <el-col :span="12">
        <div ref="chartRef" style="height:320px"></div>
      </el-col>
      <el-col :span="12">
        <el-descriptions border title="班级汇总">
          <el-descriptions-item label="总人数">{{ stat.totalStudents ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="已提交">{{ stat.submitted ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="完成率">{{ formatRate(stat.completionRate) }}</el-descriptions-item>
          <el-descriptions-item label="班级平均分">{{ formatScore(stat.avgScore) }}</el-descriptions-item>
        </el-descriptions>
      </el-col>
    </el-row>
  </el-card>
</template>
<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const chartRef = ref(null)
const loading = ref(false)
let chart = null

const stat = ref({
  totalStudents: 0,
  submitted: 0,
  completionRate: 0,
  avgScore: 0,
  indicators: []
})

function formatRate(value) {
  const num = Number(value)
  return Number.isFinite(num) ? `${num.toFixed(1)}%` : '0.0%'
}

function formatScore(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num.toFixed(1) : '0.0'
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const indicators = stat.value.indicators || []
  chart.setOption({
    title: { text: '各评价指标平均分分布', left: 'center' },
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 60, bottom: 30 },
    xAxis: { type: 'category', data: indicators.map(item => item.name) },
    yAxis: { type: 'value', max: 100 },
    series: [{
      type: 'bar',
      barMaxWidth: 46,
      data: indicators.map(item => item.score),
      itemStyle: { color: '#409eff' },
      label: { show: true, position: 'top' }
    }]
  }, true)
}

async function loadStat() {
  loading.value = true
  try {
    const res = await request.get('/evaluation/stat')
    if (res.code === 200 && res.data) {
      stat.value = {
        totalStudents: res.data.totalStudents ?? 0,
        submitted: res.data.submitted ?? 0,
        completionRate: res.data.completionRate ?? 0,
        avgScore: res.data.avgScore ?? 0,
        indicators: res.data.indicators || []
      }
    }
  } catch (e) {
    // 展示空数据，避免页面白屏
  } finally {
    loading.value = false
  }
  renderChart()
}

function exportCsv() {
  const indicators = stat.value.indicators || []
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  const timeText = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} `
      + `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`
      + `_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`

  const rows = [
    ['实训数据统计'],
    ['导出时间', timeText],
    [],
    ['汇总项', '数值'],
    ['总人数', stat.value.totalStudents ?? 0],
    ['已提交', stat.value.submitted ?? 0],
    ['完成率', formatRate(stat.value.completionRate)],
    ['平均分', formatScore(stat.value.avgScore)],
    [],
    ['评价指标', '平均分'],
    ...indicators.map(item => [item.name, item.score])
  ]

  const csv = rows
      .map(cols => cols.map(value => {
        const text = value === null || value === undefined ? '' : String(value)
        return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
      }).join(','))
      .join('\r\n')

  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `实训数据统计_${stamp}.csv`
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success('统计报表已导出')
}

onMounted(loadStat)

onBeforeUnmount(() => {
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>
<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
