import api from './index'

export function login(data) {
  return api.post('/auth/login', data)
}

export function register(data) {
  return api.post('/auth/register', data)
}

export function getMe() {
  return api.get('/auth/me')
}

export function updateProfile(data) {
  return api.put('/auth/profile', data)
}

export function uploadUserAvatar(file) {
  const fd = new FormData()
  fd.append('file', file)
  return api.post('/auth/avatar-upload', fd)
}
