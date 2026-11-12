import { useState } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { ApiError } from '@/lib/api'
import { useRepoLabels, useSaveRules } from '../api'
import type { DevRepo } from '../types'

type Props = { repo: DevRepo | null; onOpenChange: (open: boolean) => void }

/** Regras do repositório: issues viram tarefas, concluir fecha a issue e as raias por etiqueta ao abrir e aceitar MR. */
export function RulesDialog({ repo, onOpenChange }: Props) {
  return (
    <Dialog open={repo !== null} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        {repo && <RulesForm key={repo.id} repo={repo} onDone={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

function RulesForm({ repo, onDone }: { repo: DevRepo; onDone: () => void }) {
  const labels = useRepoLabels(repo.id)
  const save = useSaveRules()
  const [importIssues, setImportIssues] = useState(repo.importIssues)
  const [closeIssues, setCloseIssues] = useState(repo.closeIssues)
  const [opened, setOpened] = useState(repo.labelMrOpened ?? '')
  const [merged, setMerged] = useState(repo.labelMrMerged ?? '')
  const [clear, setClear] = useState(repo.labelsClear.join(', '))
  const mr = repo.provider === 'GITHUB' ? 'PR' : 'MR'

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    try {
      await save.mutateAsync({ repoId: repo.id, importIssues, closeIssues, labelMrOpened: opened.trim() || null,
        labelMrMerged: merged.trim() || null, labelsClear: clear.split(',').map((s) => s.trim()).filter(Boolean) })
      toast.success('Regras salvas.')
      onDone()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível salvar.')
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Regras de {repo.fullName}</DialogTitle>
        <DialogDescription>As raias do board são as etiquetas da issue. Escolha qual etiqueta entra em cada momento.</DialogDescription>
      </DialogHeader>
      <DialogBody>
        <form id="rules-form" onSubmit={submit} className="space-y-5">
          {!repo.canWrite && (
            <p className="rounded-xl bg-amber-500/10 px-3 py-2 text-xs text-amber-700 dark:text-amber-300">
              O token desta conta é só de leitura: as raias e o fechamento de issues não vão funcionar. Troque por um token com
              permissão {repo.provider === 'GITHUB' ? 'de escrita em Issues' : 'api'} em Integrações.
            </p>
          )}
          <div className="flex items-start justify-between gap-3">
            <div className="space-y-0.5">
              <Label htmlFor="rules-import">Issues viram tarefas</Label>
              <p className="text-xs text-muted-foreground">Cada issue atribuída a você vira uma tarefa com a etiqueta do projeto.</p>
            </div>
            <Switch id="rules-import" checked={importIssues} onCheckedChange={setImportIssues} />
          </div>
          <div className="flex items-start justify-between gap-3">
            <div className="space-y-0.5">
              <Label htmlFor="rules-close">Concluir a tarefa fecha a issue</Label>
              <p className="text-xs text-muted-foreground">Desligado, a tarefa só conclui sozinha quando a issue fecha lá.</p>
            </div>
            <Switch id="rules-close" checked={closeIssues} onCheckedChange={setCloseIssues} />
          </div>

          <datalist id={`labels-${repo.id}`}>
            {(labels.data ?? []).map((l) => <option key={l} value={l} />)}
          </datalist>
          <Field>
            <FieldLabel htmlFor="rules-opened">Etiqueta quando o {mr} abre</FieldLabel>
            <Input id="rules-opened" list={`labels-${repo.id}`} value={opened} maxLength={100} placeholder="Ex.: Waiting for merge request"
              className="text-base md:text-sm" onChange={(e) => setOpened(e.target.value)} />
            <FieldDescription>Vale para {mr}s fora de rascunho que citam a issue ("Closes #12") ou cujo branch começa com o número dela.</FieldDescription>
          </Field>
          <Field>
            <FieldLabel htmlFor="rules-merged">Etiqueta quando o {mr} é aceito</FieldLabel>
            <Input id="rules-merged" list={`labels-${repo.id}`} value={merged} maxLength={100} placeholder="Ex.: Free for tests"
              className="text-base md:text-sm" onChange={(e) => setMerged(e.target.value)} />
          </Field>
          <Field>
            <FieldLabel htmlFor="rules-clear">Etiquetas que saem nessas horas (opcional)</FieldLabel>
            <Input id="rules-clear" list={`labels-${repo.id}`} value={clear} placeholder="Ex.: Doing, To Do" className="text-base md:text-sm"
              onChange={(e) => setClear(e.target.value)} />
            <FieldDescription>Separe por vírgula. A etiqueta da outra raia sai sozinha.</FieldDescription>
          </Field>
          {labels.data && labels.data.length > 0 && (
            <div className="space-y-1.5">
              <p className="text-xs text-muted-foreground">Etiquetas do repositório (toque para usar no {mr} aceito):</p>
              <div className="flex flex-wrap gap-1">
                {labels.data.slice(0, 30).map((l) => (
                  <button key={l} type="button" onClick={() => setMerged(l)} className="rounded-full focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
                    <Badge variant={l === merged || l === opened ? 'default' : 'outline'} className="font-normal">{l}</Badge>
                  </button>
                ))}
              </div>
            </div>
          )}
          <p className="text-xs text-muted-foreground">Regra nova só vale para {mr}s abertos ou aceitos daqui para frente.</p>
        </form>
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onDone}>Cancelar</Button>
        <Button type="submit" form="rules-form" disabled={save.isPending}>Salvar</Button>
      </DialogFooter>
    </>
  )
}
