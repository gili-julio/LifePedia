import { AlertTriangleIcon, ClipboardPasteIcon, CreditCardIcon, LandmarkIcon, PencilIcon, PlusIcon, ReceiptIcon, ZapIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { BankLogo } from '@/components/bank/BankLogo'
import { PageHeader } from '@/components/PageHeader'
import { StatCard } from '@/components/StatCard'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import { useWallet } from '../api'
import { AccountDialog } from '../components/AccountDialog'
import { CardDialog } from '../components/CardDialog'
import { CardFace } from '../components/CardFace'
import { QuickPurchaseDialog } from '../components/QuickPurchaseDialog'
import { invoiceHint, invoiceName } from '../labels'
import type { AccountView, CardView } from '../types'

type AccountEditor = { open: boolean; account?: AccountView; onlyBalance?: boolean }

/** Cartões com a fatura do momento e contas com o saldo projetado. */
export function WalletPage() {
  const { data } = useWallet()
  const { formatCurrency } = useMoney()
  const [params, setParams] = useSearchParams()
  const [cardOpen, setCardOpen] = useState(false)
  const [account, setAccount] = useState<AccountEditor>({ open: false })
  const quick = params.get('rapido')

  function setQuick(value: string | null) {
    setParams((current) => {
      const next = new URLSearchParams(current)
      if (value) next.set('rapido', value)
      else next.delete('rapido')
      return next
    }, { replace: true })
  }

  const empty = data && !data.cards.length && !data.accounts.length
  return (
    <div className="space-y-6">
      <PageHeader
        hideValues
        title="Carteira"
        description="Faturas dos cartões e saldo das contas, acompanhados durante o mês"
        actions={[
          { label: 'Lançamento rápido', icon: ZapIcon, primary: true, onSelect: () => setQuick('1') },
          { label: 'Colar aviso do banco', icon: ClipboardPasteIcon, onSelect: () => setQuick('colar') },
          { label: 'Novo cartão', icon: CreditCardIcon, onSelect: () => setCardOpen(true) },
          { label: 'Nova conta', icon: LandmarkIcon, onSelect: () => setAccount({ open: true }) },
        ]}
      />

      {!data ? <Skeleton className="h-64" /> : empty ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
            <CreditCardIcon className="size-10 text-muted-foreground" />
            <div className="space-y-1">
              <p className="font-semibold">Acompanhe a fatura enquanto ela está aberta</p>
              <p className="max-w-md text-sm text-muted-foreground">
                Cadastre seus cartões com o dia do fechamento e do vencimento, e suas contas com o saldo de hoje.
              </p>
            </div>
            <div className="flex flex-wrap justify-center gap-2">
              <Button onClick={() => setCardOpen(true)}><PlusIcon /> Cadastrar cartão</Button>
              <Button variant="outline" onClick={() => setAccount({ open: true })}><PlusIcon /> Cadastrar conta</Button>
            </div>
          </CardContent>
        </Card>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2">
            <StatCard label="Faturas abertas" tone="expense" icon={ReceiptIcon} hint="Soma do que já entrou nas faturas em aberto.">
              {formatCurrency(data.openTotal)}
            </StatCard>
            <StatCard label="Fechadas a pagar" tone="data" icon={AlertTriangleIcon}
              hint={data.toPay > 0 ? 'Faturas que já fecharam e ainda não foram marcadas como pagas.' : 'Nada fechado esperando pagamento.'}>
              {formatCurrency(data.toPay)}
            </StatCard>
          </div>

          <section className="space-y-3" aria-labelledby="wallet-cards">
            <div className="flex items-center justify-between gap-2">
              <h2 id="wallet-cards" className="font-semibold">Cartões</h2>
              <Button size="sm" variant="ghost" onClick={() => setCardOpen(true)}><PlusIcon /> Cartão</Button>
            </div>
            {!data.cards.length ? (
              <p className="rounded-xl border border-dashed p-4 text-sm text-muted-foreground">
                Nenhum cartão. <button type="button" className="font-medium text-foreground underline underline-offset-4" onClick={() => setCardOpen(true)}>Cadastrar cartão</button>
              </p>
            ) : (
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
                {data.cards.map((card) => <CardTile key={card.id} card={card} today={data.today} />)}
              </div>
            )}
          </section>

          <section className="space-y-3" aria-labelledby="wallet-accounts">
            <div className="flex items-center justify-between gap-2">
              <h2 id="wallet-accounts" className="font-semibold">Contas</h2>
              <Button size="sm" variant="ghost" onClick={() => setAccount({ open: true })}><PlusIcon /> Conta</Button>
            </div>
            {!data.accounts.length ? (
              <p className="rounded-xl border border-dashed p-4 text-sm text-muted-foreground">
                Nenhuma conta. Cadastre para ver o saldo projetado até o fim do mês, já com as faturas.{' '}
                <button type="button" className="font-medium text-foreground underline underline-offset-4" onClick={() => setAccount({ open: true })}>Cadastrar conta</button>
              </p>
            ) : (
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
                {data.accounts.map((a) => (
                  <AccountTile key={a.id} account={a}
                    onBalance={() => setAccount({ open: true, account: a, onlyBalance: true })}
                    onEdit={() => setAccount({ open: true, account: a })} />
                ))}
              </div>
            )}
          </section>
        </>
      )}

      <CardDialog open={cardOpen} onOpenChange={setCardOpen} />
      <AccountDialog open={account.open} onOpenChange={(open) => setAccount((a) => ({ ...a, open }))}
        account={account.account} onlyBalance={account.onlyBalance} />
      <QuickPurchaseDialog open={!!quick} onOpenChange={(open) => !open && setQuick(null)} paste={quick === 'colar'} />
    </div>
  )
}

