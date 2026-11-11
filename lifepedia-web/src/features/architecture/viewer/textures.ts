import { BufferAttribute, type BufferGeometry, CanvasTexture, RepeatWrapping, SRGBColorSpace, type Texture } from 'three'

/** Textura pronta do app: desenhada no navegador (nada vem de fora), repetida a cada `scale` metros. */
export type LibraryTexture = { id: string; name: string; group: string; scale: number }

export const TEXTURES: LibraryTexture[] = [
  { id: 'madeira-clara', name: 'Madeira clara', group: 'Madeiras', scale: 1.2 },
  { id: 'madeira-media', name: 'Madeira média', group: 'Madeiras', scale: 1.2 },
  { id: 'madeira-escura', name: 'Madeira escura', group: 'Madeiras', scale: 1.2 },
  { id: 'taco-trancado', name: 'Taco trançado', group: 'Madeiras', scale: 0.6 },
  { id: 'porcelanato-claro', name: 'Porcelanato claro', group: 'Pisos', scale: 1.2 },
  { id: 'porcelanato-cinza', name: 'Porcelanato cinza', group: 'Pisos', scale: 1.2 },
  { id: 'cimento-queimado', name: 'Cimento queimado', group: 'Pisos', scale: 2 },
  { id: 'ladrilho-hidraulico', name: 'Ladrilho hidráulico', group: 'Pisos', scale: 0.4 },
  { id: 'marmore-branco', name: 'Mármore branco', group: 'Pedras', scale: 1.5 },
  { id: 'granito-preto', name: 'Granito preto', group: 'Pedras', scale: 1 },
  { id: 'pedra-natural', name: 'Pedra natural', group: 'Pedras', scale: 1.2 },
  { id: 'tijolo-aparente', name: 'Tijolo aparente', group: 'Paredes', scale: 0.9 },
  { id: 'azulejo-metro', name: 'Azulejo metrô', group: 'Paredes', scale: 0.6 },
  { id: 'concreto', name: 'Concreto aparente', group: 'Paredes', scale: 2 },
  { id: 'linho', name: 'Linho', group: 'Tecidos', scale: 0.3 },
  { id: 'grama', name: 'Grama', group: 'Externo', scale: 1.5 },
]

const SIZE = 512
const cache = new Map<string, HTMLCanvasElement>()
const previews = new Map<string, string>()

export function textureById(id: string) {
  return TEXTURES.find((t) => t.id === id) ?? null
}

/** Textura do three (sRGB, repetindo), no canal de UV 1 (coordenadas em metros, `boxUv`). */
export function libraryTexture(id: string, scale: number): Texture | null {
  const canvas = draw(id)
  if (!canvas) return null
  const texture = new CanvasTexture(canvas)
  texture.colorSpace = SRGBColorSpace
  texture.wrapS = RepeatWrapping
  texture.wrapT = RepeatWrapping
  texture.channel = 1
  texture.repeat.set(1 / scale, 1 / scale)
  texture.anisotropy = 8
  return texture
}

/** Amostra pequena (data URL) para a lista. */
export function texturePreview(id: string) {
  let url = previews.get(id)
  if (url) return url
  const canvas = draw(id)
  if (!canvas) return ''
  const small = document.createElement('canvas')
  small.width = 96
  small.height = 96
  small.getContext('2d')!.drawImage(canvas, 0, 0, 96, 96)
  url = small.toDataURL('image/jpeg', 0.8)
  previews.set(id, url)
  return url
}

/**
 * Coordenadas de textura em metros pela direção da face (projeção de caixa), no atributo `uv1`: a textura da
 * biblioteca não depende do UV do arquivo, que o SketchUp só gera para superfícies que já tinham textura.
 */
