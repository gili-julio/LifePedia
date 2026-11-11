import type { Object3D } from 'three'

/** Constantes e tipos da conversão (leves: o conversor em si só é baixado ao enviar uma versão). */

/** Lado máximo das texturas na maquete enviada. */
export const MAX_TEXTURE = 2048
/** Extensões aceitas na escolha de arquivos (modelo + texturas, ou um .zip com tudo). */
export const ACCEPT = '.zip,.dae,.obj,.mtl,.fbx,.glb,.gltf,.bin,.png,.jpg,.jpeg,.webp,.bmp,.skp'

export type Unit = 'm' | 'cm' | 'mm' | 'in' | 'ft'

export const UNITS: { value: Unit; label: string; meters: number }[] = [
  { value: 'm', label: 'Metros', meters: 1 },
  { value: 'cm', label: 'Centímetros', meters: 0.01 },
  { value: 'mm', label: 'Milímetros', meters: 0.001 },
  { value: 'in', label: 'Polegadas', meters: 0.0254 },
  { value: 'ft', label: 'Pés', meters: 0.3048 },
]

export type Stage = 'reading' | 'building' | 'textures' | 'geometry'

export const STAGE_LABEL: Record<Stage, string> = {
  reading: 'Lendo os arquivos',
  building: 'Montando a maquete',
  textures: 'Comprimindo as texturas',
  geometry: 'Comprimindo a geometria',
}

/** Modelo lido, antes de escolher unidade e posição. */
export type LoadedModel = {
  object: Object3D
  sourceName: string
  sourceBytes: number
  /** Unidade que o arquivo já traz (COLLADA e glTF) ou um palpite pelo tamanho */
  unit: Unit
  /** Tamanho em unidades do arquivo (x, altura, z) */
  rawSize: [number, number, number]
  triangles: number
  /** Texturas pedidas pelo modelo que não vieram nos arquivos (ou que o navegador não lê): a cor fica branca */
  missing: string[]
}

export type ConvertedModel = { glb: Uint8Array; sizeBytes: number }

export class ConvertError extends Error {}

/** Tamanho em metros com a unidade e a posição escolhidas (largura, altura, profundidade). */
export function sizeIn(model: LoadedModel, unit: Unit, upright: boolean): [number, number, number] {
  const k = UNITS.find((u) => u.value === unit)!.meters
  const [x, y, z] = model.rawSize
  return upright ? [x * k, z * k, y * k] : [x * k, y * k, z * k]
}
