/** Área do recorte em pixels da imagem original (formato do react-easy-crop). */
export type PixelArea = { x: number; y: number; width: number; height: number }

/** Tamanho final (quadrado) e qualidade do JPEG enviado. */
export const OUTPUT_SIDE = 512
const QUALITY = 0.88

function loadImage(src: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('Não foi possível abrir a imagem.'))
    image.src = src
  })
}

/** Recorta (com rotação em graus) e reduz para `OUTPUT_SIDE` px, em JPEG. */
export async function cropImage(src: string, area: PixelArea, rotation = 0): Promise<Blob> {
  const image = await loadImage(src)
  const radians = (rotation * Math.PI) / 180
  const sin = Math.abs(Math.sin(radians))
  const cos = Math.abs(Math.cos(radians))
  const boxWidth = image.width * cos + image.height * sin
  const boxHeight = image.width * sin + image.height * cos

  // Imagem inteira girada; o recorte vem dela.
  const rotated = document.createElement('canvas')
  rotated.width = boxWidth
  rotated.height = boxHeight
  const rctx = rotated.getContext('2d')!
  rctx.translate(boxWidth / 2, boxHeight / 2)
  rctx.rotate(radians)
  rctx.drawImage(image, -image.width / 2, -image.height / 2)

  const output = document.createElement('canvas')
  const side = Math.min(OUTPUT_SIDE, Math.round(area.width))
  output.width = side
  output.height = side
  const ctx = output.getContext('2d')!
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, side, side)
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(rotated, area.x, area.y, area.width, area.height, 0, 0, side, side)

  return new Promise((resolve, reject) =>
    output.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Falha ao gerar a imagem.'))), 'image/jpeg', QUALITY),
  )
}