export function ensureBoxUv(geometry: BufferGeometry) {
  if (geometry.getAttribute('uv1')) return
  const p = geometry.getAttribute('position')
  const n = geometry.getAttribute('normal')
  const uv = new Float32Array(p.count * 2)
  for (let i = 0; i < p.count; i++) {
    const ax = Math.abs(n.getX(i))
    const ay = Math.abs(n.getY(i))
    const az = Math.abs(n.getZ(i))
    if (ay >= ax && ay >= az) {
      uv[i * 2] = p.getX(i)
      uv[i * 2 + 1] = p.getZ(i)
    } else if (ax >= az) {
      uv[i * 2] = p.getZ(i)
      uv[i * 2 + 1] = p.getY(i)
    } else {
      uv[i * 2] = p.getX(i)
      uv[i * 2 + 1] = p.getY(i)
    }
  }
  geometry.setAttribute('uv1', new BufferAttribute(uv, 2))
}

function draw(id: string) {
  const cached = cache.get(id)
  if (cached) return cached
  const painter = PAINTERS[id]
  if (!painter) return null
  const canvas = document.createElement('canvas')
  canvas.width = SIZE
  canvas.height = SIZE
  const ctx = canvas.getContext('2d')!
  const random = seeded(hash(id))
  painter(ctx, random)
  cache.set(id, canvas)
  return canvas
}

type Painter = (ctx: CanvasRenderingContext2D, random: () => number) => void

const PAINTERS: Record<string, Painter> = {
  'madeira-clara': (ctx, r) => planks(ctx, r, [214, 184, 142]),
  'madeira-media': (ctx, r) => planks(ctx, r, [160, 112, 70]),
  'madeira-escura': (ctx, r) => planks(ctx, r, [92, 60, 38]),
  'taco-trancado': (ctx, r) => basket(ctx, r, [176, 128, 80]),
  'porcelanato-claro': (ctx, r) => tiles(ctx, r, [232, 228, 220], 2, 3, 0.04),
  'porcelanato-cinza': (ctx, r) => tiles(ctx, r, [150, 150, 148], 2, 3, 0.05),
  'cimento-queimado': (ctx, r) => cloudy(ctx, r, [168, 166, 160], 26),
  'ladrilho-hidraulico': (ctx, r) => hydraulic(ctx, r),
  'marmore-branco': (ctx, r) => marble(ctx, r, [240, 238, 234], [150, 150, 155]),
  'granito-preto': (ctx, r) => speckle(ctx, r, [38, 38, 40], [[110, 110, 112], [70, 70, 72], [200, 200, 200]]),
  'pedra-natural': (ctx, r) => stones(ctx, r),
  'tijolo-aparente': (ctx, r) => bricks(ctx, r),
  'azulejo-metro': (ctx, r) => subway(ctx, r),
  concreto: (ctx, r) => concrete(ctx, r),
  linho: (ctx, r) => linen(ctx, r),
  grama: (ctx, r) => grass(ctx, r),
}

type Rgb = [number, number, number]

function rgb([r, g, b]: Rgb, k = 1, a = 1) {
  return `rgba(${Math.round(clamp(r * k))}, ${Math.round(clamp(g * k))}, ${Math.round(clamp(b * k))}, ${a})`
}

function clamp(v: number) {
  return Math.max(0, Math.min(255, v))
}

/** Desenha nas 9 posições em volta para a textura emendar sem costura. */
function wrapped(draw: (dx: number, dy: number) => void) {
  for (const dx of [-SIZE, 0, SIZE]) for (const dy of [-SIZE, 0, SIZE]) draw(dx, dy)
}

function noise(ctx: CanvasRenderingContext2D, r: () => number, amount: number, dots = 9000) {
  for (let i = 0; i < dots; i++) {
    const v = r() > 0.5 ? 255 : 0
    ctx.fillStyle = `rgba(${v}, ${v}, ${v}, ${r() * amount})`
    ctx.fillRect(r() * SIZE, r() * SIZE, 1 + r() * 2, 1 + r() * 2)
  }
}

