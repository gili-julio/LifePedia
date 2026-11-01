import { BankLogo } from '@/components/bank/BankLogo'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { BANK_KIND_LABEL, type BankKind } from '@/lib/banks'
import type { BankLine } from '../types'

const ALL = 'ALL'

type Props = {
  /** Bancos com itens, da API */
  lines: BankLine[] | undefined
  value: string
  onChange: (value: string) => void
}

/** Filtro por banco das listas; conta e cartão aparecem separados quando o banco tem os dois. */
export function BankFilterSelect({ lines, value, onChange }: Props) {
  const banks = lines ?? []
  return (
    <Select value={value || ALL} onValueChange={(v) => onChange(v === ALL ? '' : v)}>
      <SelectTrigger size="sm" className="w-full sm:w-52" aria-label="Filtrar por banco"><SelectValue /></SelectTrigger>
      <SelectContent>
        <SelectItem value={ALL}>Todos os bancos</SelectItem>
        {banks.map((line) => (
          <SelectItem key={line.key} value={line.key}>
            <BankLogo code={line.code} name={line.name} size="xs" /> {line.name}
          </SelectItem>
        ))}
        {banks.filter((line) => line.account > 0 && line.card > 0).flatMap((line) =>
          (Object.keys(BANK_KIND_LABEL) as BankKind[]).map((kind) => (
            <SelectItem key={`${line.key}|${kind}`} value={`${line.key}|${kind}`}>
              <BankLogo code={line.code} name={line.name} size="xs" /> {line.name} · {BANK_KIND_LABEL[kind]}
            </SelectItem>
          )))}
      </SelectContent>
    </Select>
  )
}
