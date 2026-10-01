import { CheckIcon, Loader2Icon, PencilIcon, PlusIcon, Trash2Icon, XIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ColorPicker } from '@/components/ColorPicker'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ApiError } from '@/lib/api'
import { COLOR_SWATCHES } from '@/lib/colors'
import { useDeleteLabel, useLabels, useSaveLabel } from '../api'
import { useBoard } from '../board'
import type { Label, LabelKind } from '../types'

const TEXT: Record<LabelKind, { one: string; max: number; empty: string }> = {
  lists: { one: 'lista', max: 60, empty: 'Listas agrupam tarefas (ex.: Casa, Trabalho, Faculdade).' },
  tags: { one: 'etiqueta', max: 40, empty: 'Etiquetas marcam tarefas de listas diferentes (ex.: mercado, urgente).' },
}

/** Criar, renomear, trocar a cor e excluir listas e etiquetas. */
export function LabelsDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const { groupId } = useBoard()
  const { data } = useLabels(groupId)
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Listas e etiquetas</DialogTitle>
          <DialogDescription>
            {groupId ? 'Do grupo, separadas das suas. ' : ''}Excluir uma lista ou etiqueta não apaga as tarefas.
          </DialogDescription>
        </DialogHeader>
        <DialogBody>
          <Tabs defaultValue="lists">
            <TabsList className="w-full">
              <TabsTrigger value="lists">Listas</TabsTrigger>
              <TabsTrigger value="tags">Etiquetas</TabsTrigger>
            </TabsList>
            <TabsContent value="lists" className="pt-3"><LabelEditor kind="lists" items={data?.lists ?? []} /></TabsContent>
            <TabsContent value="tags" className="pt-3"><LabelEditor kind="tags" items={data?.tags ?? []} /></TabsContent>
          </Tabs>
        </DialogBody>
      </DialogScrollContent>
    </Dialog>
  )
}

function LabelEditor({ kind, items }: { kind: LabelKind; items: Label[] }) {
  const [editing, setEditing] = useState<string | 'new' | null>(null)
  const [removing, setRemoving] = useState<{ open: boolean; label: Label | null }>({ open: false, label: null })
  const remove = useDeleteLabel()
  const { groupId } = useBoard()
  const text = TEXT[kind]

  async function onDelete(label: Label) {
    try {
      await remove.mutateAsync({ groupId, kind, id: label.id })
      toast.success(`${text.one === 'lista' ? 'Lista excluída' : 'Etiqueta excluída'}.`)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir.')
    }
  }

  return (
    <div className="space-y-2">
      {items.length === 0 && editing !== 'new' && <p className="text-sm text-muted-foreground">{text.empty}</p>}
      <ul className="divide-y">
        {items.map((label) => editing === label.id ? (
          <li key={label.id} className="py-2"><LabelForm kind={kind} label={label} onDone={() => setEditing(null)} /></li>
        ) : (
          <li key={label.id} className="flex items-center gap-2 py-2">
            <span className="size-3 shrink-0 rounded-full" style={{ background: label.color }} aria-hidden />
            <span className="min-w-0 flex-1 truncate">{label.name}</span>
            <span className="text-xs text-muted-foreground tabular-nums">
              {label.openCount} {label.openCount === 1 ? 'aberta' : 'abertas'}
            </span>
            <Button variant="ghost" size="icon-sm" aria-label={`Editar ${label.name}`} onClick={() => setEditing(label.id)}>
              <PencilIcon />
            </Button>
            <Button variant="ghost" size="icon-sm" aria-label={`Excluir ${label.name}`}
              onClick={() => setRemoving({ open: true, label })}>
              <Trash2Icon />
            </Button>
          </li>
        ))}
      </ul>
      {editing === 'new' ? (
        <LabelForm kind={kind} onDone={() => setEditing(null)} />
      ) : (
        <Button variant="outline" size="sm" onClick={() => setEditing('new')}>
          <PlusIcon /> Nova {text.one}
        </Button>
      )}
      <ConfirmDelete open={removing.open} onOpenChange={(open) => setRemoving((r) => ({ ...r, open }))}
        title={`Excluir ${text.one}?`}
        description={removing.label ? `“${removing.label.name}” sai das tarefas; as tarefas continuam.` : ''}
        onConfirm={() => removing.label && void onDelete(removing.label)} />
    </div>
  )
}

function LabelForm({ kind, label, onDone }: { kind: LabelKind; label?: Label; onDone: () => void }) {
  const save = useSaveLabel()
  const { groupId } = useBoard()
  const [name, setName] = useState(label?.name ?? '')
  const [color, setColor] = useState(label?.color ?? COLOR_SWATCHES[0])
  const max = TEXT[kind].max
  const valid = name.trim().length > 0 && name.trim().length <= max

  async function submit() {
    if (!valid) return
    try {
      await save.mutateAsync({ groupId, kind, id: label?.id, name: name.trim(), color })
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  return (
    <div className="space-y-2 rounded-lg border p-2">
      <div className="flex gap-1">
        <Input autoFocus value={name} maxLength={max} aria-label="Nome" placeholder="Nome"
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') void submit()
            if (e.key === 'Escape') {
              e.stopPropagation()
              onDone()
            }
          }} />
        <Button size="icon" aria-label="Salvar" disabled={!valid || save.isPending} onClick={() => void submit()}>
          {save.isPending ? <Loader2Icon className="animate-spin" /> : <CheckIcon />}
        </Button>
        <Button variant="ghost" size="icon" aria-label="Cancelar" onClick={onDone}><XIcon /></Button>
      </div>
      <ColorPicker value={color} onChange={setColor} />
    </div>
  )
}
