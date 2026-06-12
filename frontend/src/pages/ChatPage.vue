<template>
  <div class="chat-page">
    <!-- Header -->
    <header class="chat-header">
      <button class="back-btn" @click="goBack">
        <el-icon :size="18"><ArrowLeft /></el-icon>
      </button>
      <div class="header-info">
        <h2 class="header-name">{{ characterName || '聊天' }}</h2>
        <span v-if="streaming" class="typing-indicator">正在输入...</span>
      </div>
      <button class="header-action" @click="goBack">
        <el-icon :size="18"><More /></el-icon>
      </button>
    </header>

    <!-- Message List -->
    <div ref="msgListRef" class="message-list" @scroll="onScroll">
      <!-- Scroll-to-bottom button -->
      <button
        v-if="isScrolledUp"
        class="scroll-bottom-btn"
        @click="scrollToBottom(true)"
      >
        <el-icon :size="18"><ArrowDown /></el-icon>
      </button>

      <div v-if="loadingMessages" class="loading-state">
        <el-icon class="loading-icon" :size="24"><Loading /></el-icon>
        <span>加载消息中...</span>
      </div>

      <el-empty v-if="!loadingMessages && messages.length === 0" description="发送第一条消息吧" />

      <div
        v-for="msg in messages"
        :key="msg.seq || msg._key"
        class="msg-row"
        :class="msg.role === 'user' ? 'msg-row--user' : 'msg-row--assistant'"
      >
        <!-- Assistant Avatar -->
        <div v-if="msg.role === 'assistant'" class="msg-avatar msg-avatar--assistant">
          <img v-if="characterAvatar" :src="characterAvatar" alt="" />
          <el-icon v-else :size="20"><User /></el-icon>
        </div>

        <!-- Message Bubble -->
        <div class="msg-bubble" :class="msg.role === 'user' ? 'bubble-user' : 'bubble-assistant'">
          <!-- Image attachment -->
          <div v-if="msg.imageUrl" class="bubble-image">
            <img :src="msg.imageUrl" alt="" @click="previewImage(msg.imageUrl)" />
          </div>
          <!-- Text content -->
          <div
            v-if="msg.content"
            class="bubble-text"
            :class="{ 'streaming-cursor': msg.streaming }"
          >
            <template v-if="msg.role === 'assistant'">
              <div v-html="renderMarkdown(msg.content)" class="markdown-body" />
            </template>
            <template v-else>
              {{ msg.content }}
            </template>
          </div>
          <!-- Timestamp -->
          <span class="bubble-time">{{ formatTime(msg.createdAt || msg.seq) }}</span>
        </div>

        <!-- User Avatar -->
        <div v-if="msg.role === 'user'" class="msg-avatar msg-avatar--user">
          <el-icon :size="20"><UserFilled /></el-icon>
        </div>
      </div>

      <div ref="scrollAnchor" />
    </div>

    <!-- Input Area -->
    <div class="input-area">
      <!-- Image preview -->
      <div v-if="pendingImageUrl" class="image-preview">
        <img :src="pendingImageUrl" alt="" />
        <el-button
          class="image-preview-close"
          :icon="Close"
          circle
          size="small"
          @click="clearPendingImage"
        />
      </div>

      <div class="input-row">
        <input
          ref="fileInputRef"
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif"
          style="display: none"
          @change="handleImageSelect"
        />
        <el-button
          :icon="Picture"
          :disabled="streaming || uploadingImage"
          circle
          @click="triggerImageSelect"
        />
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="1"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="输入消息..."
          :disabled="streaming"
          @keydown.enter.exact.prevent="handleSend"
        />
        <el-button
          type="primary"
          :icon="Promotion"
          :disabled="(!inputText.trim() && !pendingImageUrl) || streaming || uploadingImage"
          circle
          @click="handleSend"
        />
      </div>
    </div>

    <!-- Image Preview Dialog -->
    <el-dialog v-model="imagePreviewVisible" title="图片预览" width="auto" :close-on-click-modal="true">
      <img :src="imagePreviewUrl" alt="" style="max-width: 80vw; max-height: 70vh;" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, ArrowDown, Picture, Promotion, Close,
  User, UserFilled, Loading, More
} from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import { getConversation, getMessages, sendMessageStream, uploadImage } from '@/api/conversation'
import { getCharacter } from '@/api/character'

const route = useRoute()
const router = useRouter()

const convId = ref(null)
const characterName = ref('')
const characterAvatar = ref('')
const messages = ref([])
const loadingMessages = ref(false)
const streaming = ref(false)
const uploadingImage = ref(false)

