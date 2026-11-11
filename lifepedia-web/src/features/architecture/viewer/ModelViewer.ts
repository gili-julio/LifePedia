import { MeshBVH } from 'three-mesh-bvh'
import { GenerateMeshBVHWorker } from 'three-mesh-bvh/worker'
import {
  ACESFilmicToneMapping,
  Box3,
  Box3Helper,
  BufferAttribute,
  BufferGeometry,
  Color,
  DirectionalLight,
  DoubleSide,
  Euler,
  FrontSide,
  Group,
  HemisphereLight,
  Line,
  LineBasicMaterial,
  LineSegments,
  type Material,
  Mesh,
  MeshBasicMaterial,
  type Object3D,
  OrthographicCamera,
  PCFSoftShadowMap,
  PerspectiveCamera,
  Plane,
  PMREMGenerator,
  PointLight,
  Raycaster,
  Scene,
  SphereGeometry,
  type Texture,
  Vector2,
  Vector3,
  WebGLRenderer,
} from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { MeshoptDecoder } from 'three/addons/libs/meshopt_decoder.module.js'
import { GLTFLoader, type GLTF } from 'three/addons/loaders/GLTFLoader.js'
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js'
import type { MaterialOverrides, Point } from '../types'
import type { Category } from './categories'
import { findLevels, type Level } from './levels'
import { MaterialTable } from './materials'
import { Minimap, type MinimapRoom } from './minimap'
import { type MergedData, type ModelObject, optimizeModel, savedCamera } from './optimize'
import { sunDirection, sunPosition } from './sun'

export type Tool = 'orbit' | 'walk' | 'measure' | 'comment' | 'info' | 'paint' | 'room' | 'light'

/** Ferramentas em que um toque escolhe um ponto da maquete. */
const PICK_TOOLS: Tool[] = ['measure', 'comment', 'info', 'paint', 'room', 'light']

export type Layer = { id: Category; name: string; visible: boolean }

export type Pin = { id: string; point: Point; label: string; resolved: boolean; active: boolean }

export type Measure = { id: number; meters: number }

export type SunSettings = { enabled: boolean; latitude: number; north: number; dayOfYear: number; hour: number }

/** Ponto tocado: onde, a peça e o material. */
export type Hit = { point: Point; normal: Point; object: ModelObject | null; materialIndex: number | null }

/** Luz acesa à noite (as dos cômodos e as colocadas à mão). */
export type NightLight = { point: Point; color: string; intensity: number }

/** Cômodo achado a partir de um ponto do piso. */
export type DetectedRoom = { point: Point; ceiling: number | null; area: number; outline: [number, number][] }

/** Um lado da comparação: a maquete principal ou a outra versão, com os ajustes de uma variante. */
export type CompareSide = { model: 'main' | 'other'; overrides: MaterialOverrides }

type Overlay = { element: HTMLElement; position: Vector3 }

type Callbacks = {
  /** Ponto tocado numa ferramenta de escolher (comentar, detalhes, acabamento, cômodo, luz) */
  onPick?: (tool: Tool, hit: Hit) => void
  /** Toque que não achou a maquete: ainda preparando ou fora dela */
  onMiss?: (reason: 'loading' | 'empty') => void
  onPinClick?: (id: string) => void
  /** Algo que a tela mostra mudou (medidas, estado do caminhar) */
  onChange?: () => void
  /** A ferramenta mudou por dentro (Esc sai do caminhar) */
  onToolChange?: (tool: Tool) => void
}

type Flight = {
  from: { position: Vector3; target: Vector3 }
  to: { position: Vector3; target: Vector3 }
  start: number
  ms: number
  done: () => void
}

type ExtraModel = { root: Group; materials: MaterialTable }

const EYE = 1.6
const STEP = 0.45
const RADIUS = 0.3
const WALK_SPEED = 1.4
const GHOST_SPEED = 2.2
const MAX_LIGHTS = 16
const ROOM_RAYS = 72
/** Teto mais baixo que isso é móvel (mesa, bancada, prateleira). */
const MIN_CEILING = 2
const NIGHT_SKY = new Color('#0b1220')

/** Visualizador da maquete (three.js puro): órbita, caminhar, corte, planta, partes, medidas, sol, noite e pins. */
export class ModelViewer {
  readonly size = new Vector3()
  tool: Tool = 'orbit'
  /** No modo caminhar, se a pessoa já escolheu onde começar */
  walking = false
  /** Caminhar sem colisão (atravessa paredes e portas, sobe e desce) */
  ghost = false
  measures: Measure[] = []
  objects: ModelObject[] = []
  levels: Level[] = []
  materials: MaterialTable | null = null

  private readonly container: HTMLElement
  private readonly overlay: HTMLDivElement
  private readonly renderer: WebGLRenderer
  private readonly scene = new Scene()
  private readonly camera: PerspectiveCamera
  private readonly ortho = new OrthographicCamera()
  private readonly controls: OrbitControls
  private readonly hemi = new HemisphereLight(0xffffff, 0x8a8a8a, 1.2)
  private readonly sun = new DirectionalLight(0xffffff, 1.5)
  private readonly cutPlane = new Plane(new Vector3(0, -1, 0), 0)
  private readonly box = new Box3()
  private readonly raycaster = new Raycaster()
  private readonly measureGroup = new Group()
  private readonly lightGroup = new Group()
  private readonly callbacks: Callbacks
  private readonly minimap: Minimap
  private model: Group | null = null
  private extra: ExtraModel | null = null
  private layerNodes: Group[] = []
  private collider: MeshBVH | null = null
  private colliderMeshes: Mesh[] = []
  private vertexMesh: Uint16Array = new Uint16Array(0)
  private vertexObject: Uint32Array = new Uint32Array(0)
  private colliderVersion = 0
  private worker: GenerateMeshBVHWorker | null = null
  private cut: number | null = null
  private plan = false
  private night = false
  private sunSettings: SunSettings | null = null
  private compare: { left: CompareSide; right: CompareSide; split: number } | null = null
  private selection: Box3Helper | null = null
  private flight: Flight | null = null
  private rise = 0
  private dirty = true
  private pins: Overlay[] = []
  private measureLabels: Overlay[] = []
  private roomLabels: Overlay[] = []
  private roomLabelsVisible = false
  private pendingMeasure: Vector3 | null = null
  private readonly keys = new Set<string>()
  private readonly move = new Vector2()
  private readonly look = new Euler(0, 0, 0, 'YXZ')
  private drag: { x: number; y: number; moved: boolean; touch: boolean } | null = null
  private last = performance.now()
  private readonly observer: ResizeObserver
  private envTexture: Texture | null = null

