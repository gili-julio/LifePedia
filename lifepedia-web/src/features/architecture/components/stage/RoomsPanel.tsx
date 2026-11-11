import { FootprintsIcon, PencilIcon, PlusIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { skipAutoFocusOnTouch } from '@/lib/utils'
import type { Room } from '../../types'
import type { DetectedRoom } from '../../viewer/ModelViewer'
import { StagePanel } from './StagePanel'

const SUGGESTIONS = ['Sala', 'Cozinha', 'Quarto', 'Suíte', 'Banheiro', 'Lavabo', 'Varanda', 'Área gourmet', 'Lavanderia',
  'Escritório', 'Garagem', 'Corredor']

type Props = {
  rooms: Room[]
  marking: boolean
  onMark?: () => void
  onGo: (room: Room) => void
  onRename?: (room: Room, name: string) => void
  onDelete?: (room: Room) => void
  labels: boolean
  onLabels: (on: boolean) => void
  onClose: () => void
}

/** Lista dos cômodos: ir até cada um (caminhando), área e, para quem edita, marcar, renomear e excluir. */
export function RoomsPanel({ rooms, marking, onMark, onGo, onRename, onDelete, labels, onLabels, onClose }: Props) {
  const [editing, setEditing] = useState<Room | null>(null)
  const total = rooms.reduce((sum, r) => sum + (r.area ?? 0), 0)

  return (
    <StagePanel title="Cômodos" onClose={onClose}>
      {onMark && (
        <Button size="sm" variant={marking ? 'default' : 'outline'} className="w-full" onClick={onMark}>
          <PlusIcon /> {marking ? 'Toque no piso do cômodo…' : 'Marcar cômodo'}
        </Button>
      )}
      {rooms.length === 0 ? (
        <p className="rounded-xl bg-muted/60 p-3 text-xs text-muted-foreground">
          {onMark ? 'Marque os cômodos tocando no piso de cada um: o app acha as paredes em volta e calcula a área.'
            : 'Nenhum cômodo marcado nesta versão.'}
        </p>
      ) : (
        <>
          <ul className="space-y-1">
            {rooms.map((room) => (
              <li key={room.id} className="flex items-center gap-1 rounded-lg hover:bg-muted">
                <button type="button" onClick={() => onGo(room)} className="flex min-w-0 flex-1 items-center gap-2 px-2 py-1.5 text-left text-sm"
                  title="Ir até o cômodo">
                  <FootprintsIcon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                  <span className="min-w-0 flex-1 truncate">{room.name}</span>
                  {room.area != null && <span className="shrink-0 text-xs text-muted-foreground tabular-nums">{squareMeters(room.area)}</span>}
                </button>
                {onRename && (
                  <Button size="icon-xs" variant="ghost" aria-label={`Renomear ${room.name}`} onClick={() => setEditing(room)}>
                    <PencilIcon />
                  </Button>
                )}
                {onDelete && (
                  <Button size="icon-xs" variant="ghost" aria-label={`Excluir ${room.name}`} onClick={() => onDelete(room)}>
                    <Trash2Icon />
                  </Button>
                )}
              </li>
            ))}
          </ul>
          <p className="text-xs text-muted-foreground">
            Soma: {squareMeters(total)}. Área aproximada pelo contorno das paredes, sem descontar móveis.
          </p>
        </>
      )}
      <div className="flex items-center justify-between gap-3 border-t pt-3">
        <Label htmlFor="room-labels">Nomes na maquete</Label>
        <Switch id="room-labels" checked={labels} onCheckedChange={onLabels} />
      </div>
      {onRename && (
        <RoomNameDialog key={editing?.id ?? 'none'} open={editing != null} title="Renomear cômodo" initial={editing?.name ?? ''}
          onClose={() => setEditing(null)} onSave={async (name) => {
            if (editing) onRename(editing, name)
            setEditing(null)
          }} />
      )}
    </StagePanel>
  )
}

type DialogProps = {
  open: boolean
  title: string
  initial: string
  room?: DetectedRoom | null
  onSave: (name: string) => Promise<void>
  onClose: () => void
}

/** Nome do cômodo (com sugestões) e a área achada, antes de salvar. */
export function RoomNameDialog({ open, title, initial, room, onSave, onClose }: DialogProps) {
  const [name, setName] = useState(initial)
  const [saving, setSaving] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim()) return
    setSaving(true)
    try {
      await onSave(name.trim())
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md" onOpenAutoFocus={skipAutoFocusOnTouch}>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{title}</DialogTitle>
            {room && (
              <DialogDescription>
                Área aproximada: {squareMeters(room.area)}
                {room.ceiling != null ? `, pé-direito de ${(room.ceiling - room.point[1]).toLocaleString('pt-BR', { maximumFractionDigits: 2 })} m` : ''}.
              </DialogDescription>
            )}
          </DialogHeader>
          <div className="space-y-2">
            <Input autoFocus value={name} maxLength={60} placeholder="Nome do cômodo" className="text-base md:text-sm"
              aria-label="Nome do cômodo" onChange={(e) => setName(e.target.value)} />
            <div className="flex flex-wrap gap-1.5">
              {SUGGESTIONS.map((s) => (
                <Button key={s} type="button" size="xs" variant={name === s ? 'default' : 'outline'} onClick={() => setName(s)}>{s}</Button>
              ))}
            </div>
          </div>
          {room && room.area > 150 && (
            <p className="text-xs text-amber-700 dark:text-amber-400">
              A área ficou grande: o contorno pode ter escapado por uma abertura. Tente tocar mais no meio do cômodo.
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" disabled={saving || !name.trim()}>Salvar</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function squareMeters(value: number) {
  return `${value.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} m²`
}
