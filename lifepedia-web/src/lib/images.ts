/** Reduz uma foto (JPEG, PNG ou WebP) para JPEG com até {@code maxSide} px e {@code maxBytes} (fundo branco). */
export async function shrinkPhoto(file: File, maxSide: number, maxBytes: number): Promise<Blob> {
  if (!/^image\/(png|jpe?g|webp)$/.test(file.type)) throw new Error('Envie uma imagem JPEG, PNG ou WebP.')
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  const context = canvas.getContext('2d')
  if (!context) throw new Error('Não foi possível ler a imagem.')
  context.fillStyle = '#fff'
  context.fillRect(0, 0, canvas.width, canvas.height)
  context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()
  for (const quality of [0.85, 0.7, 0.55]) {
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
    if (blob && blob.size <= maxBytes) return blob
  }
  throw new Error('A imagem ficou grande demais. Tente outra menor.')
}
