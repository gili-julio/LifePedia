import {
  BookmarkIcon,
  BoxIcon,
  CameraIcon,
  EyeIcon,
  Layers3Icon,
  LightbulbIcon,
  PlayIcon,
  ScissorsIcon,
  SunIcon,
  Trash2Icon,
  XIcon,
} from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Slider } from '@/components/ui/slider'
import { Switch } from '@/components/ui/switch'
import { cn } from '@/lib/utils'
import { meters } from '../../labels'
import type { Light, SavedView } from '../../types'
import type { Level } from '../../viewer/levels'
import type { Layer } from '../../viewer/ModelViewer'
import { ToolButton } from './ToolButton'

const MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

type CutProps = {
  cut: number | null
  maxHeight: number
  plan: boolean
  onCut: (value: number | null) => void
  onPlan: (on: boolean) => void
}

export function CutPopover({ cut, maxHeight, plan, onCut, onPlan }: CutProps) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <ToolButton icon={ScissorsIcon} label="Corte e planta" active={cut != null || plan} />
      </PopoverTrigger>
      <PopoverContent align="end" className="w-72 space-y-4">
        <div className="flex items-center justify-between gap-3">
          <Label htmlFor="cut-on">Cortar na altura</Label>
          <Switch id="cut-on" checked={cut != null} onCheckedChange={(on) => onCut(on ? Math.min(1.2, maxHeight / 2) : null)} />
        </div>
        {cut != null && (
          <div className="space-y-2">
            <Slider min={0} max={Math.max(maxHeight, 0.1)} step={0.05} value={[cut]} onValueChange={([v]) => onCut(v)}
              aria-label="Altura do corte" />
            <p className="text-xs text-muted-foreground">{meters(cut)} acima do chão</p>
          </div>
        )}
        <div className="flex items-center justify-between gap-3">
          <div>
            <Label htmlFor="plan-on">Planta (vista de cima)</Label>
            <p className="text-xs text-muted-foreground">Sem perspectiva, cortada na altura escolhida.</p>
          </div>
          <Switch id="plan-on" checked={plan} onCheckedChange={onPlan} />
        </div>
      </PopoverContent>
    </Popover>
  )
}

type PartsProps = {
  layers: Layer[]
  levels: Level[]
  level: number | null
  edges: boolean
  onLayer: (layer: Layer, visible: boolean) => void
  onLevel: (index: number | null) => void
  onEdges: (on: boolean) => void
}

/** Andares (corta logo abaixo do próximo) e partes por tipo de peça, achadas pelo nome dos componentes. */
export function PartsPopover({ layers, levels, level, edges, onLayer, onLevel, onEdges }: PartsProps) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <ToolButton icon={Layers3Icon} label="Andares e partes" active={level != null || layers.some((l) => !l.visible)} />
      </PopoverTrigger>
      <PopoverContent align="end" className="w-76 space-y-4">
        {levels.length > 1 && (
          <div className="space-y-2">
            <Label>Andar</Label>
            <div className="flex flex-wrap gap-1.5">
              <Button size="xs" variant={level == null ? 'default' : 'outline'} onClick={() => onLevel(null)}>Todos</Button>
              {levels.map((l, i) => (
                <Button key={l.name} size="xs" variant={level === i ? 'default' : 'outline'} onClick={() => onLevel(i)}>{l.name}</Button>
              ))}
            </div>
            <p className="text-xs text-muted-foreground">Esconde o que está acima do andar escolhido (como um corte).</p>
          </div>
        )}
        <div className="space-y-1">
          <Label>Partes</Label>
          <ul className="space-y-0.5">
            {layers.map((layer) => (
              <li key={layer.id}>
                <label className="flex items-center gap-2 rounded-md px-1 py-1 text-sm hover:bg-muted">
                  <Checkbox checked={layer.visible} onCheckedChange={(v) => onLayer(layer, v === true)} />
                  <span className="min-w-0 truncate">{layer.name}</span>
                </label>
              </li>
            ))}
          </ul>
          <p className="text-xs text-muted-foreground">
            Separadas pelo nome dos componentes do SketchUp (Porta, Cadeira, Pia...). O que não tem nome conhecido fica em
            paredes, pisos e forros.
          </p>
        </div>
        <div className="flex items-center justify-between gap-3 border-t pt-3">
          <Label htmlFor="edges-on">Mostrar arestas</Label>
          <Switch id="edges-on" checked={edges} onCheckedChange={onEdges} />
        </div>
      </PopoverContent>
    </Popover>
  )
}

