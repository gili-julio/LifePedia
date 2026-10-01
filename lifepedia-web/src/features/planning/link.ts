/** Só links http(s) (a API já valida); evita `javascript:` e afins no href. */
export function isSafeLink(url: string | null | undefined): url is string {
  return !!url && /^https?:\/\//i.test(url)
}

/** "https://www.loja.com.br/x" → "loja.com.br" */
export function hostOf(url: string) {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}