  constructor(container: HTMLElement, callbacks: Callbacks = {}) {
    this.container = container
    this.callbacks = callbacks
    this.renderer = new WebGLRenderer({ antialias: true, alpha: true })
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    this.renderer.toneMapping = ACESFilmicToneMapping
    this.renderer.shadowMap.type = PCFSoftShadowMap
    this.renderer.localClippingEnabled = true
    this.renderer.domElement.className = 'block size-full touch-none outline-none'
    container.appendChild(this.renderer.domElement)

    this.overlay = document.createElement('div')
    this.overlay.className = 'pointer-events-none absolute inset-0 overflow-hidden'
    container.appendChild(this.overlay)
    this.minimap = new Minimap(container, (x, z) => this.teleport(x, z))

    this.camera = new PerspectiveCamera(55, 1, 0.05, 5000)
    this.controls = new OrbitControls(this.camera, this.renderer.domElement)
    this.controls.enableDamping = true
    this.controls.addEventListener('change', () => this.invalidate())

    const pmrem = new PMREMGenerator(this.renderer)
    this.envTexture = pmrem.fromScene(new RoomEnvironment(), 0.04).texture
    pmrem.dispose()
    this.scene.environment = this.envTexture
    this.scene.environmentIntensity = 0.6
    this.sun.position.set(30, 50, 20)
    this.scene.add(this.hemi, this.sun, this.sun.target, this.measureGroup, this.lightGroup)

    const canvas = this.renderer.domElement
    canvas.addEventListener('pointerdown', this.onPointerDown)
    canvas.addEventListener('pointermove', this.onPointerMove)
    canvas.addEventListener('pointerup', this.onPointerUp)
    canvas.addEventListener('dblclick', this.onDoubleClick)
    window.addEventListener('keydown', this.onKeyDown)
    window.addEventListener('keyup', this.onKeyUp)
    this.observer = new ResizeObserver(() => this.resize())
    this.observer.observe(container)
    this.resize()
    this.renderer.setAnimationLoop(this.frame)
  }

  /** Abre o GLB (já comprimido), junta as malhas, acha os andares e enquadra a câmera. */
  async load(buffer: ArrayBuffer) {
    const { root, materials, objects } = await parseModel(buffer)
    if (this.model) this.disposeModel()
    this.layerNodes = root.children as Group[]
    this.model = root
    this.objects = objects
    this.materials = materials
    this.scene.add(root)
    root.updateMatrixWorld(true)
    this.box.setFromObject(root)
    this.box.getSize(this.size)
    this.levels = findLevels(this.layerNodes)
    this.buildCollider()
    this.fit()
    if (root.userData.saved) this.startFrom(root.userData.saved.position, root.userData.saved.direction)
    this.applyLighting()
    this.invalidate()
  }

  /** Outra versão para comparar lado a lado (nulo tira). */
  async loadCompare(buffer: ArrayBuffer | null) {
    this.disposeExtra()
    if (buffer) {
      const { root, materials } = await parseModel(buffer)
      root.visible = false
      this.extra = { root, materials }
      this.scene.add(root)
      this.applyLighting()
    }
    this.invalidate()
  }

  dispose() {
    this.renderer.setAnimationLoop(null)
    this.observer.disconnect()
    const canvas = this.renderer.domElement
    canvas.removeEventListener('pointerdown', this.onPointerDown)
    canvas.removeEventListener('pointermove', this.onPointerMove)
    canvas.removeEventListener('pointerup', this.onPointerUp)
    canvas.removeEventListener('dblclick', this.onDoubleClick)
    window.removeEventListener('keydown', this.onKeyDown)
    window.removeEventListener('keyup', this.onKeyUp)
    this.controls.dispose()
    this.colliderVersion++
    this.worker?.dispose()
    this.worker = null
    this.flight?.done()
    this.disposeModel()
    this.disposeExtra()
    this.clearLights()
    this.envTexture?.dispose()
    this.renderer.dispose()
    this.minimap.dispose()
    canvas.remove()
    this.overlay.remove()
  }

  setTool(tool: Tool) {
    if (tool === this.tool) return
    if (this.tool === 'walk') this.leaveWalk()
    this.tool = tool
    this.pendingMeasure = null
    if (tool !== 'info') this.select(null)
    if (tool === 'walk' && this.plan) this.setPlan(false)
    this.controls.enabled = tool !== 'walk'
    this.minimap.setVisible(false)
    this.lightGroup.visible = this.night || tool === 'light'
    this.callbacks.onChange?.()
    this.invalidate()
  }

  /** Joystick da tela (celular): x = lado, y = frente, de -1 a 1. */
  setMove(x: number, y: number) {
    this.move.set(x, y)
  }

  /** Subir (1) ou descer (-1) no modo fantasma, pelos botões da tela. */
  setRise(value: number) {
    this.rise = value
  }

  setGhost(on: boolean) {
    this.ghost = on
    this.callbacks.onChange?.()
  }

  layers(): Layer[] {
    return this.layerNodes.map((node) => ({ id: node.userData.category as Category, name: node.name, visible: node.visible }))
  }

