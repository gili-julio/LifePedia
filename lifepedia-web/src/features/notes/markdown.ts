/** `[[Nome da nota]]`: link para outra nota do mesmo dono. */
export const WIKI_LINK = /\[\[([^[\]\n]{1,200})\]\]/g

const TASK_MARKER = /^(\s*(?:[-*+]|\d+[.)])\s+\[)([ xX])(\])/gm
const WORDS_PER_MINUTE = 200
const MAX_IMAGE_SIDE = 1600
const MAX_IMAGE_BYTES = 1024 * 1024

/** `\[\[Nome\]\]` como o editor gera (colchetes e outros símbolos com barra). */
const ESCAPED_WIKI_LINK = /\\\[\\\[((?:\\.|[^\\\n])*?)\\\]\\\]/g
const ESCAPED_CHAR = /\\([\\`*_[\]~])/g

/** O editor escapa colchetes ao gerar o markdown; `[[Nome]]` precisa sair sem escape para continuar sendo link. */
export function unescapeWikiLinks(markdown: string) {
  return markdown.replace(ESCAPED_WIKI_LINK, (_match, inner: string) => `[[${inner.replace(ESCAPED_CHAR, '$1')}]]`)
}

/** Texto sem marcação, para contar palavras. */
export function plainText(markdown: string) {
  return markdown
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(WIKI_LINK, '$1')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/^\s*(?:[-*+]|\d+[.)])\s+\[[ xX]\]\s*/gm, '')
    .replace(/^\s*(?:#{1,6}|>|[-*+]|\d+[.)])\s+/gm, '')
    .replace(/[*_~`|#>-]/g, ' ')
}

export function wordCount(markdown: string) {
  const words = plainText(markdown).match(/[\p{L}\p{N}]+(?:['’-][\p{L}\p{N}]+)*/gu)
  return words?.length ?? 0
}

/** Minutos de leitura (pelo menos 1 quando há texto). */
export function readingMinutes(words: number) {
  return words === 0 ? 0 : Math.max(1, Math.round(words / WORDS_PER_MINUTE))
}

/** Inverte o `index`-ésimo item de checklist no markdown, sem mexer no resto do texto. */
export function toggleTask(markdown: string, index: number) {
  let seen = -1
  return markdown.replace(TASK_MARKER, (match, open: string, mark: string, close: string) => {
    seen++
    return seen === index ? `${open}${mark === ' ' ? 'x' : ' '}${close}` : match
  })
}

/** Foto enviada pela API para uma nota (a única que o editor mostra). */
export function isNoteImage(src: unknown): src is string {
  return typeof src === 'string' && /^\/api\/(?:groups\/[0-9a-f-]{36}\/)?notes\/[0-9a-f-]{36}\/images\/[0-9a-f]{32}$/.test(src)
}

/** Baixa a nota como arquivo `.md`. */
export function downloadMarkdown(title: string, content: string) {
  const name = title.replace(/[\\/:*?"<>|]+/g, ' ').trim().slice(0, 80) || 'nota'
  const url = URL.createObjectURL(new Blob([content], { type: 'text/markdown;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `${name}.md`
  link.click()
  URL.revokeObjectURL(url)
}

/** Reduz a foto para até 1600px em JPEG (a API aceita até 1 MB). */
export async function shrinkImage(file: File): Promise<Blob> {
  if (!/^image\/(png|jpe?g|webp)$/.test(file.type)) throw new Error('Envie uma imagem JPEG, PNG ou WebP.')
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, MAX_IMAGE_SIDE / Math.max(bitmap.width, bitmap.height))
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
    if (blob && blob.size <= MAX_IMAGE_BYTES) return blob
  }
  throw new Error('A imagem ficou grande demais. Tente outra menor.')
}
