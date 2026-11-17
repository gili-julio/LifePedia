import { matchRanges } from '@/lib/search-text'

/** Texto com as palavras da busca destacadas (sem diferenciar acento e maiúscula). */
export function Highlight({ text, query }: { text: string; query: string }) {
  const ranges = query ? matchRanges(text, query) : []
  if (!ranges.length) return <>{text}</>
  const parts: React.ReactNode[] = []
  let cursor = 0
  ranges.forEach(([start, end], i) => {
    if (start > cursor) parts.push(text.slice(cursor, start))
    parts.push(<mark key={i} className="rounded-sm bg-primary/15 px-0.5 text-foreground">{text.slice(start, end)}</mark>)
    cursor = end
  })
  if (cursor < text.length) parts.push(text.slice(cursor))
  return <>{parts}</>
}
