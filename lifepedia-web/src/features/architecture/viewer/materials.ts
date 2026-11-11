import {
  Color,
  DoubleSide,
  type Group,
  type Material,
  Mesh,
  MeshStandardMaterial,
  type Texture,
  Vector3,
} from 'three'
import type { MaterialOverride, MaterialOverrides } from '../types'
import type { MergedData } from './optimize'
import { ensureBoxUv, libraryTexture } from './textures'

/** Material da maquete como a lista mostra. `color` em `#rrggbb`; `preview` é a textura original (data URL), se houver. */
export type MaterialInfo = {
  index: number
  name: string
  color: string
  preview: string | null
  /** Área das superfícies com ele, em m² */
  area: number
}

/** Realista (cores e texturas), maquete branca ou raio-x (paredes transparentes). */
export type VisualMode = 'real' | 'white' | 'xray'

type Standard = MeshStandardMaterial

type Slot = {
  index: number
  live: Standard
  original: { color: Color; map: Texture | null; opacity: number; transparent: boolean; emissive: Color }
  meshes: Mesh[]
  white: Standard | null
  xray: Standard | null
  area: number | null
  preview: string | null | undefined
}

const HIGHLIGHT = new Color('#ff8a1f')

/** Materiais de uma maquete: variantes (cor/textura por material), modos de visual e destaque. */
export class MaterialTable {
  private readonly slots = new Map<number, Slot>()
  private readonly textures = new Map<string, Texture>()
  private mode: VisualMode = 'real'
  private highlighted: number | null = null
  private applied: MaterialOverrides = {}

  constructor(layers: Group[]) {
    for (const layer of layers) {
      layer.traverse((node) => {
        if (!(node instanceof Mesh) || !isStandard(node.material)) return
        const { materialIndex } = node.userData as MergedData
        let slot = this.slots.get(materialIndex)
        if (!slot) {
          const live = node.material
          slot = {
            index: materialIndex, live, meshes: [], white: null, xray: null, area: null, preview: undefined,
            original: { color: live.color.clone(), map: live.map, opacity: live.opacity, transparent: live.transparent,
              emissive: live.emissive.clone() },
          }
          this.slots.set(materialIndex, slot)
        }
        slot.meshes.push(node)
      })
    }
  }

  has(index: number) {
    return this.slots.has(index)
  }

  /** Lista para a paleta (área calculada na primeira vez). */
  list(): MaterialInfo[] {
    return [...this.slots.values()].map((slot) => ({
      index: slot.index,
      name: materialName(slot.live.name, slot.index),
      color: `#${slot.original.color.getHexString()}`,
      preview: this.preview(slot),
      area: (slot.area ??= area(slot.meshes)),
    }))
  }

  /** Ajustes desta variante; material fora da lista volta ao original. */
  apply(overrides: MaterialOverrides) {
    this.applied = overrides
    for (const slot of this.slots.values()) this.applySlot(slot, overrides[String(slot.index)] ?? null)
    if (this.mode !== 'real') this.setMode(this.mode, true)
  }

  get current() {
    return this.applied
  }

  setMode(mode: VisualMode, force = false) {
    if (mode === this.mode && !force) return
    this.mode = mode
    for (const slot of this.slots.values()) {
      const material = mode === 'real' ? slot.live : mode === 'white' ? this.white(slot) : this.xray(slot)
      slot.meshes.forEach((mesh) => {
        mesh.material = material
      })
    }
    this.paintHighlight()
  }

  get visualMode() {
    return this.mode
  }

  /** Acende o material (laranja) para mostrar onde ele está; nulo apaga. */
  highlight(index: number | null) {
    this.highlighted = index
    this.paintHighlight()
  }

  /** Todos os materiais (para ligar sombra e recompilar). */
  all(): Material[] {
    const list: Material[] = []
    for (const slot of this.slots.values()) {
      list.push(slot.live)
      if (slot.white) list.push(slot.white)
      if (slot.xray) list.push(slot.xray)
    }
    return list
  }

  dispose() {
    this.textures.forEach((t) => t.dispose())
    for (const slot of this.slots.values()) {
      slot.white?.dispose()
      slot.xray?.dispose()
    }
  }

