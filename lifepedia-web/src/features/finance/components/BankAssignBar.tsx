import { Loader2Icon, WalletCardsIcon, XIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { BankField } from '@/components/bank/BankSelect'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldLabel } from '@/components/ui/field'
import { ApiError } from '@/lib/api'
import { OTHER_BANK, type BankRef } from '@/lib/banks'
import { useAssignBank } from '../api'

type Props = {
  ids: string[]
  /** Fixos ou lançamentos ocasionais */
  target: 'transactions' | 'recurring'
  onClear: () => void
}

function AssignForm({ ids, target, onClear, onClose }: Props & { onClose: () => void }) {
  const assign = useAssignBank()
  const [bank, setBank] = useState<BankRef | null>(null)
  const missingName = bank?.code === OTHER_BANK && !bank.name?.trim()

  async function save() {
    try {
      await assign.mutateAsync({ ...(target === 'transactions' ? { transactionIds: ids } : { recurringIds: ids }), bank })
      toast.success(bank ? `Banco definido em ${ids.length} item(ns).` : `Banco retirado de ${ids.length} item(ns).`)
      onClear()
      onClose()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível definir o banco.')
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Definir banco</DialogTitle>
        <DialogDescription>Vale para os {ids.length} item(ns) selecionados. Itens levados aos grupos acompanham.</DialogDescription>
      </DialogHeader>
      <DialogBody>
        <Field>
          <FieldLabel htmlFor="assign-bank">Banco</FieldLabel>
          <BankField id="assign-bank" value={bank} onChange={setBank} invalid={missingName} />
        </Field>
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onClose}>Cancelar</Button>
        <Button onClick={save} disabled={missingName || assign.isPending}>
          {assign.isPending && <Loader2Icon className="animate-spin" />}
          Salvar
        </Button>
      </DialogFooter>
    </>
  )
}

/** Faixa que aparece com itens selecionados: define o banco de todos de uma vez. */
export function BankAssignBar({ ids, target, onClear }: Props) {
  const [open, setOpen] = useState(false)
  if (!ids.length) return null
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-muted px-3 py-2 text-sm">
      <span className="font-medium">{ids.length} selecionado(s)</span>
      <div className="flex gap-2">
        <Button size="sm" variant="ghost" onClick={onClear}><XIcon /> Limpar</Button>
        <Button size="sm" onClick={() => setOpen(true)}><WalletCardsIcon /> Definir banco</Button>
      </div>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogScrollContent className="sm:max-w-md">
          <AssignForm ids={ids} target={target} onClear={onClear} onClose={() => setOpen(false)} />
        </DialogScrollContent>
      </Dialog>
    </div>
  )
}
