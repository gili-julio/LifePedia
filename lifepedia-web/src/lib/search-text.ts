/** Busca no navegador: sem acento e sem maiúscula, com tolerância a um erro de digitação em palavras longas. */

const MARKS = /\p{M}/gu

/** Mesmo tamanho do texto original (cada letra acentuada vira a letra sem acento). */
export function normalizeText(value: string) {
  return value.normalize('NFD').replace(MARKS, '').toLowerCase()
}

export function searchWords(query: string) {
  return [...new Set(normalizeText(query).split(/\s+/).filter(Boolean))].slice(0, 8)
}

/** Todas as palavras da busca aparecem em algum dos textos (ou parecidas, nas de 5 letras ou mais). */
export function matchesSearch(query: string, ...texts: (string | null | undefined)[]) {
  const words = searchWords(query)
  if (!words.length) return true
  const haystack = normalizeText(texts.filter(Boolean).join(' '))
  const tokens = haystack.split(/[^\p{L}\p{N}]+/u).filter(Boolean)
  return words.every((word) => haystack.includes(word) || (word.length >= 5 && tokens.some((t) => close(word, t))))
}

/** Trechos `[início, fim)` do texto que batem com as palavras, para destacar. */
export function matchRanges(text: string, query: string): [number, number][] {
  const normalized = normalizeText(text)
  if (normalized.length !== text.length) return []
  const ranges: [number, number][] = []
  for (const word of searchWords(query)) {
    let at = normalized.indexOf(word)
    while (at >= 0 && word) {
      ranges.push([at, at + word.length])
      at = normalized.indexOf(word, at + word.length)
    }
  }
  ranges.sort((a, b) => a[0] - b[0])
  return ranges.reduce<[number, number][]>((merged, range) => {
    const last = merged.at(-1)
    if (last && range[0] <= last[1]) last[1] = Math.max(last[1], range[1])
    else merged.push([...range])
    return merged
  }, [])
}

/** Até 1 erro (2 em palavras de 8 letras ou mais), comparando com a palavra do texto inteira ou com o começo dela. */
function close(word: string, token: string) {
  const limit = word.length >= 8 ? 2 : 1
  if (token.length < word.length - limit) return false
  return distance(word, token) <= limit || distance(word, token.slice(0, word.length)) <= limit
}

/** Distância de edição contando a troca de duas letras vizinhas como um erro. */
function distance(a: string, b: string) {
  const rows = Array.from({ length: a.length + 1 }, (_, i) => [i, ...Array<number>(b.length).fill(0)])
  for (let j = 1; j <= b.length; j++) rows[0][j] = j
  for (let i = 1; i <= a.length; i++) {
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1
      rows[i][j] = Math.min(rows[i - 1][j] + 1, rows[i][j - 1] + 1, rows[i - 1][j - 1] + cost)
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) rows[i][j] = Math.min(rows[i][j], rows[i - 2][j - 2] + 1)
    }
  }
  return rows[a.length][b.length]
}
