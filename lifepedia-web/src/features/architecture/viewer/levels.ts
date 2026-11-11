import { type Group, Mesh, Vector3 } from 'three'
import type { MergedData } from './optimize'

/** Andar achado na maquete: altura do piso (y, em metros) e nome. */
export type Level = { y: number; name: string }

const BUCKET = 0.05
/** Pisos a menos que isso de distância são o mesmo andar (degraus, rebaixos). */
const SAME_LEVEL = 2
/** Parede acima do piso que faz dele um andar (e não uma laje de cobertura). */
const WALL_ABOVE = 4
const MIN_FLOOR = 6

/**
 * Andares pela geometria das paredes, pisos e forros: alturas com muita superfície virada para cima e com parede em
 * cima. Térreo é o mais baixo; o resto vira 1º, 2º... andar.
 */
export function findLevels(layers: Group[]): Level[] {
  const floors = new Map<number, number>()
  const walls = new Map<number, number>()
  const a = new Vector3()
  const b = new Vector3()
  const c = new Vector3()
  const n = new Vector3()
  const d = new Vector3()
  let total = 0
  for (const layer of layers) {
    if (layer.userData.category !== 'structure') continue
    layer.traverse((node) => {
      if (!(node instanceof Mesh) || !(node.userData as MergedData).objectOf) return
      const p = node.geometry.getAttribute('position')
      const index = node.geometry.index
      if (!index) return
      for (let i = 0; i < index.count; i += 3) {
        a.fromBufferAttribute(p, index.getX(i))
        b.fromBufferAttribute(p, index.getX(i + 1))
        c.fromBufferAttribute(p, index.getX(i + 2))
        n.subVectors(b, a).cross(d.subVectors(c, a))
        const area = n.length() / 2
        if (area < 1e-4) continue
        n.normalize()
        const y = (a.y + b.y + c.y) / 3
        if (Math.abs(n.y) > 0.95) {
          const key = Math.round(Math.min(a.y, b.y, c.y) / BUCKET)
          floors.set(key, (floors.get(key) ?? 0) + area)
          total += area
        } else if (Math.abs(n.y) < 0.2) {
          const key = Math.round(y / 0.25)
          walls.set(key, (walls.get(key) ?? 0) + area)
        }
      }
    })
  }
  const minimum = Math.max(MIN_FLOOR, total * 0.03)
  const peaks = [...floors.entries()].filter(([, area]) => area >= minimum).sort((x, y) => y[1] - x[1])
  const chosen: { y: number; area: number }[] = []
  for (const [key, area] of peaks) {
    const y = key * BUCKET
    if (chosen.some((l) => Math.abs(l.y - y) < SAME_LEVEL)) continue
    if (wallsBetween(walls, y + 0.5, y + 2) < WALL_ABOVE) continue
    chosen.push({ y, area })
  }
  return chosen.sort((x, y) => x.y - y.y).map((l, i) => ({ y: l.y, name: i === 0 ? 'Térreo' : `${i}º andar` }))
}

function wallsBetween(walls: Map<number, number>, from: number, to: number) {
  let sum = 0
  for (let k = Math.round(from / 0.25); k <= Math.round(to / 0.25); k++) sum += walls.get(k) ?? 0
  return sum
}
