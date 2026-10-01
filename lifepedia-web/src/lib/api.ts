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
