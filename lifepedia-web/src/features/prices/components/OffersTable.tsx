import { ExternalLinkIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import { SOURCE_LABEL } from '../labels'
import type { Offer } from '../types'

/** Lojas da última consulta, da mais barata à mais cara. */
export function OffersTable({ offers }: { offers: Offer[] }) {
  const { formatCurrency } = useMoney()
  if (!offers.length) return <p className="text-sm text-muted-foreground">Nenhuma loja com preço ainda.</p>
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Loja</TableHead>
          <TableHead className="hidden sm:table-cell">Origem</TableHead>
          <TableHead className="text-right">À vista</TableHead>
          <TableHead className="hidden text-right md:table-cell">Parcelado</TableHead>
          <TableHead className="w-10"><span className="sr-only">Abrir</span></TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {offers.map((o, i) => (
          <TableRow key={`${o.source}-${o.store}`}>
            <TableCell>
              <p className={cn('font-medium', i === 0 && 'text-income')}>{o.store}</p>
              {o.source === 'MANUAL' && <p className="text-xs text-muted-foreground">anotado em {formatDate(o.day)}</p>}
            </TableCell>
            <TableCell className="hidden sm:table-cell"><Badge variant="secondary">{SOURCE_LABEL[o.source]}</Badge></TableCell>
            <TableCell className={cn('text-right font-medium tabular-nums', i === 0 && 'text-income')}>{formatCurrency(o.price)}</TableCell>
            <TableCell className="hidden text-right tabular-nums text-muted-foreground md:table-cell">
              {o.totalPrice ? formatCurrency(o.totalPrice) : '-'}
            </TableCell>
            <TableCell>
              {o.url && (
                <a href={o.url} target="_blank" rel="noopener noreferrer nofollow" aria-label={`Abrir ${o.store}`}
                  className="inline-flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground">
                  <ExternalLinkIcon className="size-4" />
                </a>
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