  setLayerVisible(id: Category, visible: boolean) {
    const node = this.layerNodes.find((n) => n.userData.category === id)
    if (!node) return
    node.visible = visible
    if (this.extra) {
      const twin = this.extra.root.children.find((n) => n.userData.category === id)
      if (twin) twin.visible = visible
    }
    this.buildCollider()
    this.minimap.invalidate()
    this.invalidate()
  }

  setEdges(visible: boolean) {
    for (const root of [this.model, this.extra?.root]) {
      root?.traverse((node) => {
        if (node instanceof LineSegments && node.parent !== this.measureGroup) node.visible = visible
      })
    }
    this.invalidate()
  }

  /** Corte horizontal a {@code height} metros do chão da maquete (nulo = sem corte). */
  setCut(height: number | null) {
    this.cut = height
    this.cutPlane.constant = height ?? 0
    this.renderer.clippingPlanes = height == null ? [] : [this.cutPlane]
    this.invalidate()
  }

  /** Vista de cima, sem perspectiva (planta). */
  setPlan(on: boolean) {
    this.plan = on
    if (on) {
      if (this.tool === 'walk') this.setTool('orbit')
      const center = this.box.getCenter(new Vector3())
      const span = Math.max(this.size.x, this.size.z) * 0.6 + 1
      const aspect = this.aspect()
      this.ortho.left = -span * aspect
      this.ortho.right = span * aspect
      this.ortho.top = span
      this.ortho.bottom = -span
      this.ortho.near = 0.1
      this.ortho.far = this.size.y + 200
      this.ortho.zoom = 1
      this.ortho.position.set(center.x, this.box.max.y + 50, center.z)
      this.ortho.up.set(0, 0, -1)
      this.ortho.lookAt(center.x, 0, center.z)
      this.ortho.updateProjectionMatrix()
      this.controls.object = this.ortho
      this.controls.target.set(center.x, 0, center.z)
      this.controls.enableRotate = false
    } else {
      this.controls.object = this.camera
      this.controls.enableRotate = true
      this.fit()
    }
    this.controls.update()
    this.invalidate()
  }

  setSun(settings: SunSettings) {
    this.sunSettings = settings
    this.applyLighting()
  }

  /** Noite: céu escuro e só as luzes dos cômodos e as colocadas à mão. */
  setNight(on: boolean) {
    this.night = on
    this.lightGroup.visible = on || this.tool === 'light'
    this.applyLighting()
  }

  get isNight() {
    return this.night
  }

  setNightLights(lights: NightLight[]) {
    this.clearLights()
    lights.slice(0, MAX_LIGHTS).forEach((light) => {
      const point = new PointLight(light.color, 30 * light.intensity, 10, 2)
      point.position.set(...light.point)
      const bulb = new Mesh(new SphereGeometry(0.06, 12, 8), new MeshBasicMaterial({ color: light.color }))
      bulb.position.copy(point.position)
      this.lightGroup.add(point, bulb)
    })
    this.applyLighting()
  }

  /** Nomes dos cômodos na tela (planta e ferramenta de cômodo) e no minimapa. */
  setRooms(rooms: { name: string; point: Point; outline: [number, number][] }[]) {
    this.roomLabels.forEach((r) => r.element.remove())
    this.roomLabels = rooms.map((room) => {
      const label = document.createElement('span')
      label.className = 'absolute -translate-x-1/2 -translate-y-1/2 rounded-full bg-background/90 px-2 py-0.5 text-xs '
        + 'font-semibold text-foreground shadow'
      label.textContent = room.name
      this.overlay.appendChild(label)
      return { element: label, position: new Vector3(room.point[0], room.point[1] + 1.2, room.point[2]) }
    })
    this.minimap.setRooms(rooms.map((r): MinimapRoom => ({ name: r.name, outline: r.outline })))
    this.invalidate()
  }

  setRoomLabels(visible: boolean) {
    this.roomLabelsVisible = visible
    this.invalidate()
  }

  /** Aplica os ajustes de uma variante (vazio = como no arquivo). */
  applyVariant(overrides: MaterialOverrides) {
    this.materials?.apply(overrides)
    this.invalidate()
  }

  setVisualMode(mode: 'real' | 'white' | 'xray') {
    this.materials?.setMode(mode)
    this.extra?.materials.setMode(mode)
    this.setEdges(true)
    this.invalidate()
  }

  highlightMaterial(index: number | null) {
    this.materials?.highlight(index)
    this.invalidate()
  }

  /** Lado a lado: esquerda e direita com a divisória em {@code split} (0 a 1); nulo desliga. */
  setCompare(compare: { left: CompareSide; right: CompareSide; split: number } | null) {
    this.compare = compare
    if (!compare) {
      if (this.model) this.model.visible = true
      if (this.extra) this.extra.root.visible = false
    }
    this.invalidate()
  }

  /** Câmera atual (posição e alvo), para salvar uma vista. */
  view(): { position: Point; target: Point } {
    const p = this.camera.position
    const t = this.tool === 'walk' ? this.walkTarget() : this.controls.target
    return { position: [round(p.x), round(p.y), round(p.z)], target: [round(t.x), round(t.y), round(t.z)] }
  }

  goTo(position: Point, target: Point) {
    this.leaveForOrbit()
    this.camera.position.set(...position)
    this.controls.target.set(...target)
    this.controls.update()
    this.invalidate()
  }

  /** Voa até a vista em {@code ms} (tour); resolve ao chegar ou se outro voo começar. */
  flyTo(position: Point, target: Point, ms = 2200): Promise<void> {
    this.leaveForOrbit()
    this.flight?.done()
    return new Promise((resolve) => {
      this.flight = {
        from: { position: this.camera.position.clone(), target: this.controls.target.clone() },
        to: { position: new Vector3(...position), target: new Vector3(...target) },
        start: performance.now(),
        ms,
        done: () => {
          this.flight = null
          resolve()
        },
      }
    })
  }

  stopFlight() {
    this.flight?.done()
  }

