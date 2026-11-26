<template>
  <el-card class="chat-card">
    <div class="chat-wrap">
      <!-- 群列表 -->
      <div class="room-list">
        <div class="room-title">实训群聊</div>
        <el-scrollbar>
          <div
            v-for="r in rooms" :key="r.trainingId"
            class="room-item" :class="{ active: currentRoom?.trainingId === r.trainingId }"
            @click="selectRoom(r)"
          >
            <div class="room-name">{{ r.trainingName }}</div>
            <div class="room-meta">指导：{{ r.teacherName || '公共实训' }}</div>
          </div>
          <el-empty v-if="!rooms.length" description="暂无可参与的实训群" :image-size="60"/>
        </el-scrollbar>
      </div>

      <!-- 消息区 -->
      <div class="chat-main">
        <template v-if="currentRoom">
          <div class="chat-header">
            <span>{{ currentRoom.trainingName }}</span>
            <el-tag size="small" :type="connected ? 'success' : 'info'">
              {{ connected ? '已连接' : '未连接' }}
            </el-tag>
          </div>

          <el-scrollbar ref="msgScroll" class="msg-list">
            <div v-for="m in messages" :key="m.messageId" class="msg-row"
                 :class="{ mine: m.senderId === userStore.userId }">
              <div class="msg-bubble">
                <div class="msg-meta">
                  {{ m.senderName }} · {{ m.createTime }}
                </div>
                <div class="msg-content">{{ m.content }}</div>
              </div>
            </div>
            <el-empty v-if="!messages.length" description="还没有消息，来发第一条吧" :image-size="60"/>
          </el-scrollbar>

          <div class="chat-input">
            <el-input
              v-model="input"
              type="textarea"
              :rows="2"
              resize="none"
              maxlength="2000"
              placeholder="输入消息，Enter 发送，Shift+Enter 换行"
              @keydown.enter.exact.prevent="send"
            />
            <el-button type="primary" class="send-btn" :disabled="!input.trim()" @click="send">发送</el-button>
          </div>
        </template>
        <el-empty v-else description="请选择左侧实训群" :image-size="80"/>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/useUserStore'

const userStore = useUserStore()

const rooms = ref([])
const currentRoom = ref(null)
const messages = ref([])
const input = ref('')
const connected = ref(false)
const msgScroll = ref(null)

let ws = null
let reconnectTimer = null

async function loadRooms() {
  try {
    const res = await request.get('/chat/rooms')
    if (res.code === 200) {
      rooms.value = res.data || []
      if (rooms.value.length && !currentRoom.value) selectRoom(rooms.value[0])
    }
  } catch (e) {
    ElMessage.error('群列表加载失败')
  }
}

function selectRoom(room) {
  currentRoom.value = room
  messages.value = []
  connectWs()
  joinRoom(room.trainingId)
  loadHistory(room.trainingId)
}

async function loadHistory(trainingId) {
  try {
    const res = await request.get('/chat/messages', { params: { trainingId } })
    if (res.code === 200) {
      messages.value = res.data || []
      scrollToBottom()
    }
  } catch (e) {
    ElMessage.error('历史消息加载失败')
  }
}

function connectWs() {
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
    return
  }
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  const url = `${proto}://${location.host}/ws/chat?token=${userStore.token}`
  ws = new WebSocket(url)

  ws.onopen = () => { connected.value = true }
  ws.onmessage = (ev) => {
    let frame
    try { frame = JSON.parse(ev.data) } catch (e) { return }
    if (frame.type === 'message') {
      if (frame.trainingId === currentRoom.value?.trainingId) {
        messages.value.push(frame)
        scrollToBottom()
      }
    } else if (frame.type === 'error') {
      ElMessage.error(frame.msg || '消息发送失败')
    }
  }
  ws.onclose = () => {
    connected.value = false
    // 页面未卸载时延迟重连
    reconnectTimer = setTimeout(connectWs, 3000)
  }
  ws.onerror = () => { connected.value = false }
}

function joinRoom(trainingId) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type: 'join', trainingId }))
  }
}

async function send() {
  const content = input.value.trim()
  if (!content || !currentRoom.value) return
  const trainingId = currentRoom.value.trainingId

  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type: 'message', trainingId, content }))
    input.value = ''
  } else {
    // WebSocket 不可用时走 HTTP 兜底
    try {
      const res = await request.post('/chat/messages', { trainingId, content })
      if (res.code === 200) {
        messages.value.push(res.data)
        scrollToBottom()
        input.value = ''
      } else {
        ElMessage.error(res.msg || '发送失败')
      }
    } catch (e) {
      ElMessage.error('发送失败')
    }
  }
}

function scrollToBottom() {
  nextTick(() => {
    const bar = msgScroll.value?.wrapRef
    if (bar) bar.scrollTop = bar.scrollHeight
  })
}

onMounted(loadRooms)

onUnmounted(() => {
  clearTimeout(reconnectTimer)
  if (ws) {
    ws.onclose = null
    ws.close()
    ws = null
  }
})
</script>

<style scoped>
.chat-card {
  height: calc(100vh - 110px);
}

.chat-wrap {
  display: flex;
  gap: 16px;
  height: 100%;
}

.room-list {
  width: 260px;
  border-right: 1px solid #ebeef5;
  padding-right: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.room-title {
  font-weight: 600;
  margin-bottom: 10px;
}

.room-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 6px;
}

.room-item:hover {
  background: #f5f7fa;
}

.room-item.active {
  background: #ecf5ff;
}

.room-name {
  font-size: 14px;
  color: #303133;
}

.room-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 10px;
}

.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 4px;
}

.msg-row {
  display: flex;
  margin-bottom: 14px;
}

.msg-row.mine {
  justify-content: flex-end;
}

.msg-bubble {
  max-width: 60%;
  background: #f4f4f5;
  border-radius: 8px;
  padding: 8px 12px;
}

.msg-row.mine .msg-bubble {
  background: #ecf5ff;
}

.msg-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.msg-content {
  font-size: 14px;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-input {
  display: flex;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px solid #ebeef5;
}

.send-btn {
  align-self: flex-end;
}
</style>
