import { useMoney } from '@/lib/hide-values'

/** Valor da notificação (texto da API), respeitando o ocultar valores. */
export function Amount({ value }: { value: string | undefined }) {
  const { formatCurrency } = useMoney()
  return <strong className="font-medium">{formatCurrency(Number(value ?? 0))}</strong>
}