  /** Começa a caminhar num ponto do piso, olhando para {@code lookAt}. */
  walkTo(point: Point, lookAt?: Point) {
    if (this.plan) this.setPlan(false)
    if (this.tool !== 'walk') {
      this.setTool('walk')
      this.callbacks.onToolChange?.('walk')
    }
    this.startWalk(new Vector3(...point), lookAt ? new Vector3(...lookAt) : undefined)
  }

  fit() {
    const center = this.box.getCenter(new Vector3())
    const radius = Math.max(this.size.length() / 2, 1)
    this.camera.near = Math.max(radius / 1000, 0.02)
    this.camera.far = radius * 50
    this.camera.position.copy(center).add(new Vector3(radius * 1.2, radius * 0.9, radius * 1.4))
    this.camera.updateProjectionMatrix()
    this.controls.target.copy(center)
    this.controls.maxDistance = radius * 10
    this.controls.update()
    this.invalidate()
  }

  setPins(pins: Pin[]) {
    this.pins.forEach((p) => p.element.remove())
    this.pins = pins.map((pin) => {
      const button = document.createElement('button')
      button.type = 'button'
      button.textContent = pin.label
      button.title = 'Ver comentário'
      button.className = 'pointer-events-auto absolute flex size-7 -translate-x-1/2 -translate-y-full items-center justify-center '
        + 'rounded-full rounded-bl-none border-2 border-background text-xs font-semibold shadow-md transition-transform '
        + 'motion-reduce:transition-none '
        + (pin.active ? 'scale-125 bg-primary text-primary-foreground ' : pin.resolved ? 'bg-muted text-muted-foreground '
          : 'bg-data text-white ')
      button.addEventListener('click', () => this.callbacks.onPinClick?.(pin.id))
      this.overlay.appendChild(button)
      return { element: button, position: new Vector3(...pin.point) }
    })
    this.invalidate()
  }

  /** Contorna a peça escolhida na ferramenta Detalhes (nulo tira). */
  select(object: ModelObject | null) {
    if (this.selection) {
      this.scene.remove(this.selection)
      this.selection.dispose()
      this.selection = null
    }
    if (object) {
      this.selection = new Box3Helper(object.box.clone().expandByScalar(0.02), 0xff8a1f)
      const material = this.selection.material as LineBasicMaterial
      material.depthTest = false
      this.selection.renderOrder = 20
      this.scene.add(this.selection)
    }
    this.invalidate()
  }

  clearMeasures() {
    this.measureGroup.clear()
    this.measureLabels.forEach((m) => m.element.remove())
    this.measureLabels = []
    this.measures = []
    this.pendingMeasure = null
    this.callbacks.onChange?.()
    this.invalidate()
  }

  /**
   * Acha o cômodo em volta de um ponto do piso: raios para todos os lados logo abaixo do teto (acima das portas, então
   * o contorno não vaza para o cômodo ao lado) e área pelo contorno.
   */
  detectRoom(point: Point): DetectedRoom | null {
    if (!this.collider) return null
    const down = new Vector3(0, -1, 0)
    const floor = new Vector3(...point)
    let ceiling: number | null = null
    // Tocou num móvel (teto a menos de 2 m, ou vão livre embaixo)? Desce até o piso de verdade.
    for (let i = 0; i < 6; i++) {
      ceiling = this.ceilingAbove(floor)
      const below = this.firstHit(floor.clone().setY(floor.y - 0.02), down, 3, true)
      const gap = below ? floor.y - below.point.y : 0
      const furniture = (ceiling != null && ceiling - floor.y < MIN_CEILING) || (gap > 0.3 && gap < 2.4)
      if (!furniture || !below) break
      floor.copy(below.point)
    }
    // Logo acima das portas (2,10 m): o raio bate na verga e não vaza para o cômodo ao lado.
    const top = ceiling != null ? Math.min(ceiling - 0.15, floor.y + 2.25) : floor.y + 2.25
    const outline: [number, number][] = []
    let misses = 0
    for (let i = 0; i < ROOM_RAYS; i++) {
      const a = (i / ROOM_RAYS) * Math.PI * 2
      const dir = new Vector3(Math.cos(a), 0, Math.sin(a))
      const hit = this.firstHit(new Vector3(floor.x, top, floor.z), dir, 30, true)
        ?? this.firstHit(new Vector3(floor.x, top - 0.4, floor.z), dir, 30, true)
      if (!hit) misses++
      const distance = hit ? hit.distance : 30
      outline.push([round(floor.x + dir.x * distance), round(floor.z + dir.z * distance)])
    }
    // Sem parede em volta (área aberta ou em cima de algo): não é um cômodo.
    if (misses > ROOM_RAYS / 3) return null
    let area = 0
    for (let i = 0; i < outline.length; i++) {
      const [x1, z1] = outline[i]
      const [x2, z2] = outline[(i + 1) % outline.length]
      area += x1 * z2 - x2 * z1
    }
    return { point: [round(floor.x), round(floor.y), round(floor.z)], ceiling: ceiling == null ? null : round(ceiling),
      area: Math.round(Math.abs(area) / 2 * 100) / 100, outline }
  }

  /** Altura do teto acima do ponto: pula o que estiver a menos de {@code MIN_CEILING} (luminária, prateleira). */
  private ceilingAbove(floor: Vector3) {
    const up = new Vector3(0, 1, 0)
    let from = floor.y + 0.05
    for (let i = 0; i < 6; i++) {
      const hit = this.firstHit(new Vector3(floor.x, from, floor.z), up, 20, true)
      if (!hit) return null
      if (hit.point.y - floor.y >= MIN_CEILING + 0.2) return hit.point.y
      from = hit.point.y + 0.02
      if (i === 0 && hit.point.y - floor.y < MIN_CEILING) return hit.point.y
    }
    return null
  }

  /** Foto da maquete (capa do projeto): JPEG com até {@code width} px de largura. */
  async snapshot(width = 960): Promise<Blob | null> {
    this.renderFrame()
    return toImage(this.renderer.domElement, width, '#f4f4f5', 'image/jpeg')
  }

