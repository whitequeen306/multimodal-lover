<template>
  <div class="chat-page">
    <div class="chat-shell">
    <!-- Header -->
    <header class="chat-header">
      <button class="icon-btn back-btn" aria-label="返回" @click="goBack">
        <el-icon :size="18"><ArrowLeft /></el-icon>
      </button>
      <div class="header-info">
        <div class="header-avatar-ring">
          <img v-if="characterAvatar" :src="resolveImageSrc(characterAvatar)" alt="" class="header-avatar" />
          <el-icon v-else :size="18"><User /></el-icon>
        </div>
        <div class="header-text">
          <h2 class="header-name">{{ characterName || '聊天' }}</h2>
          <span v-if="streaming" class="typing-indicator">
            <span class="typing-label">{{ streamStatusText }}</span>
            <span class="typing-dots"><i /><i /><i /></span>
          </span>
        </div>
      </div>
      <button class="icon-btn header-action" aria-label="更多" @click="goBack">
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
        <template v-if="msg.role === 'assistant'">
          <div class="msg-avatar msg-avatar--assistant">
            <img v-if="characterAvatar" :src="resolveImageSrc(characterAvatar)" alt="" />
            <el-icon v-else :size="20"><User /></el-icon>
          </div>
          <div class="msg-bubble bubble-assistant">
            <div v-if="resolveMessageImageUrls(msg).length" class="bubble-images">
              <img
                v-for="(imgSrc, idx) in resolveMessageImageUrls(msg)"
                :key="idx"
                :src="resolveImageSrc(imgSrc)"
                alt=""
                class="bubble-image-item"
                @click="previewImage(imgSrc)"
              />
            </div>
            <div
              v-if="displayMessageText(msg)"
              class="bubble-text"
              :class="{ 'streaming-cursor': msg.streaming && msg.content }"
            >
              <div v-html="renderMarkdown(msg.content)" class="markdown-body" />
            </div>
            <span class="bubble-time">{{ formatTime(msg.createdAt || msg.seq) }}</span>
          </div>
        </template>

        <template v-else>
          <div class="msg-bubble bubble-user">
            <div v-if="resolveMessageImageUrls(msg).length" class="bubble-images">
              <img
                v-for="(imgSrc, idx) in resolveMessageImageUrls(msg)"
                :key="idx"
                :src="resolveImageSrc(imgSrc)"
                alt=""
                class="bubble-image-item"
                @click="previewImage(imgSrc)"
              />
            </div>
            <div v-if="displayMessageText(msg)" class="bubble-text">
              {{ displayMessageText(msg) }}
            </div>
            <span class="bubble-time">{{ formatTime(msg.createdAt || msg.seq) }}</span>
          </div>
          <div class="msg-avatar msg-avatar--user">
            <img v-if="userAvatar" :src="resolveImageSrc(userAvatar)" alt="" />
            <el-icon v-else :size="20"><UserFilled /></el-icon>
          </div>
        </template>
      </div>

      <div ref="scrollAnchor" />
    </div>

    <!-- Input Area -->
    <div class="input-area">
      <!-- Image preview (max 2) -->
      <div v-if="pendingImages.length" class="image-preview-row">
        <div v-for="(item, idx) in pendingImages" :key="item.id" class="image-preview">
          <img :src="item.preview" alt="" />
          <el-button
            class="image-preview-close"
            :icon="Close"
            circle
            size="small"
            @click="removePendingImage(idx)"
          />
        </div>
      </div>

      <div class="input-row">
        <input
          ref="fileInputRef"
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif"
          multiple
          style="display: none"
          @change="handleImageSelect"
        />
        <el-button
          class="input-btn input-btn--attach"
          :icon="Picture"
          :disabled="streaming || uploadingImage || pendingImages.length >= 2"
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
          class="input-btn input-btn--send"
          :icon="Promotion"
          :disabled="(!inputText.trim() && !allPendingUploaded) || streaming || uploadingImage"
          circle
          @click="handleSend"
        />
      </div>
    </div>
    </div>

    <!-- Image Preview Dialog -->
    <el-dialog v-model="imagePreviewVisible" title="图片预览" width="auto" :close-on-click-modal="true">
      <img :src="resolveImageSrc(imagePreviewUrl)" alt="" style="max-width: 80vw; max-height: 70vh;" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, ArrowDown, Picture, Promotion, Close,
  User, UserFilled, Loading, More
} from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import { getConversation, getMessages, sendMessageStream, uploadImage } from '@/api/conversation'
import { getCharacter } from '@/api/character'
import { getMe } from '@/api/auth'
import { resolveImageSrc } from '@/utils/image'

const route = useRoute()
const router = useRouter()

const convId = ref(null)
const characterName = ref('')
const characterAvatar = ref('')
const userAvatar = ref('')
const messages = ref([])
const loadingMessages = ref(false)
const streaming = ref(false)
const streamPhase = ref('')
const uploadingImage = ref(false)

