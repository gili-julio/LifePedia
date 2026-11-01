import { LandmarkIcon } from 'lucide-react'
import { bankLabel, BANK_KIND_LABEL, OTHER_BANK, type BankRef } from '@/lib/banks'
import { cn } from '@/lib/utils'

const LOGOS = import.meta.glob<string>('/src/assets/banks/*.svg', { eager: true, query: '?url', import: 'default' })

/** Logo branco: precisa de fundo da marca em vez do círculo branco. */
const TILE: Record<string, string> = { IFOOD: '#ea1d2c' }

const SIZE = {
  xs: 'size-4 text-[8px]',
  sm: 'size-6 text-[9px]',
  md: 'size-9 text-xs',
}

type Props = {
  /** Nulo = sem banco */
  code: string | null
  /** Para as iniciais de banco fora do catálogo */
  name?: string | null
  size?: keyof typeof SIZE
  /** Sem texto ao lado: vira o nome acessível do logo */
  title?: string
  className?: string
}

/** Logo do banco num círculo; fora do catálogo, as iniciais. O nome sempre aparece em texto ao lado. */
export function BankLogo({ code, name, size = 'sm', title, className }: Props) {
  const label = title ? { role: 'img', title, 'aria-label': title } : { 'aria-hidden': true }
  const logo = code && code !== OTHER_BANK ? LOGOS[`/src/assets/banks/${code.toLowerCase().replace('_', '-')}.svg`] : undefined
  const base = cn('inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full', SIZE[size], className)
  if (logo) {
    return (
      <span {...label} className={cn(base, 'bg-white ring-1 ring-black/10 dark:ring-white/10')}
        style={code && TILE[code] ? { background: TILE[code] } : undefined}>
        <img src={logo} alt="" className="size-[86%] object-contain" loading="lazy" />
      </span>
    )
  }
  if (code) {
    const initials = (name ?? '?').split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase()
    return <span {...label} className={cn(base, 'bg-muted font-semibold text-muted-foreground')}>{initials}</span>
  }
  return <span {...label} className={cn(base, 'bg-muted text-muted-foreground')}><LandmarkIcon className="size-[60%]" /></span>
}

/** Só o logo, com o nome no título: para linhas sem espaço. */
export function BankMark({ bank, className }: { bank: BankRef; className?: string }) {
  return (
    <BankLogo code={bank.code} name={bank.name} size="xs" className={className}
      title={`${bankLabel(bank)}, ${BANK_KIND_LABEL[bank.kind].toLowerCase()}`} />
  )
}

/** Logo + nome + conta ou cartão. */
export function BankBadge({ bank, className }: { bank: BankRef | null; className?: string }) {
  if (!bank) return <span className={cn('text-muted-foreground', className)}>-</span>
  return (
    <span className={cn('inline-flex min-w-0 items-center gap-2', className)}>
      <BankLogo code={bank.code} name={bank.name} />
      <span className="min-w-0">
        <span className="block truncate">{bankLabel(bank)}</span>
        <span className="block truncate text-xs text-muted-foreground">{BANK_KIND_LABEL[bank.kind]}</span>
      </span>
    </span>
  )
}
