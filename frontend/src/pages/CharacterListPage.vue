<template>
  <div class="character-page">
    <header class="page-header">
      <div class="header-content">
        <div class="header-brand">
          <h1 class="page-title">MultiModal Lover</h1>
          <p class="page-subtitle">选择一位角色，开始私密对话</p>
        </div>
        <div class="header-actions">
          <el-button :icon="User" circle @click="goProfile" title="个人资料" />
          <div class="hint-anchor hint-anchor--create">
            <el-button type="primary" :icon="Plus" @click="openCreateDialog">
              创建角色
            </el-button>
            <Transition name="hint-fade">
              <div v-if="showPageHints" class="page-hint page-hint--create">
                <span class="page-hint__icon">✨</span>
                添加自己想要的角色
              </div>
            </Transition>
          </div>
          <el-button :icon="SwitchButton" text @click="handleLogout">
            退出
          </el-button>
        </div>
      </div>
    </header>

    <div class="character-grid" v-loading="loading">
      <el-empty v-if="!loading && characters.length === 0" description="还没有角色，创建一个吧" />

      <div
        v-for="char in characters"
        :key="char.id"
        class="character-card"
        :class="{ 'character-card--builtin': char.isBuiltin === 1 }"
        @click="enterChat(char)"
      >
        <Transition name="hint-fade">
          <div
            v-if="showPageHints && char.isBuiltin === 1"
            class="page-hint page-hint--builtin"
          >
            <span class="page-hint__icon">💬</span>
            试试该内置角色，直接开聊
          </div>
        </Transition>
        <div class="card-avatar">
          <img
            v-if="char.avatarUrl"
            :src="resolveImageSrc(char.avatarUrl)"
            :alt="char.name"
          />
          <el-icon v-else :size="40"><User /></el-icon>
        </div>
        <div class="card-body">
          <h3 class="card-name">{{ char.name }}</h3>
          <p class="card-personality">{{ char.personality || '暂无描述' }}</p>
        </div>
        <div class="card-actions" @click.stop>
          <el-button :icon="Edit" circle size="small" @click="openEditDialog(char)" />
          <el-button
            :icon="Delete"
            circle
            size="small"
            type="danger"
            :disabled="char.isBuiltin === 1"
            @click="handleDelete(char)"
          />
        </div>
      </div>
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingChar ? '编辑角色' : '创建角色'"
      width="500px"
      :close-on-click-modal="false"
    >
      <!-- AI 生成区块 -->
      <div class="ai-generate-box">
        <div class="ai-generate-tip">💡 输入动漫/游戏角色名，AI 会联网搜索并自动填充下方表单</div>
        <div class="ai-generate-row">
          <el-input
            v-model="generateName"
            placeholder="输入动漫/游戏角色名，如：时崎狂三"
            :disabled="generateLoading"
            @keyup.enter="handleGenerate"
          />
          <el-button
            type="primary"
            :loading="generateLoading"
            :disabled="generateLoading || !canGenerate"
            @click="handleGenerate"
          >
            {{ generateLoading ? 'AI 生成中...' : '✨ AI 生成设定' }}
          </el-button>
        </div>
      </div>

      <el-form
        ref="dialogFormRef"
        :model="dialogForm"
        :rules="dialogRules"
        label-position="top"
      >
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="dialogForm.name" placeholder="输入角色名称" />
        </el-form-item>
        <el-form-item label="性格描述" prop="personality">
          <el-input
            v-model="dialogForm.personality"
            type="textarea"
            :rows="3"
            placeholder="描述角色的性格特点..."
          />
        </el-form-item>
        <el-form-item label="角色头像">
          <div class="avatar-upload">
            <div class="avatar-preview" v-if="dialogForm.avatarUrl">
              <img :src="resolveImageSrc(dialogForm.avatarUrl)" alt="预览" />
              <el-button type="danger" size="small" circle @click="dialogForm.avatarUrl = ''">×</el-button>
            </div>
            <el-upload
              v-else
              :show-file-list="false"
              :before-upload="handleAvatarUpload"
              accept="image/jpeg,image/png,image/gif,image/webp"
              drag
            >
              <el-icon :size="32"><Plus /></el-icon>
              <div>点击或拖拽上传头像</div>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="角色设定 (System Prompt)" prop="systemPrompt" required>
          <el-input
            v-model="dialogForm.systemPrompt"
            type="textarea"
            :rows="4"
            placeholder="角色的系统提示词..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="handleDialogSubmit">
          {{ editingChar ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, User, SwitchButton } from '@element-plus/icons-vue'
import { listCharacters, createCharacter, updateCharacter, deleteCharacter, uploadAvatar, generateCharacter } from '@/api/character'
import { createConversation, listConversations } from '@/api/conversation'
import { resolveImageSrc } from '@/utils/image'

const router = useRouter()
const characters = ref([])
const loading = ref(false)
const showPageHints = ref(false)
let hintTimer = null

// Dialog state
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const editingChar = ref(null)
const dialogFormRef = ref(null)

// AI generation state
const generateName = ref('')
const generateLoading = ref(false)

const dialogForm = reactive({
  name: '',
  gender: '',
  personality: '',
  speakingStyle: '',
  backstory: '',
  avatarUrl: '',
  systemPrompt: ''
})

const canGenerate = computed(() =>
  Boolean(generateName.value.trim() || dialogForm.name.trim())
)

const dialogRules = {
  name: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 64, message: '名称不能超过 64 个字符', trigger: 'blur' }
  ],
  personality: [
    { max: 512, message: '描述不能超过 512 个字符', trigger: 'blur' }
  ],
  systemPrompt: [
    { required: true, message: '请输入角色 Prompt', trigger: 'blur' },
    { max: 2048, message: '提示词不能超过 2048 个字符', trigger: 'blur' }
  ]
}

