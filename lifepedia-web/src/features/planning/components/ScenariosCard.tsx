import { CheckIcon, EyeIcon, Loader2Icon, SaveIcon, Trash2Icon } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { ApiError } from '@/lib/api'
import { formatMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import { useApplyScenario, useDeleteScenario, useSaveScenario, useScenarioComparison, useScenarios } from '../api'
import type { Scenario, ScenarioComparison, WhatIf } from '../types'

type Props = {
  groupId: string
  whatIf: WhatIf
  /** Cenário sendo visto (nulo = itens ativos) */
  viewing: string | null
  onView: (scenarioId: string | null) => void
}

/** Cenários salvos lado a lado: ver na tela, aplicar (vira os ativos) ou excluir. */
export function ScenariosCard({ groupId, whatIf, viewing, onView }: Props) {
  const scenarios = useScenarios(groupId)
  const list = scenarios.data ?? []
  const comparison = useScenarioComparison(groupId, whatIf, list.length > 0)
  const apply = useApplyScenario()
  const remove = useDeleteScenario()
  const [saving, setSaving] = useState(false)
  const [removing, setRemoving] = useState<Scenario | null>(null)
  const byId = new Map(list.map((s) => [s.id, s]))

  async function run(action: () => Promise<unknown>, success: string) {
    try {
      await action()
      toast.success(success)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível concluir.')
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Cenários</CardTitle>
        <CardDescription>Conjuntos de itens para comparar. Aplicar deixa ativos só os itens do cenário.</CardDescription>
        <CardAction>
          <Button variant="outline" size="sm" onClick={() => setSaving(true)}><SaveIcon /> Salvar ativos</Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        {scenarios.isLoading ? (
          <Skeleton className="h-24" />
        ) : !list.length ? (
          <p className="text-sm text-muted-foreground">
            Nenhum cenário ainda. Ligue os itens que quer testar juntos e clique em "Salvar ativos".
          </p>
        ) : !comparison.data ? (
          <Skeleton className="h-24" />
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {comparison.data.map((row) => {
              const scenario = row.scenarioId ? byId.get(row.scenarioId) : undefined
              const isViewing = viewing === row.scenarioId
              return (
                <ScenarioTile key={row.scenarioId ?? 'active'} row={row} viewing={isViewing}
                  onView={() => onView(row.scenarioId)}
                  onApply={scenario ? () => run(() => apply.mutateAsync({ groupId, id: scenario.id }), `Cenário "${scenario.name}" aplicado.`).then(() => onView(null)) : undefined}
                  onRemove={scenario?.canEdit ? () => setRemoving(scenario) : undefined}
                  busy={apply.isPending} />
              )
            })}
          </ul>
        )}
      </CardContent>

      <SaveScenarioDialog open={saving} onOpenChange={setSaving} groupId={groupId} />
      <ConfirmDelete
        open={!!removing}
        onOpenChange={(open) => !open && setRemoving(null)}
        title={`Excluir o cenário "${removing?.name}"?`}
        description="Os itens continuam no planejamento."
        onConfirm={() => {
          const target = removing!
          if (viewing === target.id) onView(null)
          void run(() => remove.mutateAsync({ groupId, id: target.id }), 'Cenário excluído.')
        }}
      />
    </Card>
  )
}

function ScenarioTile({ row, viewing, onView, onApply, onRemove, busy }: {
  row: ScenarioComparison
  viewing: boolean
  onView: () => void
  onApply?: () => void
  onRemove?: () => void
  busy: boolean
}) {
  const { formatCurrency } = useMoney()
  const { outcome } = row
  const fits = outcome.shortfall === 0
  return (
    <li className={cn('flex flex-col gap-3 rounded-lg border p-3', viewing && 'border-primary bg-primary/5')}>
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate font-medium">{row.name}</p>
          <p className="text-xs text-muted-foreground">
            {outcome.count} {outcome.count === 1 ? 'item' : 'itens'} · {formatCurrency(outcome.total)}
          </p>
        </div>
        {!row.scenarioId && <Badge variant="secondary">Atual</Badge>}
      </div>
      <dl className="grid grid-cols-2 gap-2 text-xs">
        <div>
          <dt className="text-muted-foreground">Situação</dt>
          <dd className={cn('font-medium tabular-nums', !fits && 'text-destructive')}>
            {fits ? 'Tudo cabe' : `Faltam ${formatCurrency(outcome.shortfall)}`}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Saldo em {formatMonth(outcome.endMonth, 'short')}</dt>
          <dd className={cn('font-medium tabular-nums', outcome.endBalance < 0 && 'text-destructive')}>
            {formatCurrency(outcome.endBalance)}
          </dd>
        </div>
      </dl>
      <div className="mt-auto flex flex-wrap gap-2">
        <Button variant={viewing ? 'secondary' : 'outline'} size="sm" disabled={viewing} onClick={onView}>
          <EyeIcon /> {viewing ? 'Vendo' : 'Ver'}
        </Button>
        {onApply && (
          <Button variant="outline" size="sm" disabled={busy} onClick={onApply}><CheckIcon /> Aplicar</Button>
        )}
        {onRemove && (
          <Button variant="ghost" size="icon-sm" className="ml-auto" aria-label={`Excluir ${row.name}`} onClick={onRemove}>
            <Trash2Icon />
          </Button>
        )}
      </div>
    </li>
  )
}

function SaveScenarioDialog({ open, onOpenChange, groupId }: { open: boolean; onOpenChange: (open: boolean) => void; groupId: string }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Salvar cenário</DialogTitle>
          <DialogDescription>Guarda quais itens estão ativos agora, para comparar depois.</DialogDescription>
        </DialogHeader>
        {open && <SaveScenarioForm groupId={groupId} onDone={() => onOpenChange(false)} />}
      </DialogContent>
    </Dialog>
  )
}

function SaveScenarioForm({ groupId, onDone }: { groupId: string; onDone: () => void }) {
  const save = useSaveScenario()
  const [name, setName] = useState('')

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!name.trim()) return
    try {
      await save.mutateAsync({ groupId, name: name.trim() })
      toast.success('Cenário salvo.')
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <Field>
        <FieldLabel htmlFor="scenario-name">Nome</FieldLabel>
        <Input id="scenario-name" autoFocus maxLength={60} placeholder="Ex.: Viagem primeiro" value={name}
          onChange={(e) => setName(e.target.value)} />
      </Field>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>Cancelar</Button>
        <Button type="submit" disabled={!name.trim() || save.isPending}>
          {save.isPending && <Loader2Icon className="animate-spin" />} Salvar
        </Button>
      </DialogFooter>
    </form>
  )
}