function planks(ctx: CanvasRenderingContext2D, r: () => number, base: Rgb) {
  const rows = 4
  const h = SIZE / rows
  for (let row = 0; row < rows; row++) {
    const offset = r() * SIZE
    for (const start of [offset - SIZE, offset]) {
      const tone = 0.85 + r() * 0.3
      ctx.fillStyle = rgb(base, tone)
      ctx.fillRect(start, row * h, SIZE, h)
      for (let k = 0; k < 14; k++) {
        const y = row * h + r() * h
        ctx.strokeStyle = rgb(base, tone * (0.75 + r() * 0.2), 0.35)
        ctx.lineWidth = 0.6 + r() * 1.6
        ctx.beginPath()
        ctx.moveTo(start, y)
        for (let x = 0; x <= SIZE; x += 32) ctx.lineTo(start + x, y + Math.sin(x / 70 + k) * 2.5)
        ctx.stroke()
      }
      ctx.fillStyle = rgb(base, 0.45, 0.9)
      ctx.fillRect(start, row * h, 2, h)
    }
    ctx.fillStyle = rgb(base, 0.5, 0.8)
    ctx.fillRect(0, row * h, SIZE, 2)
  }
  noise(ctx, r, 0.05)
}

/** Taco em cesto: quadrados de 3 réguas, deitadas e em pé alternando. */
function basket(ctx: CanvasRenderingContext2D, r: () => number, base: Rgb) {
  const cells = 4
  const cell = SIZE / cells
  const strip = cell / 3
  ctx.fillStyle = rgb(base, 0.5)
  ctx.fillRect(0, 0, SIZE, SIZE)
  for (let i = 0; i < cells; i++) {
    for (let j = 0; j < cells; j++) {
      const horizontal = (i + j) % 2 === 0
      for (let k = 0; k < 3; k++) {
        ctx.fillStyle = rgb(base, 0.8 + r() * 0.35)
        if (horizontal) ctx.fillRect(i * cell + 1, j * cell + k * strip + 1, cell - 2, strip - 2)
        else ctx.fillRect(i * cell + k * strip + 1, j * cell + 1, strip - 2, cell - 2)
      }
    }
  }
  noise(ctx, r, 0.06)
}

function tiles(ctx: CanvasRenderingContext2D, r: () => number, base: Rgb, cols: number, rows: number, variation: number) {
  const w = SIZE / cols
  const h = SIZE / rows
  for (let i = 0; i < cols; i++) {
    for (let j = 0; j < rows; j++) {
      ctx.fillStyle = rgb(base, 1 - variation + r() * variation * 2)
      ctx.fillRect(i * w, j * h, w, h)
    }
  }
  noise(ctx, r, 0.04, 14000)
  ctx.fillStyle = rgb(base, 0.72)
  for (let i = 0; i < cols; i++) ctx.fillRect(i * w, 0, 3, SIZE)
  for (let j = 0; j < rows; j++) ctx.fillRect(0, j * h, SIZE, 3)
}

function cloudy(ctx: CanvasRenderingContext2D, r: () => number, base: Rgb, blobs: number) {
  ctx.fillStyle = rgb(base)
  ctx.fillRect(0, 0, SIZE, SIZE)
  for (let i = 0; i < blobs * 4; i++) {
    const x = r() * SIZE
    const y = r() * SIZE
    const radius = 30 + r() * 140
    const k = 0.85 + r() * 0.3
    wrapped((dx, dy) => {
      const g = ctx.createRadialGradient(x + dx, y + dy, 0, x + dx, y + dy, radius)
      g.addColorStop(0, rgb(base, k, 0.25))
      g.addColorStop(1, rgb(base, k, 0))
      ctx.fillStyle = g
      ctx.fillRect(x + dx - radius, y + dy - radius, radius * 2, radius * 2)
    })
  }
  noise(ctx, r, 0.05, 16000)
}

