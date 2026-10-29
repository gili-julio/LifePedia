import { useEffect, useRef, useState } from 'react'
import { useAuthConfig } from './auth-config'

type GoogleId = {
  initialize: (options: { client_id: string; callback: (response: { credential: string }) => void; ux_mode: 'popup' }) => void
  renderButton: (parent: HTMLElement, options: Record<string, string | number>) => void
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleId } }
  }
}

const SCRIPT = 'https://accounts.google.com/gsi/client'
let loading: Promise<GoogleId> | null = null

/** Carrega o script do Google uma vez só (exceção à regra de "sem recurso externo": liberado na CSP do nginx). */
function loadGoogle(): Promise<GoogleId> {
  loading ??= new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = SCRIPT
    script.async = true
    script.onload = () => (window.google ? resolve(window.google.accounts.id) : reject(new Error('Google indisponível')))
    script.onerror = () => {
      loading = null
      reject(new Error('Google indisponível'))
    }
    document.head.appendChild(script)
  })
  return loading
}

type Props = {
  /** ID token do Google, para mandar à API */
  onCredential: (credential: string) => void
  text?: 'signin_with' | 'signup_with' | 'continue_with'
}

/** Botão oficial "Entrar com o Google"; não aparece se a API não tiver Client ID configurado. */
export function GoogleButton({ onCredential, text = 'signin_with' }: Props) {
  const { data } = useAuthConfig()
  const clientId = data?.googleClientId
  const container = useRef<HTMLDivElement>(null)
  const callback = useRef(onCredential)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    callback.current = onCredential
  }, [onCredential])

  useEffect(() => {
    if (!clientId) return
    let cancelled = false
    loadGoogle()
      .then((google) => {
        const parent = container.current
        if (cancelled || !parent) return
        google.initialize({ client_id: clientId, callback: (r) => callback.current(r.credential), ux_mode: 'popup' })
        google.renderButton(parent, {
          type: 'standard',
          theme: document.documentElement.classList.contains('dark') ? 'filled_black' : 'outline',
          size: 'large',
          shape: 'rectangular',
          text,
          locale: 'pt-BR',
          width: Math.min(400, Math.max(200, Math.round(parent.getBoundingClientRect().width))),
        })
      })
      .catch(() => !cancelled && setFailed(true))
    return () => {
      cancelled = true
    }
  }, [clientId, text])

  if (!clientId) return null
  if (failed) return <p className="text-center text-xs text-muted-foreground">Não foi possível carregar o login do Google.</p>
  return <div ref={container} className="flex min-h-10 w-full justify-center" />
}