  /** Foto em alta da vista atual (PNG), desenhada de novo no tamanho pedido. */
  async photo(width = 3840): Promise<Blob | null> {
    const w = Math.max(this.container.clientWidth, 1)
    const h = Math.max(this.container.clientHeight, 1)
    const ratio = this.renderer.getPixelRatio()
    const height = Math.round((width * h) / w)
    this.renderer.setPixelRatio(1)
    this.renderer.setSize(width, height, false)
    this.renderFrame()
    const blob = await toImage(this.renderer.domElement, width, this.night ? `#${NIGHT_SKY.getHexString()}` : '#f4f4f5',
      'image/png')
    this.renderer.setPixelRatio(ratio)
    this.resize()
    return blob
  }

  private frame = () => {
    const now = performance.now()
    const dt = Math.min((now - this.last) / 1000, 0.1)
    this.last = now
    if (this.flight) this.stepFlight(now)
    else if (this.tool === 'walk' && this.walking) this.stepWalk(dt)
    else if (this.controls.enabled) this.controls.update()
    if (!this.dirty) return
    this.dirty = false
    this.renderFrame()
    if (this.tool === 'walk' && this.walking) {
      this.minimap.update(this.model, this.box, this.camera.position, this.camera.position.y - EYE, this.look.y)
    }
  }

  private renderFrame() {
    const camera = this.activeCamera()
    if (this.compare && this.model) {
      const { x: width, y: height } = this.renderer.getSize(new Vector2())
      const split = Math.round(width * this.compare.split)
      const saved = this.materials?.current ?? {}
      this.renderer.setScissorTest(true)
      for (const [side, x, sw] of [[this.compare.left, 0, split], [this.compare.right, split, width - split]] as const) {
        this.showSide(side)
        this.renderer.setScissor(x, 0, sw, height)
        this.renderer.render(this.scene, camera)
      }
      this.renderer.setScissorTest(false)
      this.materials?.apply(saved)
      if (this.model) this.model.visible = true
      if (this.extra) this.extra.root.visible = false
    } else {
      this.renderer.render(this.scene, camera)
    }
    this.placeOverlays(camera)
  }

  private showSide(side: CompareSide) {
    const other = side.model === 'other' && this.extra
    if (this.model) this.model.visible = !other
    if (this.extra) this.extra.root.visible = !!other
    if (other) this.extra!.materials.apply(side.overrides)
    else this.materials?.apply(side.overrides)
  }

  private invalidate() {
    this.dirty = true
  }

  private activeCamera() {
    return this.plan ? this.ortho : this.camera
  }

  private aspect() {
    return Math.max(this.container.clientWidth, 1) / Math.max(this.container.clientHeight, 1)
  }

  private resize() {
    const w = Math.max(this.container.clientWidth, 1)
    const h = Math.max(this.container.clientHeight, 1)
    this.renderer.setSize(w, h, false)
    this.camera.aspect = w / h
    this.camera.updateProjectionMatrix()
    if (this.plan) {
      const span = this.ortho.top
      this.ortho.left = -span * (w / h)
      this.ortho.right = span * (w / h)
      this.ortho.updateProjectionMatrix()
    }
    this.invalidate()
  }

  private applyLighting() {
    const all = [...(this.materials?.all() ?? []), ...(this.extra?.materials.all() ?? [])]
    const settings = this.sunSettings
    const sunOn = !this.night && !!settings?.enabled
    this.renderer.shadowMap.enabled = sunOn
    for (const root of [this.model, this.extra?.root]) {
      root?.traverse((node) => {
        if (node instanceof Mesh) {
          node.castShadow = sunOn
          node.receiveShadow = sunOn
        }
      })
    }
    // Material dos dois lados projeta sombra em si mesmo (a superfície inteira escurece): só a frente projeta.
    all.forEach((m) => {
      m.shadowSide = FrontSide
      m.needsUpdate = true
    })
    const center = this.box.getCenter(new Vector3())
    this.renderer.setClearColor(this.night ? NIGHT_SKY : 0x000000, this.night ? 1 : 0)
    this.renderer.toneMappingExposure = this.night ? 1.15 : 1
    this.lightGroup.children.forEach((child) => {
      if (child instanceof PointLight) child.visible = this.night
    })
    if (this.night) {
      this.sun.castShadow = false
      this.sun.intensity = 0.12
      this.sun.color.set(0x9db4ff)
      this.sun.position.set(-30, 50, -20).add(center)
      this.sun.target.position.copy(center)
      this.hemi.intensity = 0.06
      this.scene.environmentIntensity = 0.02
    } else if (!sunOn || !settings) {
      this.sun.castShadow = false
      this.sun.intensity = 1.5
      this.sun.color.set(0xffffff)
      this.hemi.intensity = 1.2
      this.scene.environmentIntensity = 0.6
      this.sun.position.set(30, 50, 20).add(center)
      this.sun.target.position.copy(center)
    } else {
      const { elevation, azimuth } = sunPosition(settings.latitude, settings.dayOfYear, settings.hour)
      const dir = new Vector3(...sunDirection(elevation, azimuth, settings.north))
      const radius = Math.max(this.size.length() / 2, 5)
      const day = Math.max(0, Math.sin((elevation * Math.PI) / 180))
      this.sun.position.copy(center).addScaledVector(dir, radius * 3)
      this.sun.target.position.copy(center)
      this.sun.intensity = elevation > 0 ? 0.8 + 3.2 * day : 0
      this.sun.color.set(new Color().setHSL(0.1, 0.7 * (1 - day), 0.5 + 0.45 * day))
      this.hemi.intensity = 0.3 + 0.35 * day
      this.scene.environmentIntensity = 0.08 + 0.2 * day
      this.sun.castShadow = elevation > 0
      const shadow = this.sun.shadow
      shadow.mapSize.set(2048, 2048)
      shadow.bias = -0.0005
      shadow.normalBias = 0.02
      const cam = shadow.camera
      cam.left = -radius
      cam.right = radius
      cam.top = radius
      cam.bottom = -radius
      cam.near = 0.5
      cam.far = radius * 6
      cam.updateProjectionMatrix()
    }
    this.minimap.invalidate()
    this.invalidate()
  }