function hydraulic(ctx: CanvasRenderingContext2D, r: () => number) {
  const cell = SIZE / 2
  const colors: Rgb[] = [[236, 230, 218], [52, 86, 110], [196, 120, 82]]
  for (let i = 0; i < 2; i++) {
    for (let j = 0; j < 2; j++) {
      const x = i * cell
      const y = j * cell
      ctx.fillStyle = rgb(colors[0])
      ctx.fillRect(x, y, cell, cell)
      ctx.fillStyle = rgb(colors[1])
      for (const [cx, cy] of [[0, 0], [cell, 0], [0, cell], [cell, cell]]) {
        ctx.beginPath()
        ctx.arc(x + cx, y + cy, cell * 0.38, 0, Math.PI * 2)
        ctx.fill()
      }
      ctx.fillStyle = rgb(colors[2])
      ctx.beginPath()
      ctx.moveTo(x + cell / 2, y + cell * 0.2)
      ctx.lineTo(x + cell * 0.8, y + cell / 2)
      ctx.lineTo(x + cell / 2, y + cell * 0.8)
      ctx.lineTo(x + cell * 0.2, y + cell / 2)
      ctx.fill()
      ctx.fillStyle = rgb(colors[0])
      ctx.beginPath()
      ctx.arc(x + cell / 2, y + cell / 2, cell * 0.1, 0, Math.PI * 2)
      ctx.fill()
    }
  }
  noise(ctx, r, 0.06)
  ctx.fillStyle = 'rgba(120, 115, 105, 0.6)'
  ctx.fillRect(0, 0, SIZE, 2)
  ctx.fillRect(0, cell, SIZE, 2)
  ctx.fillRect(0, 0, 2, SIZE)
  ctx.fillRect(cell, 0, 2, SIZE)
}

function marble(ctx: CanvasRenderingContext2D, r: () => number, base: Rgb, vein: Rgb) {
  cloudy(ctx, r, base, 10)
  for (let v = 0; v < 9; v++) {
    let x = r() * SIZE
    let y = 0
    const width = 0.5 + r() * 2.5
    const alpha = 0.15 + r() * 0.35
    ctx.strokeStyle = rgb(vein, 1, alpha)
    ctx.lineWidth = width
    const points: [number, number][] = [[x, y]]
    while (y < SIZE) {
      x += (r() - 0.45) * 40
      y += 10 + r() * 25
      points.push([x, y])
    }
    wrapped((dx, dy) => {
      ctx.beginPath()
      points.forEach(([px, py], i) => (i === 0 ? ctx.moveTo(px + dx, py + dy) : ctx.lineTo(px + dx, py + dy)))
      ctx.stroke()
    })
  }
}

function speckle(ctx: CanvasRenderingContext2D, r: () => number, base: Rgb, dots: Rgb[]) {
  ctx.fillStyle = rgb(base)
  ctx.fillRect(0, 0, SIZE, SIZE)
  for (let i = 0; i < 26000; i++) {
    ctx.fillStyle = rgb(dots[Math.floor(r() * dots.length)], 0.8 + r() * 0.4, 0.2 + r() * 0.6)
    const s = 1 + r() * 3.5
    ctx.fillRect(r() * SIZE, r() * SIZE, s, s)
  }
}

function stones(ctx: CanvasRenderingContext2D, r: () => number) {
  ctx.fillStyle = 'rgb(120, 112, 100)'
  ctx.fillRect(0, 0, SIZE, SIZE)
  const step = SIZE / 6
  for (let i = 0; i < 6; i++) {
    for (let j = 0; j < 6; j++) {
      const cx = i * step + step / 2 + (r() - 0.5) * step * 0.3
      const cy = j * step + step / 2 + (r() - 0.5) * step * 0.3
      const tone: Rgb = [170 + r() * 50, 158 + r() * 40, 135 + r() * 30]
      wrapped((dx, dy) => {
        ctx.fillStyle = rgb(tone)
        ctx.beginPath()
        for (let k = 0; k < 7; k++) {
          const a = (k / 7) * Math.PI * 2
          const rad = step * (0.38 + r() * 0.12)
          const px = cx + dx + Math.cos(a) * rad
          const py = cy + dy + Math.sin(a) * rad
          if (k === 0) ctx.moveTo(px, py)
          else ctx.lineTo(px, py)
        }
        ctx.closePath()
        ctx.fill()
      })
    }
  }
  noise(ctx, r, 0.08)
}