export type VisualMode = 'real' | 'white' | 'xray'

const MODES: { id: VisualMode; label: string; hint: string }[] = [
  { id: 'real', label: 'Realista', hint: 'Cores e texturas do projeto.' },
  { id: 'white', label: 'Maquete branca', hint: 'Só as formas, como maquete de papel.' },
  { id: 'xray', label: 'Raio-x', hint: 'Paredes transparentes para ver o interior.' },
]

type VisualProps = {
  mode: VisualMode
  onMode: (mode: VisualMode) => void
  /** Sem ele, não há o que comparar */
  onCompare?: () => void
  comparing: boolean
}

export function VisualPopover({ mode, onMode, onCompare, comparing }: VisualProps) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <ToolButton icon={EyeIcon} label="Visual e comparar" active={mode !== 'real' || comparing} />
      </PopoverTrigger>
      <PopoverContent align="end" className="w-72 space-y-3">
        <Label>Visual</Label>
        <div className="space-y-1">
          {MODES.map((m) => (
            <button key={m.id} type="button" onClick={() => onMode(m.id)}
              className={cn('w-full rounded-lg border px-3 py-2 text-left text-sm hover:bg-muted', mode === m.id && 'border-primary bg-muted')}>
              <span className="font-medium">{m.label}</span>
              <span className="block text-xs text-muted-foreground">{m.hint}</span>
            </button>
          ))}
        </div>
        {onCompare && (
          <Button size="sm" variant={comparing ? 'default' : 'outline'} className="w-full" onClick={onCompare}>
            <BoxIcon /> {comparing ? 'Parar de comparar' : 'Comparar lado a lado'}
          </Button>
        )}
      </PopoverContent>
    </Popover>
  )
}

export type SunState = { enabled: boolean; month: number; hour: number; night: boolean }

type SunProps = {
  sun: SunState
  onSun: (sun: SunState) => void
  latitude: number | null
  north: number
  lights: Light[]
  roomLights: number
  placing: boolean
  /** Quem edita coloca e apaga luzes */
  onPlace?: () => void
  onDeleteLight?: (light: Light) => void
}

