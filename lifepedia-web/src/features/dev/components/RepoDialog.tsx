import { CheckIcon, SearchIcon } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { useIntegrations } from '@/features/integrations/api'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/utils'
import { useAddRepo, useRepoOptions } from '../api'

type Props = { projectId: string; open: boolean; onOpenChange: (open: boolean) => void }

/** Liga um repositório do GitLab/GitHub ao projeto: escolhe a conta, busca e toca no repositório. */
export function RepoDialog({ projectId, open, onOpenChange }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        {open && <RepoPicker projectId={projectId} onDone={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

function RepoPicker({ projectId, onDone }: { projectId: string; onDone: () => void }) {
  const integrations = useIntegrations()
  const accounts = (integrations.data?.accounts ?? []).filter((a) => a.provider === 'GITLAB' || a.provider === 'GITHUB')
  const [accountId, setAccountId] = useState<string | null>(null)
  const [typed, setTyped] = useState('')
  const [query, setQuery] = useState('')
  const [chosen, setChosen] = useState<string | null>(null)
  const account = accountId ?? accounts[0]?.id ?? null
  const options = useRepoOptions(account, query)
  const add = useAddRepo()

  useEffect(() => {
    const timer = setTimeout(() => setQuery(typed.trim()), 400)
    return () => clearTimeout(timer)
  }, [typed])

  async function submit() {
    if (!account || !chosen) return
    try {
      await add.mutateAsync({ projectId, accountId: account, externalId: chosen })
      toast.success('Repositório ligado. Os MRs e issues aparecem em instantes.')
      onDone()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível ligar o repositório.')
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Ligar repositório</DialogTitle>
        <DialogDescription>MRs, issues atribuídas a você e pipelines dele passam a aparecer no projeto.</DialogDescription>
      </DialogHeader>
      <DialogBody>
        {!integrations.data ? <Skeleton className="h-40" /> : accounts.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            Primeiro ligue uma conta do GitLab ou do GitHub em{' '}
            <Link to="/integracoes" className="font-medium text-foreground underline underline-offset-2">Integrações</Link>.
          </p>
        ) : (
          <div className="space-y-4">
            <Field>
              <FieldLabel htmlFor="repo-account">Conta</FieldLabel>
              <Select value={account ?? undefined} onValueChange={(v) => { setAccountId(v); setChosen(null) }}>
                <SelectTrigger id="repo-account" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {accounts.map((a) => (
                    <SelectItem key={a.id} value={a.id}>{a.nickname} · {a.provider === 'GITLAB' ? 'GitLab' : 'GitHub'} ({a.login})</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel htmlFor="repo-search">Buscar</FieldLabel>
              <div className="relative">
                <SearchIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
                <Input id="repo-search" value={typed} placeholder="Nome do repositório" className="pl-9 text-base md:text-sm"
                  onChange={(e) => setTyped(e.target.value)} />
              </div>
            </Field>
            {options.isLoading ? <Skeleton className="h-32" /> : options.isError ? (
              <p className="text-sm text-destructive">
                {options.error instanceof ApiError ? options.error.message : 'Não deu para buscar os repositórios.'}
              </p>
            ) : (options.data ?? []).length === 0 ? (
              <p className="text-sm text-muted-foreground">Nada encontrado.</p>
            ) : (
              <ul className="max-h-72 divide-y overflow-y-auto rounded-xl border" aria-label="Repositórios">
                {(options.data ?? []).map((repo) => (
                  <li key={repo.externalId}>
                    <button type="button" disabled={repo.linked} aria-pressed={chosen === repo.externalId}
                      onClick={() => setChosen(repo.externalId)}
                      className={cn('flex w-full items-center gap-2 px-3 py-2 text-left text-sm outline-none hover:bg-muted focus-visible:bg-muted',
                        'disabled:cursor-not-allowed disabled:opacity-50', chosen === repo.externalId && 'bg-muted font-medium')}>
                      <span className="min-w-0 flex-1 truncate">{repo.fullName}</span>
                      {repo.linked ? <span className="text-xs text-muted-foreground">Já ligado</span>
                        : chosen === repo.externalId ? <CheckIcon className="size-4 text-primary" aria-hidden /> : null}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onDone}>Cancelar</Button>
        <Button onClick={() => void submit()} disabled={!chosen || add.isPending}>Ligar</Button>
      </DialogFooter>
    </>
  )
}