function bricks(ctx: CanvasRenderingContext2D, r: () => number) {
  ctx.fillStyle = 'rgb(205, 196, 182)'
  ctx.fillRect(0, 0, SIZE, SIZE)
  const rows = 8
  const h = SIZE / rows
  const w = SIZE / 4
  for (let row = 0; row < rows; row++) {
    const offset = row % 2 ? w / 2 : 0
    for (let i = -1; i < 5; i++) {
      ctx.fillStyle = rgb([164, 82, 56], 0.8 + r() * 0.35)
      ctx.fillRect(i * w + offset + 3, row * h + 3, w - 6, h - 6)
    }
  }
  noise(ctx, r, 0.1)
}

function subway(ctx: CanvasRenderingContext2D, r: () => number) {
  ctx.fillStyle = 'rgb(200, 200, 198)'
  ctx.fillRect(0, 0, SIZE, SIZE)
  const rows = 8
  const h = SIZE / rows
  const w = SIZE / 4
  for (let row = 0; row < rows; row++) {
    const offset = row % 2 ? w / 2 : 0
    for (let i = -1; i < 5; i++) {
      const x = i * w + offset + 2
      const y = row * h + 2
      const g = ctx.createLinearGradient(x, y, x, y + h - 4)
      g.addColorStop(0, rgb([250, 250, 248], 0.99 + r() * 0.02))
      g.addColorStop(1, rgb([236, 236, 234]))
      ctx.fillStyle = g
      ctx.fillRect(x, y, w - 4, h - 4)
    }
  }
}

function concrete(ctx: CanvasRenderingContext2D, r: () => number) {
  cloudy(ctx, r, [150, 148, 144], 18)
  ctx.fillStyle = 'rgba(80, 80, 80, 0.35)'
  for (const [x, y] of [[SIZE / 4, SIZE / 4], [SIZE * 0.75, SIZE / 4], [SIZE / 4, SIZE * 0.75], [SIZE * 0.75, SIZE * 0.75]]) {
    ctx.beginPath()
    ctx.arc(x, y, 6, 0, Math.PI * 2)
    ctx.fill()
  }
  ctx.fillStyle = 'rgba(90, 90, 90, 0.4)'
  ctx.fillRect(0, 0, SIZE, 2)
  ctx.fillRect(0, 0, 2, SIZE)
}

function linen(ctx: CanvasRenderingContext2D, r: () => number) {
  ctx.fillStyle = 'rgb(222, 214, 198)'
  ctx.fillRect(0, 0, SIZE, SIZE)
  for (let i = 0; i < SIZE; i += 3) {
    ctx.fillStyle = `rgba(120, 100, 80, ${0.05 + r() * 0.1})`
    ctx.fillRect(i, 0, 1, SIZE)
    ctx.fillStyle = `rgba(255, 255, 255, ${0.05 + r() * 0.12})`
    ctx.fillRect(0, i, SIZE, 1)
  }
  noise(ctx, r, 0.05)
}

function grass(ctx: CanvasRenderingContext2D, r: () => number) {
  ctx.fillStyle = 'rgb(84, 126, 52)'
  ctx.fillRect(0, 0, SIZE, SIZE)
  for (let i = 0; i < 9000; i++) {
    const x = r() * SIZE
    const y = r() * SIZE
    ctx.strokeStyle = rgb([90 + r() * 60, 140 + r() * 50, 50 + r() * 30], 1, 0.7)
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(x, y)
    ctx.lineTo(x + (r() - 0.5) * 6, y - 4 - r() * 8)
    ctx.stroke()
  }
}

function hash(text: string) {
  let h = 2166136261
  for (let i = 0; i < text.length; i++) h = Math.imul(h ^ text.charCodeAt(i), 16777619)
  return h >>> 0
}

function seeded(seed: number) {
  let s = seed || 1
  return () => {
    s ^= s << 13
    s ^= s >>> 17
    s ^= s << 5
    return (s >>> 0) / 4294967296
  }
}