  private placeOverlays(camera: PerspectiveCamera | OrthographicCamera) {
    const w = this.container.clientWidth
    const h = this.container.clientHeight
    const v = new Vector3()
    const showRooms = this.roomLabelsVisible || this.plan || this.tool === 'room'
    for (const item of [...this.pins, ...this.measureLabels, ...this.roomLabels]) {
      v.copy(item.position).project(camera)
      const isRoom = this.roomLabels.includes(item)
      const hidden = v.z > 1 || v.z < -1 || (this.cut != null && !isRoom && item.position.y > this.cut + 0.01)
        || (isRoom && !showRooms)
      item.element.style.display = hidden ? 'none' : ''
      item.element.style.left = `${((v.x + 1) / 2) * w}px`
      item.element.style.top = `${((1 - v.y) / 2) * h}px`
    }
  }

  /** Ponto da maquete sob o cursor (ignora o que está acima do corte e partes escondidas). */
  private pick(clientX: number, clientY: number) {
    if (!this.collider) return null
    const rect = this.renderer.domElement.getBoundingClientRect()
    const ndc = new Vector2(((clientX - rect.left) / rect.width) * 2 - 1, -((clientY - rect.top) / rect.height) * 2 + 1)
    this.raycaster.setFromCamera(ndc, this.activeCamera())
    return this.firstHit(this.raycaster.ray.origin, this.raycaster.ray.direction, Infinity)
  }

  private firstHit(origin: Vector3, direction: Vector3, far: number, ignoreCut = false) {
    if (!this.collider) return null
    this.raycaster.set(origin, direction)
    // Sem corte, só o primeiro triângulo importa (bem mais rápido; caminhar faz vários raios por quadro).
    if (this.cut == null || ignoreCut) return this.collider.raycastFirst(this.raycaster.ray, DoubleSide, 0, far)
    const hits = this.collider.raycast(this.raycaster.ray, DoubleSide, 0, far)
      .filter((hit) => hit.point.y <= this.cut! + 0.001)
      .sort((a, b) => a.distance - b.distance)
    return hits[0] ?? null
  }

  private hitInfo(hit: NonNullable<ReturnType<ModelViewer['firstHit']>>): Hit {
    const vertex = hit.face?.a ?? -1
    const mesh = vertex >= 0 ? this.colliderMeshes[this.vertexMesh[vertex]] : undefined
    const object = vertex >= 0 ? this.objects[this.vertexObject[vertex]] ?? null : null
    const n = hit.face?.normal ?? new Vector3(0, 1, 0)
    return {
      point: [round(hit.point.x), round(hit.point.y), round(hit.point.z)],
      normal: [round(n.x), round(n.y), round(n.z)],
      object,
      materialIndex: mesh ? (mesh.userData as MergedData).materialIndex : null,
    }
  }

  private onPointerDown = (e: PointerEvent) => {
    this.drag = { x: e.clientX, y: e.clientY, moved: false, touch: e.pointerType === 'touch' }
    if (this.flight) this.flight.done()
    if (this.tool === 'walk' && this.walking) this.renderer.domElement.setPointerCapture(e.pointerId)
  }

  private onPointerMove = (e: PointerEvent) => {
    if (!this.drag) return
    const dx = e.clientX - this.drag.x
    const dy = e.clientY - this.drag.y
    if (Math.abs(dx) + Math.abs(dy) > (this.drag.touch ? 10 : 4)) this.drag.moved = true
    if (this.tool === 'walk' && this.walking && this.drag.moved) {
      this.look.y -= dx * 0.004
      this.look.x = Math.max(-1.4, Math.min(1.4, this.look.x - dy * 0.004))
      this.camera.quaternion.setFromEuler(this.look)
      this.drag.x = e.clientX
      this.drag.y = e.clientY
      this.invalidate()
    }
  }

  private onPointerUp = (e: PointerEvent) => {
    const drag = this.drag
    this.drag = null
    if (!drag || drag.moved) return
    const picking = PICK_TOOLS.includes(this.tool) || (this.tool === 'walk' && !this.walking)
    if (!picking) return
    if (!this.collider) {
      this.callbacks.onMiss?.('loading')
      return
    }
    const hit = this.pick(e.clientX, e.clientY)
    if (!hit) {
      this.callbacks.onMiss?.('empty')
      return
    }
    if (this.tool === 'walk') this.startWalk(hit.point)
    else if (this.tool === 'measure') this.addMeasurePoint(hit.point.clone())
    else this.callbacks.onPick?.(this.tool, this.hitInfo(hit))
  }

  private onDoubleClick = (e: MouseEvent) => {
    if (this.tool !== 'orbit') return
    const hit = this.pick(e.clientX, e.clientY)
    if (!hit) return
    this.controls.target.copy(hit.point)
    this.controls.update()
    this.invalidate()
  }

  private onKeyDown = (e: KeyboardEvent) => {
    if (this.tool !== 'walk' || isTyping(e.target)) return
    const key = e.key.toLowerCase()
    if (['w', 'a', 's', 'd', 'q', 'e', ' ', 'c', 'arrowup', 'arrowdown', 'arrowleft', 'arrowright', 'shift'].includes(key)) {
      this.keys.add(key)
      if (key.startsWith('arrow') || key === ' ') e.preventDefault()
    }
    if (key === 'g' && this.walking) this.setGhost(!this.ghost)
    if (key === 'escape') {
      this.setTool('orbit')
      this.callbacks.onToolChange?.('orbit')
    }
  }

