import { CalendarClockIcon, CircleDollarSignIcon, Loader2Icon, PencilIcon, PercentIcon, PiggyBankIcon, PlusIcon, Trash2Icon, XIcon } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { BankLogo } from '@/components/bank/BankLogo'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { PageHeader } from '@/components/PageHeader'
import { StatCard } from '@/components/StatCard'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Field, FieldLabel } from '@/components/ui/field'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { CategorySelect } from '@/features/finance/components/CategorySelect'
import { useCategoryLookup } from '@/features/finance/use-category-lookup'
import { ApiError } from '@/lib/api'
import { formatDate, formatMonth } from '@/lib/format'
import { OTHER_BANK } from '@/lib/banks'
import { useMoney } from '@/lib/hide-values'
import { useDeleteFinancing, useFinancing, useMyFixed, useRemoveCost, useRemoveExtra } from '../api'
import { CostDialog, ExtraDialog } from '../components/ExtraCostDialogs'
import { FinancingDialog } from '../components/FinancingDialog'
import { ScheduleChart, ScheduleTable } from '../components/Schedule'
import { conditionLabel, formatMonths, formatRate, INDEX_LABEL, kindInfo, MONTH_NAMES, SYSTEM_LABEL } from '../labels'
import type { Financing, FinancingCost } from '../types'

/** Financiamento em andamento: saldo, próxima parcela, quitação, extras, gastos do bem e partes do grupo. */
export function FinancingPage() {
  const { financingId = '', groupId = null } = useParams()
  const { data, isLoading, error } = useFinancing(groupId, financingId)
  if (isLoading) return <Skeleton className="h-96" />
  if (error || !data) {
    return (
      <div className="flex flex-col items-center gap-3 py-16 text-center">
        <p className="text-sm text-muted-foreground">
          {error instanceof ApiError && error.status !== 404 ? error.message : 'Financiamento não encontrado.'}
        </p>
        <Button asChild variant="outline"><Link to={groupId ? `/grupos/${groupId}?aba=financiamentos` : '/financiamentos'}>Voltar</Link></Button>
      </div>
    )
  }
  return <FinancingView financing={data} />
}

