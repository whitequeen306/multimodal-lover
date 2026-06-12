<template>
  <div class="character-page">
    <header class="page-header">
      <div class="header-content">
        <h1 class="page-title">我的角色</h1>
        <div class="header-actions">
          <el-button type="primary" :icon="Plus" @click="openCreateDialog">
            创建角色
          </el-button>
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
        @click="enterChat(char)"
      >
        <div class="card-avatar">
          <img
            v-if="char.avatarUrl"
            :src="char.avatarUrl"
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
            :disabled="char.builtin"
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
              <img :src="dialogForm.avatarUrl" alt="预览" />
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
        <el-form-item label="角色设定 (System Prompt)" prop="systemPrompt">
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
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, User, SwitchButton } from '@element-plus/icons-vue'
import { listCharacters, createCharacter, updateCharacter, deleteCharacter, uploadAvatar } from '@/api/character'
import { createConversation, listConversations } from '@/api/conversation'

const router = useRouter()
const characters = ref([])
const loading = ref(false)

// Dialog state
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const editingChar = ref(null)
const dialogFormRef = ref(null)

const dialogForm = reactive({
  name: '',
  personality: '',
  avatarUrl: '',
  systemPrompt: ''
})

const dialogRules = {
  name: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 64, message: '名称不能超过 64 个字符', trigger: 'blur' }
  ],
  personality: [
    { max: 512, message: '描述不能超过 512 个字符', trigger: 'blur' }
  ],
  systemPrompt: [
    { max: 2048, message: '提示词不能超过 2048 个字符', trigger: 'blur' }
  ]
}

onMounted(() => {
  fetchCharacters()
})

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
  dialogForm.name = ''
  dialogForm.personality = ''
  dialogForm.avatarUrl = ''
  dialogForm.systemPrompt = ''
  dialogVisible.value = true
}

function openEditDialog(char) {
  editingChar.value = char
  dialogForm.name = char.name || ''
  dialogForm.personality = char.personality || ''
  dialogForm.avatarUrl = char.avatarUrl || ''
  dialogForm.systemPrompt = char.systemPrompt || ''
  dialogVisible.value = true
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
      personality: dialogForm.personality || undefined,
      avatarUrl: dialogForm.avatarUrl || undefined,
      systemPrompt: dialogForm.systemPrompt || undefined
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

function handleLogout() {
  localStorage.removeItem('vlover-token')
  router.push('/login')
}
</script>

<style scoped>
.character-page {
  min-height: 100vh;
  padding-bottom: 40px;
}

.page-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(236, 64, 122, 0.08);
  padding: 16px 24px;
}

.header-content {
  max-width: 1000px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(135deg, #ec407a, #7c4dff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.character-grid {
  max-width: 1000px;
  margin: 24px auto;
  padding: 0 24px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 20px;
  min-height: 200px;
}

.character-card {
  position: relative;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(236, 64, 122, 0.08);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
  padding: 24px 20px 20px;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.character-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 28px rgba(236, 64, 122, 0.10);
}

.card-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fce4ec, #f3e5f5);
  margin-bottom: 14px;
  color: #ec407a;
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
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 6px;
}

.card-personality {
  font-size: 13px;
  color: #999;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.character-card:hover .card-actions {
  opacity: 1;
}
</style>