  private onKeyUp = (e: KeyboardEvent) => {
    this.keys.delete(e.key.toLowerCase())
  }

  private startWalk(point: Vector3, lookAt?: Vector3) {
    this.walking = true
    const t = lookAt ?? this.controls.target
    this.camera.position.set(point.x, point.y + EYE, point.z)
    this.look.set(0, Math.atan2(this.camera.position.x - t.x, this.camera.position.z - t.z), 0)
    this.camera.quaternion.setFromEuler(this.look)
    this.camera.near = 0.05
    this.camera.updateProjectionMatrix()
    this.minimap.setVisible(true)
    this.callbacks.onChange?.()
    this.invalidate()
  }

  private leaveWalk() {
    if (this.walking) {
      this.controls.target.copy(this.walkTarget())
      this.camera.up.set(0, 1, 0)
    }
    this.walking = false
    this.ghost = false
    this.keys.clear()
    this.move.set(0, 0)
    this.rise = 0
    this.minimap.setVisible(false)
  }

  private leaveForOrbit() {
    if (this.plan) this.setPlan(false)
    if (this.tool === 'walk') {
      this.setTool('orbit')
      this.callbacks.onToolChange?.('orbit')
    }
  }

  private walkTarget() {
    return new Vector3(0, 0, -2).applyQuaternion(this.camera.quaternion).add(this.camera.position)
  }

  /** Teleporte pelo minimapa: acha o piso no ponto, perto do andar de quem anda. */
  private teleport(x: number, z: number) {
    if (!this.walking) return
    const current = this.camera.position.y - EYE
    const below = this.firstHit(new Vector3(x, current + 1.2, z), new Vector3(0, -1, 0), 4, true)
    const floor = below ?? this.firstHit(new Vector3(x, this.box.max.y + 1, z), new Vector3(0, -1, 0), Infinity, true)
    if (!floor) return
    this.camera.position.set(x, floor.point.y + EYE, z)
    this.invalidate()
  }

  /** Anda com colisão nas paredes, sobe degraus de até 45 cm e cai até o chão; fantasma atravessa e voa. */
  private stepWalk(dt: number) {
    const forward = (this.keys.has('w') || this.keys.has('arrowup') ? 1 : 0)
      - (this.keys.has('s') || this.keys.has('arrowdown') ? 1 : 0) + this.move.y
    const side = (this.keys.has('d') || this.keys.has('arrowright') ? 1 : 0)
      - (this.keys.has('a') || this.keys.has('arrowleft') ? 1 : 0) + this.move.x
    const pos = this.camera.position
    let changed = false
    if (forward !== 0 || side !== 0) {
      const speed = (this.ghost ? GHOST_SPEED : WALK_SPEED) * (this.keys.has('shift') ? 2.2 : 1) * dt
      const yaw = this.look.y
      const delta = new Vector3(Math.sin(yaw) * -forward + Math.cos(yaw) * side, 0, Math.cos(yaw) * -forward - Math.sin(yaw) * side)
      if (delta.lengthSq() > 1) delta.normalize()
      delta.multiplyScalar(speed)
      if (this.ghost) {
        pos.add(delta)
        changed = true
      } else {
        for (const axis of [new Vector3(delta.x, 0, 0), new Vector3(0, 0, delta.z)]) {
          const length = axis.length()
          if (length === 0) continue
          const dir = axis.clone().normalize()
          // Raios na altura do joelho (acima de um degrau), da cintura e da cabeça.
          const blocked = [STEP + 0.05, EYE * 0.6, EYE - 0.1].some((height) =>
            this.firstHit(pos.clone().setY(pos.y - EYE + height), dir, length + RADIUS) !== null)
          if (!blocked) {
            pos.add(axis)
            changed = true
          }
        }
      }
    }
    if (this.ghost) {
      const up = (this.keys.has('e') || this.keys.has(' ') ? 1 : 0) - (this.keys.has('q') || this.keys.has('c') ? 1 : 0)
        + this.rise
      if (up !== 0) {
        pos.y += up * GHOST_SPEED * dt
        changed = true
      }
    } else {
      const floor = this.firstHit(pos.clone().setY(pos.y - EYE + STEP), new Vector3(0, -1, 0), 200)
      if (floor) {
        const target = floor.point.y + EYE
        if (Math.abs(target - pos.y) > 0.001) {
          pos.y = target > pos.y ? target : Math.max(target, pos.y - 6 * dt)
          changed = true
        }
      }
    }
    if (changed) this.invalidate()
  }

  private stepFlight(now: number) {
    const f = this.flight!
    const t = Math.min((now - f.start) / f.ms, 1)
    const k = t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2
    this.camera.position.lerpVectors(f.from.position, f.to.position, k)
    this.controls.target.lerpVectors(f.from.target, f.to.target, k)
    this.camera.lookAt(this.controls.target)
    this.invalidate()
    if (t >= 1) {
      this.controls.update()
      f.done()
    }
  }

  private addMeasurePoint(point: Vector3) {
    if (!this.pendingMeasure) {
      this.pendingMeasure = point
      return
    }
    const a = this.pendingMeasure
    this.pendingMeasure = null
    const meters = a.distanceTo(point)
    const geometry = new BufferGeometry().setFromPoints([a, point])
    const line = new Line(geometry, new LineBasicMaterial({ color: 0xe11d48, depthTest: false }))
    line.renderOrder = 10
    this.measureGroup.add(line)
    const label = document.createElement('span')
    label.className = 'absolute -translate-x-1/2 -translate-y-1/2 rounded-md bg-background/90 px-1.5 py-0.5 text-xs '
      + 'font-semibold tabular-nums text-foreground shadow'
    label.textContent = `${meters.toLocaleString('pt-BR', { maximumFractionDigits: 2, minimumFractionDigits: 2 })} m`
    this.overlay.appendChild(label)
    this.measureLabels.push({ element: label, position: a.clone().lerp(point, 0.5) })
    this.measures.push({ id: this.measures.length + 1, meters })
    this.callbacks.onChange?.()
    this.invalidate()
  }

