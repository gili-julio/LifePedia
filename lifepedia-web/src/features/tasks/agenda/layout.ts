/** Bloco na grade: início e fim em minutos desde 0h. */
export type Span<T> = { value: T; start: number; end: number }

/** Bloco posicionado: coluna dentro do grupo de blocos que se cruzam (`cluster`) e quantas colunas o grupo tem. */
export type Placed<T> = Span<T> & { col: number; cols: number; cluster: number }

/** Altura mínima de um bloco (tarefa curta continua legível e conta assim para os cruzamentos). */
export const MIN_BLOCK_MINUTES = 20

/**
 * Distribui os blocos do dia em colunas, como numa agenda: os que se cruzam ficam lado a lado dividindo a largura;
 * cada bloco fica na primeira coluna livre do grupo.
 */
export function place<T>(spans: Span<T>[]): Placed<T>[] {
  const sorted = [...spans]
    .map((s) => ({ ...s, end: Math.max(s.end, s.start + MIN_BLOCK_MINUTES) }))
    .sort((a, b) => a.start - b.start || b.end - a.end)
  const result: Placed<T>[] = []
  let group: Placed<T>[] = []
  let columns: number[] = []
  let groupEnd = -1
  let cluster = 0

  const close = () => {
    for (const p of group) p.cols = columns.length
    result.push(...group)
    group = []
    columns = []
    cluster++
  }

  for (const span of sorted) {
    if (group.length && span.start >= groupEnd) close()
    let col = columns.findIndex((end) => end <= span.start)
    if (col < 0) {
      col = columns.length
      columns.push(span.end)
    } else {
      columns[col] = span.end
    }
    group.push({ ...span, col, cols: 0, cluster })
    groupEnd = Math.max(groupEnd, span.end)
  }
  if (group.length) close()
  return result
}

/**
 * Corta grupos largos demais: até `max` colunas visíveis; o que passa vira "+N" na última coluna, ocupando o
 * intervalo dos escondidos.
 */
export function fit<T>(placed: Placed<T>[], max: number) {
  const shown: Placed<T>[] = []
  const hidden = new Map<number, Placed<T>[]>()
  for (const p of placed) {
    if (p.cols <= max) shown.push(p)
    else if (p.col < max - 1) shown.push({ ...p, cols: max })
    else hidden.set(p.cluster, [...(hidden.get(p.cluster) ?? []), p])
  }
  const more = [...hidden.values()].map((items) => ({
    items,
    start: Math.min(...items.map((i) => i.start)),
    end: Math.max(...items.map((i) => i.end)),
    col: max - 1,
    cols: max,
  }))
  return { shown, more }
}
