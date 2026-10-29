import { CheckIcon, FolderIcon, PencilIcon, PlusIcon, Trash2Icon } from 'lucide-react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { toast } from 'sonner'
import { ColorPicker } from '@/components/ColorPicker'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { ApiError } from '@/lib/api'
import { COLOR_SWATCHES } from '@/lib/colors'
import { skipAutoFocusOnTouch } from '@/lib/utils'
import { useDeleteFolder, useDeleteTag, useNoteLabels, useSaveFolder, useSaveTag } from '../api'

type Props = { open: boolean; onOpenChange: (open: boolean) => void; groupId: string | null }

/** Criar, renomear e excluir pastas e etiquetas (excluir só desliga das notas). */
export function LabelsDialog({ open, onOpenChange, groupId }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg" onOpenAutoFocus={skipAutoFocusOnTouch}>
        <DialogHeader>
          <DialogTitle>Pastas e etiquetas</DialogTitle>
          <DialogDescription>Excluir uma pasta ou etiqueta não apaga as notas, só tira delas.</DialogDescription>
        </DialogHeader>
        <DialogBody className="space-y-6">
          <Labels groupId={groupId} />
        </DialogBody>
      </DialogScrollContent>
    </Dialog>
  )
}

function Labels({ groupId }: { groupId: string | null }) {
  const { data } = useNoteLabels(groupId)
  const saveFolder = useSaveFolder(groupId)
  const deleteFolder = useDeleteFolder(groupId)
  const saveTag = useSaveTag(groupId)
  const deleteTag = useDeleteTag(groupId)
  const [deleting, setDeleting] = useState<{ open: boolean; kind?: 'folder' | 'tag'; id?: string; name?: string }>({ open: false })

  async function run(action: Promise<unknown>, done: string) {
    try {
      await action
      toast.success(done)
      return true
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
      return false
    }
  }

  return (
    <>
      <section className="space-y-2">
        <h3 className="text-sm font-semibold">Pastas</h3>
        <ul className="space-y-1">
          {data?.folders.map((f) => (
            <LabelRow key={f.id} name={f.name} count={f.count} icon={<FolderIcon className="size-4 text-muted-foreground" />}
              onRename={(name) => run(saveFolder.mutateAsync({ id: f.id, name }), 'Pasta renomeada.')}
              onDelete={() => setDeleting({ open: true, kind: 'folder', id: f.id, name: f.name })} />
          ))}
        </ul>
        <NewLabel placeholder="Nova pasta" onCreate={(name) => run(saveFolder.mutateAsync({ name }), 'Pasta criada.')} />
      </section>

      <section className="space-y-2">
        <h3 className="text-sm font-semibold">Etiquetas</h3>
        <ul className="space-y-1">
          {data?.tags.map((t) => (
            <LabelRow key={t.id} name={t.name} count={t.count}
              icon={<TagColor color={t.color} onChange={(color) => void run(saveTag.mutateAsync({ id: t.id, name: t.name, color }), 'Cor trocada.')} />}
              onRename={(name) => run(saveTag.mutateAsync({ id: t.id, name, color: t.color }), 'Etiqueta renomeada.')}
              onDelete={() => setDeleting({ open: true, kind: 'tag', id: t.id, name: t.name })} />
          ))}
        </ul>
        <NewLabel placeholder="Nova etiqueta" withColor
          onCreate={(name, color) => run(saveTag.mutateAsync({ name, color: color ?? COLOR_SWATCHES[0] }), 'Etiqueta criada.')} />
      </section>

      <ConfirmDelete open={deleting.open} onOpenChange={(open) => setDeleting((s) => ({ ...s, open }))}
        title={deleting.kind === 'folder' ? 'Excluir pasta?' : 'Excluir etiqueta?'}
        description={`"${deleting.name ?? ''}" sai das notas, que continuam onde estão.`}
        onConfirm={() => {
          if (!deleting.id) return
          void run(deleting.kind === 'folder' ? deleteFolder.mutateAsync(deleting.id) : deleteTag.mutateAsync(deleting.id),
            deleting.kind === 'folder' ? 'Pasta excluída.' : 'Etiqueta excluída.')
        }} />
    </>
  )
}

function LabelRow({ name, count, icon, onRename, onDelete }: {
  name: string
  count: number
  icon: ReactNode
  onRename: (name: string) => Promise<boolean>
  onDelete: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState(name)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!value.trim() || value.trim() === name) return setEditing(false)
    if (await onRename(value.trim())) setEditing(false)
  }

  return (
    <li className="flex min-h-10 items-center gap-2 rounded-lg px-1">
      <span className="flex size-6 shrink-0 items-center justify-center">{icon}</span>
      {editing ? (
        <form onSubmit={onSubmit} className="flex min-w-0 flex-1 items-center gap-1">
          <Input autoFocus value={value} onChange={(e) => setValue(e.target.value)} maxLength={80}
            aria-label="Novo nome" className="h-8 text-base md:text-sm" />
          <Button type="submit" size="icon-sm" variant="ghost" aria-label="Salvar nome"><CheckIcon /></Button>
        </form>
      ) : (
        <>
          <span className="min-w-0 flex-1 truncate text-sm">{name}</span>
          <span className="shrink-0 text-xs text-muted-foreground tabular-nums">{count}</span>
          <Button size="icon-sm" variant="ghost" aria-label={`Renomear ${name}`} onClick={() => {
            setValue(name)
            setEditing(true)
          }}><PencilIcon /></Button>
          <Button size="icon-sm" variant="ghost" aria-label={`Excluir ${name}`} onClick={onDelete}><Trash2Icon /></Button>
        </>
      )}
    </li>
  )
}

function NewLabel({ placeholder, withColor, onCreate }: {
  placeholder: string
  withColor?: boolean
  onCreate: (name: string, color?: string) => Promise<boolean>
}) {
  const [name, setName] = useState('')
  const [color, setColor] = useState(COLOR_SWATCHES[0])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!name.trim()) return
    if (await onCreate(name.trim(), withColor ? color : undefined)) setName('')
  }

  return (
    <form onSubmit={onSubmit} className="flex items-center gap-2">
      {withColor && <TagColor color={color} onChange={setColor} />}
      <Input value={name} onChange={(e) => setName(e.target.value)} placeholder={placeholder} maxLength={withColor ? 40 : 80}
        aria-label={placeholder} className="h-9 text-base md:text-sm" />
      <Button type="submit" size="sm" variant="outline" disabled={!name.trim()}><PlusIcon /> Criar</Button>
    </form>
  )
}

function TagColor({ color, onChange }: { color: string; onChange: (color: string) => void }) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <button type="button" aria-label="Cor da etiqueta"
          className="relative size-5 shrink-0 rounded-full ring-1 ring-border after:absolute after:-inset-2" style={{ background: color }} />
      </PopoverTrigger>
      <PopoverContent align="start" className="w-64">
        <ColorPicker value={color} onChange={onChange} />
      </PopoverContent>
    </Popover>
  )
}