const inputText = ref('')
const pendingImageUrl = ref('')
const fileInputRef = ref(null)

const msgListRef = ref(null)
const scrollAnchor = ref(null)
const isScrolledUp = ref(false)

// Image preview
const imagePreviewVisible = ref(false)
const imagePreviewUrl = ref('')

// Markdown renderer
const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true
})

onMounted(async () => {
  const id = route.params.id
  if (id) {
    convId.value = Number(id)
    await loadConversation(convId.value)
  }
})

onUnmounted(() => {
  // Cleanup if needed
})

async function loadConversation(id) {
  loadingMessages.value = true
  try {
    const conv = await getConversation(id)
    characterName.value = conv.data?.characterName || conv.data?.title || '角色'

    // Load character info
    const charId = conv.data?.characterId
    if (charId) {
      try {
        const char = await getCharacter(charId)
        characterAvatar.value = char.data?.avatarUrl || ''
      } catch { /* ignore */ }
    }

    // Load messages
    await loadMessages(id)
  } catch {
    ElMessage.error('加载对话失败')
  } finally {
    loadingMessages.value = false
    await nextTick()
    scrollToBottom(true)
  }
}

async function loadMessages(id) {
  try {
    const res = await getMessages(id, { limit: 100 })
    messages.value = (res.data?.records || res.data || []).map(m => ({
      ...m,
      role: (m.role || '').toLowerCase(),
      _key: m.id || m.seq || Math.random()
    }))
  } catch {
    messages.value = []
  }
}

async function handleSend() {
  const text = inputText.value.trim()
  const imageUrl = pendingImageUrl.value
  if ((!text && !imageUrl) || streaming.value) return

  // Add user message
  const userMsg = {
    _key: 'u' + Date.now(),
    role: 'user',
    content: text || (imageUrl ? '（发送了一张图片）' : ''),
    imageUrl: imageUrl || undefined,
    seq: Date.now()
  }
  messages.value.push(userMsg)

  inputText.value = ''
  pendingImageUrl.value = ''
  await nextTick()
  scrollToBottom(true)

  streaming.value = true

  try {
    const response = await sendMessageStream(convId.value, {
      content: text || undefined,
      imageUrl: imageUrl || undefined
    })

    if (!response.ok) {
      let errMsg = '消息发送失败'
      try {
        const err = await response.json()
        errMsg = err.message || errMsg
      } catch { /* ignore */ }
      throw new Error(errMsg)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()

    // Add placeholder assistant message
    const assistantMsg = {
      _key: 'a' + Date.now(),
      role: 'assistant',
      content: '',
      streaming: true,
      seq: Date.now()
    }
    messages.value.push(assistantMsg)
    await nextTick()
    scrollToBottom()

    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed.startsWith('data:')) continue
        const data = trimmed.slice(5).trim()
        if (!data || data === '[DONE]') continue

        try {
          const payload = JSON.parse(data)
          if (payload.error) {
            throw new Error(payload.error)
          }
          if (payload.content) {
            assistantMsg.content += payload.content
          }
          if (payload.done) {
            assistantMsg.streaming = false
          }
          await nextTick()
          scrollToBottom()
        } catch (e) {
          if (e instanceof SyntaxError) continue
          throw e
        }
      }
    }

    assistantMsg.streaming = false
    scrollToBottom(true)
  } catch (err) {
    ElMessage.error(err.message || '消息发送失败')
    // Remove the failed assistant placeholder
    messages.value = messages.value.filter(m => m._key !== assistantMsg?._key)
  } finally {
    streaming.value = false
  }
}

function renderMarkdown(text) {
  if (!text) return ''
  return md.render(text)
}

function triggerImageSelect() {
  fileInputRef.value?.click()
}

function clearPendingImage() {
  pendingImageUrl.value = ''
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

async function handleImageSelect(event) {
  const file = event.target.files?.[0]
  if (!file) return

  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    event.target.value = ''
    return
  }

  uploadingImage.value = true
  try {
    const result = await uploadImage(file)
    pendingImageUrl.value = result.data?.imageUrl || result.data?.url || ''
    if (!pendingImageUrl.value) {
      throw new Error('图片上传失败')
    }
  } catch (err) {
    ElMessage.error(err.message || '图片上传失败')
    clearPendingImage()
  } finally {
    uploadingImage.value = false
    if (fileInputRef.value) {
      fileInputRef.value.value = ''
    }
  }
}

function previewImage(url) {
  imagePreviewUrl.value = url
  imagePreviewVisible.value = true
}

