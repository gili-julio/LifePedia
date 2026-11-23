import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { getDocument, GlobalWorkerOptions } from 'pdfjs-dist'

type Piece = { text: string; x: number; y: number; width: number; size: number }

const MAX_PAGES = 30
const SAME_LINE = 2

/**
 * Linhas de texto do PDF, lido só no navegador: itens agrupados por página e altura (±2), em ordem da esquerda para a
 * direita; colunas afastadas viram TAB.
 */
export async function pdfLines(file: File): Promise<string[]> {
  GlobalWorkerOptions.workerSrc = workerUrl
  const task = getDocument({ data: new Uint8Array(await file.arrayBuffer()), useWasm: false, disableFontFace: true, useSystemFonts: false, verbosity: 0 })
  const pdf = await task.promise
  try {
    if (pdf.numPages > MAX_PAGES) throw new Error(`O PDF tem mais de ${MAX_PAGES} páginas. Confira se é o histórico.`)
    const lines: string[] = []
    for (let n = 1; n <= pdf.numPages; n++) {
      const page = await pdf.getPage(n)
      const content = await page.getTextContent()
      const pieces: Piece[] = []
      for (const item of content.items) {
        if (!('str' in item) || !item.str.trim()) continue
        const [, , c, d, x, y] = item.transform as number[]
        pieces.push({ text: item.str, x, y, width: item.width, size: Math.hypot(c, d) || item.height || 8 })
      }
      lines.push(...toLines(pieces))
      page.cleanup()
    }
    return lines
  } finally {
    await task.destroy()
  }
}

function toLines(pieces: Piece[]): string[] {
  const rows: Piece[][] = []
  for (const piece of [...pieces].sort((a, b) => b.y - a.y)) {
    const row = rows.find((r) => Math.abs(r[0].y - piece.y) <= SAME_LINE)
    if (row) row.push(piece)
    else rows.push([piece])
  }
  return rows.map((row) => {
    const sorted = row.sort((a, b) => a.x - b.x)
    let line = ''
    let end = Number.NEGATIVE_INFINITY
    for (const piece of sorted) {
      const gap = piece.x - end
      if (line) line += gap > piece.size * 0.9 ? '\t' : gap > piece.size * 0.15 && !line.endsWith(' ') ? ' ' : ''
      line += piece.text
      end = piece.x + piece.width
    }
    return line.split('\t').map((cell) => cell.replace(/\s+/g, ' ').trim()).filter(Boolean).join('\t')
  }).filter(Boolean)
}