const streamStatusText = computed(() => {
  if (streamPhase.value === 'vision') return '正在看图片…'
  return '正在输入…'
})

const MAX_PENDING_IMAGES = 2

const inputText = ref('')
const pendingImages = ref([])
const fileInputRef = ref(null)

const allPendingUploaded = computed(() =>
  pendingImages.value.length > 0
  && pendingImages.value.every(p => !!p.remoteUrl)
  && !pendingImages.value.some(p => p.uploading)
)

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
  try {
    const me = await getMe()
    userAvatar.value = me.data?.avatarUrl || ''
  } catch { /* ignore */ }

  const id = route.params.id
  if (id) {
    convId.value = Number(id)
    await loadConversation(convId.value)
  }
})

onUnmounted(() => {
  pendingImages.value.forEach((item) => revokeBlobUrl(item.preview))
  messages.value.forEach((msg) => {
    msg.localPreviewUrls?.forEach((url) => revokeBlobUrl(url))
    revokeBlobUrl(msg.localPreviewUrl)
  })
})

function revokeBlobUrl(url) {
  if (url?.startsWith('blob:')) {
    URL.revokeObjectURL(url)
  }
}

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
    messages.value = (res.data || []).map(m => ({
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
  const imageUrls = pendingImages.value.map(p => p.remoteUrl).filter(Boolean)

  if (pendingImages.value.length && !allPendingUploaded.value) {
    ElMessage.warning('图片还在上传，请稍候')
    return
  }
  if ((!text && !imageUrls.length) || streaming.value) return

  const localPreviewUrls = pendingImages.value
    .filter(p => p.objectUrl)
    .map(p => p.preview)

  const userMsg = {
    _key: 'u' + Date.now(),
    role: 'user',
    content: text,
    imageUrls: imageUrls.length ? imageUrls : undefined,
    imageUrl: imageUrls[0] || undefined,
    localPreviewUrls: localPreviewUrls.length ? localPreviewUrls : undefined,
    seq: Date.now()
  }
  messages.value.push(userMsg)

  inputText.value = ''
  detachPendingImagesWithoutRevoke()
  await nextTick()
  scrollToBottom(true)

  streaming.value = true
  streamPhase.value = imageUrls.length ? 'vision' : 'chat'

  let accumulatedContent = ''
  let splitPieces = null
  const baseSeq = Date.now()

  try {
    const response = await sendMessageStream(convId.value, {
      content: text,
      imageUrls: imageUrls.length ? imageUrls : undefined
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
          if (payload.status === 'vision') {
            streamPhase.value = 'vision'
          } else if (payload.status === 'chat') {
            streamPhase.value = 'chat'
          }
          if (payload.content) {
            accumulatedContent += payload.content
          }
          if (payload.splitContents?.length) {
            splitPieces = payload.splitContents
          }
        } catch (e) {
          if (e instanceof SyntaxError) continue
          throw e
        }
      }
    }

    publishAssistantReplies(splitPieces, accumulatedContent, baseSeq)
    await nextTick()
    scrollToBottom(true)
  } catch (err) {
    ElMessage.error(err.message || '消息发送失败')
  } finally {
    streaming.value = false
    streamPhase.value = ''
  }
}

/** 等整轮响应结束后再一次性展示，避免「识图中/思考中」占位气泡 */
function publishAssistantReplies(splitPieces, accumulatedContent, baseSeq) {
  const pieces = splitPieces?.length
    ? splitPieces.filter(p => p?.trim())
    : accumulatedContent.trim()
      ? [accumulatedContent.trim()]
      : []
  if (!pieces.length) return

  const baseKey = 'a' + baseSeq
  for (let i = 0; i < pieces.length; i++) {
    messages.value.push({
      _key: `${baseKey}-s${i}`,
      role: 'assistant',
      content: pieces[i].trim(),
      seq: baseSeq + i
    })
  }
}

function renderMarkdown(text) {
  if (!text) return ''
  return md.render(text)
}

function displayMessageText(msg) {
  if (!msg?.content) return ''
  return msg.content.trim()
}

function resolveMessageImageUrls(msg) {
  if (msg.localPreviewUrls?.length) {
    return msg.localPreviewUrls
  }
  if (Array.isArray(msg.imageUrls) && msg.imageUrls.length) {
    return msg.imageUrls
  }
  if (typeof msg.imageUrls === 'string' && msg.imageUrls) {
    try {
      const parsed = JSON.parse(msg.imageUrls)
      if (Array.isArray(parsed) && parsed.length) return parsed
    } catch { /* ignore */ }
  }
  if (msg.localPreviewUrl) return [msg.localPreviewUrl]
  if (msg.imageUrl) return [msg.imageUrl]
  return []
}

function triggerImageSelect() {
  if (pendingImages.value.length >= MAX_PENDING_IMAGES) {
    ElMessage.warning('一次最多发送 2 张图片')
    return
  }
  fileInputRef.value?.click()
}

function detachPendingImagesWithoutRevoke() {
  pendingImages.value = []
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

function removePendingImage(index) {
  const item = pendingImages.value[index]
  if (item?.preview) {
    revokeBlobUrl(item.preview)
  }
  pendingImages.value.splice(index, 1)
}

function clearPendingImages() {
  pendingImages.value.forEach((item) => revokeBlobUrl(item.preview))
  detachPendingImagesWithoutRevoke()
}

async function handleImageSelect(event) {
  const files = Array.from(event.target.files || [])
  if (!files.length) return

  const remaining = MAX_PENDING_IMAGES - pendingImages.value.length
  if (remaining <= 0) {
    ElMessage.warning('一次最多发送 2 张图片')
    event.target.value = ''
    return
  }
  const toAdd = files.slice(0, remaining)
  if (files.length > remaining) {
    ElMessage.warning('一次最多发送 2 张图片')
  }

  uploadingImage.value = true
  try {
    for (const file of toAdd) {
      if (file.size > 5 * 1024 * 1024) {
        ElMessage.warning('图片大小不能超过 5MB')
        continue
      }
      const objectUrl = URL.createObjectURL(file)
      const slotId = 'p' + Date.now() + Math.random()
      pendingImages.value.push({
        id: slotId,
        preview: objectUrl,
        objectUrl,
        remoteUrl: '',
        uploading: true
      })
      const slotIndex = pendingImages.value.findIndex(p => p.id === slotId)
      try {
        const result = await uploadImage(file)
        const remoteUrl = extractUploadedImageUrl(result)
        if (!remoteUrl) {
          throw new Error('图片上传失败')
        }
        // 必须通过数组下标替换，确保 Vue 响应式更新 remoteUrl
        pendingImages.value[slotIndex] = {
          ...pendingImages.value[slotIndex],
          remoteUrl,
          uploading: false
        }
      } catch (err) {
        const failed = pendingImages.value[slotIndex]
        if (failed?.preview) {
          revokeBlobUrl(failed.preview)
        }
        pendingImages.value.splice(slotIndex, 1)
        throw err
      }
    }
  } catch (err) {
    ElMessage.error(err.message || '图片上传失败')
  } finally {
    uploadingImage.value = false
    if (fileInputRef.value) {
      fileInputRef.value.value = ''
    }
  }
}

function extractUploadedImageUrl(result) {
  const data = result?.data
  if (!data) return ''
  if (typeof data === 'string') return data
  return data.imageUrl || data.url || ''
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
  justify-content: center;
  min-height: 100vh;
  padding: 0;
}

.chat-shell {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 480px;
  height: 100vh;
  height: 100dvh;
  background: var(--bg-base);
  border-left: 1px solid rgba(255, 255, 255, 0.06);
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: var(--shadow-soft);
  position: relative;
  overflow: hidden;
}

.chat-shell::before {
  content: '';
  position: absolute;
  top: -120px;
  left: 50%;
  transform: translateX(-50%);
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, var(--accent-glow), transparent 70%);
  pointer-events: none;
  opacity: 0.5;
}

/* Header */
.chat-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  padding-top: max(14px, env(safe-area-inset-top));
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  background: var(--bg-glass);
  backdrop-filter: blur(20px);
  position: sticky;
  top: 0;
  z-index: 10;
  flex-shrink: 0;
}

