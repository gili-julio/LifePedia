import { Loader2Icon, SlidersHorizontalIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ApiError } from '@/lib/api'
import { useAgendaSettings, useSaveAgendaSettings } from '../api'
import type { AgendaSettings } from '../types'

/** Mínimo de horas visíveis (igual à API). */
const MIN_HOURS = 4
const hour = (h: number) => `${String(h).padStart(2, '0')}:00`

/** Horas visíveis na agenda (salvas na conta; valem também nos grupos). */
export function AgendaSettingsButton() {
  const [open, setOpen] = useState(false)
  const { data } = useAgendaSettings()
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="outline" size="icon-sm" aria-label="Horário visível da agenda" disabled={!data}>
          <SlidersHorizontalIcon />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-64">
        {data && <SettingsForm initial={data} onDone={() => setOpen(false)} />}
      </PopoverContent>
    </Popover>
  )
}

function SettingsForm({ initial, onDone }: { initial: AgendaSettings; onDone: () => void }) {
  const save = useSaveAgendaSettings()
  const [value, setValue] = useState(initial)

  async function submit() {
    try {
      await save.mutateAsync(value)
      toast.success('Horário da agenda salvo.')
      onDone()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  return (
    <div className="space-y-3">
      <p className="text-sm font-medium">Horário visível</p>
      <div className="grid grid-cols-2 gap-2">
        <div className="space-y-1">
          <Label htmlFor="agenda-start" className="text-xs">Começa</Label>
          <Select value={String(value.dayStart)}
            onValueChange={(v) => setValue((s) => ({ dayStart: Number(v), dayEnd: Math.max(s.dayEnd, Number(v) + MIN_HOURS) }))}>
            <SelectTrigger id="agenda-start" size="sm" className="w-full"><SelectValue /></SelectTrigger>
            <SelectContent>
              {Array.from({ length: 24 - MIN_HOURS + 1 }, (_, h) => <SelectItem key={h} value={String(h)}>{hour(h)}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="agenda-end" className="text-xs">Termina</Label>
          <Select value={String(value.dayEnd)} onValueChange={(v) => setValue((s) => ({ ...s, dayEnd: Number(v) }))}>
            <SelectTrigger id="agenda-end" size="sm" className="w-full"><SelectValue /></SelectTrigger>
            <SelectContent>
              {Array.from({ length: 24 - value.dayStart - MIN_HOURS + 1 }, (_, i) => value.dayStart + MIN_HOURS + i).map((h) => (
                <SelectItem key={h} value={String(h)}>{h === 24 ? '24:00' : hour(h)}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>
      <p className="text-xs text-muted-foreground">Tarefas fora desse horário aparecem num aviso acima da agenda.</p>
      <Button size="sm" className="w-full" onClick={() => void submit()} disabled={save.isPending}>
        {save.isPending && <Loader2Icon className="animate-spin" />} Salvar
      </Button>
    </div>
  )
}