function CardTile({ card, today }: { card: CardView; today: string }) {
  const { formatCurrency } = useMoney()
  return (
    <div className="space-y-2">
      <Link to={`/financeiro/carteira/${card.id}`} className="block rounded-2xl focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
        aria-label={`${card.label}: ${invoiceName(card.current.month)}, ${invoiceHint(card.current)}`}>
        <CardFace label={card.label} bank={card.bank} lastDigits={card.lastDigits} color={card.color} invoice={card.current} today={today}
          className="transition-transform hover:-translate-y-0.5 motion-reduce:transition-none" />
      </Link>
      {card.pending && (
        <Link to={`/financeiro/carteira/${card.id}?fatura=${card.pending.month}`}
          className={cn('flex items-center gap-2 rounded-xl border px-3 py-2 text-sm',
            card.pending.status === 'OVERDUE' ? 'border-expense/40 bg-expense/10' : 'border-amber-500/40 bg-amber-500/10')}>
          <AlertTriangleIcon className="size-4 shrink-0" aria-hidden />
          <span className="min-w-0 flex-1">
            {card.pending.status === 'OVERDUE' ? 'Fatura vencida' : 'Fatura fechada'} de {formatCurrency(card.pending.total)}: {invoiceHint(card.pending).toLowerCase()}
          </span>
        </Link>
      )}
      {card.limit && (
        <div className="space-y-1 px-1">
          <div className="h-1.5 overflow-hidden rounded-full bg-muted" role="progressbar" aria-valuemin={0} aria-valuemax={100}
            aria-valuenow={Math.round(Math.min(100, (card.limit.used / card.limit.limit) * 100))} aria-label="Limite usado">
            <div className="h-full rounded-full bg-data" style={{ width: `${Math.min(100, (card.limit.used / card.limit.limit) * 100)}%` }} />
          </div>
          <p className="flex justify-between gap-2 text-xs text-muted-foreground">
            <span>Limite livre <span className="font-medium text-foreground tabular-nums">{formatCurrency(card.limit.available)}</span></span>
            <span className="tabular-nums">de {formatCurrency(card.limit.limit)}</span>
          </p>
        </div>
      )}
    </div>
  )
}

function AccountTile({ account, onBalance, onEdit }: { account: AccountView; onBalance: () => void; onEdit: () => void }) {
  const { formatCurrency } = useMoney()
  return (
    <Card size="sm">
      <CardContent className="space-y-3">
        <div className="flex items-center gap-3">
          <BankLogo code={account.bank.code} name={account.bank.name} size="md" />
          <div className="min-w-0 flex-1">
            <p className="truncate font-semibold">{account.label}</p>
            <p className="text-xs text-muted-foreground">Saldo informado em {formatDate(account.balanceOn)}: {formatCurrency(account.balance)}</p>
          </div>
          <Button size="icon" variant="ghost" aria-label={`Editar ${account.label}`} onClick={onEdit}><PencilIcon /></Button>
        </div>
        <dl className="grid grid-cols-2 gap-2 text-sm">
          <div>
            <dt className="text-xs text-muted-foreground">Hoje (estimado)</dt>
            <dd className={cn('text-lg font-semibold tabular-nums', account.today < 0 && 'text-destructive')}>{formatCurrency(account.today)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">No fim do mês</dt>
            <dd className={cn('text-lg font-semibold tabular-nums', account.endOfMonth < 0 && 'text-destructive')}>{formatCurrency(account.endOfMonth)}</dd>
          </div>
        </dl>
        {account.pendingInvoices > 0 && (
          <p className="text-xs text-muted-foreground">Inclui {formatCurrency(account.pendingInvoices)} de faturas a pagar até o fim do mês.</p>
        )}
        <Button size="sm" variant="outline" className="w-full" onClick={onBalance}>Atualizar saldo</Button>
      </CardContent>
    </Card>
  )
}