.icon-btn {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.04);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text-secondary);
  transition: background 0.2s, color 0.2s, border-color 0.2s;
  flex-shrink: 0;
}

.icon-btn:hover {
  background: rgba(255, 107, 122, 0.12);
  border-color: rgba(255, 107, 122, 0.3);
  color: var(--accent-soft);
}

.header-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.header-avatar-ring {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  padding: 2px;
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-soft);
  overflow: hidden;
}

.header-avatar {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--bg-base);
}

.header-text {
  min-width: 0;
}

.header-name {
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}

.typing-label {
  font-size: 12px;
  color: var(--accent-soft);
  font-weight: 500;
}

.typing-dots {
  display: flex;
  gap: 3px;
  align-items: center;
}

.typing-dots i {
  display: block;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--accent);
  animation: dotBounce 1.2s ease-in-out infinite;
}

.typing-dots i:nth-child(2) { animation-delay: 0.15s; }
.typing-dots i:nth-child(3) { animation-delay: 0.3s; }

@keyframes dotBounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-4px); opacity: 1; }
}

/* Message List */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background:
    radial-gradient(ellipse at 50% 0%, rgba(255, 107, 122, 0.04), transparent 50%),
    var(--bg-base);
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.12) transparent;
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 48px 0;
  color: var(--text-muted);
  font-size: 14px;
}

