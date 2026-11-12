import { ExternalLinkIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ColorPicker } from '@/components/ColorPicker'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api'
import { useConnectDuolingo, useConnectToken, useGoogleStart, useReconnect } from '../api'
import { PROVIDERS } from '../providers'
import type { IntegrationAccount, IntegrationProvider } from '../types'

type Props = {
  /** Nulo = fechado */
  provider: IntegrationProvider | null
  /** Trocar o token desta conta (mesma pessoa no serviço) */
  account?: IntegrationAccount | null
  onOpenChange: (open: boolean) => void
}

/** Conectar um serviço: o passo a passo e o formulário de cada um (token, usuário ou ida ao Google). */
export function ConnectDialog({ provider, account, onOpenChange }: Props) {
  return (
    <Dialog open={provider !== null} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        {provider && <ConnectForm key={`${provider}-${account?.id ?? 'new'}`} provider={provider} account={account ?? null}
          onDone={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

function ConnectForm({ provider, account, onDone }: { provider: IntegrationProvider; account: IntegrationAccount | null; onDone: () => void }) {
  const meta = PROVIDERS[provider]
  const connectToken = useConnectToken()
  const connectDuolingo = useConnectDuolingo()
  const reconnect = useReconnect()
  const googleStart = useGoogleStart()
  const [baseUrl, setBaseUrl] = useState('')
  const [token, setToken] = useState('')
  const [username, setUsername] = useState('')
  const [nickname, setNickname] = useState('')
  const [color, setColor] = useState('#6d28d9')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const pending = connectToken.isPending || connectDuolingo.isPending || reconnect.isPending || googleStart.isPending

  async function goToGoogle() {
    try {
      const { url } = await googleStart.mutateAsync()
      window.location.assign(url)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível abrir o Google.')
    }
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    const found: Record<string, string> = {}
    if (provider === 'DUOLINGO' && !/^[A-Za-z0-9_.-]{1,60}$/.test(username.trim().replace(/^@/, ''))) {
      found.username = 'Digite o usuário do Duolingo (letras, números, ponto, hífen ou _).'
    }
    if ((provider === 'GITLAB' || provider === 'GITHUB') && !token.trim()) found.token = 'Cole o token.'
    setErrors(found)
    if (Object.keys(found).length) return
    try {
      if (account) {
        await reconnect.mutateAsync({ id: account.id, token: token.trim() })
        toast.success('Token trocado.')
      } else if (provider === 'DUOLINGO') {
        await connectDuolingo.mutateAsync({ username: username.trim().replace(/^@/, '') })
        toast.success('Duolingo conectado.')
      } else if (provider === 'GITLAB' || provider === 'GITHUB') {
        await connectToken.mutateAsync({ provider, token: token.trim(), baseUrl: baseUrl.trim() || undefined,
          nickname: nickname.trim() || undefined, color })
        toast.success(`${meta.label} conectado.`)
      }
      onDone()
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length) setErrors(err.fieldErrors)
      else toast.error(err instanceof ApiError ? err.message : 'Não foi possível conectar.')
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>{account ? `Trocar o token de ${account.nickname}` : `Conectar ${meta.label}`}</DialogTitle>
        <DialogDescription>{meta.description}</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <section aria-labelledby="connect-steps" className="space-y-2">
          <h3 id="connect-steps" className="text-sm font-medium">Passo a passo</h3>
          <ol className="list-decimal space-y-1.5 pl-5 text-sm text-muted-foreground marker:text-foreground">
            {meta.steps.map((step) => <li key={step}>{step}</li>)}
          </ol>
          {meta.note && <p className="rounded-xl bg-muted px-3 py-2 text-xs text-muted-foreground">{meta.note}</p>}
        </section>

        {provider === 'GOOGLE' ? null : (
          <form id="connect-form" onSubmit={submit} noValidate className="space-y-4">
            {provider === 'GITLAB' && !account && (
              <Field data-invalid={!!errors.baseUrl}>
                <FieldLabel htmlFor="connect-base">Endereço do GitLab (opcional)</FieldLabel>
                <Input id="connect-base" value={baseUrl} inputMode="url" placeholder="https://gitlab.com" className="text-base md:text-sm"
                  aria-invalid={!!errors.baseUrl} onChange={(e) => setBaseUrl(e.target.value)} />
                <FieldDescription>Vazio = gitlab.com. Só https e aberto na internet.</FieldDescription>
                <FieldError>{errors.baseUrl}</FieldError>
              </Field>
            )}
            {provider === 'DUOLINGO' ? (
              <Field data-invalid={!!errors.username}>
                <FieldLabel htmlFor="connect-user">Usuário do Duolingo</FieldLabel>
                <Input id="connect-user" value={username} autoComplete="off" autoCapitalize="none" placeholder="Ex.: maria_duo"
                  className="text-base md:text-sm" aria-invalid={!!errors.username} onChange={(e) => setUsername(e.target.value)} />
                <FieldError>{errors.username}</FieldError>
              </Field>
            ) : (
              <Field data-invalid={!!errors.token}>
                <FieldLabel htmlFor="connect-token">Token</FieldLabel>
                <Input id="connect-token" type="password" value={token} autoComplete="off" spellCheck={false}
                  placeholder={provider === 'GITLAB' ? 'glpat-...' : 'github_pat_...'} className="text-base md:text-sm"
                  aria-invalid={!!errors.token} onChange={(e) => setToken(e.target.value)} />
                <FieldDescription>Guardado cifrado. Nunca aparece de novo, nem para você.</FieldDescription>
                <FieldError>{errors.token}</FieldError>
              </Field>
            )}
            {(provider === 'GITLAB' || provider === 'GITHUB') && !account && (
              <>
                <Field>
                  <FieldLabel htmlFor="connect-nickname">Apelido (opcional)</FieldLabel>
                  <Input id="connect-nickname" value={nickname} maxLength={40} placeholder="Ex.: Trabalho" className="text-base md:text-sm"
                    onChange={(e) => setNickname(e.target.value)} />
                </Field>
                <Field>
                  <FieldLabel>Cor</FieldLabel>
                  <ColorPicker value={color} onChange={setColor} />
                </Field>
              </>
            )}
          </form>
        )}
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onDone}>Cancelar</Button>
        {provider === 'GOOGLE' ? (
          <Button onClick={() => void goToGoogle()} disabled={pending}>
            Continuar no Google <ExternalLinkIcon />
          </Button>
        ) : (
          <Button type="submit" form="connect-form" disabled={pending}>{account ? 'Trocar token' : 'Conectar'}</Button>
        )}
      </DialogFooter>
    </>
  )
}