  private applySlot(slot: Slot, override: MaterialOverride | null) {
    const m = slot.live
    const hadMap = m.map
    if (override?.texture) {
      const texture = this.texture(override.texture, override.scale)
      if (texture) {
        slot.meshes.forEach((mesh) => ensureBoxUv(mesh.geometry))
        m.map = texture
        m.color.set(override.color ?? '#ffffff')
      }
    } else if (override?.color) {
      m.map = null
      m.color.set(override.color)
    } else {
      m.map = slot.original.map
      m.color.copy(slot.original.color)
    }
    if (hadMap !== m.map) m.needsUpdate = true
    if (slot.xray) slot.xray.color.copy(m.color)
  }

  private texture(id: string, scale: number | null) {
    const key = `${id}@${scale ?? ''}`
    let texture = this.textures.get(key)
    if (!texture) {
      const fallback = scale ?? 1
      texture = libraryTexture(id, fallback) ?? undefined
      if (texture) this.textures.set(key, texture)
    }
    return texture ?? null
  }

  private white(slot: Slot) {
    slot.white ??= new MeshStandardMaterial({
      color: 0xf3f2ef, roughness: 0.9, metalness: 0, side: DoubleSide,
      transparent: slot.original.transparent, opacity: slot.original.transparent ? 0.35 : 1,
      clippingPlanes: slot.live.clippingPlanes, shadowSide: slot.live.shadowSide,
    })
    return slot.white
  }

  private xray(slot: Slot) {
    slot.xray ??= new MeshStandardMaterial({
      color: slot.live.color.clone(), roughness: 0.8, side: DoubleSide, transparent: true, opacity: 0.16,
      depthWrite: false, clippingPlanes: slot.live.clippingPlanes,
    })
    return slot.xray
  }

  private paintHighlight() {
    for (const slot of this.slots.values()) {
      const on = slot.index === this.highlighted
      for (const m of [slot.live, slot.white, slot.xray]) {
        if (!m) continue
        if (on) {
          m.emissive.copy(HIGHLIGHT)
          m.emissiveIntensity = 0.55
        } else {
          m.emissive.copy(m === slot.live ? slot.original.emissive : BLACK)
          m.emissiveIntensity = 1
        }
      }
    }
  }

  private preview(slot: Slot) {
    if (slot.preview !== undefined) return slot.preview
    const image = slot.original.map?.image as CanvasImageSource | undefined
    slot.preview = null
    if (!image) return null
    try {
      const canvas = document.createElement('canvas')
      canvas.width = 48
      canvas.height = 48
      canvas.getContext('2d')!.drawImage(image, 0, 0, 48, 48)
      slot.preview = canvas.toDataURL('image/jpeg', 0.8)
    } catch {
      slot.preview = null
    }
    return slot.preview
  }
}

const BLACK = new Color(0, 0, 0)

function isStandard(material: unknown): material is Standard {
  return !!material && !Array.isArray(material) && (material as Standard).isMeshStandardMaterial === true
}

function materialName(name: string, index: number) {
  const clean = name.replace(/^_+/, '').replace(/_+/g, ' ').replace(/\s+\d+$/, '').trim()
  if (!clean || /^material\s*\d*$/i.test(clean)) return `Material ${index + 1}`
  return clean.replace(/^color\s+/i, 'Cor ').replace(/^edge color\s*\d*/i, 'Aresta')
}

function area(meshes: Mesh[]) {
  const a = new Vector3()
  const b = new Vector3()
  const c = new Vector3()
  let total = 0
  for (const mesh of meshes) {
    const p = mesh.geometry.getAttribute('position')
    const index = mesh.geometry.index
    if (!index) continue
    for (let i = 0; i < index.count; i += 3) {
      a.fromBufferAttribute(p, index.getX(i))
      b.fromBufferAttribute(p, index.getX(i + 1))
      c.fromBufferAttribute(p, index.getX(i + 2))
      total += b.sub(a).cross(c.sub(a)).length() / 2
    }
  }
  return total
}