  /**
   * Geometria de colisão (só o que está visível) com BVH. As malhas já vêm juntas e na posição final
   * (`optimizeModel`); cada vértice guarda a malha e a peça de onde veio, para saber o que foi tocado.
   */
  private buildCollider() {
    if (!this.model) return
    const visible: Mesh[] = []
    this.model.traverseVisible((node) => {
      if (node instanceof Mesh && node.geometry.index && (node.userData as MergedData).objectOf) visible.push(node)
    })
    let vertices = 0
    let indices = 0
    for (const mesh of visible) {
      vertices += mesh.geometry.attributes.position.count
      indices += mesh.geometry.index!.count
    }
    const version = ++this.colliderVersion
    this.collider = null
    if (indices === 0) return
    const position = new Float32Array(vertices * 3)
    const index = new Uint32Array(indices)
    const vertexMesh = new Uint16Array(vertices)
    const vertexObject = new Uint32Array(vertices)
    let vo = 0
    let io = 0
    visible.forEach((mesh, m) => {
      const g = mesh.geometry
      position.set(g.attributes.position.array as Float32Array, vo * 3)
      const source = g.index!.array
      for (let i = 0; i < source.length; i++) index[io++] = source[i] + vo
      vertexMesh.fill(m, vo, vo + g.attributes.position.count)
      vertexObject.set((mesh.userData as MergedData).objectOf, vo)
      vo += g.attributes.position.count
    })
    const geometry = new BufferGeometry()
    geometry.setAttribute('position', new BufferAttribute(position, 3))
    geometry.setIndex(new BufferAttribute(index, 1))
    // Maquete grande leva segundos: monta num worker para a tela não travar (cliques esperam `pickReady`).
    if (this.worker?.running) {
      this.worker.dispose()
      this.worker = null
    }
    this.worker ??= new GenerateMeshBVHWorker()
    this.worker.generate(geometry)
      .catch(() => new MeshBVH(geometry))
      .then((bvh) => {
        if (version !== this.colliderVersion) return
        this.collider = bvh
        this.colliderMeshes = visible
        this.vertexMesh = vertexMesh
        this.vertexObject = vertexObject
        this.minimap.invalidate()
        this.callbacks.onChange?.()
      })
  }

  /** Se já dá para clicar na maquete (caminhar, medir, comentar). */
  get pickReady() {
    return this.collider !== null
  }

  /** Começa na vista salva no arquivo, olhando para o ponto da maquete mais próximo do centro dessa direção. */
  private startFrom(position: Vector3, direction: Vector3) {
    const center = this.box.getCenter(new Vector3())
    const radius = Math.max(this.size.length() / 2, 1)
    if (position.distanceTo(center) > radius * 8) return
    const distance = Math.max(center.clone().sub(position).dot(direction), radius * 0.3)
    this.camera.position.copy(position)
    this.controls.target.copy(position).addScaledVector(direction, distance)
    this.controls.update()
  }

  private clearLights() {
    this.lightGroup.children.forEach((child) => {
      if (child instanceof Mesh) {
        child.geometry.dispose()
        ;(child.material as Material).dispose()
      }
    })
    this.lightGroup.clear()
  }

  private disposeModel() {
    disposeTree(this.model)
    this.materials?.dispose()
    if (this.model) this.scene.remove(this.model)
    this.model = null
    this.materials = null
    this.collider = null
    this.select(null)
  }

  private disposeExtra() {
    if (!this.extra) return
    disposeTree(this.extra.root)
    this.extra.materials.dispose()
    this.scene.remove(this.extra.root)
    this.extra = null
  }
}

/** Lê o GLB e devolve as partes juntadas, a tabela de materiais e as peças. */
async function parseModel(buffer: ArrayBuffer) {
  const loader = new GLTFLoader().setMeshoptDecoder(MeshoptDecoder)
  const gltf: GLTF = await loader.parseAsync(buffer, '')
  const json = gltf.parser.json as { nodes?: { name?: string }[] }
  const associations = gltf.parser.associations
  const extraIndex = new Map<Material, number>()
  const saved = savedCamera(gltf.scene)
  const { layers, objects } = optimizeModel(gltf.scene, {
    nameOf: (node: Object3D) => {
      const index = associations.get(node)?.nodes
      return (index != null ? json.nodes?.[index]?.name : undefined) ?? node.name
    },
    indexOf: (material: Material) => {
      const index = associations.get(material)?.materials
      if (index != null) return index
      if (!extraIndex.has(material)) extraIndex.set(material, 10_000 + extraIndex.size)
      return extraIndex.get(material)!
    },
  })
  const root = new Group()
  root.add(...layers)
  root.userData.saved = saved
  return { root, materials: new MaterialTable(layers), objects }
}

function disposeTree(root: Object3D | null | undefined) {
  root?.traverse((node) => {
    if (node instanceof Mesh || node instanceof Line) {
      node.geometry.dispose()
      const list: Material[] = Array.isArray(node.material) ? node.material : [node.material]
      list.forEach((m) => {
        Object.values(m).forEach((value) => {
          if (value && typeof value === 'object' && 'isTexture' in value) (value as Texture).dispose()
        })
        m.dispose()
      })
    }
  })
}

/** Copia o canvas do 3D para uma imagem com fundo (o 3D é transparente). */
function toImage(source: HTMLCanvasElement, width: number, background: string, type: string): Promise<Blob | null> {
  const scale = Math.min(1, width / source.width)
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(source.width * scale)
  canvas.height = Math.round(source.height * scale)
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = background
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(source, 0, 0, canvas.width, canvas.height)
  return new Promise((resolve) => canvas.toBlob(resolve, type, 0.9))
}

function isTyping(target: EventTarget | null) {
  return target instanceof HTMLElement && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))
}

function round(n: number) {
  return Math.round(n * 1000) / 1000
}