export function SunPopover({ sun, onSun, latitude, north, lights, roomLights, placing, onPlace, onDeleteLight }: SunProps) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <ToolButton icon={sun.night ? LightbulbIcon : SunIcon} label="Sol e noite" active={sun.enabled || sun.night} />
      </PopoverTrigger>
      <PopoverContent align="end" className="w-76 space-y-4">
        <div className="flex items-center justify-between gap-3">
          <Label htmlFor="sun-on">Simular o sol</Label>
          <Switch id="sun-on" checked={sun.enabled && !sun.night} onCheckedChange={(on) => onSun({ ...sun, enabled: on, night: false })} />
        </div>
        <div className="space-y-2">
          <div className="flex justify-between text-sm"><span>Mês</span><span className="tabular-nums">{MONTHS[sun.month - 1]}</span></div>
          <Slider min={1} max={12} step={1} value={[sun.month]} disabled={!sun.enabled || sun.night} aria-label="Mês"
            onValueChange={([v]) => onSun({ ...sun, month: v })} />
        </div>
        <div className="space-y-2">
          <div className="flex justify-between text-sm">
            <span>Hora</span>
            <span className="tabular-nums">{`${Math.floor(sun.hour)}h${String(Math.round((sun.hour % 1) * 60)).padStart(2, '0')}`}</span>
          </div>
          <Slider min={5} max={19} step={0.25} value={[sun.hour]} disabled={!sun.enabled || sun.night} aria-label="Hora"
            onValueChange={([v]) => onSun({ ...sun, hour: v })} />
        </div>
        <p className="text-xs text-muted-foreground">
          {latitude == null ? 'Sem latitude no projeto: usando a de Brasília. ' : ''}
          Norte girado {north}°. Ajuste em Editar projeto. Hora solar, aproximada.
        </p>
        <div className="space-y-3 border-t pt-3">
          <div className="flex items-center justify-between gap-3">
            <div>
              <Label htmlFor="night-on">Noite com luzes</Label>
              <p className="text-xs text-muted-foreground">
                {roomLights > 0 ? `Uma luz no teto de cada cômodo (${roomLights})` : 'Marque cômodos para acender o teto deles'}
                {lights.length > 0 ? ` e ${lights.length} colocada${lights.length > 1 ? 's' : ''} à mão.` : '.'}
              </p>
            </div>
            <Switch id="night-on" checked={sun.night} onCheckedChange={(on) => onSun({ ...sun, night: on })} />
          </div>
          {onPlace && (
            <Button size="sm" variant={placing ? 'default' : 'outline'} className="w-full" onClick={onPlace}>
              <LightbulbIcon /> {placing ? 'Toque onde quer a luz…' : 'Colocar luz'}
            </Button>
          )}
          {lights.length > 0 && (
            <ul className="max-h-36 space-y-0.5 overflow-y-auto">
              {lights.map((light, i) => (
                <li key={light.id} className="flex items-center gap-2 text-sm">
                  <span className="size-3 shrink-0 rounded-full border" style={{ backgroundColor: light.color }} />
                  <span className="flex-1">Luz {i + 1}</span>
                  {onDeleteLight && (
                    <Button size="icon-xs" variant="ghost" aria-label={`Apagar a luz ${i + 1}`} onClick={() => onDeleteLight(light)}>
                      <Trash2Icon />
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      </PopoverContent>
    </Popover>
  )
}

type ViewsProps = {
  views: SavedView[]
  onGo: (view: SavedView) => void
  onSave?: (name: string) => Promise<void>
  onDelete?: (id: string) => void
  onTour: () => void
}

export function ViewsPopover({ views, onGo, onSave, onDelete, onTour }: ViewsProps) {
  const [name, setName] = useState('')
  const [saving, setSaving] = useState(false)
  if (views.length === 0 && !onSave) return null

  async function save(e: React.FormEvent) {
    e.preventDefault()
    if (!onSave || !name.trim()) return
    setSaving(true)
    try {
      await onSave(name.trim())
      setName('')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <ToolButton icon={BookmarkIcon} label="Vistas salvas e tour" />
      </PopoverTrigger>
      <PopoverContent align="end" className="w-72 space-y-3">
        {views.length === 0 ? <p className="text-sm text-muted-foreground">Nenhuma vista salva ainda.</p> : (
          <>
            <Button size="sm" className="w-full" disabled={views.length < 2} onClick={onTour}>
              <PlayIcon /> Tour pelas vistas
            </Button>
            {views.length < 2 && <p className="text-xs text-muted-foreground">Salve pelo menos duas vistas para o tour.</p>}
            <ul className="max-h-56 space-y-1 overflow-y-auto">
              {views.map((v) => (
                <li key={v.id} className="flex items-center gap-1">
                  <Button variant="ghost" size="sm" className="min-w-0 flex-1 justify-start" onClick={() => onGo(v)}>
                    <span className="truncate">{v.name}</span>
                  </Button>
                  {onDelete && (
                    <Button variant="ghost" size="icon-xs" aria-label={`Excluir a vista ${v.name}`} onClick={() => onDelete(v.id)}>
                      <XIcon />
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          </>
        )}
        {onSave && (
          <form onSubmit={save} className="flex gap-2 border-t pt-3">
            <Input value={name} maxLength={60} placeholder="Nome da vista atual" className="text-base md:text-sm"
              onChange={(e) => setName(e.target.value)} aria-label="Nome da vista" />
            <Button type="submit" size="sm" disabled={saving || !name.trim()}>Salvar</Button>
          </form>
        )}
      </PopoverContent>
    </Popover>
  )
}

type PhotoProps = { onPhoto: (width: number) => Promise<void> }

export function PhotoPopover({ onPhoto }: PhotoProps) {
  const [busy, setBusy] = useState(false)
  const [width, setWidth] = useState('3840')

  async function take() {
    setBusy(true)
    try {
      await onPhoto(Number(width))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <ToolButton icon={CameraIcon} label="Foto em alta" />
      </PopoverTrigger>
      <PopoverContent align="end" className="w-64 space-y-3">
        <Label htmlFor="photo-size">Foto da vista atual</Label>
        <Select value={width} onValueChange={setWidth}>
          <SelectTrigger id="photo-size" className="w-full"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="1920">Full HD (1920 px)</SelectItem>
            <SelectItem value="3840">4K (3840 px)</SelectItem>
            <SelectItem value="5120">Para imprimir (5120 px)</SelectItem>
          </SelectContent>
        </Select>
        <Button size="sm" className="w-full" disabled={busy} onClick={take}><CameraIcon /> Baixar PNG</Button>
        <p className="text-xs text-muted-foreground">Sai como está na tela: visual, sol ou noite, corte e acabamentos.</p>
      </PopoverContent>
    </Popover>
  )
}
