import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// Request interceptor — attach token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('vlover-token')
    if (token) {
      config.headers['vlover-token'] = token
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor — 401 redirect to login
api.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        // 业务失败：显示错误消息，但仍返回给调用方处理
        ElMessage.error(body.message || '操作失败')
        return Promise.reject(new Error(body.message || '操作失败'))
      }
      // 成功：拆出 data
      if ('data' in body) {
        return { ...response, data: body.data }
      }
    }
    return response
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        localStorage.removeItem('vlover-token')
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
        ElMessage.error(data?.message || '请先登录后再操作')
        return Promise.reject(error)
      }
      // 提取错误消息（兼容 Result<T> 和 Spring 默认错误格式）
      const msg = data?.message || data?.error || (typeof data === 'string' ? data : '操作失败，请稍后重试')
      ElMessage.error(msg)
    } else if (error.message && error.message !== 'canceled') {
      ElMessage.error(error.message)
    }
    return Promise.reject(error)
  }
)

export default api
