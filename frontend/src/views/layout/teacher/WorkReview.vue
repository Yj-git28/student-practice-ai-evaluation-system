<template>
  <el-card>
    <template #header>学生实训成果AI复核打分</template>
    <el-table :data="reviewList" border>
      <el-table-column prop="stuName" label="学生姓名"/>
      <el-table-column prop="fileName" label="提交文件"/>
      <el-table-column prop="aiScore" label="AI初步得分"/>
      <el-table-column prop="aiComment" label="AI评语"/>
      <el-table-column label="操作">
        <template #default><el-button type="primary" link @click="dialogShow=true">人工复核调整分数</el-button></template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogShow" title="人工复核（修改最终成绩）">
    <el-form :model="editForm">
      <el-form-item label="修改最终总分"><el-input-number v-model="editForm.finalScore" :min="0" :max="100"/></el-form-item>
      <el-form-item label="教师补充评语"><el-input v-model="editForm.teaComment" type="textarea"/></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogShow=false">取消</el-button>
      <el-button type="primary">确认保存最终成绩</el-button>
    </template>
  </el-dialog>
</template>
<script setup>
import {ref} from 'vue'
const dialogShow = ref(false)
const reviewList = ref([
  {stuName:"张三",fileName:"实训报告.pdf",aiScore:76,aiComment:"步骤完整，文档少量格式问题"}
])
const editForm = ref({finalScore:76,teaComment:''})
</script>
