import { type Box3, Mesh, type Object3D, type Vector3 } from 'three'
import type { Category } from './categories'
import type { MergedData } from './optimize'

const PIXELS = 320
/** Quantos metros em volta de quem anda o minimapa mostra. */
const VIEW_METERS = 16
/** Altura do corte acima do piso (abaixo das janelas altas, acima das bancadas). */
const CUT_ABOVE = 1.2
const MAX_BASE = 2048

const STYLE: Partial<Record<Category, { color: string; width: number }>> = {
  structure: { color: '#27272a', width: 3 },
  windows: { color: '#38bdf8', width: 2.5 },
  doors: { color: '#a16207', width: 2 },
  furniture: { color: '#a1a1aa', width: 1.2 },
  fixtures: { color: '#a1a1aa', width: 1.2 },
  appliances: { color: '#a1a1aa', width: 1.2 },
}

export type MinimapRoom = { name: string; outline: [number, number][] }

/**
 * Planta do andar de quem caminha: o corte das paredes na altura de 1,2 m, calculado da geometria (as linhas onde as
 * faces cruzam o plano), desenhado uma vez por andar e mostrado em volta de quem anda. Clicar leva até o ponto.
 */
export class Minimap {
  readonly element: HTMLCanvasElement
  private readonly base = document.createElement('canvas')
  private floor: number | null = null
  private rooms: MinimapRoom[] = []
  private origin = { x: 0, z: 0, scale: 40 }
  private center = { x: 0, z: 0 }
  private readonly onTeleport: (x: number, z: number) => void

  constructor(parent: HTMLElement, onTeleport: (x: number, z: number) => void) {
    this.onTeleport = onTeleport
    this.element = document.createElement('canvas')
    this.element.width = PIXELS
    this.element.height = PIXELS
    this.element.title = 'Planta: toque para ir até o ponto'
    this.element.className = 'absolute top-14 left-2 z-10 hidden size-40 cursor-crosshair rounded-full border-2 border-background '
      + 'bg-background shadow-md sm:size-52'
    this.element.addEventListener('pointerup', this.onClick)
    parent.appendChild(this.element)
  }

  setVisible(visible: boolean) {
    this.element.classList.toggle('hidden', !visible)
    if (!visible) this.floor = null
  }

  get visible() {
    return !this.element.classList.contains('hidden')
  }

  setRooms(rooms: MinimapRoom[]) {
    this.rooms = rooms
    this.floor = null
  }

  /** Força desenhar a planta de novo (partes ou maquete mudaram). */
  invalidate() {
    this.floor = null
  }

  /** Desenha a vizinhança e o marcador; refaz a planta se o andar mudou mais de 1 m. */
  update(model: Object3D | null, box: Box3, eye: Vector3, floorY: number, yaw: number) {
    if (!this.visible || !model) return
    if (this.floor == null || Math.abs(this.floor - floorY) > 1) this.drawBase(model, box, floorY)
    this.center = { x: eye.x, z: eye.z }
    const ctx = this.element.getContext('2d')!
    const { scale } = this.origin
    const span = VIEW_METERS * scale
    const [cx, cy] = this.toBase(eye.x, eye.z)
    ctx.fillStyle = '#f4f4f5'
    ctx.fillRect(0, 0, PIXELS, PIXELS)
    ctx.drawImage(this.base, cx - span / 2, cy - span / 2, span, span, 0, 0, PIXELS, PIXELS)
    ctx.save()
    ctx.translate(PIXELS / 2, PIXELS / 2)
    ctx.rotate(-yaw)
    ctx.fillStyle = 'rgba(37, 99, 235, 0.22)'
    ctx.beginPath()
    ctx.moveTo(0, 0)
    ctx.arc(0, 0, 70, -Math.PI / 2 - 0.55, -Math.PI / 2 + 0.55)
    ctx.closePath()
    ctx.fill()
    ctx.fillStyle = '#2563eb'
    ctx.strokeStyle = '#ffffff'
    ctx.lineWidth = 4
    ctx.beginPath()
    ctx.arc(0, 0, 10, 0, Math.PI * 2)
    ctx.fill()
    ctx.stroke()
    ctx.restore()
  }

