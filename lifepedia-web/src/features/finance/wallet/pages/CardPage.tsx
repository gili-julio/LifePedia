import {
  ArrowLeftIcon, CheckCircle2Icon, ChevronLeftIcon, ChevronRightIcon, ClipboardPasteIcon, GaugeIcon, ListIcon, PencilIcon,
  RefreshCwIcon, RepeatIcon, Trash2Icon, TrendingUpIcon, Undo2Icon, ZapIcon,
} from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { PageHeader, type PageAction } from '@/components/PageHeader'
import { StatCard } from '@/components/StatCard'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { addMonths, formatDate, formatMonth, isYearMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import { ColorDot } from '../../components/CategoryBadge'
import { TypeIcon } from '../../components/TypeIcon'
import { useCard, useDeleteCard, usePayInvoice } from '../api'
import { CardDialog } from '../components/CardDialog'
import { CardFace, CycleBar, StatusBadge } from '../components/CardFace'
import { InvoiceChart } from '../components/InvoiceChart'
import { PayDialog } from '../components/PayDialog'
import { QuickPurchaseDialog } from '../components/QuickPurchaseDialog'
import { RecalculateDialog } from '../components/RecalculateDialog'
import { invoiceHint, invoiceName } from '../labels'
import type { InvoiceDetail, InvoiceItem } from '../types'

type Dialogs = 'edit' | 'pay' | 'recalculate' | 'delete' | 'quick' | 'paste' | null

/** Uma fatura do cartão: valor, situação, previsão, itens e as faturas vizinhas. */
export function CardPage() {
  const { cardId = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const requested = params.get('fatura')
  const month = isYearMonth(requested) ? requested : null
  const { data, isError } = useCard(cardId, month)
  const { formatCurrency } = useMoney()
  const [dialog, setDialog] = useState<Dialogs>(null)
  const pay = usePayInvoice()
  const remove = useDeleteCard()

  function show(next: string) {
    setParams((current) => {
      const copy = new URLSearchParams(current)
      copy.set('fatura', next)
      return copy
    }, { replace: true })
  }

  if (isError) {
    return (
      <div className="space-y-4">
        <PageHeader title="Cartão não encontrado" />
        <Button variant="outline" asChild><Link to="/financeiro/carteira"><ArrowLeftIcon /> Voltar para a Carteira</Link></Button>
      </div>
    )
  }
  if (!data) return <Skeleton className="h-96" />

  const { card, invoice } = data
  const summary = invoice.summary
  const today = new Date().toLocaleDateString('sv-SE')
  const canPay = summary.status === 'CLOSED' || summary.status === 'OVERDUE' || summary.status === 'OPEN'

  async function unpay() {
    try {
      await pay.mutateAsync({ cardId: card.id, month: summary.month, paid: false })
      toast.success('Pagamento desfeito.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível desfazer.')
    }
  }

  const actions: PageAction[] = [
    { label: 'Lançamento rápido', icon: ZapIcon, primary: true, onSelect: () => setDialog('quick') },
    summary.status === 'PAID' && summary.paidOn
      ? { label: 'Desfazer pagamento', icon: Undo2Icon, onSelect: unpay }
      : { label: 'Paguei', icon: CheckCircle2Icon, onSelect: () => setDialog('pay'), ...(canPay ? {} : { secondary: true }) },
    { label: 'Colar aviso do banco', icon: ClipboardPasteIcon, secondary: true, onSelect: () => setDialog('paste') },
    { label: 'Editar cartão', icon: PencilIcon, onSelect: () => setDialog('edit') },
    { label: 'Recalcular faturas', icon: RefreshCwIcon, secondary: true, onSelect: () => setDialog('recalculate') },
    { label: 'Excluir cartão', icon: Trash2Icon, secondary: true, destructive: true, onSelect: () => setDialog('delete') },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        hideValues
        title={card.label}
        description={`${invoiceName(summary.month)}. Fecha dia ${card.closingDay}, vence dia ${card.dueDay}.`}
        toolbar={
          <div className="flex items-center gap-1">
            <Button size="icon" variant="outline" aria-label="Fatura anterior" onClick={() => show(addMonths(summary.month, -1))}><ChevronLeftIcon /></Button>
            <span className="min-w-24 text-center text-sm font-medium">{formatMonth(summary.month, 'short')}</span>
            <Button size="icon" variant="outline" aria-label="Próxima fatura" onClick={() => show(addMonths(summary.month, 1))}><ChevronRightIcon /></Button>
          </div>
        }
        actions={actions}
      />

      <div className="grid gap-4 lg:grid-cols-[minmax(0,22rem)_1fr]">
        <CardFace label={card.label} bank={card.bank} lastDigits={card.lastDigits} color={card.color} invoice={summary} today={today} />
        <div className="grid content-start gap-4 sm:grid-cols-2">
          <StatCard label="Previsão de fechamento" tone="data" icon={TrendingUpIcon}
            hint={invoice.projection.projected == null
              ? (summary.status === 'OPEN' ? 'Aparece a partir do 3º dia do ciclo.' : 'Só para a fatura aberta.')
              : `Pelo ritmo de ${invoice.projection.elapsedDays} de ${invoice.projection.cycleDays} dias do ciclo.`}>
            {invoice.projection.projected == null ? '-' : formatCurrency(invoice.projection.projected)}
          </StatCard>
          <StatCard label="Média das últimas" tone="data" icon={RepeatIcon}
            hint={invoice.projection.average == null ? 'Sem faturas anteriores com valor.' : compare(summary.total, invoice.projection.average)}>
            {invoice.projection.average == null ? '-' : formatCurrency(invoice.projection.average)}
          </StatCard>
          {card.budget != null && (
            <StatCard label="Meta da fatura" tone={(invoice.projection.budgetPercent ?? 0) >= 100 ? 'expense' : 'data'} icon={GaugeIcon}
              hint={`${invoice.projection.budgetPercent ?? 0}% de ${formatCurrency(card.budget)}`}>
              <Meter percent={invoice.projection.budgetPercent ?? 0} />
            </StatCard>
          )}
          {card.limit && (
            <StatCard label="Limite livre" tone="data" icon={GaugeIcon}
              hint={`Usado ${formatCurrency(card.limit.used)} de ${formatCurrency(card.limit.limit)}, com parcelas futuras.`}>
              {formatCurrency(card.limit.available)}
            </StatCard>
          )}
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex flex-wrap items-center gap-2">{invoiceName(summary.month)} <StatusBadge status={summary.status} /></CardTitle>
          <CardDescription>{invoiceHint(summary)}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <CycleBar invoice={summary} today={today} />
          <Breakdown invoice={invoice} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Fatura a fatura</CardTitle>
          <CardDescription>6 anteriores e 6 próximas (parcelas e fixos já lançados). Toque numa barra para abrir a fatura.</CardDescription>
        </CardHeader>
        <CardContent>
          <InvoiceChart months={[...data.history, { month: summary.month, total: summary.total, status: summary.status }, ...data.upcoming]}
            selected={summary.month} budget={card.budget} onSelect={show} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-2">
          <div className="space-y-1">
            <CardTitle>Itens da fatura</CardTitle>
            <CardDescription>
              {invoice.count ? `${invoice.checked} de ${invoice.count} conferidos com a fatura do banco.` : 'Nenhum lançamento nesta fatura.'}
            </CardDescription>
          </div>
          <Button size="sm" variant="outline" asChild>
            <Link to={`/financeiro/lancamentos?banco=${encodeURIComponent(`${card.key}|CARD`)}&mes=${summary.month}`}><ListIcon /> Ver nos lançamentos</Link>
          </Button>
        </CardHeader>
        <CardContent>
          {!invoice.items.length ? (
            <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed p-6 text-center text-sm text-muted-foreground">
              Nada nesta fatura ainda.
              <Button size="sm" onClick={() => setDialog('quick')}><ZapIcon /> Lançamento rápido</Button>
            </div>
          ) : (
            <ul className="divide-y">{invoice.items.map((item, i) => <ItemRow key={item.transactionId ?? `${item.recurringId}-${i}`} item={item} />)}</ul>
          )}
        </CardContent>
      </Card>

      <CardDialog open={dialog === 'edit'} onOpenChange={(open) => setDialog(open ? 'edit' : null)} card={card} />
      <PayDialog open={dialog === 'pay'} onOpenChange={(open) => setDialog(open ? 'pay' : null)} card={card} invoice={summary} />
      <RecalculateDialog open={dialog === 'recalculate'} onOpenChange={(open) => setDialog(open ? 'recalculate' : null)} card={card} />
      <QuickPurchaseDialog open={dialog === 'quick' || dialog === 'paste'} onOpenChange={(open) => !open && setDialog(null)}
        cardId={card.id} paste={dialog === 'paste'} />
      <ConfirmDelete
        open={dialog === 'delete'}
        onOpenChange={(open) => setDialog(open ? 'delete' : null)}
        title={`Excluir ${card.label}?`}
        description="Os lançamentos continuam no financeiro. Saem o ciclo, os pagamentos marcados e os avisos deste cartão."
        confirmLabel="Excluir"
        onConfirm={async () => {
          await remove.mutateAsync(card.id)
          toast.success('Cartão excluído.')
          navigate('/financeiro/carteira')
        }}
      />
    </div>
  )
}

function compare(total: number, average: number) {
  if (average <= 0) return ''
  const diff = Math.round(((total - average) / average) * 100)
  if (diff === 0) return 'Esta fatura está na média.'
  return diff > 0 ? `Esta fatura está ${diff}% acima da média.` : `Esta fatura está ${-diff}% abaixo da média.`
}

function Meter({ percent }: { percent: number }) {
  return (
    <div className="space-y-1 pt-1">
      <div className="h-2 overflow-hidden rounded-full bg-muted" role="progressbar" aria-valuemin={0} aria-valuemax={100}
        aria-valuenow={Math.min(100, percent)} aria-label="Meta usada">
        <div className={cn('h-full rounded-full', percent >= 100 ? 'bg-expense' : percent >= 80 ? 'bg-amber-500' : 'bg-data')}
          style={{ width: `${Math.min(100, percent)}%` }} />
      </div>
      <span className="text-base">{percent}%</span>
    </div>
  )
}

/** De onde vem o total: compras do ciclo, fixos e parcelas, créditos. */
function Breakdown({ invoice }: { invoice: InvoiceDetail }) {
  const { formatCurrency } = useMoney()
  const rows = [
    { label: 'Compras à vista do ciclo', value: invoice.purchases },
    { label: 'Fixos, parcelas e compras de antes', value: invoice.committed },
    { label: 'Estornos e créditos', value: -invoice.credits },
  ]
  return (
    <dl className="grid gap-2 text-sm sm:grid-cols-4">
      {rows.map((r) => (
        <div key={r.label} className="rounded-lg bg-muted/50 px-3 py-2">
          <dt className="text-xs text-muted-foreground">{r.label}</dt>
          <dd className="font-semibold tabular-nums">{r.value < 0 ? '−' : ''}{formatCurrency(Math.abs(r.value))}</dd>
        </div>
      ))}
      <div className="rounded-lg bg-data/10 px-3 py-2">
        <dt className="text-xs text-data">Total</dt>
        <dd className="font-semibold tabular-nums">{formatCurrency(invoice.summary.total)}</dd>
      </div>
    </dl>
  )
}

function ItemRow({ item }: { item: InvoiceItem }) {
  const { formatCurrency } = useMoney()
  return (
    <li className="flex items-center gap-3 py-2.5">
      <TypeIcon type={item.type} />
      <div className="min-w-0 flex-1">
        <p className="flex items-center gap-1.5">
          <span className="truncate text-sm font-medium">{item.description}</span>
          {item.checked && <CheckCircle2Icon className="size-3.5 shrink-0 text-income" aria-label="Conferido com a fatura" />}
        </p>
        <p className="flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
          <span>{item.recurringId ? 'Fixo' : formatDate(item.date)}</span>
          {item.installment && <span>Parcela {item.installment}</span>}
          {item.category && <span className="inline-flex items-center gap-1">{item.color && <ColorDot color={item.color} />}{item.category}</span>}
        </p>
      </div>
      <span className={cn('shrink-0 text-sm font-semibold tabular-nums', item.type === 'INCOME' && 'text-income')}>
        {item.type === 'INCOME' ? '+' : '−'}{formatCurrency(item.amount)}
      </span>
    </li>
  )
}
