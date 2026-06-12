import api from './index'

export function createConversation(data) {
  return api.post('/conversation', data)
}

export function listConversations() {
  return api.get('/conversation')
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
  // 不要手动设 Content-Type，浏览器会自动加 boundary
  return api.post('/conversation/upload-image', formData)
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
