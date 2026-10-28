<template>
<el-card>
  <template #header>班级实训数据统计 <el-button type="primary">导出Excel/PDF报表</el-button></template>
  <el-row :gutter="20">
    <el-col :span="12">
      <div ref="chartRef" style="height:320px"></div>
    </el-col>
    <el-col :span="12">
      <el-descriptions border title="班级汇总">
        <el-descriptions-item label="总人数">36</el-descriptions-item>
        <el-descriptions-item label="已提交">32</el-descriptions-item>
        <el-descriptions-item label="完成率">88.8%</el-descriptions-item>
        <el-descriptions-item label="班级平均分">74.6</el-descriptions-item>
      </el-descriptions>
    </el-col>
  </el-row>
</el-card>
</template>
<script setup>
import {ref,onMounted} from 'vue'
import * as echarts from 'echarts'
const chartRef = ref(null)

onMounted(()=>{
  const myChart = echarts.init(chartRef.value)
  myChart.setOption({
    title:{text:"各评价指标平均分分布"},
    xAxis:{data:["代码质量","文档规范","功能实现","步骤完整"]},
    yAxis:{},
    series:[{type:'bar',data:[73,75,76,72]}]
  })
})
</script>
