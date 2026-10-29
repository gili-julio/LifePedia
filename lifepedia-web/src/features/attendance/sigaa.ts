import type { Mark } from './types'

/** Situação de um dia no Mapa de Frequências do SIGAA. */
export type SigaaDayStatus = 'PRESENT' | 'ABSENT' | 'UNREGISTERED'

/** O que dá para tirar do texto da página de frequência da turma virtual. */
export type SigaaClass = {
  name: string
  code: string | null
  /** 1 = segunda ... 7 = domingo */
  weekdays: number[]
  startDate: string
  endDate: string | null
  time: string | null
  durationMinutes: number | null
  /** Horário no código do SIGAA (ex.: T12). */
  slots: string | null
  classesPerDay: number
  totalClasses: number | null
  /** Presença mínima exigida (%). */
  minPresence: number
  /** Dias de falta que cabem: aulas que pode faltar ÷ aulas por dia. */
  maxAbsences: number
  days: { date: string; status: SigaaDayStatus }[]
  /** Dias avisados nas notícias como sem aula (só os "Não Registrada"). */
  canceledByNews: string[]
}

/** Início de cada aula de 50 min na UFRN (M1..M6, T1..T6, N1..N4). */
const SLOT_STARTS: Record<string, string[]> = {
  M: ['07:00', '07:50', '08:55', '09:45', '10:50', '11:40'],
  T: ['13:00', '13:50', '14:55', '15:45', '16:50', '17:40'],
  N: ['18:45', '19:35', '20:35', '21:25'],
}
const CLASS_MINUTES = 50
const SMALL_WORDS = new Set(['a', 'à', 'as', 'ao', 'com', 'da', 'das', 'de', 'do', 'dos', 'e', 'em', 'na', 'nas', 'no', 'nos', 'o', 'os', 'ou', 'para', 'por'])
const CANCEL_WORDS = /n[ãa]o haver[áa] aula|sem aula|cancelamento d[ae] aula|aula cancelada|aula suspensa/i

/** Lê o texto copiado (Ctrl + A) da página de frequência; erro com mensagem em pt-BR se não for essa página. */
export function parseSigaa(text: string): SigaaClass {
  const lines = text.split(/\r?\n/).map((l) => l.trim())
  const days = lines.flatMap((line) => {
    const m = line.match(/^(\d{2})\/(\d{2})\/(\d{4})\s+(Presente|N[ãa]o Registrada|(\d+)\s*Falta\(s\))$/i)
    if (!m) return []
    const status: SigaaDayStatus = m[5] ? 'ABSENT' : /^presente$/i.test(m[4]) ? 'PRESENT' : 'UNREGISTERED'
    return [{ date: `${m[3]}-${m[2]}-${m[1]}`, status, absences: m[5] ? Number(m[5]) : 0 }]
  }).sort((a, b) => a.date.localeCompare(b.date))
  if (days.length === 0) {
    throw new Error('Não encontrei o Mapa de Frequências no texto. Confira se copiou a página de Frequência inteira.')
  }

  const year = Number(days.at(-1)?.date.slice(0, 4))
  const title = text.match(/\b([A-Z]{2,4}\d{3,4})\s*-\s*(.+?)\s*-\s*T\d+\s*\(\d{4}\.\d\)/)
  const schedule = text.match(/\b([2-7]{1,6})([MTN])([1-6]{1,6})\b/)
  const slot = schedule ?? text.match(/\b\d{2}\/\d{2}\s+([MTN])([1-6]{1,6})\b/)
  const shift = schedule ? schedule[2] : slot?.[1]
  const numbers = schedule ? schedule[3] : slot?.[2]
  const time = shift && numbers ? slotTime(shift, numbers) : null
  const classesPerDay = numbers?.length ?? Math.max(1, ...days.map((d) => d.absences))
  const total = text.match(/Aulas definidas pela CH do Componente:\s*(\d+)/i)
  const min = text.match(/(\d+(?:[.,]\d+)?)\s*%\s*da carga hor[áa]ria/i)
  const minPresence = min ? Number(min[1].replace(',', '.')) : 75
  const totalClasses = total ? Number(total[1]) : null
  const weekdays = schedule ? [...new Set([...schedule[1]].map((d) => Number(d) - 1))].sort() : usualWeekdays(days.map((d) => d.date))
  const startDate = days[0].date
  const endDate = lastExam(text, year, days.at(-1)!.date)
  const recorded = days.map(({ date, status }) => ({ date, status }))

  return {
    name: title ? titleCase(title[2]) : 'Matéria do SIGAA',
    code: title?.[1] ?? null,
    weekdays,
    startDate,
    endDate,
    time: time?.start ?? null,
    durationMinutes: time?.minutes ?? null,
    slots: shift && numbers ? `${shift}${numbers}` : null,
    classesPerDay,
    totalClasses,
    minPresence,
    maxAbsences: totalClasses === null ? 0 : Math.floor((totalClasses * (100 - minPresence)) / 100 / classesPerDay),
    days: recorded,
    canceledByNews: newsCancellations(lines, new Set(recorded.filter((d) => d.status === 'UNREGISTERED').map((d) => d.date))),
  }
}

type MarkOptions = {
  weekdays: number[]
  startDate: string
  endDate: string | null
  today: string
  /** "Não Registrada" avisada nas notícias vira "Sem aula". */
  useNews: boolean
  /** Dia de aula que nem aparece no Mapa (ex.: feriado) vira "Sem aula". */
  useMissing: boolean
}

