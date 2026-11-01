import type { BankKind } from '@/lib/banks'
import type { BankFilter } from './types'

/** Valor do filtro na tela e na URL (`chave` ou `chave|CARD`) → parâmetros da API. */
export function toBankFilter(value: string): BankFilter {
  const [bank, kind] = value.split('|')
  return bank ? { bank, ...(kind && { bankKind: kind as BankKind }) } : {}
}
