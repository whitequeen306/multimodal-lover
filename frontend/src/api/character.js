import api from './index'

export function listCharacters() {
  return api.get('/character')
}

export function getCharacter(id) {
  return api.get(`/character/${id}`)
}

export function createCharacter(data) {
  return api.post('/character', data)
}

export function updateCharacter(id, data) {
  return api.put(`/character/${id}`, data)
}

export function deleteCharacter(id) {
  return api.delete(`/character/${id}`)
}

export function uploadAvatar(file) {
  const fd = new FormData()
  fd.append('file', file)
  return api.post('/character/avatar-upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