.loading-icon {
  animation: spin 1s linear infinite;
  color: var(--accent);
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
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: 1px solid rgba(255, 107, 122, 0.25);
  background: var(--bg-glass);
  backdrop-filter: blur(12px);
  color: var(--accent-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: var(--shadow-soft);
  transition: transform 0.2s, box-shadow 0.2s;
}

.scroll-bottom-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-glow);
}

/* Message Row */
.msg-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  animation: msgIn 0.45s cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes msgIn {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.msg-row--user {
  justify-content: flex-end;
}

.msg-row--assistant {
  justify-content: flex-start;
}

.msg-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 2px;
}

.msg-avatar--assistant {
  background: var(--bg-elevated);
  border: 1px solid rgba(255, 107, 122, 0.25);
  color: var(--accent-soft);
}

.msg-avatar--user {
  background: rgba(255, 107, 122, 0.15);
  border: 1px solid rgba(255, 107, 122, 0.2);
  color: var(--accent-soft);
}

.msg-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* Bubbles */
.msg-bubble {
  max-width: 78%;
  padding: 11px 15px;
  border-radius: 20px;
  position: relative;
  word-break: break-word;
}

.bubble-user {
  background: var(--user-bubble);
  color: #fff;
  border-bottom-right-radius: 6px;
  box-shadow: 0 4px 20px rgba(232, 69, 97, 0.35);
}

.bubble-assistant {
  background: var(--assistant-bubble);
  color: var(--text-primary);
  border-bottom-left-radius: 6px;
  border: 1px solid var(--assistant-border);
  backdrop-filter: blur(8px);
}

.bubble-images {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.bubble-image-item {
  width: 112px;
  height: 112px;
  object-fit: cover;
  border-radius: var(--radius-sm);
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.1);
  transition: transform 0.2s, box-shadow 0.2s;
}

.bubble-image-item:hover {
  transform: scale(1.03);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}

.bubble-user .bubble-image-item {
  border-color: rgba(255, 255, 255, 0.2);
}

.bubble-text {
  font-size: 15px;
  line-height: 1.65;
  white-space: pre-wrap;
}

.bubble-user .bubble-text {
  color: #fff;
}

.bubble-time {
  display: block;
  font-size: 10px;
  margin-top: 6px;
  text-align: right;
  letter-spacing: 0.02em;
}

.bubble-user .bubble-time {
  color: rgba(255, 255, 255, 0.55);
}

.bubble-assistant .bubble-time {
  color: var(--text-muted);
}

/* Markdown */
.markdown-body :deep(p) {
  margin: 0 0 6px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(code) {
  background: rgba(255, 255, 255, 0.08);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}

.markdown-body :deep(pre) {
  background: rgba(0, 0, 0, 0.25);
  padding: 12px;
  border-radius: var(--radius-sm);
  overflow-x: auto;
  font-size: 13px;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--accent);
  padding-left: 12px;
  margin: 8px 0;
  color: var(--text-secondary);
}

/* Input Area */
.input-area {
  flex-shrink: 0;
  padding: 12px 14px;
  padding-bottom: max(14px, env(safe-area-inset-bottom));
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  background: var(--bg-glass);
  backdrop-filter: blur(20px);
}

.image-preview-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.image-preview {
  position: relative;
  display: inline-block;
}

.image-preview img {
  width: 68px;
  height: 68px;
  object-fit: cover;
  border-radius: var(--radius-sm);
  border: 2px solid rgba(255, 107, 122, 0.35);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
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
  border-radius: var(--radius-pill);
  padding: 11px 18px;
  resize: none;
  background: var(--bg-elevated) !important;
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.5;
}

.input-row :deep(.el-textarea__inner::placeholder) {
  color: var(--text-muted);
}

.input-btn {
  flex-shrink: 0;
}

.input-btn--attach {
  background: rgba(255, 255, 255, 0.05) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  color: var(--text-secondary) !important;
}

.input-btn--attach:hover:not(:disabled) {
  border-color: rgba(255, 107, 122, 0.35) !important;
  color: var(--accent-soft) !important;
}

.input-btn--send {
  box-shadow: 0 4px 16px rgba(255, 107, 122, 0.4);
}

@media (min-width: 520px) {
  .chat-page {
    padding: 24px 16px;
    align-items: center;
  }

  .chat-shell {
    height: calc(100vh - 48px);
    height: calc(100dvh - 48px);
    border-radius: var(--radius-lg);
    border: 1px solid rgba(255, 255, 255, 0.08);
    overflow: hidden;
  }
}

@media (max-width: 519px) {
  .chat-shell {
    border-left: none;
    border-right: none;
  }

  .msg-bubble {
    max-width: 84%;
  }
}
</style>
