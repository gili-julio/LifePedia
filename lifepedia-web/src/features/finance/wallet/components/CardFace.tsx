import type { ReactNode } from 'react'
import { BankLogo } from '@/components/bank/BankLogo'
import { formatDate } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import { DEFAULT_CARD_COLOR, invoiceHint, STATUS } from '../labels'
import type { InvoiceStatus, InvoiceSummary } from '../types'

export function StatusBadge({ status, className }: { status: InvoiceStatus; className?: string }) {
  const { label, icon: Icon, tone } = STATUS[status]
  return (
    <span className={cn('inline-flex shrink-0 items-center gap-1 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-semibold', tone, className)}>
      <Icon className="size-3.5" aria-hidden /> {label}
    </span>
  )
}

/** Dias do dia `from` até `to` (yyyy-MM-dd), sem fuso. */
function daysBetween(from: string, to: string) {
  const [a, b] = [from, to].map((d) => Date.UTC(+d.slice(0, 4), +d.slice(5, 7) - 1, +d.slice(8, 10)))
  return Math.round((b - a) / 86_400_000)
}

/** Linha do ciclo: da abertura ao fechamento, com hoje marcado e o vencimento no fim. */
export function CycleBar({ invoice, today, onColor }: { invoice: InvoiceSummary; today: string; onColor?: boolean }) {
  const total = Math.max(1, daysBetween(invoice.opensOn, invoice.closesOn))
  const elapsed = Math.min(total, Math.max(0, daysBetween(invoice.opensOn, today)))
  const percent = invoice.status === 'OPEN' ? Math.round((elapsed / total) * 100) : invoice.status === 'FUTURE' ? 0 : 100
  const muted = onColor ? 'text-white/80' : 'text-muted-foreground'
  return (
    <div className="space-y-1">
      <div className={cn('relative h-2 overflow-hidden rounded-full', onColor ? 'bg-white/25' : 'bg-muted')}
        role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}
        aria-label={`Ciclo da fatura: ${percent}% passado`}>
        <div className={cn('h-full rounded-full', onColor ? 'bg-white' : 'bg-data')} style={{ width: `${percent}%` }} />
      </div>
      <div className={cn('flex justify-between gap-2 text-[11px] tabular-nums', muted)}>
        <span>Abriu {formatDate(invoice.opensOn).slice(0, 5)}</span>
        <span>Fecha {formatDate(invoice.closesOn).slice(0, 5)}</span>
        <span>Vence {formatDate(invoice.dueOn).slice(0, 5)}</span>
      </div>
    </div>
  )
}

type Props = {
  label: string
  bank: { code: string; name: string | null }
  lastDigits: string | null
  color: string | null
  invoice: InvoiceSummary
  today: string
  /** Linha extra embaixo (ex.: dono no grupo) */
  footer?: ReactNode
  className?: string
}

/** Cartão desenhado na cor escolhida, com a fatura do momento e o ciclo. */
export function CardFace({ label, bank, lastDigits, color, invoice, today, footer, className }: Props) {
  const { formatCurrency } = useMoney()
  const base = color ?? DEFAULT_CARD_COLOR
  return (
    <div className={cn('relative flex min-h-48 flex-col justify-between gap-3 overflow-hidden rounded-2xl p-4 text-white shadow-sm', className)}
      style={{ background: `linear-gradient(135deg, ${base}, color-mix(in oklab, ${base} 45%, black))` }}>
      <div className="flex items-start gap-2">
        <BankLogo code={bank.code} name={bank.name} size="md" />
        <div className="min-w-0 flex-1">
          <p className="truncate font-semibold">{label}</p>
          {lastDigits && <p className="text-xs tabular-nums text-white/80">•••• {lastDigits}</p>}
        </div>
        <StatusBadge status={invoice.status} className="bg-white/90 text-neutral-900" />
      </div>
      <div>
        <p className="text-xs text-white/80">Fatura atual</p>
        <p className="text-2xl font-bold tabular-nums">{formatCurrency(invoice.total)}</p>
        <p className="text-xs text-white/90">{invoiceHint(invoice)}</p>
      </div>
      <CycleBar invoice={invoice} today={today} onColor />
      {footer}
    </div>
  )
}
