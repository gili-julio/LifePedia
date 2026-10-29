import { ColorPicker } from '@/components/ColorPicker'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { COLOR_SWATCHES } from '@/lib/colors'
import { cn } from '@/lib/utils'
import { useLinkOptions, useNoteLabels } from '../api'
import type { NoteRequest } from '../types'

const NONE = '__none'
const ICONS = ['📄', '📝', '📚', '🎓', '💡', '✅', '📌', '🛒', '💼', '🏠', '❤️', '⭐', '🧠', '🔬', '🎯', '📅']

export type NoteDraft = Omit<NoteRequest, 'version' | 'template'>

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  groupId: string | null
  draft: NoteDraft
  template: boolean
  readOnly: boolean
  onChange: (patch: Partial<NoteDraft>) => void
}

/** Pasta, etiquetas, ícone, cor e ligações da nota; cada mudança entra no salvar automático. */
export function NoteDetailsSheet({ open, onOpenChange, groupId, draft, template, readOnly, onChange }: Props) {
  const labels = useNoteLabels(groupId).data
  const options = useLinkOptions(groupId, open && !template).data
  const tasks = options?.tasks ?? []
  const commitments = options?.commitments ?? []

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader>
          <SheetTitle>Detalhes da nota</SheetTitle>
          <SheetDescription>Organize e ligue a nota a uma tarefa{groupId ? '' : ' ou compromisso'}.</SheetDescription>
        </SheetHeader>
        <fieldset disabled={readOnly} className="space-y-5 px-4 pb-6">
          <Field>
            <FieldLabel>Ícone</FieldLabel>
            <div className="flex flex-wrap gap-1">
              {ICONS.map((icon) => (
                <button key={icon} type="button" aria-label={`Ícone ${icon}`} aria-pressed={draft.icon === icon}
                  onClick={() => onChange({ icon: draft.icon === icon ? null : icon })}
                  className={cn('flex size-9 items-center justify-center rounded-lg text-lg hover:bg-accent',
                    draft.icon === icon && 'bg-accent ring-2 ring-ring')}>
                  {icon}
                </button>
              ))}
            </div>
            <Input value={draft.icon ?? ''} maxLength={4} placeholder="Ou digite um emoji" aria-label="Emoji da nota"
              onChange={(e) => onChange({ icon: e.target.value.trim() || null })} className="w-40 text-base md:text-sm" />
          </Field>

          <Field>
            <FieldLabel>Cor</FieldLabel>
            {draft.color ? (
              <>
                <ColorPicker value={draft.color} onChange={(color) => onChange({ color })} />
                <Button type="button" variant="ghost" size="sm" className="self-start" onClick={() => onChange({ color: null })}>
                  Tirar a cor
                </Button>
              </>
            ) : (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                Sem cor
                <Button type="button" variant="outline" size="sm" onClick={() => onChange({ color: COLOR_SWATCHES[0] })}>
                  Escolher cor
                </Button>
              </div>
            )}
          </Field>

          {!template && (
            <Field>
              <FieldLabel htmlFor="note-folder">Pasta</FieldLabel>
              <Select value={draft.folderId ?? NONE} onValueChange={(v) => onChange({ folderId: v === NONE ? null : v })}>
                <SelectTrigger id="note-folder" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>Sem pasta</SelectItem>
                  {labels?.folders.map((f) => <SelectItem key={f.id} value={f.id}>{f.name}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
          )}

          {!template && (
            <Field>
              <FieldLabel>Etiquetas</FieldLabel>
              {labels?.tags.length ? (
                <div className="space-y-2">
                  {labels.tags.map((t) => {
                    const checked = draft.tagIds.includes(t.id)
                    return (
                      <label key={t.id} className="flex items-center gap-2 text-sm">
                        <Checkbox checked={checked} aria-label={`Etiqueta ${t.name}`} onCheckedChange={(value) => onChange({
                          tagIds: value ? [...draft.tagIds, t.id] : draft.tagIds.filter((id) => id !== t.id),
                        })} />
                        <span className="size-2.5 rounded-full" style={{ background: t.color }} aria-hidden />
                        {t.name}
                      </label>
                    )
                  })}
                </div>
              ) : <p className="text-sm text-muted-foreground">Crie etiquetas em "Pastas e etiquetas" na lista de notas.</p>}
            </Field>
          )}

          {!template && (
            <Field>
              <FieldLabel htmlFor="note-task">Tarefa ligada</FieldLabel>
              <Select value={draft.taskId ?? NONE} onValueChange={(v) => onChange({ taskId: v === NONE ? null : v })}>
                <SelectTrigger id="note-task" className="w-full"><SelectValue placeholder="Nenhuma" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>Nenhuma</SelectItem>
                  {draft.taskId && !tasks.some((t) => t.id === draft.taskId) && (
                    <SelectItem value={draft.taskId}>Tarefa atual</SelectItem>
                  )}
                  {tasks.map((t) => <SelectItem key={t.id} value={t.id}>{t.title}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
          )}

          {!template && !groupId && (
            <Field>
              <FieldLabel htmlFor="note-commitment">Compromisso ligado</FieldLabel>
              <Select value={draft.commitmentId ?? NONE} onValueChange={(v) => onChange({ commitmentId: v === NONE ? null : v })}>
                <SelectTrigger id="note-commitment" className="w-full"><SelectValue placeholder="Nenhum" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>Nenhum</SelectItem>
                  {commitments.map((c) => <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
          )}
        </fieldset>
      </SheetContent>
    </Sheet>
  )
}
