import { ArrowDownIcon, ArrowUpIcon, ChevronLeftIcon, ChevronRightIcon, GhostIcon, PauseIcon, PlayIcon, XIcon } from 'lucide-react'
import { useRef } from 'react'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'

/** Opção de um lado da comparação. `other` = outra versão (baixada à parte). */
export type CompareOption = { key: string; label: string; versionId: string | null }

type CompareProps = {
  left: string
  right: string
  leftOptions: CompareOption[]
  rightOptions: CompareOption[]
  split: number
  loading: boolean
  onLeft: (key: string) => void
  onRight: (key: string) => void
  onSplit: (split: number) => void
  onClose: () => void
}

/** Divisória arrastável e a escolha do que aparece de cada lado. */
export function CompareOverlay(props: CompareProps) {
  const { left, right, leftOptions, rightOptions, split, loading, onLeft, onRight, onSplit, onClose } = props
  const areaRef = useRef<HTMLDivElement>(null)

  function drag(e: React.PointerEvent) {
    const rect = areaRef.current!.getBoundingClientRect()
    onSplit(Math.max(0.05, Math.min(0.95, (e.clientX - rect.left) / rect.width)))
  }

  return (
    <div ref={areaRef} className="pointer-events-none absolute inset-0 z-10">
      <div className="absolute top-0 bottom-0 w-0.5 -translate-x-1/2 bg-background shadow-[0_0_0_1px_rgba(0,0,0,0.25)]"
        style={{ left: `${split * 100}%` }}>
        <button type="button" aria-label="Arrastar a divisória" title="Arraste para comparar"
          className="pointer-events-auto absolute top-1/2 left-1/2 flex size-9 -translate-x-1/2 -translate-y-1/2 touch-none cursor-ew-resize
            items-center justify-center rounded-full border bg-background shadow-md"
          onPointerDown={(e) => e.currentTarget.setPointerCapture(e.pointerId)}
          onPointerMove={(e) => e.currentTarget.hasPointerCapture(e.pointerId) && drag(e)}
          onKeyDown={(e) => {
            if (e.key === 'ArrowLeft') onSplit(Math.max(0.05, split - 0.05))
            if (e.key === 'ArrowRight') onSplit(Math.min(0.95, split + 0.05))
          }}>
          <ChevronLeftIcon className="size-3" aria-hidden /><ChevronRightIcon className="size-3" aria-hidden />
        </button>
      </div>
      <div className="pointer-events-auto absolute inset-x-2 bottom-2 flex flex-wrap items-center gap-2 rounded-2xl bg-background/95 p-2
        shadow-md backdrop-blur sm:inset-x-auto sm:left-1/2 sm:w-max sm:-translate-x-1/2">
        <SideSelect label="Esquerda" value={left} options={leftOptions} onChange={onLeft} />
        <SideSelect label="Direita" value={right} options={rightOptions} onChange={onRight} />
        {loading && <span className="text-xs text-muted-foreground">Baixando a outra versão…</span>}
        <Button size="icon-sm" variant="ghost" aria-label="Parar de comparar" onClick={onClose}><XIcon /></Button>
      </div>
    </div>
  )
}

function SideSelect({ label, value, options, onChange }: { label: string; value: string; options: CompareOption[];
  onChange: (key: string) => void }) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger size="sm" className="w-44" aria-label={label}><SelectValue /></SelectTrigger>
      <SelectContent>
        {options.map((o) => <SelectItem key={o.key} value={o.key}>{o.label}</SelectItem>)}
      </SelectContent>
    </Select>
  )
}

type TourProps = { name: string; playing: boolean; onPlay: () => void; onPrev: () => void; onNext: () => void; onStop: () => void }

export function TourBar({ name, playing, onPlay, onPrev, onNext, onStop }: TourProps) {
  return (
    <div className="absolute bottom-3 left-1/2 z-20 flex max-w-[calc(100%-1rem)] -translate-x-1/2 items-center gap-1 rounded-full
      bg-background/95 p-1 shadow-md backdrop-blur" role="group" aria-label="Tour pelas vistas">
      <Button size="icon-sm" variant="ghost" className="rounded-full" aria-label="Vista anterior" onClick={onPrev}><ChevronLeftIcon /></Button>
      <Button size="icon-sm" variant="ghost" className="rounded-full" aria-label={playing ? 'Pausar' : 'Continuar'} onClick={onPlay}>
        {playing ? <PauseIcon /> : <PlayIcon />}
      </Button>
      <span className="min-w-0 truncate px-2 text-sm font-medium" aria-live="polite">{name}</span>
      <Button size="icon-sm" variant="ghost" className="rounded-full" aria-label="Próxima vista" onClick={onNext}><ChevronRightIcon /></Button>
      <Button size="icon-sm" variant="ghost" className="rounded-full" aria-label="Sair do tour" onClick={onStop}><XIcon /></Button>
    </div>
  )
}

type WalkProps = { ghost: boolean; touch: boolean; onGhost: (on: boolean) => void; onRise: (value: number) => void }

/** Barra do caminhar: fantasma (atravessa paredes e portas, sobe e desce). */
export function WalkBar({ ghost, touch, onGhost, onRise }: WalkProps) {
  const hold = (value: number) => ({
    onPointerDown: (e: React.PointerEvent) => {
      e.currentTarget.setPointerCapture(e.pointerId)
      onRise(value)
    },
    onPointerUp: () => onRise(0),
    onPointerCancel: () => onRise(0),
  })
  return (
    <div className="absolute right-2 bottom-12 z-10 flex flex-col items-end gap-1.5">
      {ghost && (
        <div className="flex flex-col gap-1 rounded-full bg-background/90 p-1 shadow-md backdrop-blur">
          <Button size="icon-sm" variant="ghost" className="touch-none rounded-full" aria-label="Subir" title={touch ? 'Subir' : 'Subir (E ou espaço)'}
            {...hold(1)}><ArrowUpIcon /></Button>
          <Button size="icon-sm" variant="ghost" className="touch-none rounded-full" aria-label="Descer" title={touch ? 'Descer' : 'Descer (Q ou C)'}
            {...hold(-1)}><ArrowDownIcon /></Button>
        </div>
      )}
      <Button size="sm" variant={ghost ? 'default' : 'outline'} aria-pressed={ghost} onClick={() => onGhost(!ghost)}
        className={cn('rounded-full shadow-md', !ghost && 'bg-background/90 backdrop-blur')} title={touch ? undefined : 'Atalho: G'}>
        <GhostIcon /> {ghost ? 'Fantasma ligado' : 'Atravessar paredes'}
      </Button>
    </div>
  )
}
