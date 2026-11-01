import { BankLogo } from '@/components/bank/BankLogo'
import { useMoney } from '@/lib/hide-values'
import type { BankLine } from '../types'

const percent = new Intl.NumberFormat('pt-BR', { style: 'percent', maximumFractionDigits: 0 })

type Props = {
  /** Na ordem da API (maiores primeiro, sem banco por último) */
  banks: BankLine[]
  /** Despesas do mês em todos os bancos, da API */
  total: number
  /** Mostra só os primeiros */
  limit?: number
}

/** Quanto saiu de cada banco no mês, com a parte na conta e no cartão. */
export function BankList({ banks, total, limit }: Props) {
  const { formatCurrency } = useMoney()
  const spent = banks.filter((b) => b.expense > 0)
  if (!spent.length) return <p className="py-6 text-center text-sm text-muted-foreground">Nenhum gasto neste mês.</p>
  const max = Math.max(...spent.map((b) => b.expense))
  return (
    <ul className="space-y-4">
      {spent.slice(0, limit).map((b) => (
        <li key={b.key} className="space-y-1.5">
          <div className="flex items-center justify-between gap-2 text-sm">
            <span className="flex min-w-0 items-center gap-2">
              <BankLogo code={b.code} name={b.name} />
              <span className="min-w-0">
                <span className="block truncate font-medium">{b.name}</span>
                {b.code && (
                  <span className="block text-xs text-muted-foreground tabular-nums">
                    {[b.account > 0 && `Conta ${formatCurrency(b.account)}`, b.card > 0 && `Cartão ${formatCurrency(b.card)}`].filter(Boolean).join(' · ')}
                  </span>
                )}
              </span>
            </span>
            <span className="shrink-0 text-right">
              <span className="block font-medium tabular-nums">{formatCurrency(b.expense)}</span>
              <span className="block text-xs text-muted-foreground tabular-nums">{percent.format(b.expense / total)}</span>
            </span>
          </div>
          <div className="h-1.5 rounded-full bg-data/80" style={{ width: `${(b.expense / max) * 100}%` }} aria-hidden />
        </li>
      ))}
    </ul>
  )
}
