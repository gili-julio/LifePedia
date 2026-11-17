import { CreditCardIcon } from 'lucide-react'
import { Link } from 'react-router'
import { BankLogo } from '@/components/bank/BankLogo'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useMoney } from '@/lib/hide-values'
import { useWallet } from '../api'
import { DEFAULT_CARD_COLOR, invoiceHint } from '../labels'
import { StatusBadge } from './CardFace'

/** Resumo das faturas na visão geral: um item por cartão, com valor, situação e quando fecha. */
export function WalletStrip() {
  const { data } = useWallet()
  const { formatCurrency } = useMoney()
  if (!data) return null
  if (!data.cards.length) {
    return (
      <Card className="border-dashed">
        <CardContent className="flex flex-wrap items-center gap-3">
          <CreditCardIcon className="size-5 text-muted-foreground" />
          <p className="min-w-0 flex-1 text-sm text-muted-foreground">
            Cadastre seus cartões para acompanhar a fatura aberta durante o mês.
          </p>
          <Button size="sm" variant="outline" asChild><Link to="/financeiro/carteira">Abrir a Carteira</Link></Button>
        </CardContent>
      </Card>
    )
  }
  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-2">
        <div className="space-y-1">
          <CardTitle>Faturas dos cartões</CardTitle>
          <CardDescription>Abertas somam {formatCurrency(data.openTotal)}{data.toPay > 0 ? `; ${formatCurrency(data.toPay)} fechadas a pagar` : ''}.</CardDescription>
        </div>
        <Button size="sm" variant="ghost" asChild><Link to="/financeiro/carteira">Carteira</Link></Button>
      </CardHeader>
      <CardContent className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
        {data.cards.map((card) => {
          const invoice = card.pending ?? card.current
          return (
            <Link key={card.id} to={`/financeiro/carteira/${card.id}${card.pending ? `?fatura=${card.pending.month}` : ''}`}
              className="flex items-center gap-3 rounded-xl border p-3 hover:bg-muted/50"
              style={{ borderLeft: `4px solid ${card.color ?? DEFAULT_CARD_COLOR}` }}>
              <BankLogo code={card.bank.code} name={card.bank.name} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{card.label}</p>
                <p className="truncate text-xs text-muted-foreground">{invoiceHint(invoice)}</p>
              </div>
              <div className="flex shrink-0 flex-col items-end gap-1">
                <span className="text-sm font-semibold tabular-nums">{formatCurrency(invoice.total)}</span>
                <StatusBadge status={invoice.status} />
              </div>
            </Link>
          )
        })}
      </CardContent>
    </Card>
  )
}
