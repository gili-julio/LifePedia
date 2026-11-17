import { CalendarIcon, Loader2Icon, TagIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { MonthField } from '@/components/MonthField'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldLabel } from '@/components/ui/field'
import { ApiError } from '@/lib/api'
import type { YearMonth } from '@/lib/format'
import { useBatchDeleteTransactions, useBatchUpdateTransactions } from '../api'
import type { EntryType } from '../types'
import { CategorySelect } from './CategorySelect'
import { TypeToggle } from './TypeToggle'

type Props = {
  ids: string[]
  /** Tipo dos selecionados, quando todos são iguais (escolhe as categorias oferecidas). */
  type?: EntryType
  onDone: () => void
}

/** Botões da faixa de selecionados: mudar categoria, mudar mês e excluir, tudo de uma vez. */
export function TransactionBatchActions({ ids, type, onDone }: Props) {
  const [editing, setEditing] = useState<'category' | 'month' | null>(null)
  const [deleting, setDeleting] = useState(false)
  const remove = useBatchDeleteTransactions()

  async function handleDelete() {
    try {
      await remove.mutateAsync(ids)
      toast.success(`${ids.length} lançamento(s) excluído(s).`)
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir.')
    }
  }

  return (
    <>
      <Button size="sm" variant="outline" onClick={() => setEditing('category')}><TagIcon /> Categoria</Button>
      <Button size="sm" variant="outline" onClick={() => setEditing('month')}><CalendarIcon /> Mês</Button>
      <Button size="sm" variant="outline" className="text-destructive" onClick={() => setDeleting(true)}><Trash2Icon /> Excluir</Button>
      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogScrollContent className="sm:max-w-md">
          {editing && <EditForm mode={editing} ids={ids} type={type} onDone={onDone} onClose={() => setEditing(null)} />}
        </DialogScrollContent>
      </Dialog>
      <ConfirmDelete
        open={deleting}
        onOpenChange={setDeleting}
        title={`Excluir ${ids.length} lançamento(s)?`}
        description="Os selecionados saem também dos grupos onde foram levados. Essa ação não pode ser desfeita."
        onConfirm={handleDelete}
      />
    </>
  )
}

function EditForm({ mode, ids, type, onDone, onClose }: {
  mode: 'category' | 'month'
  ids: string[]
  type?: EntryType
  onDone: () => void
  onClose: () => void
}) {
  const update = useBatchUpdateTransactions()
  const [entryType, setEntryType] = useState<EntryType>(type ?? 'EXPENSE')
  const [categoryId, setCategoryId] = useState('')
  const [month, setMonth] = useState<YearMonth | ''>('')
  const ready = mode === 'category' ? !!categoryId : !!month

  async function save() {
    try {
      const { count } = await update.mutateAsync(mode === 'category' ? { ids, categoryId } : { ids, month: month as YearMonth })
      toast.success(`${count} lançamento(s) atualizado(s).`)
      onDone()
      onClose()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível atualizar.')
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>{mode === 'category' ? 'Mudar categoria' : 'Mudar mês'}</DialogTitle>
        <DialogDescription>
          {mode === 'category'
            ? `Vale para os ${ids.length} selecionado(s), que precisam ser todos gastos ou todos ganhos.`
            : `Mês em que os ${ids.length} selecionado(s) contam (fatura). Não pode ser antes da data de cada um.`}
        </DialogDescription>
      </DialogHeader>
      <DialogBody>
        {mode === 'category' ? (
          <>
            {!type && <TypeToggle value={entryType} onChange={(t) => { setEntryType(t); setCategoryId('') }} />}
            <Field>
              <FieldLabel htmlFor="batch-category">Categoria</FieldLabel>
              <CategorySelect id="batch-category" type={entryType} value={categoryId} onChange={setCategoryId} />
            </Field>
          </>
        ) : (
          <Field>
            <FieldLabel htmlFor="batch-month">Mês</FieldLabel>
            <MonthField id="batch-month" value={month} onChange={setMonth} />
          </Field>
        )}
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onClose}>Cancelar</Button>
        <Button onClick={save} disabled={!ready || update.isPending}>
          {update.isPending && <Loader2Icon className="animate-spin" />}
          Salvar
        </Button>
      </DialogFooter>
    </>
  )
}
