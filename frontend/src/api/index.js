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
  (response) => response,
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        localStorage.removeItem('vlover-token')
        window.location.href = '/login'
        return Promise.reject(error)
      }
      const message = data?.message || data?.error || `Request failed (${status})`
      ElMessage.error(message)
    } else {
      ElMessage.error('Network error — please check your connection')
    }
    return Promise.reject(error)
  }
)

export default api
