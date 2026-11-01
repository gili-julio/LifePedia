import { ArrowDownRightIcon, CircleHelpIcon, TrendingUpIcon } from 'lucide-react'
import { Link } from 'react-router'
import { BankLogo } from '@/components/bank/BankLogo'
import { PageHeader } from '@/components/PageHeader'
import { StatCard } from '@/components/StatCard'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { currentYearMonth, formatMonth, type YearMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { useBankOverview } from '../api'
import { ColorDot } from '../components/CategoryBadge'
import { BankHistoryChart } from '../components/charts'
import { BankList } from '../components/BankList'
import { MonthPicker } from '../components/MonthPicker'
import { Money } from '../components/Money'
import type { BankLine } from '../types'
import { useMonthParam } from '../use-month-param'

const listUrl = (key: string, month: YearMonth) =>
  `/financeiro/lancamentos?banco=${encodeURIComponent(key)}${month === currentYearMonth() ? '' : `&mes=${month}`}`

function Row({ label, value }: { label: string; value: number }) {
  const { formatCurrency } = useMoney()
  if (value <= 0) return null
  return (
    <div className="flex justify-between gap-2">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-medium tabular-nums">{formatCurrency(value)}</dd>
    </div>
  )
}

/** Um banco no mês: de onde saiu, ganhos, investimentos e as maiores categorias. */
function BankCard({ line, month }: { line: BankLine; month: YearMonth }) {
  const { formatCurrency } = useMoney()
  return (
    <Card>
      <CardContent className="flex h-full flex-col gap-3">
        <div className="flex items-center gap-3">
          <BankLogo code={line.code} name={line.name} size="md" />
          <div className="min-w-0 flex-1">
            <p className="truncate font-semibold">{line.name}</p>
            <p className="text-xs text-muted-foreground">Gastos no mês</p>
          </div>
          <span className="shrink-0 font-semibold"><Money value={line.expense} type="EXPENSE" /></span>
        </div>
        <dl className="space-y-1 text-sm">
          <Row label="Na conta" value={line.account} />
          <Row label="No cartão" value={line.card} />
          <Row label="Fixos" value={line.fixed} />
          <Row label="Ocasionais" value={line.occasional} />
          <Row label="Ganhos" value={line.income} />
          <Row label="Investido" value={line.invested} />
        </dl>
        {line.categories.length > 0 && (
          <ul className="space-y-1 border-t pt-3 text-sm">
            {line.categories.slice(0, 4).map((c) => (
              <li key={c.name} className="flex items-center gap-2">
                <ColorDot color={c.color} />
                <span className="min-w-0 flex-1 truncate">{c.name}</span>
                <span className="text-xs text-muted-foreground tabular-nums">{formatCurrency(c.total)}</span>
              </li>
            ))}
          </ul>
        )}
        <div className="mt-auto flex flex-wrap gap-2">
          <Button variant="outline" size="sm" asChild><Link to={listUrl(line.key, month)}>Ver lançamentos</Link></Button>
          <Button variant="ghost" size="sm" asChild><Link to={`/financeiro/fixos?banco=${encodeURIComponent(line.key)}`}>Ver fixos</Link></Button>
        </div>
      </CardContent>
    </Card>
  )
}

export function BanksPage() {
  const [month, setMonth] = useMonthParam()
  const { data } = useBankOverview(month)
  const { formatCurrency } = useMoney()
  const active = data?.banks.filter((b) => b.expense > 0 || b.income > 0 || b.invested > 0) ?? []
  const top = data?.banks.find((b) => b.code && b.expense > 0)
  const none = data?.banks.find((b) => !b.code)

  return (
    <div className="space-y-6">
      <PageHeader
        hideValues
        title="Bancos"
        description={`De onde saiu o dinheiro em ${formatMonth(month).toLowerCase()}`}
        toolbar={<MonthPicker value={month} onChange={setMonth} />}
      />

      {!data ? <Skeleton className="h-64" /> : (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <StatCard label="Gastos no mês" tone="expense" icon={ArrowDownRightIcon}>
              <Money value={data.expense} type="EXPENSE" />
            </StatCard>
            <StatCard label="Banco com mais gastos" tone="data" icon={TrendingUpIcon}
              hint={top ? formatCurrency(top.expense) : 'Defina o banco dos seus lançamentos.'}>
              {top ? <span className="flex items-center gap-2 truncate"><BankLogo code={top.code} name={top.name} /> {top.name}</span> : '-'}
            </StatCard>
            <StatCard label="Sem banco" tone="data" icon={CircleHelpIcon}
              hint={none && none.expense > 0 ? <Link className="underline" to={listUrl('NONE', month)}>Definir o banco desses itens</Link> : 'Tudo com banco definido.'}>
              <Money value={none?.expense ?? 0} />
            </StatCard>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Gastos por banco</CardTitle>
                <CardDescription>Fixos e ocasionais do mês. Investimentos ficam de fora, como nas despesas.</CardDescription>
              </CardHeader>
              <CardContent><BankList banks={data.banks} total={data.expense} /></CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>Mês a mês</CardTitle>
                <CardDescription>Gastos dos últimos 6 meses nos bancos que mais aparecem.</CardDescription>
              </CardHeader>
              <CardContent><BankHistoryChart overview={data} /></CardContent>
            </Card>
          </div>

          {active.length > 0 && (
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {active.map((line) => <BankCard key={line.key} line={line} month={month} />)}
            </div>
          )}
        </>
      )}
    </div>
  )
}
