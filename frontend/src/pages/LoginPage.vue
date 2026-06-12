<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <div class="login-logo">♡</div>
        <h1 class="app-title">MultiModal Lover</h1>
        <p class="app-subtitle">与你心爱的角色，在深夜私信里相遇</p>
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
  padding: 24px;
}

.login-card {
  position: relative;
  z-index: 2;
  width: 420px;
  max-width: 100%;
  padding: 44px 36px 36px;
  border-radius: var(--radius-lg);
  background: var(--bg-glass);
  backdrop-filter: blur(24px);
  box-shadow: var(--shadow-soft), 0 0 0 1px rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 107, 122, 0.12);
  animation: cardIn 0.6s cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes cardIn {
  from {
    opacity: 0;
    transform: translateY(24px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.login-logo {
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #fff;
  box-shadow: 0 8px 28px rgba(255, 107, 122, 0.45);
}

.app-title {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
  letter-spacing: 0.04em;
}

.app-subtitle {
  font-size: 14px;
  color: var(--text-muted);
  line-height: 1.5;
}

.submit-btn {
  width: 100%;
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.06em;
  box-shadow: 0 6px 24px rgba(255, 107, 122, 0.35);
}

.toggle-mode {
  text-align: center;
  margin-top: 12px;
  font-size: 14px;
  color: var(--text-muted);
}

.login-bg-decor {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.decor-circle {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
}

.decor-1 {
  width: 360px;
  height: 360px;
  background: rgba(255, 107, 122, 0.18);
  top: -80px;
  right: -60px;
  animation: float 8s ease-in-out infinite;
}

.decor-2 {
  width: 280px;
  height: 280px;
  background: rgba(232, 69, 97, 0.12);
  bottom: -40px;
  left: -40px;
  animation: float 10s ease-in-out infinite reverse;
}

.decor-3 {
  width: 180px;
  height: 180px;
  background: rgba(255, 179, 188, 0.1);
  top: 40%;
  left: 55%;
  animation: float 12s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(12px, -16px); }
}
</style>
