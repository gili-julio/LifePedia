import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { BANK_KIND_LABEL, BANKS, OTHER_BANK, type BankKind, type BankRef } from '@/lib/banks'
import { BankLogo } from './BankLogo'

const NONE = 'NONE'

type SelectProps = {
  id?: string
  /** Código do banco; '' = sem banco */
  value: string
  onChange: (code: string) => void
  size?: 'sm' | 'default'
  noneLabel?: string
  invalid?: boolean
}

/** Lista de bancos com logo. */
export function BankSelect({ id, value, onChange, size, noneLabel = 'Sem banco', invalid }: SelectProps) {
  return (
    <Select value={value || NONE} onValueChange={(v) => onChange(v === NONE ? '' : v)}>
      <SelectTrigger id={id} size={size} className="w-full" aria-label="Banco" aria-invalid={invalid}><SelectValue /></SelectTrigger>
      <SelectContent>
        <SelectItem value={NONE}><BankLogo code={null} size="xs" /> {noneLabel}</SelectItem>
        {BANKS.map((bank) => (
          <SelectItem key={bank.code} value={bank.code}><BankLogo code={bank.code} size="xs" /> {bank.label}</SelectItem>
        ))}
        <SelectItem value={OTHER_BANK}><BankLogo code={OTHER_BANK} name="?" size="xs" /> Outro banco</SelectItem>
      </SelectContent>
    </Select>
  )
}

type FieldProps = {
  id: string
  value: BankRef | null
  onChange: (bank: BankRef | null) => void
  invalid?: boolean
}

/** Banco + conta ou cartão + nome, se for outro banco. */
export function BankField({ id, value, onChange, invalid }: FieldProps) {
  function setCode(code: string) {
    onChange(code ? { code, name: code === OTHER_BANK ? value?.name ?? '' : null, kind: value?.kind ?? 'ACCOUNT' } : null)
  }
  return (
    <div className="space-y-2">
      <BankSelect id={id} value={value?.code ?? ''} onChange={setCode} invalid={invalid} />
      {value?.code === OTHER_BANK && (
        <Input aria-label="Nome do banco" placeholder="Nome do banco" maxLength={60} className="text-base md:text-sm"
          aria-invalid={invalid} value={value.name ?? ''} onChange={(e) => onChange({ ...value, name: e.target.value })} />
      )}
      {value && (
        <div className="flex gap-2" role="group" aria-label="Conta ou cartão">
          {(Object.keys(BANK_KIND_LABEL) as BankKind[]).map((kind) => (
            <Button key={kind} type="button" size="sm" className="flex-1" aria-pressed={value.kind === kind}
              variant={value.kind === kind ? 'default' : 'outline'} onClick={() => onChange({ ...value, kind })}>
              {BANK_KIND_LABEL[kind]}
            </Button>
          ))}
        </div>
      )}
    </div>
  )
}
