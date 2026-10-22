import { PackagePlusIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatCurrency, type YearMonth } from '@/lib/format'
import { CategoryBreakdownList, CategoryItemList } from '../components/CategoryBreakdownList'
import { FixedVsOccasionalChart, HistoryChart } from '../components/charts'
import { TotalCard } from '../components/TotalCard'
import { useGroupCategoryItems, useGroupHistory, useGroupSummary } from './api'
import type { MemberTotals } from './types'

type Props = {
  groupId: string
  month: YearMonth
  onBring: () => void
}

const percent = new Intl.NumberFormat('pt-BR', { style: 'percent', maximumFractionDigits: 0 })

function GroupCategoryItems({ groupId, month, category }: { groupId: string; month: YearMonth; category: string }) {
  const { data, isLoading } = useGroupCategoryItems(groupId, month, category)
  return <CategoryItemList items={data} loading={isLoading} />
}

/** Quanto cada membro trouxe de despesas e ganhos no mês. */
function MemberBreakdown({ members, totalExpense }: { members: MemberTotals[]; totalExpense: number }) {
  const max = Math.max(1, ...members.map((m) => Math.max(m.expense, m.income)))
  return (
    <ul className="space-y-4">
      {members.map((m) => (
        <li key={m.member.id} className="space-y-1.5">
          <div className="flex items-baseline justify-between gap-2 text-sm">
            <span className="min-w-0">
              <span className="block truncate font-medium">{m.member.name}</span>
              <span className="block truncate text-xs text-muted-foreground">@{m.member.username}</span>
            </span>
            <span className="shrink-0 text-right">
              <span className="block font-medium tabular-nums">{formatCurrency(m.expense)}</span>
              <span className="block text-xs text-muted-foreground tabular-nums">
                {totalExpense > 0 ? `${percent.format(m.expense / totalExpense)} das despesas` : 'sem despesas'}
              </span>
            </span>
          </div>
          <div className="space-y-1" aria-hidden>
            <div className="h-2 rounded-full bg-expense/80" style={{ width: `${(m.expense / max) * 100}%` }} />
            {m.income > 0 && <div className="h-1.5 rounded-full bg-income/70" style={{ width: `${(m.income / max) * 100}%` }} />}
          </div>
          {m.income > 0 && <p className="text-xs text-muted-foreground">Ganhos: {formatCurrency(m.income)}</p>}
        </li>
      ))}
    </ul>
  )
}

/** Métricas do grupo no mês: totais, por membro, fixos x ocasionais, histórico e categorias. */
export function GroupOverview({ groupId, month, onBring }: Props) {
  const summary = useGroupSummary(groupId, month)
  const history = useGroupHistory(groupId, month, 6)
  const data = summary.data

  if (!data) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)}
      </div>
    )
  }

  const empty = data.total.income === 0 && data.total.expense === 0

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <TotalCard label="Ganhos do grupo" value={data.total.income} kind="INCOME" />
        <TotalCard label="Despesas do grupo" value={data.total.expense} kind="EXPENSE" />
        <TotalCard label="Saldo do mês" value={data.total.balance} kind="BALANCE" />
        <Card size="sm">
          <CardContent className="space-y-1">
            <p className="text-sm text-muted-foreground">Renda fixa comprometida</p>
            <p className="text-xl font-semibold tabular-nums">
              {data.fixedCommitmentPercent === null ? '—' : `${data.fixedCommitmentPercent.toLocaleString('pt-BR')}%`}
            </p>
          </CardContent>
        </Card>
      </div>

      {empty && (
        <Card className="border-dashed">
          <CardHeader>
            <CardTitle>Nada no grupo neste mês</CardTitle>
            <CardDescription>
              Cada membro leva os fixos e ocasionais que quer dividir. Sincronizados acompanham o item pessoal; cópias
              ficam independentes.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button onClick={onBring}><PackagePlusIcon /> Levar meus itens</Button>
          </CardContent>
        </Card>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Por membro</CardTitle>
            <CardDescription>Despesas e ganhos que cada um trouxe para o grupo.</CardDescription>
          </CardHeader>
          <CardContent>
            <MemberBreakdown members={data.members} totalExpense={data.total.expense} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Fixos x ocasionais</CardTitle>
            <CardDescription>
              Fixas {formatCurrency(data.fixed.expense)} · Ocasionais {formatCurrency(data.occasional.expense)}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <FixedVsOccasionalChart summary={data} />
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Últimos 6 meses</CardTitle>
            <CardDescription>Ganhos e despesas do grupo por mês.</CardDescription>
          </CardHeader>
          <CardContent>
            {history.data ? <HistoryChart points={history.data} /> : <Skeleton className="h-64" />}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Por categoria</CardTitle>
            <CardDescription>Somadas pelo nome entre os membros. Toque para ver os itens.</CardDescription>
          </CardHeader>
          <CardContent>
            <Tabs defaultValue="EXPENSE">
              <TabsList>
                <TabsTrigger value="EXPENSE">Despesas</TabsTrigger>
                <TabsTrigger value="INCOME">Ganhos</TabsTrigger>
              </TabsList>
              {(['EXPENSE', 'INCOME'] as const).map((type) => (
                <TabsContent key={type} value={type} className="pt-4">
                  <CategoryBreakdownList
                    items={type === 'EXPENSE' ? data.expensesByCategory : data.incomesByCategory}
                    month={month}
                    total={type === 'EXPENSE' ? data.total.expense : data.total.income}
                    renderItems={(category) => <GroupCategoryItems groupId={groupId} month={month} category={category} />}
                  />
                </TabsContent>
              ))}
            </Tabs>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
