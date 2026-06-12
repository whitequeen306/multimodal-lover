<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1 class="app-title">MultiModal Lover</h1>
        <p class="app-subtitle">与你心爱的角色相遇</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="输入用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>

        <el-form-item v-if="isRegister" label="昵称" prop="nickname">
          <el-input
            v-model="form.nickname"
            placeholder="输入昵称（可选）"
            :prefix-icon="UserFilled"
            size="large"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            @click="handleSubmit"
          >
            {{ isRegister ? '注册' : '登录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="toggle-mode">
        <span>{{ isRegister ? '已有账号？' : '没有账号？' }}</span>
        <el-button link type="primary" @click="toggleMode">
          {{ isRegister ? '去登录' : '去注册' }}
        </el-button>
      </div>
    </div>

    <div class="login-bg-decor">
      <div class="decor-circle decor-1" />
      <div class="decor-circle decor-2" />
      <div class="decor-circle decor-3" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, UserFilled } from '@element-plus/icons-vue'
import { login, register } from '@/api/auth'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const isRegister = ref(false)

const form = reactive({
  username: '',
  password: '',
  nickname: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 32, message: '用户名长度应在 2 到 32 个字符之间', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度应在 6 到 64 个字符之间', trigger: 'blur' }
  ],
  nickname: [
    { max: 32, message: '昵称不能超过 32 个字符', trigger: 'blur' }
  ]
}

function toggleMode() {
  isRegister.value = !isRegister.value
  formRef.value?.resetFields()
  form.nickname = ''
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = {
      username: form.username,
      password: form.password
    }
    if (isRegister.value) {
      data.nickname = form.nickname || undefined
    }

    const fn = isRegister.value ? register : login
    const res = await fn(data)
    const token = res.data?.token
    if (token) {
      localStorage.setItem('vlover-token', token)
      ElMessage.success(isRegister.value ? '注册成功' : '登录成功')
      router.push('/characters')
    } else {
      ElMessage.error('未获取到登录凭证')
    }
  } catch (err) {
    // Error already handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #fce4ec 0%, #f3e5f5 40%, #e8eaf6 100%);
}

.login-card {
  position: relative;
  z-index: 2;
  width: 420px;
  max-width: 90vw;
  padding: 40px 36px 32px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  box-shadow: 0 8px 40px rgba(236, 64, 122, 0.12), 0 2px 8px rgba(0, 0, 0, 0.06);
  border: 1px solid rgba(236, 64, 122, 0.08);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.app-title {
  font-size: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, #ec407a, #7c4dff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 8px;
}

.app-subtitle {
  font-size: 14px;
  color: #9e9e9e;
}

.submit-btn {
  width: 100%;
  background: linear-gradient(135deg, #ec407a, #7c4dff) !important;
  border: none !important;
  font-size: 16px;
  font-weight: 600;
}

.submit-btn:hover {
  opacity: 0.9;
}

.toggle-mode {
  text-align: center;
  margin-top: 8px;
  font-size: 14px;
  color: #9e9e9e;
}

/* Decorative background elements */
.login-bg-decor {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.decor-circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.15;
}

.decor-1 {
  width: 400px;
  height: 400px;
  background: linear-gradient(135deg, #ec407a, #f48fb1);
  top: -100px;
  right: -80px;
}

.decor-2 {
  width: 300px;
  height: 300px;
  background: linear-gradient(135deg, #7c4dff, #b388ff);
  bottom: -60px;
  left: -60px;
}

.decor-3 {
  width: 200px;
  height: 200px;
  background: linear-gradient(135deg, #f48fb1, #7c4dff);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}
</style>
