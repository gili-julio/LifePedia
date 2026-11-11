/** Cliente da API: caminhos relativos, cookie de sessão e `X-Requested-With` (CSRF). */

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

type UnauthorizedListener = () => void
let onUnauthorized: UnauthorizedListener | null = null

/** Chamado quando a sessão expira (401). */
export function setUnauthorizedListener(listener: UnauthorizedListener | null) {
  onUnauthorized = listener
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  query?: Record<string, string | number | undefined | null>
}

/** Faz a requisição e trata erro/401; devolve a resposta ok. */
async function request(path: string, { method = 'GET', body, query }: RequestOptions = {}): Promise<Response> {
  const url = new URL(path, window.location.origin)
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value))
  }

  const headers: Record<string, string> = { 'X-Requested-With': 'XMLHttpRequest' }
  // FormData (upload): o navegador define o Content-Type com o boundary.
  const isForm = body instanceof FormData
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(url.pathname + url.search, {
      method,
      headers,
      credentials: 'same-origin',
      body: isForm ? body : body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'Não foi possível conectar à API. Ela está rodando?')
  }

  if (response.status === 401 && !path.startsWith('/api/auth/')) onUnauthorized?.()

  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new ApiError(
      response.status,
      problem?.detail ?? `Erro inesperado (${response.status}).`,
      problem?.errors ?? {},
    )
  }
  return response
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await request(path, options)
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

/** Baixa um arquivo da API (nome do `Content-Disposition`), sem abrir outra aba. */
export async function apiDownload(path: string, fallbackName: string) {
  const response = await request(path)
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const name = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? fallbackName
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Progresso de 0 a 1 (nulo quando o tamanho é desconhecido). */
export type ProgressListener = (fraction: number | null) => void

/** Envia um arquivo cru (não multipart) com progresso; resposta JSON como `api`. */
export function apiUpload<T>(path: string, body: Blob, method: 'PUT' | 'POST', onProgress?: ProgressListener): Promise<T> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open(method, path)
    xhr.withCredentials = true
    xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest')
    xhr.setRequestHeader('Content-Type', body.type || 'application/octet-stream')
    xhr.upload.onprogress = (e) => onProgress?.(e.lengthComputable ? e.loaded / e.total : null)
    xhr.onerror = () => reject(new ApiError(0, 'Não foi possível conectar à API. Ela está rodando?'))
    xhr.onload = () => {
      if (xhr.status === 401) onUnauthorized?.()
      let json: unknown = null
      try {
        json = xhr.responseText ? JSON.parse(xhr.responseText) : null
      } catch {
        json = null
      }
      if (xhr.status >= 200 && xhr.status < 300) return resolve(json as T)
      const problem = json as { detail?: string; errors?: Record<string, string> } | null
      reject(new ApiError(xhr.status, problem?.detail ?? `Erro inesperado (${xhr.status}).`, problem?.errors ?? {}))
    }
    xhr.send(body)
  })
}

/** Baixa um arquivo binário da API (ou do link público) com progresso. */
export async function apiBinary(path: string, onProgress?: ProgressListener): Promise<ArrayBuffer> {
  const response = await request(path)
  const total = Number(response.headers.get('Content-Length')) || 0
  if (!response.body) return response.arrayBuffer()
  const reader = response.body.getReader()
  const chunks: Uint8Array[] = []
  let loaded = 0
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    chunks.push(value)
    loaded += value.length
    onProgress?.(total ? loaded / total : null)
  }
  const out = new Uint8Array(loaded)
  let offset = 0
  for (const chunk of chunks) {
    out.set(chunk, offset)
    offset += chunk.length
  }
  return out.buffer
}
