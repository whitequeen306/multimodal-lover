import api from './index'

export function listCharacters() {
  return api.get('/character/list')
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