onMounted(() => {
  fetchCharacters()
  startPageHints()
})

onUnmounted(() => {
  if (hintTimer) clearTimeout(hintTimer)
})

function startPageHints() {
  showPageHints.value = true
  if (hintTimer) clearTimeout(hintTimer)
  hintTimer = setTimeout(() => {
    showPageHints.value = false
  }, 5000)
}

async function fetchCharacters() {
  loading.value = true
  try {
    const res = await listCharacters()
    characters.value = res.data || []
  } catch {
    characters.value = []
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingChar.value = null
  generateName.value = ''
  dialogForm.name = ''
  dialogForm.gender = ''
  dialogForm.personality = ''
  dialogForm.speakingStyle = ''
  dialogForm.backstory = ''
  dialogForm.avatarUrl = ''
  dialogForm.systemPrompt = ''
  dialogVisible.value = true
}

function openEditDialog(char) {
  editingChar.value = char
  dialogForm.name = char.name || ''
  dialogForm.gender = char.gender || ''
  dialogForm.personality = char.personality || ''
  dialogForm.speakingStyle = char.speakingStyle || ''
  dialogForm.backstory = char.backstory || ''
  dialogForm.avatarUrl = char.avatarUrl || ''
  dialogForm.systemPrompt = char.promptTemplate || char.systemPrompt || ''
  dialogVisible.value = true
}

async function handleGenerate() {
  const name = generateName.value.trim() || dialogForm.name.trim()
  if (!name) {
    ElMessage.warning('请先输入动漫/游戏角色名')
    return
  }
  generateLoading.value = true
  try {
    const res = await generateCharacter(name)
    const data = res.data || {}
    dialogForm.name = data.name || name
    dialogForm.gender = data.gender || ''
    dialogForm.personality = data.personality || ''
    dialogForm.speakingStyle = data.speakingStyle || ''
    dialogForm.backstory = data.backstory || ''
    dialogForm.systemPrompt = data.promptTemplate || ''
    if (!dialogForm.systemPrompt.trim()) {
      ElMessage.warning('AI 未生成 Prompt，请手动填写角色设定')
    }
    ElMessage.success('角色设定已生成，你可以继续修改')
  } catch {
    // handled by interceptor
  } finally {
    generateLoading.value = false
  }
}

async function handleAvatarUpload(file) {
  const isImage = file.type.startsWith('image/')
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isImage) { ElMessage.error('只能上传图片文件'); return false }
  if (!isLt5M) { ElMessage.error('图片大小不能超过 5MB'); return false }
  try {
    const res = await uploadAvatar(file)
    dialogForm.avatarUrl = res.data?.avatarUrl || ''
    ElMessage.success('头像上传成功')
  } catch { /* handled by interceptor */ }
  return false // 阻止 el-upload 的默认上传行为
}

async function handleDialogSubmit() {
  const valid = await dialogFormRef.value.validate().catch(() => false)
  if (!valid) return

  dialogLoading.value = true
  try {
    const data = {
      name: dialogForm.name,
      gender: dialogForm.gender || undefined,
      personality: dialogForm.personality || undefined,
      speakingStyle: dialogForm.speakingStyle || undefined,
      backstory: dialogForm.backstory || undefined,
      avatarUrl: dialogForm.avatarUrl || undefined,
      promptTemplate: dialogForm.systemPrompt
    }
    if (editingChar.value) {
      await updateCharacter(editingChar.value.id, data)
      ElMessage.success('角色已更新')
    } else {
      await createCharacter(data)
      ElMessage.success('角色已创建')
    }
    dialogVisible.value = false
    await fetchCharacters()
  } catch {
    // handled by interceptor
  } finally {
    dialogLoading.value = false
  }
}