/**
 * Marcações para gravar: só os dias que batem com os dias da semana e o período escolhidos (e até hoje).
 * "Não Registrada" fica pendente, salvo as opções de "Sem aula".
 */
export function sigaaMarks(parsed: SigaaClass, options: MarkOptions) {
  const news = new Set(options.useNews ? parsed.canceledByNews : [])
  const marks: { date: string; status: Mark }[] = []
  let skipped = 0
  for (const day of parsed.days) {
    const status: Mark | null = day.status === 'PRESENT' ? 'PRESENT' : day.status === 'ABSENT' ? 'ABSENT'
      : news.has(day.date) ? 'CANCELED' : null
    if (!status) continue
    const fits = options.weekdays.includes(weekdayOf(day.date)) && day.date >= options.startDate
      && (!options.endDate || day.date <= options.endDate) && day.date <= options.today
    if (fits) marks.push({ date: day.date, status })
    else skipped++
  }
  if (options.useMissing) {
    for (const date of sigaaMissing(parsed, options)) marks.push({ date, status: 'CANCELED' })
  }
  return { marks, skipped }
}

/** Dias da semana escolhidos, do início até a última data do Mapa, que não aparecem nele (ex.: feriados). */
export function sigaaMissing(parsed: SigaaClass, options: Pick<MarkOptions, 'weekdays' | 'startDate' | 'endDate' | 'today'>) {
  const listed = new Set(parsed.days.map((d) => d.date))
  const candidates = [parsed.days.at(-1)?.date ?? options.startDate, options.today, options.endDate ?? options.today]
  const until = candidates.sort()[0]
  const missing: string[] = []
  for (let date = options.startDate; date <= until; date = nextDay(date)) {
    if (!listed.has(date) && options.weekdays.includes(weekdayOf(date))) missing.push(date)
  }
  return missing
}

/** "Não Registrada" nos dias escolhidos (o que fica pendente sem as opções de "Sem aula"). */
export function sigaaUnregistered(parsed: SigaaClass, weekdays: number[]) {
  return parsed.days.filter((d) => d.status === 'UNREGISTERED' && weekdays.includes(weekdayOf(d.date))).map((d) => d.date)
}

/** 1 = segunda ... 7 = domingo. */
export function weekdayOf(iso: string) {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d).getDay() || 7
}

function slotTime(shift: string, numbers: string) {
  const starts = SLOT_STARTS[shift]
  const first = Number(numbers[0])
  const last = Number(numbers.at(-1))
  if (!starts || !starts[first - 1] || !starts[last - 1]) return null
  const minutes = toMinutes(starts[last - 1]) + CLASS_MINUTES - toMinutes(starts[first - 1])
  return { start: starts[first - 1], minutes }
}

/** Dias da semana que se repetem (aula avulsa num outro dia fica de fora). */
function usualWeekdays(dates: string[]) {
  const count = new Map<number, number>()
  for (const date of dates) count.set(weekdayOf(date), (count.get(weekdayOf(date)) ?? 0) + 1)
  const top = Math.max(...count.values())
  return [...count].filter(([, n]) => n >= Math.max(2, top / 4)).map(([d]) => d).sort()
}

/** Última data das avaliações (dd/mm, sem ano), se for depois da última aula registrada. */
function lastExam(text: string, year: number, lastDay: string) {
  const section = text.split(/Avalia[çc][õo]es/i)[1]?.split(/Mensagens dos F[óo]runs|UFRN - SIGAA/i)[0] ?? ''
  const dates = [...section.matchAll(/(\d{2})\/(\d{2})\s+[MTN]\d/g)].map((m) => `${year}-${m[2]}-${m[1]}`).sort()
  const last = dates.at(-1)
  return last && last > lastDay ? last : null
}

/** Notícias do tipo "Não haverá aula..." ou "Cancelamento da aula - 16/09": a data citada, ou a da notícia. */
function newsCancellations(lines: string[], unregistered: Set<string>) {
  const found = new Set<string>()
  lines.forEach((line, i) => {
    const posted = line.match(/^(\d{2})\/(\d{2})\/(\d{4})\s+\d{2}:\d{2}$/)
    const title = lines.slice(i + 1).find((l) => l.length > 0)
    if (!posted || !title || !CANCEL_WORDS.test(title)) return
    const cited = [...title.matchAll(/(\d{2})\/(\d{2})(?:\/(\d{4}))?/g)].map((m) => `${m[3] ?? posted[3]}-${m[2]}-${m[1]}`)
    for (const date of cited.length ? cited : [`${posted[3]}-${posted[2]}-${posted[1]}`]) {
      if (unregistered.has(date)) found.add(date)
    }
  })
  return [...found].sort()
}

function titleCase(name: string) {
  return name.toLowerCase().split(/\s+/).map((word, i) =>
    i > 0 && SMALL_WORDS.has(word) ? word : /^[ivx]+$/.test(word) ? word.toUpperCase() : word.charAt(0).toUpperCase() + word.slice(1),
  ).join(' ')
}

function nextDay(iso: string) {
  const [y, m, d] = iso.split('-').map(Number)
  const date = new Date(y, m - 1, d + 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function toMinutes(time: string) {
  const [h, m] = time.split(':').map(Number)
  return h * 60 + m
}
