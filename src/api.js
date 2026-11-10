// 前端 API 客户端：所有业务读写都走服务端，登录态只保存令牌，不再缓存角色与业务数据
const TOKEN_KEY = 'zhx_token'

export const getToken = () => localStorage.getItem(TOKEN_KEY) || ''
export const setToken = (t) => localStorage.setItem(TOKEN_KEY, t)
export const clearToken = () => localStorage.removeItem(TOKEN_KEY)

async function request(method, path, body) {
  const headers = {}
  const token = getToken()
  // 登录/注册本身不携带旧令牌，避免失效会话阻塞重新登录
  if (token && !path.startsWith('/auth/')) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  let res
  try {
    res = await fetch(`/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    })
  } catch {
    return { ok: false, status: 0, msg: '无法连接服务器，请确认后端已启动（npm run server）' }
  }
  // 令牌失效/被停用：清掉本地令牌，下一次请求会以访客身份重试
  if (res.status === 401) clearToken()
  let data
  try {
    data = await res.json()
  } catch {
    return { ok: false, status: res.status, msg: `服务端响应异常（HTTP ${res.status}）` }
  }
  return { ...data, status: res.status }
}

export const api = {
  get: (path) => request('GET', path),
  post: (path, body = {}) => request('POST', path, body),
  // 图片上传走 multipart，不能按 JSON 序列化
  upload: async (file) => {
    const headers = {}
    const token = getToken()
    if (token) headers.Authorization = `Bearer ${token}`
    const fd = new FormData()
    fd.append('file', file)
    let res
    try {
      res = await fetch('/api/uploads', { method: 'POST', headers, body: fd })
    } catch {
      return { ok: false, status: 0, msg: '无法连接服务器，请确认后端已启动' }
    }
    if (res.status === 401) clearToken()
    let data
    try {
      data = await res.json()
    } catch {
      return { ok: false, status: res.status, msg: `服务端响应异常（HTTP ${res.status}）` }
    }
    return { ...data, status: res.status }
  }
}
