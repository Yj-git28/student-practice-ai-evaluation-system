<template>
  <el-card>
    <template #header>实训成果提交 & AI评价查看</template>
    <el-upload
      action="/api/upload"
      :file-list="fileList"
      accept=".pdf,.doc,.docx,.jpg,.png"
      :limit="5"
    >
      <el-button type="primary">选择实训报告/截图文件（PDF Word 图片）</el-button>
    </el-upload>
    <div style="margin:12px 0">
      <el-button type="success" @click="submitWork">确认提交成果</el-button>
    </div>

    <el-divider></el-divider>
    <div v-if="aiResult">
      <h4>✅AI初步核查评价结果</h4>
      <el-descriptions border :column="2">
        <el-descriptions-item label="AI综合得分">{{aiResult.aiScore}}</el-descriptions-item>
        <el-descriptions-item label="代码质量">{{aiResult.codeScore}}</el-descriptions-item>
        <el-descriptions-item label="文档规范性">{{aiResult.docScore}}</el-descriptions-item>
        <el-descriptions-item label="功能实现度">{{aiResult.funcScore}}</el-descriptions-item>
      </el-descriptions>
      <el-alert :title="'AI评语：'+aiResult.aiComment" type="info" style="margin-top:10px"></el-alert>

      <el-divider>教师人工复核最终结果</el-divider>
      <el-descriptions border>
        <el-descriptions-item label="教师最终分数">{{aiResult.finalScore}}</el-descriptions-item>
        <el-descriptions-item label="教师评语">{{aiResult.teacherComment}}</el-descriptions-item>
      </el-descriptions>
    </div>
  </el-card>
</template>
<script setup>
import {ref} from 'vue'
const fileList = ref([])
const aiResult = ref(null)

const submitWork = ()=>{
  if(fileList.value.length===0) return alert("请选择文件")
  //模拟提交，返回AI评价结果（后端异步大模型推理）
  aiResult.value = {
    aiScore:76,
    codeScore:78,
    docScore:74,
    funcScore:77,
    aiComment:"实训步骤基本完整，文档格式存在少量格式问题，部分功能描述不够详细。",
    finalScore:75,
    teacherComment:"整体完成尚可，建议完善文档图表说明。"
  }
}
</script>
