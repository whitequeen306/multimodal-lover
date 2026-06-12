<template>
  <div class="profile-page">
    <header class="page-header">
      <el-button :icon="ArrowLeft" circle @click="goBack" />
      <h1>个人资料</h1>
      <div class="header-spacer" />
    </header>

    <div class="profile-card" v-loading="loading">
      <div class="avatar-section">
        <div class="avatar-preview">
          <img v-if="form.avatarUrl" :src="resolveImageSrc(form.avatarUrl)" alt="头像" />
          <el-icon v-else :size="48"><UserFilled /></el-icon>
        </div>
        <input
          ref="fileInputRef"
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif"
          style="display: none"
          @change="handleAvatarSelect"
        />
        <el-button :loading="uploading" @click="triggerUpload">更换头像</el-button>
      </div>

      <el-form label-width="72px" class="profile-form">
        <el-form-item label="用户名">
          <el-input :model-value="form.username" disabled />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" maxlength="50" placeholder="设置昵称" />
        </el-form-item>
      </el-form>

      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, UserFilled } from '@element-plus/icons-vue'
import { getMe, updateProfile, uploadUserAvatar } from '@/api/auth'
import { resolveImageSrc } from '@/utils/image'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const fileInputRef = ref(null)

const form = reactive({
  username: '',
  nickname: '',
  avatarUrl: ''
})

onMounted(loadProfile)

async function loadProfile() {
  loading.value = true
  try {
    const res = await getMe()
    form.username = res.data?.username || ''
    form.nickname = res.data?.nickname || ''
    form.avatarUrl = res.data?.avatarUrl || ''
  } catch {
    ElMessage.error('加载资料失败')
  } finally {
    loading.value = false
  }
}

function triggerUpload() {
  fileInputRef.value?.click()
}

async function handleAvatarSelect(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    event.target.value = ''
    return
  }

  uploading.value = true
  try {
    const res = await uploadUserAvatar(file)
    form.avatarUrl = res.data?.avatarUrl || ''
    ElMessage.success('头像上传成功')
  } catch (err) {
    ElMessage.error(err.message || '头像上传失败')
  } finally {
    uploading.value = false
    event.target.value = ''
  }
}

async function handleSave() {
  saving.value = true
  try {
    await updateProfile({
      nickname: form.nickname.trim(),
      avatarUrl: form.avatarUrl || undefined
    })
    ElMessage.success('保存成功')
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/characters')
}
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  padding-bottom: 48px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  background: var(--bg-glass);
  backdrop-filter: blur(20px);
  position: sticky;
  top: 0;
  z-index: 10;
}

.page-header h1 {
  flex: 1;
  margin: 0;
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  text-align: center;
}

.header-spacer {
  width: 32px;
}

.profile-card {
  max-width: 440px;
  margin: 32px auto;
  padding: 32px 28px;
  background: var(--bg-surface);
  border-radius: var(--radius-lg);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: var(--shadow-soft);
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
}

.avatar-preview {
  width: 104px;
  height: 104px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--bg-elevated);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-soft);
  border: 3px solid rgba(255, 107, 122, 0.35);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.3);
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-form {
  margin-bottom: 24px;
}

.profile-card > .el-button {
  width: 100%;
  font-family: var(--font-display);
  font-weight: 600;
}
</style>
