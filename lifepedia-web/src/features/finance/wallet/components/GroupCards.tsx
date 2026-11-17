import { Link } from 'react-router'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { UserAvatar } from '@/components/UserAvatar'
import { useMoney } from '@/lib/hide-values'
import { useGroupCards } from '../api'
import { CardFace } from './CardFace'

/** Cartões que os membros mostram no grupo: fatura aberta e quanto do gasto do grupo está nela. */
export function GroupCards({ groupId }: { groupId: string }) {
  const { data } = useGroupCards(groupId)
  const { formatCurrency } = useMoney()
  if (!data?.length) return null
  const today = new Date().toLocaleDateString('sv-SE')
  return (
    <Card>
      <CardHeader>
        <CardTitle>Cartões</CardTitle>
        <CardDescription>
          Faturas abertas dos cartões que os membros mostram aqui. Cada um escolhe na própria Carteira.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {data.map((card) => {
          const face = (
            <CardFace label={card.label} bank={card.bank} lastDigits={card.lastDigits} color={card.color} invoice={card.current} today={today}
              footer={
                <div className="flex items-center gap-2 border-t border-white/20 pt-2 text-xs">
                  {card.owner && <UserAvatar user={card.owner} size="xs" />}
                  <span className="min-w-0 flex-1 truncate">{card.mine ? 'Seu cartão' : `De ${card.owner?.name ?? 'membro'}`}</span>
                  <span className="shrink-0">Do grupo: <span className="font-semibold tabular-nums">{formatCurrency(card.groupSpent)}</span></span>
                </div>
              } />
          )
          return card.mine
            ? <Link key={card.id} to={`/financeiro/carteira/${card.id}`} className="block rounded-2xl focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none">{face}</Link>
            : <div key={card.id}>{face}</div>
        })}
      </CardContent>
    </Card>
  )
}