  dispose() {
    this.element.removeEventListener('pointerup', this.onClick)
    this.element.remove()
  }

  private drawBase(model: Object3D, box: Box3, floorY: number) {
    this.floor = floorY
    const width = box.max.x - box.min.x + VIEW_METERS
    const depth = box.max.z - box.min.z + VIEW_METERS
    const scale = Math.min(40, MAX_BASE / Math.max(width, depth))
    this.origin = { x: box.min.x - VIEW_METERS / 2, z: box.min.z - VIEW_METERS / 2, scale }
    this.base.width = Math.ceil(width * scale)
    this.base.height = Math.ceil(depth * scale)
    const ctx = this.base.getContext('2d')!
    ctx.fillStyle = '#f4f4f5'
    ctx.fillRect(0, 0, this.base.width, this.base.height)

    for (const room of this.rooms) {
      if (room.outline.length < 3) continue
      ctx.beginPath()
      room.outline.forEach(([x, z], i) => {
        const [px, py] = this.toBase(x, z)
        if (i === 0) ctx.moveTo(px, py)
        else ctx.lineTo(px, py)
      })
      ctx.closePath()
      ctx.fillStyle = '#ffffff'
      ctx.fill()
    }

    const cut = floorY + CUT_ABOVE
    const order: Category[] = ['furniture', 'fixtures', 'appliances', 'doors', 'windows', 'structure']
    for (const category of order) {
      const style = STYLE[category]!
      ctx.strokeStyle = style.color
      ctx.lineWidth = style.width
      ctx.lineCap = 'round'
      ctx.beginPath()
      model.traverseVisible((node) => {
        if (!(node instanceof Mesh) || (node.userData as MergedData).category !== category) return
        const bounds = node.geometry.boundingBox
        if (bounds && (bounds.min.y > cut || bounds.max.y < cut)) return
        this.section(ctx, node, cut)
      })
      ctx.stroke()
    }

    ctx.font = '600 22px Manrope, system-ui, sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    for (const room of this.rooms) {
      if (room.outline.length < 3) continue
      const cx = room.outline.reduce((s, p) => s + p[0], 0) / room.outline.length
      const cz = room.outline.reduce((s, p) => s + p[1], 0) / room.outline.length
      const [lx, ly] = this.toBase(cx, cz)
      ctx.lineWidth = 5
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.95)'
      ctx.strokeText(room.name, lx, ly)
      ctx.fillStyle = '#1e293b'
      ctx.fillText(room.name, lx, ly)
    }
  }

  /** Linhas onde as faces da malha cruzam o plano y = {@code cut}. */
  private section(ctx: CanvasRenderingContext2D, mesh: Mesh, cut: number) {
    const position = mesh.geometry.getAttribute('position').array as ArrayLike<number>
    const index = mesh.geometry.index?.array
    if (!index) return
    const { x: ox, z: oz, scale } = this.origin
    const points: number[] = []
    for (let t = 0; t < index.length; t += 3) {
      points.length = 0
      for (let k = 0; k < 3; k++) {
        const a = index[t + k] * 3
        const b = index[t + ((k + 1) % 3)] * 3
        const ya = position[a + 1] - cut
        const yb = position[b + 1] - cut
        if ((ya < 0) === (yb < 0)) continue
        const f = ya / (ya - yb)
        points.push(position[a] + (position[b] - position[a]) * f, position[a + 2] + (position[b + 2] - position[a + 2]) * f)
      }
      if (points.length < 4) continue
      ctx.moveTo((points[0] - ox) * scale, (points[1] - oz) * scale)
      ctx.lineTo((points[2] - ox) * scale, (points[3] - oz) * scale)
    }
  }

  private toBase(x: number, z: number): [number, number] {
    const { x: ox, z: oz, scale } = this.origin
    return [(x - ox) * scale, (z - oz) * scale]
  }

  private onClick = (e: PointerEvent) => {
    const rect = this.element.getBoundingClientRect()
    const dx = ((e.clientX - rect.left) / rect.width - 0.5) * VIEW_METERS
    const dz = ((e.clientY - rect.top) / rect.height - 0.5) * VIEW_METERS
    this.onTeleport(this.center.x + dx, this.center.z + dz)
  }
}