function FinancingView({ financing: f }: { financing: Financing }) {
  const { formatCurrency } = useMoney()
  const navigate = useNavigate()
  const remove = useDeleteFinancing(f.groupId)
  const removeExtra = useRemoveExtra(f.groupId, f.id)
  const [editing, setEditing] = useState(false)
  const [extraOpen, setExtraOpen] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [costDialog, setCostDialog] = useState<{ open: boolean; cost?: FinancingCost }>({ open: false })
  const p = f.progress
  const me = f.members.find((m) => m.me)
  const kind = kindInfo(f.kind)
  const done = !p.next

  async function confirmDelete() {
    try {
      await remove.mutateAsync(f.id)
      toast.success('Financiamento excluído.')
      navigate(f.groupId ? `/grupos/${f.groupId}?aba=financiamentos` : '/financiamentos')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir.')
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={f.name}
        description={[kind.label, conditionLabel(f.kind, f.condition), f.bank].filter(Boolean).join(' · ')}
        hideValues
        actions={[
          ...(done ? [] : [{ label: 'Amortização extra', icon: PiggyBankIcon, primary: true, onSelect: () => setExtraOpen(true) }]),
          { label: 'Editar', icon: PencilIcon, onSelect: () => setEditing(true) },
          ...(f.canDelete ? [{ label: 'Excluir', icon: Trash2Icon, secondary: true, destructive: true, onSelect: () => setDeleting(true) }] : []),
        ]}
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Saldo devedor" tone="data" icon={CircleDollarSignIcon}
          hint={`de ${formatCurrency(f.financed)} financiados`}>
          {formatCurrency(p.balance)}
        </StatCard>
        <StatCard label="Próxima parcela" tone="expense" icon={CalendarClockIcon}
          hint={p.next ? `${p.next.number}ª, vence ${formatDate(p.next.date)}${me && f.groupId ? ` · sua parte ${formatCurrency(me.nextAmount)}` : ''}` : 'Tudo pago'}>
          {p.next ? formatCurrency(p.next.amount) : '-'}
        </StatCard>
        <StatCard label="Quitado" tone="data" icon={PercentIcon} hint={`${p.paid} de ${p.totalMonths} parcelas pagas`}>
          <span className="block">{formatRate(p.percent, 1)}</span>
          <span className="mt-1.5 block h-1.5 overflow-hidden rounded-full bg-data/15" role="presentation">
            <span className="block h-full rounded-full bg-data" style={{ width: `${p.percent}%` }} />
          </span>
        </StatCard>
        <StatCard label="Termina em" tone="data" icon={CalendarClockIcon}
          hint={p.remaining ? `faltam ${formatMonths(p.remaining)}` : undefined}>
          {p.lastMonth ? formatMonth(p.lastMonth, 'short') : '-'}
        </StatCard>
      </div>

      <div className="grid items-start gap-4 lg:grid-cols-2">
        {!done && (
          <Card>
            <CardHeader>
              <CardTitle>Quitar hoje</CardTitle>
              <CardDescription>Estimativa: o banco desconta os juros das parcelas que faltam.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <p className="text-2xl font-bold tabular-nums">{formatCurrency(p.payoff)}</p>
              <p className="text-sm text-muted-foreground">
                Deixaria de pagar cerca de <strong className="text-income tabular-nums">{formatCurrency(p.payoffSavings)}</strong> em
                juros e seguros. Juros que ainda faltam: {formatCurrency(p.interestRemaining)}.
              </p>
              <Button variant="outline" size="sm" onClick={() => setExtraOpen(true)}><PiggyBankIcon /> Simular amortização parcial</Button>
            </CardContent>
          </Card>
        )}
        <ContractCard financing={f} />
        {me && <MyFixedCard financing={f} />}
        {f.groupId && <MembersCard financing={f} />}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Amortizações extras</CardTitle>
          <CardDescription>Valores pagos a mais para abater a dívida.</CardDescription>
          {!done && (
            <CardAction>
              <Button variant="outline" size="sm" onClick={() => setExtraOpen(true)}><PlusIcon /> Adicionar</Button>
            </CardAction>
          )}
        </CardHeader>
        <CardContent>
          {f.extras.length === 0 ? <p className="text-sm text-muted-foreground">Nenhuma ainda.</p> : (
            <ul className="divide-y rounded-xl border">
              {f.extras.map((e) => (
                <li key={e.id} className="flex items-center gap-3 p-3 text-sm">
                  <span className="min-w-0 flex-1">
                    {formatMonth(e.month, 'short')} · {e.mode === 'TERM' ? 'reduziu o prazo' : 'reduziu a parcela'}
                  </span>
                  <span className="font-medium tabular-nums">{formatCurrency(e.amount)}</span>
                  <Button variant="ghost" size="icon-sm" aria-label="Remover amortização" disabled={removeExtra.isPending}
                    onClick={() => removeExtra.mutate(e.id, { onError: (err) => toast.error(err instanceof ApiError ? err.message : 'Não foi possível remover.') })}>
                    <XIcon />
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <CostsCard financing={f} onEdit={(cost) => setCostDialog({ open: true, cost })} />

      <ScheduleChart rows={f.schedule} />
      <ScheduleTable rows={f.schedule} paid={p.paid} fileName={`parcelas-${f.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`} />

      <FinancingDialog open={editing} onOpenChange={setEditing} groupId={f.groupId} financing={f} />
      <ExtraDialog open={extraOpen} onOpenChange={setExtraOpen} financing={f} />
      <CostDialog open={costDialog.open} onOpenChange={(open) => setCostDialog((d) => ({ ...d, open }))} financing={f} cost={costDialog.cost} />
      <ConfirmDelete open={deleting} onOpenChange={setDeleting} title="Excluir financiamento?"
        description="As parcelas, amortizações e gastos somem, e os fixos gerados nas finanças também." onConfirm={() => void confirmDelete()} />
    </div>
  )
}

function ContractCard({ financing: f }: { financing: Financing }) {
  const { formatCurrency } = useMoney()
  const rows: [string, ReactNode][] = [
    ['Taxa', `${formatRate(f.monthlyRate)} ao mês (${formatRate(f.yearlyRate)} ao ano)`],
    ['Sistema', `${SYSTEM_LABEL[f.system]}, ${formatMonths(f.months)}`],
    ['1ª parcela', `${formatMonth(f.firstMonth, 'short')}, dia ${f.dueDay}`],
    ['Correção', f.index === 'NONE' ? 'Sem correção'
      : `${INDEX_LABEL[f.index]}${f.indexProjection !== null ? ` (média recente ${formatRate(f.indexProjection)} ao mês)` : ''}`],
  ]
  const bank = <span className="inline-flex items-center gap-2"><BankLogo code={f.bankCode ?? OTHER_BANK} name={f.bank} /> {f.bank}</span>
  if (f.bank) rows.unshift(['Banco', bank])
  if (f.price) rows.push(['Preço do bem', formatCurrency(f.price)])
  if (f.downPayment) rows.push(['Entrada', formatCurrency(f.downPayment)])
  if (f.fgts) rows.push(['FGTS', formatCurrency(f.fgts)])
  if (f.insuranceRate) rows.push(['Seguro sobre o saldo', `${formatRate(f.insuranceRate, 4)} ao mês`])
  if (f.monthlyFee) rows.push(['Valores fixos na parcela', formatCurrency(f.monthlyFee)])
  rows.push(['Aviso', f.reminderDays === null ? 'Desligado' : f.reminderDays === 0 ? 'No dia do vencimento' : `${f.reminderDays} dia(s) antes`])
  return (
    <Card>
      <CardHeader>
        <CardTitle>Contrato</CardTitle>
        <CardDescription>Juros pagos até agora: {formatCurrency(f.progress.interestPaid)}.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <dl className="grid gap-2 text-sm">
          {rows.map(([label, value]) => (
            <div key={label} className="flex justify-between gap-3">
              <dt className="text-muted-foreground">{label}</dt>
              <dd className="text-right">{value}</dd>
            </div>
          ))}
        </dl>
        {f.notes && <p className="rounded-xl bg-muted/60 p-3 text-sm whitespace-pre-line">{f.notes}</p>}
      </CardContent>
    </Card>
  )
}

/** Liga a parte da pessoa nas finanças pessoais (fixo com o valor de cada mês). */
function MyFixedCard({ financing: f }: { financing: Financing }) {
  const me = f.members.find((m) => m.me)!
  const toggle = useMyFixed(f.groupId, f.id)
  const { ofType } = useCategoryLookup()
  const preferred = ofType('EXPENSE').find((c) => /d[ií]vida|parcela|financ/i.test(c.name))
  const [categoryId, setCategoryId] = useState('')
  const [shareToGroup, setShareToGroup] = useState(true)
  const category = categoryId || preferred?.id || ''

  function run(enabled: boolean) {
    toggle.mutate({ enabled, categoryId: enabled ? category : null, shareToGroup: !!f.groupId && shareToGroup }, {
      onSuccess: () => toast.success(enabled ? 'Parcelas ligadas às suas finanças.' : 'Fixo removido das suas finanças.'),
      onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.'),
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Nas suas finanças</CardTitle>
        <CardDescription>
          {f.groupId ? `Sua parte (${formatRate(me.share, 0)}) de cada parcela` : 'Cada parcela'} vira uma despesa fixa, com o valor certo de cada mês.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {me.recurringEntryId ? (
          <>
            <p className="text-sm">Ligado. As parcelas aparecem em <Link to="/financeiro/fixos" className="font-medium underline">Fixos</Link> e
              mudam sozinhas quando o financiamento muda.</p>
            <Button variant="outline" size="sm" disabled={toggle.isPending} onClick={() => run(false)}>
              {toggle.isPending && <Loader2Icon className="animate-spin" />} Desligar
            </Button>
          </>
        ) : (
          <>
            <Field>
              <FieldLabel htmlFor="fin-fixed-category">Categoria</FieldLabel>
              <CategorySelect id="fin-fixed-category" type="EXPENSE" value={category} onChange={setCategoryId} />
            </Field>
            {f.groupId && (
              <Label className="flex items-center gap-2 font-normal">
                <Checkbox checked={shareToGroup} onCheckedChange={(v) => setShareToGroup(v === true)} />
                Levar também para os fixos do grupo
              </Label>
            )}
            <Button size="sm" disabled={!category || toggle.isPending} onClick={() => run(true)}>
              {toggle.isPending && <Loader2Icon className="animate-spin" />} Ligar às minhas finanças
            </Button>
          </>
        )}
      </CardContent>
    </Card>
  )
}

function MembersCard({ financing: f }: { financing: Financing }) {
  const { formatCurrency } = useMoney()
  const total = f.members.reduce((sum, m) => sum + m.share, 0)
  return (
    <Card>
      <CardHeader>
        <CardTitle>Parte de cada um</CardTitle>
        <CardDescription>Na próxima parcela. Mude as partes em Editar.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <ul className="space-y-2">
          {f.members.map((m) => (
            <li key={m.user.id} className="flex items-center gap-2 text-sm">
              <UserAvatar user={m.user} className="size-7" />
              <span className="min-w-0 flex-1 truncate">{m.user.name}{m.me && ' (você)'}</span>
              <span className="shrink-0 text-xs text-muted-foreground">{formatRate(m.share, 0)}{m.fixed && ' · nas finanças'}</span>
              <span className="w-24 shrink-0 text-right font-medium tabular-nums">{formatCurrency(m.nextAmount)}</span>
            </li>
          ))}
        </ul>
        {Math.abs(total - 100) > 0.001 && (
          <p className="text-sm text-destructive">As partes somam {formatRate(total, 0)}. Ajuste em Editar para dar 100%.</p>
        )}
      </CardContent>
    </Card>
  )
}

function CostsCard({ financing: f, onEdit }: { financing: Financing; onEdit: (cost?: FinancingCost) => void }) {
  const { formatCurrency } = useMoney()
  const remove = useRemoveCost(f.groupId, f.id)
  const yearly = f.costs.reduce((sum, c) => sum + (c.frequency === 'YEARLY' ? c.amount : c.amount * 12), 0)
  return (
    <Card>
      <CardHeader>
        <CardTitle>Gastos do bem</CardTitle>
        <CardDescription>
          {f.costs.length ? `Cerca de ${formatCurrency(yearly)} por ano fora das parcelas.` : 'IPVA, seguro, revisão, IPTU, condomínio...'}
        </CardDescription>
        <CardAction>
          <Button variant="outline" size="sm" onClick={() => onEdit()}><PlusIcon /> Adicionar</Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        {f.costs.length === 0 ? <p className="text-sm text-muted-foreground">Nenhum gasto cadastrado.</p> : (
          <ul className="divide-y rounded-xl border">
            {f.costs.map((c) => (
              <li key={c.id} className="flex items-center gap-2 p-3 text-sm">
                <button type="button" className="min-w-0 flex-1 text-left" onClick={() => onEdit(c)}>
                  <span className="block truncate font-medium">{c.name}</span>
                  <span className="block text-xs text-muted-foreground">
                    {c.frequency === 'YEARLY' ? `Todo ano em ${MONTH_NAMES[(c.dueMonth ?? 1) - 1]}` : 'Todo mês'}, dia {c.dueDay} · próximo {formatDate(c.nextDate)}
                  </span>
                </button>
                <span className="shrink-0 font-medium tabular-nums">{formatCurrency(c.amount)}</span>
                <Button variant="ghost" size="icon-sm" aria-label={`Remover ${c.name}`} disabled={remove.isPending}
                  onClick={() => remove.mutate(c.id, { onError: (err) => toast.error(err instanceof ApiError ? err.message : 'Não foi possível remover.') })}>
                  <XIcon />
                </Button>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