function onScroll() {
  const el = msgListRef.value
  if (!el) return
  const threshold = 80
  isScrolledUp.value = el.scrollHeight - el.scrollTop - el.clientHeight > threshold
}

function scrollToBottom(force) {
  nextTick(() => {
    const el = msgListRef.value
    if (!el) return
    if (force || !isScrolledUp.value) {
      el.scrollTop = el.scrollHeight
    }
  })
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function goBack() {
  router.push('/characters')
}
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-width: 768px;
  margin: 0 auto;
  background: #fff;
  box-shadow: 0 0 40px rgba(236, 64, 122, 0.06);
}

/* Header */
.chat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  position: sticky;
  top: 0;
  z-index: 10;
  flex-shrink: 0;
}

.back-btn,
.header-action {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #666;
  transition: background 0.2s;
}

.back-btn:hover,
.header-action:hover {
  background: #e8e8e8;
}

.header-info {
  flex: 1;
}

.header-name {
  font-size: 17px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.typing-indicator {
  font-size: 12px;
  color: #ec407a;
  animation: pulse 1.2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* Message List */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: linear-gradient(180deg, #fafafa 0%, #f5f5f5 100%);
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: #999;
  font-size: 14px;
}

.loading-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.scroll-bottom-btn {
  position: sticky;
  bottom: 8px;
  align-self: center;
  z-index: 5;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: #fff;
  color: #666;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s;
}

.scroll-bottom-btn:hover {
  transform: translateY(-2px);
}

/* Message Row */
.msg-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  animation: msgIn 0.3s ease-out;
}

@keyframes msgIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.msg-row--user {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}

.msg-avatar--assistant {
  background: linear-gradient(135deg, #fce4ec, #f3e5f5);
  color: #ec407a;
}

.msg-avatar--user {
  background: linear-gradient(135deg, #e3f2fd, #e8eaf6);
  color: #5c6bc0;
}

.msg-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* Bubbles */
.msg-bubble {
  max-width: 72%;
  padding: 12px 16px;
  border-radius: 18px;
  position: relative;
  word-break: break-word;
}

.bubble-user {
  background: linear-gradient(135deg, #ec407a, #f06292);
  color: #fff;
  border-bottom-right-radius: 6px;
  box-shadow: 0 2px 12px rgba(236, 64, 122, 0.2);
}

.bubble-assistant {
  background: #fff;
  color: #333;
  border-bottom-left-radius: 6px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.bubble-image {
  margin-bottom: 8px;
}

.bubble-image img {
  max-width: 200px;
  max-height: 200px;
  border-radius: 10px;
  cursor: pointer;
  transition: transform 0.2s;
}

.bubble-image img:hover {
  transform: scale(1.02);
}

.bubble-text {
  font-size: 15px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.bubble-user .bubble-text {
  color: #fff;
}

.streaming-cursor::after {
  content: '|';
  animation: blink 0.8s step-end infinite;
  color: #ec407a;
  font-weight: 100;
}

.bubble-user .streaming-cursor::after {
  color: rgba(255, 255, 255, 0.7);
}

@keyframes blink {
  50% { opacity: 0; }
}

.bubble-time {
  display: block;
  font-size: 10px;
  margin-top: 4px;
  text-align: right;
  opacity: 0.6;
}

.bubble-user .bubble-time {
  color: rgba(255, 255, 255, 0.7);
}

.bubble-assistant .bubble-time {
  color: #bbb;
}

/* Markdown styles */
.markdown-body :deep(p) {
  margin: 0 0 6px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}

.markdown-body :deep(pre) {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 13px;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 4px 0;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid #ec407a;
  padding-left: 12px;
  margin: 8px 0;
  color: #888;
}

/* Input Area */
.input-area {
  flex-shrink: 0;
  padding: 12px 16px;
  padding-bottom: max(12px, env(safe-area-inset-bottom));
  border-top: 1px solid #f0f0f0;
  background: #fff;
}

.image-preview {
  position: relative;
  display: inline-block;
  margin-bottom: 8px;
}

.image-preview img {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid #f0f0f0;
}

.image-preview-close {
  position: absolute;
  top: -8px;
  right: -8px;
}

.input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.input-row :deep(.el-textarea__inner) {
  border-radius: 20px;
  padding: 10px 16px;
  resize: none;
}

/* Mobile responsive */
@media (max-width: 768px) {
  .chat-page {
    max-width: 100%;
  }

  .msg-bubble {
    max-width: 82%;
  }
}
</style>
