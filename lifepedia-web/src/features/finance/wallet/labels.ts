import { AlertTriangleIcon, CalendarClockIcon, CheckCircle2Icon, CircleDotIcon, LockIcon, type LucideIcon } from 'lucide-react'
import { formatDate, formatMonth } from '@/lib/format'
import type { InvoiceStatus, InvoiceSummary } from './types'

/** Texto, ícone e tom de cada situação; sempre com texto, nunca só a cor. */
export const STATUS: Record<InvoiceStatus, { label: string; icon: LucideIcon; tone: string }> = {
  FUTURE: { label: 'Próxima', icon: CalendarClockIcon, tone: 'bg-muted text-muted-foreground' },
  OPEN: { label: 'Aberta', icon: CircleDotIcon, tone: 'bg-data/15 text-data' },
  CLOSED: { label: 'Fechada', icon: LockIcon, tone: 'bg-amber-500/15 text-amber-700 dark:text-amber-300' },
  PAID: { label: 'Paga', icon: CheckCircle2Icon, tone: 'bg-income/15 text-income' },
  OVERDUE: { label: 'Vencida', icon: AlertTriangleIcon, tone: 'bg-expense/15 text-expense' },
}

/** Cor padrão do cartão sem cor escolhida. */
export const DEFAULT_CARD_COLOR = '#4a3aa7'

const day = (iso: string) => formatDate(iso).slice(0, 5)

function inDays(days: number) {
  if (days === 0) return 'hoje'
  if (days === 1) return 'amanhã'
  return `em ${days} dias`
}

/** O que importa agora: quando fecha, quando vence ou quando foi paga. */
export function invoiceHint(invoice: InvoiceSummary) {
  switch (invoice.status) {
    case 'FUTURE':
      return `Abre em ${day(invoice.opensOn)}`
    case 'OPEN':
      return `Fecha ${inDays(invoice.daysToClose)} (${day(invoice.closesOn)}), vence ${day(invoice.dueOn)}`
    case 'CLOSED':
      return `Vence ${inDays(invoice.daysToDue)} (${day(invoice.dueOn)})`
    case 'PAID':
      return invoice.paidOn ? `Paga em ${day(invoice.paidOn)}` : 'Paga'
    case 'OVERDUE':
      return `Venceu em ${day(invoice.dueOn)}`
  }
}

/** "Fatura de dezembro de 2026" */
export function invoiceName(month: string) {
  return `Fatura de ${formatMonth(month).toLowerCase()}`
}