async function handleDelete(char) {
  try {
    await ElMessageBox.confirm(`确定要删除角色 "${char.name}" 吗？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteCharacter(char.id)
    ElMessage.success('角色已删除')
    await fetchCharacters()
  } catch {
    // handled by interceptor
  }
}

async function enterChat(char) {
  try {
    // Try to find an existing conversation with this character
    const convsRes = await listConversations()
    const convs = convsRes.data || []
    let conv = convs.find(c => c.characterId === char.id)

    if (!conv) {
      const createRes = await createConversation({ characterId: char.id })
      conv = createRes.data
    }

    if (conv?.id) {
      router.push(`/chat/${conv.id}`)
    }
  } catch {
    // handled by interceptor
  }
}

function goProfile() {
  router.push('/profile')
}

function handleLogout() {
  localStorage.removeItem('vlover-token')
  router.push('/login')
}
</script>

<style scoped>
.character-page {
  min-height: 100vh;
  padding-bottom: 48px;
}

.page-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--bg-glass);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  padding: 20px 24px;
}

.header-content {
  max-width: 1040px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.header-brand {
  min-width: 0;
}

.page-title {
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.03em;
}

.page-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-muted);
  font-weight: 400;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}

.character-grid {
  max-width: 1040px;
  margin: 28px auto;
  padding: 0 24px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px;
  min-height: 200px;
}

.character-card {
  position: relative;
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  border: 1px solid rgba(255, 255, 255, 0.07);
  padding: 28px 20px 22px;
  cursor: pointer;
  transition: transform 0.25s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.25s, border-color 0.25s;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  overflow: hidden;
}

.character-card::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 50% 0%, rgba(255, 107, 122, 0.08), transparent 60%);
  opacity: 0;
  transition: opacity 0.25s;
  pointer-events: none;
}

.character-card:hover {
  transform: translateY(-6px);
  border-color: rgba(255, 107, 122, 0.25);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.35), 0 0 0 1px rgba(255, 107, 122, 0.1);
}

.character-card:hover::after {
  opacity: 1;
}

.character-card--builtin {
  overflow: visible;
  border-color: rgba(255, 107, 122, 0.2);
}

.card-avatar {
  width: 88px;
  height: 88px;
  border-radius: 22px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-elevated);
  margin-bottom: 16px;
  color: var(--accent-soft);
  border: 2px solid rgba(255, 107, 122, 0.25);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
  transition: transform 0.25s;
}

.character-card:hover .card-avatar {
  transform: scale(1.04);
}

.card-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card-body {
  flex: 1;
}

.card-name {
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.card-personality {
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-actions {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.character-card:hover .card-actions {
  opacity: 1;
}

.ai-generate-box {
  background: rgba(255, 107, 122, 0.08);
  border-radius: var(--radius-md);
  padding: 18px;
  margin-bottom: 20px;
  border: 1px dashed rgba(255, 107, 122, 0.3);
}

.ai-generate-tip {
  font-size: 13px;
  color: var(--accent-soft);
  margin-bottom: 12px;
  text-align: center;
  font-weight: 500;
}

.ai-generate-row {
  display: flex;
  gap: 8px;
}

.ai-generate-row .el-input {
  flex: 1;
}

.avatar-upload {
  text-align: center;
}

.avatar-preview {
  position: relative;
  display: inline-block;
}

.avatar-preview img {
  width: 120px;
  height: 120px;
  border-radius: var(--radius-md);
  object-fit: cover;
  border: 2px solid rgba(255, 107, 122, 0.3);
}

.avatar-preview .el-button {
  position: absolute;
  top: -8px;
  right: -8px;
}

.hint-anchor {
  position: relative;
  display: inline-flex;
}

.page-hint {
  position: absolute;
  z-index: 20;
  pointer-events: none;
  white-space: nowrap;
  padding: 8px 14px;
  border-radius: var(--radius-pill);
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  box-shadow: 0 8px 24px rgba(255, 107, 122, 0.4);
  animation: hint-bounce 1.1s ease-in-out infinite;
}

.page-hint__icon {
  margin-right: 4px;
}

.page-hint--create {
  top: calc(100% + 10px);
  right: 0;
  background: linear-gradient(135deg, var(--accent-deep), var(--accent));
}

.page-hint--create::before {
  content: '';
  position: absolute;
  top: -6px;
  right: 24px;
  border-left: 6px solid transparent;
  border-right: 6px solid transparent;
  border-bottom: 6px solid var(--accent-deep);
}

.page-hint--builtin {
  top: -12px;
  left: 50%;
  transform: translateX(-50%);
  background: linear-gradient(135deg, var(--accent), var(--accent-soft));
  animation: hint-bounce-center 1.1s ease-in-out infinite;
}

.page-hint--builtin::before {
  content: '';
  position: absolute;
  bottom: -6px;
  left: 50%;
  margin-left: -6px;
  border-left: 6px solid transparent;
  border-right: 6px solid transparent;
  border-top: 6px solid var(--accent);
}

@keyframes hint-bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-7px); }
}

@keyframes hint-bounce-center {
  0%, 100% { transform: translateX(-50%) translateY(0); }
  50% { transform: translateX(-50%) translateY(-7px); }
}

.hint-fade-enter-active,
.hint-fade-leave-active {
  transition: opacity 0.45s ease, transform 0.45s ease;
}

.hint-fade-enter-from,
.hint-fade-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

.hint-fade-enter-from.page-hint--builtin,
.hint-fade-leave-to.page-hint--builtin {
  transform: translateX(-50%) translateY(6px);
}
</style>
