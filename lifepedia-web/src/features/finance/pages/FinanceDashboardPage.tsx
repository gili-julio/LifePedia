import { ArrowDownRightIcon, ArrowUpRightIcon, PercentIcon, PlusIcon, RepeatIcon, ScaleIcon, SparklesIcon } from 'lucide-react'
import { useState, type ComponentType, type ReactNode } from 'react'
import { Link } from 'react-router'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { currentYearMonth, defaultDateInMonth, formatCurrency, formatMonth } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useHistory, useSummary } from '../api'
import { CategoryBreakdownList } from '../components/CategoryBreakdownList'
import { FixedVsOccasionalChart, HistoryChart } from '../components/charts'
import { Money } from '../components/Money'
import { MonthPicker } from '../components/MonthPicker'
import { RecurringEntryDialog } from '../components/RecurringEntryDialog'
import { TransactionDialog } from '../components/TransactionDialog'
import type { EntryType, MonthlySummary } from '../types'
import { useMonthParam } from '../use-month-param'

function StatTile({
  label,
  icon: Icon,
  iconClass,
  children,
  hint,
}: {
  label: string
  icon: ComponentType<{ className?: string }>
  iconClass?: string
  children: ReactNode
  hint?: ReactNode
}) {
  return (
    <Card size="sm">
      <CardContent className="space-y-1">
        <div className="flex items-center justify-between text-sm text-muted-foreground">
          {label}
          <Icon className={cn('size-4', iconClass)} />
        </div>
        <div className="text-2xl font-semibold tracking-tight">{children}</div>
        {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
      </CardContent>
    </Card>
  )
}

function commitmentHint(percent: number | null) {
  if (percent === null) return 'Cadastre seus ganhos fixos para calcular.'
  if (percent > 100) return 'Despesas fixas maiores que a renda fixa.'
  if (percent > 70) return 'Atenção: pouca folga para imprevistos.'
  return 'Da renda fixa já está comprometida com despesas fixas.'
}

function isEmpty(summary: MonthlySummary) {
  return summary.total.income === 0 && summary.total.expense === 0
}

export function FinanceDashboardPage() {
  const [month, setMonth] = useMonthParam()
  const summary = useSummary(month)
  const history = useHistory(month, 6)
  const [transactionOpen, setTransactionOpen] = useState(false)
  const [recurring, setRecurring] = useState<{ open: boolean; type: EntryType }>({ open: false, type: 'EXPENSE' })

  const data = summary.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="Financeiro"
        description={`Visão geral de ${formatMonth(month).toLowerCase()}`}
        toolbar={<MonthPicker value={month} onChange={setMonth} />}
        actions={[
          { label: 'Importar com IA', icon: SparklesIcon, to: '/financeiro/importar' },
          { label: 'Novo lançamento', icon: PlusIcon, primary: true, onSelect: () => setTransactionOpen(true) },
        ]}
      />

      {!data ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-28 rounded-xl" />)}
        </div>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatTile label="Ganhos" icon={ArrowUpRightIcon} iconClass="text-income"
              hint={`Fixos ${formatCurrency(data.fixed.income)} · Ocasionais ${formatCurrency(data.occasional.income)}`}>
              <Money value={data.total.income} />
            </StatTile>
            <StatTile label="Despesas" icon={ArrowDownRightIcon} iconClass="text-expense"
              hint={`Fixas ${formatCurrency(data.fixed.expense)} · Ocasionais ${formatCurrency(data.occasional.expense)}`}>
              <Money value={data.total.expense} />
            </StatTile>
            <StatTile label="Saldo do mês" icon={ScaleIcon}
              hint={`Só com os fixos: ${formatCurrency(data.fixed.balance)}`}>
              <Money value={data.total.balance} signed />
            </StatTile>
            <StatTile label="Renda fixa comprometida" icon={PercentIcon} hint={commitmentHint(data.fixedCommitmentPercent)}>
              <span className={cn('tabular-nums', (data.fixedCommitmentPercent ?? 0) > 100 && 'text-destructive')}>
                {data.fixedCommitmentPercent === null ? '—' : `${data.fixedCommitmentPercent.toLocaleString('pt-BR')}%`}
              </span>
            </StatTile>
          </div>

          {isEmpty(data) && (
            <Card className="border-dashed">
              <CardHeader>
                <CardTitle>Comece pelo que se repete todo mês</CardTitle>
                <CardDescription>
                  Cadastre seus ganhos fixos (salário, benefícios) e despesas fixas (aluguel, contas, assinaturas).
                  Depois, registre os gastos e ganhos ocasionais para ver o comparativo completo.
                </CardDescription>
              </CardHeader>
              <CardContent className="flex flex-wrap gap-2">
                <Button onClick={() => setRecurring({ open: true, type: 'INCOME' })}>
                  <PlusIcon /> Ganho fixo
                </Button>
                <Button variant="outline" onClick={() => setRecurring({ open: true, type: 'EXPENSE' })}>
                  <PlusIcon /> Despesa fixa
                </Button>
              </CardContent>
            </Card>
          )}

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Fixos x ocasionais</CardTitle>
                <CardDescription>Ganhos e despesas do mês por origem.</CardDescription>
              </CardHeader>
              <CardContent>
                <FixedVsOccasionalChart summary={data} />
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>Últimos 6 meses</CardTitle>
                <CardDescription>Total de ganhos e despesas por mês.</CardDescription>
              </CardHeader>
              <CardContent>
                {history.data ? <HistoryChart points={history.data} /> : <Skeleton className="h-64" />}
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Por categoria</CardTitle>
              <CardDescription>Para onde o dinheiro vai e de onde ele vem. Toque numa categoria para ver os itens.</CardDescription>
            </CardHeader>
            <CardContent>
              <Tabs defaultValue="EXPENSE">
                <TabsList>
                  <TabsTrigger value="EXPENSE">Despesas</TabsTrigger>
                  <TabsTrigger value="INCOME">Ganhos</TabsTrigger>
                </TabsList>
                <TabsContent value="EXPENSE" className="pt-4">
                  <CategoryBreakdownList items={data.expensesByCategory} month={month} total={data.total.expense} />
                </TabsContent>
                <TabsContent value="INCOME" className="pt-4">
                  <CategoryBreakdownList items={data.incomesByCategory} month={month} total={data.total.income} />
                </TabsContent>
              </Tabs>
            </CardContent>
          </Card>

          <div className="flex flex-wrap gap-2">
            <Button variant="outline" asChild>
              <Link to="/financeiro/fixos"><RepeatIcon /> Gerenciar fixos</Link>
            </Button>
            <Button variant="outline" asChild>
              <Link to={month === currentYearMonth() ? '/financeiro/lancamentos' : `/financeiro/lancamentos?mes=${month}`}>Ver lançamentos do mês</Link>
            </Button>
          </div>
        </>
      )}

      <TransactionDialog
        open={transactionOpen}
        onOpenChange={setTransactionOpen}
        defaultDate={defaultDateInMonth(month)}
      />
      <RecurringEntryDialog
        open={recurring.open}
        onOpenChange={(open) => setRecurring((r) => ({ ...r, open }))}
        defaultType={recurring.type}
      />
    </div>
  )
}

