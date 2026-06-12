import api from './index'

export function createConversation(data) {
  return api.post('/conversation', data)
}

export function listConversations() {
  return api.get('/conversation/list')
}

export function getConversation(id) {
  return api.get(`/conversation/${id}`)
}

export function deleteConversation(id) {
  return api.delete(`/conversation/${id}`)
}

export function getMessages(id, params) {
  return api.get(`/conversation/${id}/messages`, { params })
}

export function uploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/conversation/upload-image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * SSE streaming — send a message and return the raw fetch Response.
 * The caller reads the stream with response.body.getReader().
 */
export function sendMessageStream(convId, data) {
  const token = localStorage.getItem('vlover-token')
  return fetch(`/api/conversation/${convId}/messages/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'vlover-token': token || ''
    },
    body: JSON.stringify(data)
  })
}
