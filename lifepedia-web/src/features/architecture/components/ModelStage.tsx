import {
  DoorOpenIcon,
  FootprintsIcon,
  InfoIcon,
  Loader2Icon,
  LocateFixedIcon,
  MaximizeIcon,
  MessageSquarePlusIcon,
  MinimizeIcon,
  PaintbrushIcon,
  Rotate3dIcon,
  RulerIcon,
  Trash2Icon,
} from 'lucide-react'
import { type Ref, useEffect, useImperativeHandle, useMemo, useReducer, useRef, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { ApiError, apiBinary } from '@/lib/api'
import { cn } from '@/lib/utils'
import { meters } from '../labels'
import type { MaterialOverride, MaterialOverrides, Point, RoomInput, SavedView, Scene, Variant } from '../types'
import type { MaterialInfo } from '../viewer/materials'
import type { DetectedRoom, Hit, ModelViewer, NightLight, Pin, Tool } from '../viewer/ModelViewer'
import type { ModelObject } from '../viewer/optimize'
import { DEFAULT_LATITUDE, dayOfYear } from '../viewer/sun'
import { useModelViewer } from '../viewer/use-model-viewer'
import { FinishesPanel } from './stage/FinishesPanel'
import { ObjectCard } from './stage/ObjectCard'
import { type CompareOption, CompareOverlay, TourBar, WalkBar } from './stage/overlays'
import {
  CutPopover,
  PartsPopover,
  PhotoPopover,
  SunPopover,
  type SunState,
  ViewsPopover,
  type VisualMode,
  VisualPopover,
} from './stage/popovers'
import { RoomNameDialog, RoomsPanel } from './stage/RoomsPanel'
import { ToolButton } from './stage/ToolButton'
import { WalkJoystick } from './WalkJoystick'

/** O que quem edita pode gravar na cena da versão. */
export type SceneActions = {
  saveVariant: (input: { id?: string; name: string; materials: MaterialOverrides }) => Promise<Variant>
  deleteVariant: (id: string) => Promise<void>
  addRoom: (room: RoomInput) => Promise<void>
  renameRoom: (id: string, name: string) => Promise<void>
  deleteRoom: (id: string) => Promise<void>
  addLight: (point: Point, color: string, intensity: number) => Promise<void>
  deleteLight: (id: string) => Promise<void>
}

/** Comandos de fora (ex.: o botão Novo comentário do painel). */
export type StageControl = { pickTool: (tool: Tool) => void }

/** Outra versão que pode ir para o lado direito da comparação. */
export type CompareVersion = { id: string; label: string; modelUrl: string }

type Props = {
  /** URL da maquete na API ou GLB em memória (prévia) */
  source: string | Uint8Array
  latitude: number | null
  north: number
  views?: SavedView[]
  onSaveView?: (view: { name: string; position: Point; target: Point }) => Promise<unknown>
  onDeleteView?: (id: string) => void
  pins?: Pin[]
  /** Com ele aparece a ferramenta Comentar */
  onComment?: (point: Point) => void
  onPinClick?: (id: string) => void
  onViewer?: (viewer: ModelViewer | null) => void
  /** Variantes, cômodos e luzes da versão (sem ela, nada disso aparece) */
  scene?: Scene
  /** Só para quem edita */
  actions?: SceneActions
  /** Nome desta versão e as outras, para comparar */
  versionLabel?: string
  compareVersions?: CompareVersion[]
  controlRef?: Ref<StageControl>
  className?: string
}

const EMPTY_SCENE: Scene = { variants: [], rooms: [], lights: [] }
const ROOM_LIGHT = '#ffd9a8'

const HINTS: Partial<Record<Tool, string>> = {
  measure: 'Toque em dois pontos para medir.',
  comment: 'Toque no ponto da maquete que quer comentar.',
  info: 'Toque numa peça para ver nome, medidas e material.',
  paint: 'Toque numa parede, piso ou móvel para trocar o acabamento.',
  room: 'Toque no piso, no meio do cômodo.',
  light: 'Toque no teto (ou numa parede) onde quer a luz.',
}

/** Maquete 3D com as ferramentas: girar, caminhar, medir, detalhes, acabamentos, cômodos, comentar e as de vista. */
export function ModelStage(props: Props) {
  const { source, latitude, north, views = [], onSaveView, onDeleteView, pins, onComment, onPinClick, onViewer, actions,
    versionLabel, compareVersions = [], controlRef, className } = props
  const scene = props.scene ?? EMPTY_SCENE
  const stageRef = useRef<HTMLDivElement>(null)
  const [, refresh] = useReducer((n: number) => n + 1, 0)
  const [tool, setTool] = useState<Tool>('orbit')
  const [panel, setPanel] = useState<'finishes' | 'rooms' | null>(null)
  const [flash, setFlash] = useState<string | null>(null)
  const [info, setInfo] = useState<{ object: ModelObject; materialIndex: number | null } | null>(null)
  const [roomDraft, setRoomDraft] = useState<DetectedRoom | null>(null)
  const pickRef = useRef<(tool: Tool, hit: Hit) => void>(() => {})
  const { containerRef, viewer, progress, error } = useModelViewer(source, {
    onPick: (t, hit) => pickRef.current(t, hit),
    onMiss: (reason) => showFlash(reason === 'loading' ? 'Ainda preparando a maquete para cliques, só alguns segundos.'
      : 'Toque numa superfície da maquete.'),
    onPinClick,
    onChange: refresh,
    onToolChange: setTool,
  })
  const [cut, setCut] = useState<number | null>(null)
  const [level, setLevel] = useState<number | null>(null)
  const [plan, setPlan] = useState(false)
  const [edges, setEdges] = useState(true)
  const [mode, setMode] = useState<VisualMode>('real')
  const [sun, setSun] = useState<SunState>(() => ({ enabled: false, month: new Date().getMonth() + 1, hour: 15, night: false }))
  const [fullscreen, setFullscreen] = useState(false)
  const realFullscreen = useRef(false)
  const [roomLabels, setRoomLabels] = useState(false)
  const [tour, setTour] = useState<{ index: number; playing: boolean } | null>(null)
  const [variantId, setVariantId] = useState<string | null>(null)
  const [draft, setDraft] = useState<MaterialOverrides>({})
  const [selected, setSelected] = useState<number | null>(null)
  const [hover, setHover] = useState<number | null>(null)
  const [compare, setCompare] = useState<{ left: string; right: string; split: number } | null>(null)
  const [other, setOther] = useState<{ id: string; loading: boolean } | null>(null)
  /** Versão baixada para comparar (ligada ao visualizador: abrir outra maquete baixa de novo). */
  const loadingRef = useRef<{ viewer: ModelViewer; id: string } | null>(null)
  const touch = typeof window !== 'undefined' && window.matchMedia('(pointer: coarse)').matches
  const flashTimer = useRef<number | undefined>(undefined)

  const variant = scene.variants.find((v) => v.id === variantId) ?? null
  const dirty = JSON.stringify(draft) !== JSON.stringify(variant?.materials ?? {})
  const materials = useMemo<MaterialInfo[]>(() => (panel === 'finishes' || info ? viewer?.materials?.list() ?? [] : []),
    [viewer, panel, info])

  function showFlash(text: string) {
    setFlash(text)
    window.clearTimeout(flashTimer.current)
    flashTimer.current = window.setTimeout(() => setFlash(null), 2600)
  }

  pickRef.current = (picked, hit) => {
    if (!viewer) return
    if (picked === 'comment') onComment?.(hit.point)
    else if (picked === 'info') {
      setInfo(hit.object ? { object: hit.object, materialIndex: hit.materialIndex } : null)
      viewer.select(hit.object)
    } else if (picked === 'paint') {
      if (hit.materialIndex != null) setSelected(hit.materialIndex)
    } else if (picked === 'room') {
      if (hit.normal[1] < 0.6) {
        showFlash('Toque no piso do cômodo (não na parede).')
        return
      }
      const room = viewer.detectRoom(hit.point)
      if (room) setRoomDraft(room)
      else showFlash('Não achei paredes em volta desse ponto. Toque no piso, dentro do cômodo.')
    } else if (picked === 'light' && actions) {
      const [x, y, z] = hit.point
      const [nx, ny, nz] = hit.normal
      const offset = ny < -0.6 ? 0.25 : 0.2
      void run(() => actions.addLight([x + nx * offset, y + ny * offset, z + nz * offset], ROOM_LIGHT, 1), 'Luz colocada.')
    }
  }

  useImperativeHandle(controlRef, () => ({ pickTool: (t: Tool) => pickTool(t, true) }))

  useEffect(() => {
    onViewer?.(viewer)
  }, [viewer, onViewer])

  useEffect(() => {
    viewer?.setPins(pins ?? [])
  }, [viewer, pins])

  useEffect(() => {
    viewer?.setSun({ enabled: sun.enabled, latitude: latitude ?? DEFAULT_LATITUDE, north, dayOfYear: dayOfYear(sun.month, 21),
      hour: sun.hour })
    viewer?.setNight(sun.night)
  }, [viewer, sun, latitude, north])

  useEffect(() => {
    if (!viewer) return
    const lights: NightLight[] = [
      ...scene.rooms.filter((r) => r.ceiling != null).map((r) => ({
        point: [r.point[0], r.ceiling! - 0.3, r.point[2]] as Point, color: ROOM_LIGHT, intensity: 1,
      })),
      ...scene.lights.map((l) => ({ point: l.point, color: l.color, intensity: l.intensity })),
    ]
    viewer.setNightLights(lights)
    viewer.setRooms(scene.rooms)
  }, [viewer, scene])

  useEffect(() => {
    viewer?.setRoomLabels(roomLabels || panel === 'rooms')
  }, [viewer, roomLabels, panel])

  useEffect(() => {
    if (!compare) viewer?.applyVariant(draft)
  }, [viewer, draft, compare])

  useEffect(() => {
    viewer?.setVisualMode(mode)
  }, [viewer, mode])

  useEffect(() => {
    viewer?.highlightMaterial(panel === 'finishes' ? hover ?? selected : null)
  }, [viewer, panel, hover, selected])

  useEffect(() => {
    const onChange = () => {
      if (!document.fullscreenElement && realFullscreen.current) {
        realFullscreen.current = false
        setFullscreen(false)
      }
    }
    document.addEventListener('fullscreenchange', onChange)
    return () => document.removeEventListener('fullscreenchange', onChange)
  }, [])

  // Tour: voa até a vista e, tocando, espera um pouco e segue para a próxima (em volta).
  useEffect(() => {
    if (!viewer || !tour || views.length === 0) return
    let cancelled = false
    let timer: number | undefined
    const view = views[tour.index % views.length]
    void viewer.flyTo(view.position, view.target, 2400).then(() => {
      if (cancelled || !tour.playing) return
      timer = window.setTimeout(() => setTour((t) => t && { ...t, index: (t.index + 1) % views.length }), 2500)
    })
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [viewer, tour, views])

  // Comparar: monta os dois lados e baixa a outra versão quando ela vai para a direita.
  const sides = useMemo(() => {
    const main: CompareOption[] = [
      { key: 'original', label: `${versionLabel ?? 'Esta versão'} (original)`, versionId: null },
      ...scene.variants.map((v) => ({ key: `v:${v.id}`, label: v.name, versionId: null })),
      ...(dirty ? [{ key: 'draft', label: 'Mudanças não salvas', versionId: null }] : []),
    ]
    const versions = compareVersions.map((v) => ({ key: `version:${v.id}`, label: v.label, versionId: v.id }))
    return { left: main, right: [...main, ...versions] }
  }, [scene.variants, dirty, compareVersions, versionLabel])

  const overridesOf = useMemo(() => (key: string): MaterialOverrides => {
    if (key === 'draft') return draft
    if (key.startsWith('v:')) return scene.variants.find((v) => `v:${v.id}` === key)?.materials ?? {}
    return {}
  }, [draft, scene.variants])

  useEffect(() => {
    if (!viewer || !compare) {
      viewer?.setCompare(null)
      return
    }
    const rightVersion = compare.right.startsWith('version:') ? compare.right.slice(8) : null
    const ready = !rightVersion || (other?.id === rightVersion && !other.loading)
    viewer.setCompare({
      left: { model: 'main', overrides: overridesOf(compare.left) },
      right: { model: rightVersion && ready ? 'other' : 'main', overrides: overridesOf(compare.right) },
      split: compare.split,
    })
  }, [viewer, compare, other, overridesOf])

  useEffect(() => {
    if (!viewer || !compare?.right.startsWith('version:')) return
    const id = compare.right.slice(8)
    const version = compareVersions.find((v) => v.id === id)
    const current = () => loadingRef.current?.viewer === viewer && loadingRef.current.id === id
    if (!version || current()) return
    loadingRef.current = { viewer, id }
    queueMicrotask(() => setOther({ id, loading: true }))
    apiBinary(version.modelUrl)
      .then((buffer) => (current() ? viewer.loadCompare(buffer) : undefined))
      .then(() => current() && setOther({ id, loading: false }))
      .catch((e) => {
        if (!current()) return
        toast.error(e instanceof ApiError ? e.message : 'Não foi possível abrir a outra versão.')
        loadingRef.current = null
        setCompare((c) => c && { ...c, right: 'original' })
        setOther(null)
      })
  }, [viewer, compare?.right, compareVersions])

  function pickTool(next: Tool, force = false) {
    const value = tool === next && !force ? 'orbit' : next
    if (value === 'walk') setPlan(false)
    if (value !== 'info') setInfo(null)
    if (value === 'paint') setPanel('finishes')
    else if (panel === 'finishes' && tool === 'paint') setPanel(null)
    setTour(null)
    viewer?.setTool(value)
    setTool(value)
  }

  function togglePlan(on: boolean) {
    if (!viewer) return
    if (on && cut == null) changeCut(Math.min(1.2, viewer.size.y / 2))
    viewer.setPlan(on)
    setPlan(on)
    if (on) setTool('orbit')
  }

  function changeCut(value: number | null) {
    viewer?.setCut(value)
    setCut(value)
  }

  function pickLevel(index: number | null) {
    if (!viewer) return
    setLevel(index)
    if (index == null) {
      changeCut(null)
      return
    }
    const next = viewer.levels[index + 1]
    changeCut(next ? next.y - 0.3 : viewer.levels[index].y + 2.4)
  }

  function toggleFullscreen() {
    // A página inteira em tela cheia, com a maquete por cima de tudo: menus, diálogos e avisos são desenhados fora da
    // maquete (no body) e sumiriam se só ela ficasse em tela cheia.
    // Sem a tela cheia do navegador (recusada ou sem suporte, como no iPhone), a maquete ocupa a janela mesmo assim.
    if (fullscreen) {
      setFullscreen(false)
      if (document.fullscreenElement) void document.exitFullscreen()
      return
    }
    setFullscreen(true)
    document.documentElement.requestFullscreen?.()
      .then(() => {
        realFullscreen.current = true
      })
      .catch(() => {})
  }

  function pickVariant(id: string | null) {
    setVariantId(id)
    setDraft(structuredClone(scene.variants.find((v) => v.id === id)?.materials ?? {}))
  }

  function changeMaterial(index: number, override: MaterialOverride | null) {
    setMode('real')
    setDraft((d) => {
      const next = { ...d }
      if (override && (override.color || override.texture)) next[String(index)] = override
      else delete next[String(index)]
      return next
    })
  }

  /** Grava, avisa e diz se deu certo (o erro já vira aviso). */
  async function run(action: () => Promise<unknown>, success: string) {
    try {
      await action()
      toast.success(success)
      return true
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Não foi possível salvar.')
      return false
    }
  }

  async function photo(width: number) {
    const blob = await viewer?.photo(width)
    if (!blob) return
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'maquete.png'
    link.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  }

  const hint = flash ?? (!viewer ? null
    : tool !== 'orbit' && !viewer.pickReady ? 'Preparando a maquete para cliques… só alguns segundos.'
      : tool === 'walk' ? (viewer.walking
        ? (touch ? 'Use o círculo para andar e arraste a tela para olhar.'
          : `W A S D ou setas para andar, arraste para olhar, Shift corre${viewer.ghost ? ', E/Q sobe e desce' : ''}, Esc sai.`)
        : 'Toque no chão onde quer começar a andar.')
        : tour || compare ? null : HINTS[tool] ?? null)

  const infoMaterial = info?.materialIndex != null ? materials.find((m) => m.index === info.materialIndex) ?? null : null

  return (
    <div ref={stageRef} className={cn('relative overflow-hidden rounded-2xl border bg-muted/40',
      fullscreen ? 'fixed inset-0 z-40 rounded-none border-0 bg-background' : cn('h-[65vh] min-h-80', className))}>
      <div ref={containerRef} className="absolute inset-0" />

      {!viewer && (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 p-6 text-center text-sm text-muted-foreground">
          {error ? <p className="max-w-sm text-destructive">{error}</p> : (
            <>
              <Loader2Icon className="size-6 animate-spin motion-reduce:animate-none" aria-hidden />
              <p>Abrindo a maquete{progress != null && progress > 0 ? ` (${Math.round(progress * 100)}%)` : '…'}</p>
              {progress != null && (
                <div className="h-1.5 w-48 overflow-hidden rounded-full bg-muted" role="progressbar" aria-valuemin={0}
                  aria-valuemax={100} aria-valuenow={Math.round(progress * 100)} aria-label="Baixando a maquete">
                  <div className="h-full bg-data transition-[width] motion-reduce:transition-none" style={{ width: `${progress * 100}%` }} />
                </div>
              )}
            </>
          )}
        </div>
      )}

      {viewer && (
        <>
          <div className="absolute inset-x-2 top-2 z-10 flex gap-2 overflow-x-auto pb-1">
            <div className="flex shrink-0 gap-1 rounded-full bg-background/90 p-1 shadow-sm backdrop-blur">
              <ToolButton icon={Rotate3dIcon} label="Girar" active={tool === 'orbit'} onClick={() => pickTool('orbit')} />
              <ToolButton icon={FootprintsIcon} label="Caminhar" active={tool === 'walk'} onClick={() => pickTool('walk')} />
              <ToolButton icon={RulerIcon} label="Medir" active={tool === 'measure'} onClick={() => pickTool('measure')} />
              <ToolButton icon={InfoIcon} label="Detalhes da peça" active={tool === 'info'} onClick={() => pickTool('info')} />
              <ToolButton icon={PaintbrushIcon} label="Acabamentos (cores e texturas)" active={tool === 'paint'}
                onClick={() => pickTool('paint')} />
              {props.scene && (
                <ToolButton icon={DoorOpenIcon} label="Cômodos" active={panel === 'rooms'} onClick={() => {
                  setPanel(panel === 'rooms' ? null : 'rooms')
                  if (tool === 'room' || tool === 'paint') pickTool('orbit', true)
                }} />
              )}
              {onComment && (
                <ToolButton icon={MessageSquarePlusIcon} label="Comentar" active={tool === 'comment'} onClick={() => pickTool('comment')} />
              )}
            </div>
            <div className="ml-auto flex shrink-0 gap-1 rounded-full bg-background/90 p-1 shadow-sm backdrop-blur">
              <CutPopover cut={cut} maxHeight={viewer.size.y} plan={plan} onCut={(v) => {
                setLevel(null)
                changeCut(v)
              }} onPlan={togglePlan} />
              <PartsPopover layers={viewer.layers()} levels={viewer.levels} level={level} edges={edges} onLevel={pickLevel}
                onLayer={(layer, visible) => {
                  viewer.setLayerVisible(layer.id, visible)
                  refresh()
                }}
                onEdges={(on) => {
                  viewer.setEdges(on)
                  setEdges(on)
                }} />
              <VisualPopover mode={mode} onMode={setMode} comparing={compare != null}
                onCompare={sides.right.length > 1 ? () => setCompare(compare ? null
                  : { left: 'original', right: sides.right[1].key, split: 0.5 }) : undefined} />
              <SunPopover sun={sun} onSun={setSun} latitude={latitude} north={north} lights={scene.lights}
                roomLights={scene.rooms.filter((r) => r.ceiling != null).length} placing={tool === 'light'}
                onPlace={actions ? () => {
                  setSun((s) => ({ ...s, night: true }))
                  pickTool('light')
                } : undefined}
                onDeleteLight={actions ? (light) => void run(() => actions.deleteLight(light.id), 'Luz apagada.') : undefined} />
              <ViewsPopover views={views} onGo={(v) => {
                setPlan(false)
                setTool('orbit')
                setTour(null)
                void viewer.flyTo(v.position, v.target, 900)
              }} onSave={onSaveView && (async (name) => {
                await onSaveView({ name, ...viewer.view() })
              })} onDelete={onDeleteView} onTour={() => {
                pickTool('orbit', true)
                setTour({ index: 0, playing: true })
              }} />
              <PhotoPopover onPhoto={photo} />
              <ToolButton icon={LocateFixedIcon} label="Enquadrar" onClick={() => {
                if (plan) togglePlan(false)
                viewer.fit()
              }} />
              <ToolButton icon={fullscreen ? MinimizeIcon : MaximizeIcon} label={fullscreen ? 'Sair da tela cheia' : 'Tela cheia'}
                onClick={toggleFullscreen} />
            </div>
          </div>

          {hint && (
            <p role="status" className="pointer-events-none absolute inset-x-0 bottom-3 z-10 mx-auto w-fit max-w-[90%] rounded-full bg-background/90
              px-3 py-1 text-center text-xs shadow-sm">
              {hint}
            </p>
          )}

          {tool === 'measure' && viewer.measures.length > 0 && (
            <div className="absolute right-2 bottom-12 z-10 w-44 space-y-1 rounded-xl bg-background/95 p-2 text-sm shadow">
              <ul className="max-h-40 space-y-0.5 overflow-y-auto">
                {viewer.measures.map((m) => (
                  <li key={m.id} className="flex justify-between tabular-nums"><span className="text-muted-foreground">#{m.id}</span>{meters(m.meters)}</li>
                ))}
              </ul>
              <Button size="xs" variant="ghost" className="w-full" onClick={() => viewer.clearMeasures()}>
                <Trash2Icon /> Limpar medidas
              </Button>
            </div>
          )}

          {tool === 'walk' && viewer.walking && (
            <WalkBar ghost={viewer.ghost} touch={touch} onGhost={(on) => viewer.setGhost(on)} onRise={(v) => viewer.setRise(v)} />
          )}
          {tool === 'walk' && viewer.walking && touch && <WalkJoystick onMove={(x, y) => viewer.setMove(x, y)} />}

          {info && (
            <ObjectCard object={info.object} material={infoMaterial} onClose={() => {
              setInfo(null)
              viewer.select(null)
            }} onFinish={(index) => {
              setSelected(index)
              pickTool('paint')
            }} />
          )}

          {panel === 'finishes' && (
            <FinishesPanel materials={materials} selected={selected} onSelect={setSelected} onHighlight={setHover}
              draft={draft} onChange={changeMaterial} variants={scene.variants} variantId={variantId} onPickVariant={pickVariant}
              dirty={dirty}
              onSave={actions ? (name, asNew) => run(async () => {
                const saved = await actions.saveVariant({ id: asNew ? undefined : variantId ?? undefined, name, materials: draft })
                setVariantId(saved.id)
              }, 'Variante salva.') : undefined}
              onDelete={actions ? (id) => void run(async () => {
                await actions.deleteVariant(id)
                pickVariant(null)
              }, 'Variante excluída.') : undefined}
              onClose={() => {
                setPanel(null)
                setHover(null)
                if (tool === 'paint') pickTool('orbit', true)
              }} />
          )}

          {panel === 'rooms' && (
            <RoomsPanel rooms={scene.rooms} marking={tool === 'room'} labels={roomLabels} onLabels={setRoomLabels}
              onMark={actions ? () => pickTool('room') : undefined}
              onGo={(room) => {
                const cx = room.outline.reduce((s, p) => s + p[0], 0) / Math.max(room.outline.length, 1)
                const cz = room.outline.reduce((s, p) => s + p[1], 0) / Math.max(room.outline.length, 1)
                setTour(null)
                viewer.walkTo(room.point, [cx, room.point[1], cz])
              }}
              onRename={actions ? (room, name) => void run(() => actions.renameRoom(room.id, name), 'Cômodo renomeado.') : undefined}
              onDelete={actions ? (room) => void run(() => actions.deleteRoom(room.id), 'Cômodo excluído.') : undefined}
              onClose={() => {
                setPanel(null)
                if (tool === 'room') pickTool('orbit', true)
              }} />
          )}

          {compare && (
            <CompareOverlay left={compare.left} right={compare.right} leftOptions={sides.left} rightOptions={sides.right}
              split={compare.split} loading={!!other?.loading && compare.right.startsWith('version:')}
              onLeft={(key) => setCompare({ ...compare, left: key })} onRight={(key) => setCompare({ ...compare, right: key })}
              onSplit={(split) => setCompare({ ...compare, split })} onClose={() => setCompare(null)} />
          )}

          {tour && views.length > 0 && (
            <TourBar name={views[tour.index % views.length].name} playing={tour.playing}
              onPlay={() => setTour({ ...tour, playing: !tour.playing })}
              onPrev={() => setTour({ index: (tour.index - 1 + views.length) % views.length, playing: tour.playing })}
              onNext={() => setTour({ index: (tour.index + 1) % views.length, playing: tour.playing })}
              onStop={() => {
                viewer.stopFlight()
                setTour(null)
              }} />
          )}

          {actions && (
            <RoomNameDialog key={roomDraft ? roomDraft.point.join() : 'none'} open={roomDraft != null} title="Novo cômodo" initial=""
              room={roomDraft} onClose={() => setRoomDraft(null)} onSave={async (name) => {
                if (!roomDraft) return
                const ok = await run(() => actions.addRoom({ name, point: roomDraft.point, ceiling: roomDraft.ceiling,
                  area: roomDraft.area, outline: roomDraft.outline }), 'Cômodo marcado.')
                if (ok) setRoomDraft(null)
              }} />
          )}
        </>
      )}
    </div>
  )
}
