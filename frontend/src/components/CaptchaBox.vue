<template>
  <div class="captcha-box">
    <el-input
      v-model="answer"
      :placeholder="placeholder"
      maxlength="4"
      clearable
      @input="emitValue"
      @keyup.enter="$emit('enter')"/>
    <img
      v-if="image"
      class="captcha-img"
      :src="image"
      alt="人机验证码"
      title="看不清？点击换一张"
      @click="refresh"/>
    <span v-else class="captcha-loading" @click="refresh">加载中…</span>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import request from '@/utils/request'

defineProps({
  placeholder: { type: String, default: '请输入右侧验证码' }
})
const emit = defineEmits(['update:modelValue', 'enter'])

const answer = ref('')
const image = ref('')
const captchaId = ref('')

function emitValue() {
  emit('update:modelValue', { captchaId: captchaId.value, answer: answer.value.trim() })
}

async function refresh() {
  try {
    const res = await request.get('/auth/captcha')
    if (res.code === 200 && res.data) {
      captchaId.value = res.data.captchaId
      image.value = res.data.image
      answer.value = ''
      emitValue()
    }
  } catch (e) {
    image.value = ''
  }
}

defineExpose({ refresh })
onMounted(refresh)
</script>

<style scoped>
.captcha-box {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.captcha-img {
  width: 120px;
  height: 44px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  user-select: none;
  flex-shrink: 0;
}

.captcha-loading {
  width: 120px;
  height: 44px;
  line-height: 44px;
  text-align: center;
  font-size: 12px;
  color: #909399;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  flex-shrink: 0;
}
</style>
