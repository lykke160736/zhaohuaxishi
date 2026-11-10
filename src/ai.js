import { api } from './api.js'
export async function aiStatus() {
  const r = await api.get('/ai/status')
  return { configured: !!r.configured, message: r.message || '智能服务暂不可用' }
}
export async function aiChat(messages) {
  const r = await api.post('/ai/chat', { messages })
  if (!r.ok) throw new Error(r.msg || '智能服务暂不可用')
  return { text: r.text, queries: r.queries || [] }
}
