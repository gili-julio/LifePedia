import { useState } from 'react'
import { DatePicker } from '@/components/DatePicker'
import { Button } from '@/components/ui/button'
import { Drawer, DrawerClose, DrawerContent, DrawerFooter, DrawerHeader, DrawerTitle } from '@/components/ui/drawer'
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { formatMonth } from '@/lib/format'
import { CategorySelect } from '../components/CategorySelect'
import { TypeToggle } from '../components/TypeToggle'
import type { Category, RecurringEntry } from '../types'
import { FixedOutcomeNote } from './FixedOutcomeNote'
import { FixedSelect } from './FixedSelect'
import { CountsIn } from './ImportRowItem'
import { rowErrors, rowMonth, withSource, type ImportRow } from './parse'
import { SOURCE_LABEL, type ImportSource } from './prompt'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  row: ImportRow
  categoriesById: Map<string, Category>
  fixedEntries: RecurringEntry[]
  fixedById: Map<string, RecurringEntry>
  onSave: (row: ImportRow) => void
}

/** Edição de uma linha da prévia no mobile. Remonte com `key` a cada abertura. */
export function ImportRowSheet({ open, onOpenChange, row, categoriesById, fixedEntries, fixedById, onSave }: Props) {
  const [draft, setDraft] = useState(row)
  const errors = rowErrors(draft, categoriesById, fixedById)
  const fixed = fixedById.get(draft.fixedEntryId)
  const set = (patch: Partial<ImportRow>) => setDraft((d) => ({ ...d, ...patch }))
  const error = (field: string, message: string) => (errors.includes(field) ? [{ message }] : [])

  function save() {
    // Revisado: mantém só o aviso de duplicado.
    onSave({ ...draft, warnings: draft.warnings.filter((w) => /duplicado|importado/i.test(w)) })
    onOpenChange(false)
  }

  return (
    <Drawer open={open} onOpenChange={onOpenChange}>
      <DrawerContent>
        <DrawerHeader className="text-left">
          <DrawerTitle>Editar lançamento</DrawerTitle>
        </DrawerHeader>
        <div className="overflow-y-auto px-4">
          <FieldGroup className="gap-4">
            <TypeToggle
              value={draft.type}
              onChange={(type) =>
                set({
                  type,
                  categoryId: categoriesById.get(draft.categoryId)?.type === type ? draft.categoryId : '',
                  fixedEntryId: fixed?.type === type ? draft.fixedEntryId : '',
                })
              }
            />
            <Field>
              <FieldLabel htmlFor="imp-source">Origem</FieldLabel>
              <Select value={draft.source} onValueChange={(v) => set(withSource(draft, v as ImportSource))}>
                <SelectTrigger id="imp-source" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {(Object.keys(SOURCE_LABEL) as ImportSource[]).map((key) => (
                    <SelectItem key={key} value={key}>{SOURCE_LABEL[key]}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <Field data-invalid={errors.includes('fixo')}>
              <FieldLabel htmlFor="imp-fixed">Lançar como</FieldLabel>
              <FixedSelect id="imp-fixed" type={draft.type} month={rowMonth(draft)} value={draft.fixedEntryId}
                fixedEntries={fixedEntries} invalid={errors.includes('fixo')}
                onChange={(fixedEntryId) => set({ fixedEntryId })} />
              {fixed && <FixedOutcomeNote row={draft} fixed={fixed} />}
              <FieldError errors={error('fixo', 'Fixo não vigente nesse mês.')} />
            </Field>
            {draft.originalDescription && (
              <Field>
                <FieldLabel htmlFor="imp-original">No documento</FieldLabel>
                <Input id="imp-original" value={draft.originalDescription} readOnly className="bg-muted text-muted-foreground" />
              </Field>
            )}
            <Field data-invalid={errors.includes('descrição')}>
              <FieldLabel htmlFor="imp-description">Descrição</FieldLabel>
              <Input id="imp-description" value={draft.description} maxLength={120}
                aria-invalid={errors.includes('descrição')} onChange={(e) => set({ description: e.target.value })} />
              <FieldError errors={error('descrição', 'Informe a descrição.')} />
            </Field>
            {!fixed && (
              <Field data-invalid={errors.includes('categoria')}>
                <FieldLabel htmlFor="imp-category">Categoria</FieldLabel>
                <CategorySelect id="imp-category" type={draft.type} value={draft.categoryId}
                  invalid={errors.includes('categoria')} onChange={(categoryId) => set({ categoryId })} />
                <FieldError errors={error('categoria', 'Escolha uma categoria.')} />
              </Field>
            )}
            <div className="grid grid-cols-2 gap-4">
              <Field data-invalid={errors.includes('valor')}>
                <FieldLabel htmlFor="imp-amount">Valor (R$)</FieldLabel>
                <Input id="imp-amount" inputMode="decimal" value={draft.amount}
                  aria-invalid={errors.includes('valor')} onChange={(e) => set({ amount: e.target.value })} />
                <FieldError errors={error('valor', 'Valor inválido.')} />
              </Field>
              <Field data-invalid={errors.includes('data')}>
                <FieldLabel htmlFor="imp-date">Data</FieldLabel>
                <DatePicker id="imp-date" value={draft.date} invalid={errors.includes('data')}
                  onChange={(date) => set({ date })} />
                <CountsIn row={draft} />
                <FieldError errors={error('data', draft.date && draft.month
                  ? `Depois do mês da fatura (${formatMonth(draft.month, 'short')}).`
                  : 'Informe a data.')} />
              </Field>
            </div>
            <Field>
              <FieldLabel htmlFor="imp-notes">Observações</FieldLabel>
              <Textarea id="imp-notes" rows={2} maxLength={500} value={draft.notes}
                onChange={(e) => set({ notes: e.target.value })} />
            </Field>
          </FieldGroup>
        </div>
        <DrawerFooter className="pb-[max(1rem,env(safe-area-inset-bottom))]">
          <Button onClick={save}>Salvar</Button>
          <DrawerClose asChild>
            <Button variant="outline">Cancelar</Button>
          </DrawerClose>
        </DrawerFooter>
      </DrawerContent>
    </Drawer>
  )
}
