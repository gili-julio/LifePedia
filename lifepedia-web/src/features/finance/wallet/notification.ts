import { matchBank } from '@/lib/banks'
import { parseAmount, todayIso } from '@/lib/format'

/** O que dá para tirar do aviso de compra do banco; campo não achado fica nulo. */
export type ParsedPurchase = {
  amount: number | null
  description: string | null
  date: string | null
  installments: number
  bankCode: string | null
  lastDigits: string | null
}

const STOP = '(?=\\s+(?:(?:para|no valor|valor|dia|às|com|aprovad[ao]|foi)(?![a-zà-ú])|as\\s+\\d|R\\$|em\\s+\\d|\\d{1,2}\\s*x\\b)|[,.;!\\n]|\\s+-\\s|$)'
const MERCHANT = [
  new RegExp(`\\bem\\s+([A-Za-zÀ-ú*][^,.;!\\n]*?)${STOP}`, 'i'),
  new RegExp(`\\bestabelecimento\\s+([^,.;!\\n]+?)${STOP}`, 'i'),
  /-\s*([A-Za-zÀ-ú*][^-\n]*?)\s+(?:valor|no valor)\b/i,
]
const PREFIX = /^(?:pag\*|pg\s*\*|mp\s*\*|pix\s+|compra\s+)/i

/** Lê o texto colado do aviso de compra (ex.: "Compra de R$ 45,90 aprovada em PADARIA, cartão final 1234"). */
export function parsePurchase(text: string): ParsedPurchase {
  const amountMatch = text.match(/R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2}|\d+,\d{2}|\d+\.\d{2})/i)
  const installmentMatch = text.match(/\b(\d{1,2})\s*x\b/i) ?? text.match(/\bem\s+(\d{1,2})\s+parcelas\b/i)
  const installments = installmentMatch ? Number(installmentMatch[1]) : 1
  const merchant = MERCHANT.map((r) => text.match(r)?.[1]).find((m) => m && !/^cart[aã]o\b|^\d/i.test(m.trim()))
  return {
    amount: amountMatch ? parseAmount(amountMatch[1]) : null,
    description: merchant ? titleCase(merchant.trim().replace(PREFIX, '')).slice(0, 100) : null,
    date: dateOf(text),
    installments: installments >= 2 && installments <= 48 ? installments : 1,
    bankCode: matchBank(text)?.code ?? null,
    lastDigits: text.match(/final\s*(\d{4})/i)?.[1] ?? null,
  }
}

/** "17/11" ou "17/11/2026"; sem ano, o mais recente que não passa de hoje. */
function dateOf(text: string) {
  const match = text.match(/\b(\d{1,2})\/(\d{1,2})(?:\/(\d{2}|\d{4}))?\b/)
  if (!match) return null
  const today = todayIso()
  const day = Number(match[1])
  const month = Number(match[2])
  let year = match[3] ? (match[3].length === 2 ? 2000 + Number(match[3]) : Number(match[3])) : Number(today.slice(0, 4))
  if (month < 1 || month > 12 || day < 1 || day > 31) return null
  let iso = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  if (!match[3] && iso > today) {
    year -= 1
    iso = `${year}${iso.slice(4)}`
  }
  const check = new Date(year, month - 1, day)
  return check.getMonth() === month - 1 && iso <= today ? iso : null
}

const SMALL = new Set(['de', 'da', 'do', 'das', 'dos', 'e', 'em'])

function titleCase(value: string) {
  return value.toLowerCase().split(/\s+/).filter(Boolean)
    .map((word, i) => (i > 0 && SMALL.has(word) ? word : word.charAt(0).toUpperCase() + word.slice(1)))
    .join(' ')
}
