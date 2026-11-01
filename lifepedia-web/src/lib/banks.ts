import { z } from 'zod'

export type BankKind = 'ACCOUNT' | 'CARD'

/** Banco de um item: `name` só em OTHER (digitado). */
export type BankRef = { code: string; name: string | null; kind: BankKind }

export const OTHER_BANK = 'OTHER'

/** Espelha `finance/bank/Bank.java`. Logo em `assets/banks/<code>.svg`; `aliases` reconhecem o banco em texto livre. */
export const BANKS: { code: string; label: string; aliases: string[] }[] = [
  { code: 'BB', label: 'Banco do Brasil', aliases: ['banco do brasil', 'bb'] },
  { code: 'CAIXA', label: 'Caixa', aliases: ['caixa', 'caixa economica', 'caixa economica federal', 'cef'] },
  { code: 'ITAU', label: 'Itaú', aliases: ['itau', 'itau unibanco', 'iti'] },
  { code: 'BRADESCO', label: 'Bradesco', aliases: ['bradesco', 'next'] },
  { code: 'SANTANDER', label: 'Santander', aliases: ['santander'] },
  { code: 'NUBANK', label: 'Nubank', aliases: ['nubank', 'nu pagamentos', 'nu financeira'] },
  { code: 'INTER', label: 'Inter', aliases: ['inter', 'banco inter', 'intermedium'] },
  { code: 'C6', label: 'C6 Bank', aliases: ['c6', 'c6 bank', 'c6 consignado'] },
  { code: 'BTG', label: 'BTG Pactual', aliases: ['btg', 'btg pactual'] },
  { code: 'XP', label: 'XP', aliases: ['xp', 'xp investimentos'] },
  { code: 'SAFRA', label: 'Safra', aliases: ['safra'] },
  { code: 'ORIGINAL', label: 'Original', aliases: ['original'] },
  { code: 'PAN', label: 'Banco Pan', aliases: ['pan', 'banco pan', 'bancopan'] },
  { code: 'BV', label: 'BV', aliases: ['bv', 'votorantim', 'bv financeira'] },
  { code: 'NEON', label: 'Neon', aliases: ['neon'] },
  { code: 'PICPAY', label: 'PicPay', aliases: ['picpay'] },
  { code: 'MERCADO_PAGO', label: 'Mercado Pago', aliases: ['mercado pago', 'mercadopago', 'mercado credito'] },
  { code: 'PAGBANK', label: 'PagBank', aliases: ['pagbank', 'pagseguro'] },
  { code: 'STONE', label: 'Stone', aliases: ['stone'] },
  { code: 'BANRISUL', label: 'Banrisul', aliases: ['banrisul'] },
  { code: 'BRB', label: 'BRB', aliases: ['brb', 'banco de brasilia'] },
  { code: 'BNB', label: 'Banco do Nordeste', aliases: ['banco do nordeste', 'bnb'] },
  { code: 'BMG', label: 'BMG', aliases: ['bmg'] },
  { code: 'MERCANTIL', label: 'Mercantil', aliases: ['mercantil', 'mercantil do brasil'] },
  { code: 'SICOOB', label: 'Sicoob', aliases: ['sicoob'] },
  { code: 'SICREDI', label: 'Sicredi', aliases: ['sicredi'] },
  { code: 'UNICRED', label: 'Unicred', aliases: ['unicred'] },
  { code: 'CRESOL', label: 'Cresol', aliases: ['cresol'] },
  { code: 'IFOOD', label: 'iFood Pago', aliases: ['ifood', 'ifood pago'] },
  { code: 'MAGALU', label: 'MagaluPay', aliases: ['magalu', 'magalupay', 'magazine luiza'] },
]

export const BANK_KIND_LABEL: Record<BankKind, string> = { ACCOUNT: 'Conta', CARD: 'Cartão' }

/** Nome para mostrar. */
export function bankLabel(bank: Pick<BankRef, 'code' | 'name'>) {
  if (bank.code === OTHER_BANK) return bank.name ?? 'Outro banco'
  return BANKS.find((b) => b.code === bank.code)?.label ?? bank.code
}

function normalize(text: string) {
  const plain = text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()
  return ` ${plain.replace(/\bbco\b/g, 'banco').replace(/[^a-z0-9]+/g, ' ').trim()} `
}

/** Banco do catálogo citado no texto (ex.: "BCO DO BRASIL S.A."); `null` se nenhum. */
export function matchBank(text: string) {
  const value = normalize(text)
  return BANKS.find((b) => b.aliases.some((alias) => value.includes(` ${alias} `))) ?? null
}

/** Campo de formulário: nulo = sem banco; outro banco exige o nome. */
export const bankSchema = z
  .object({ code: z.string(), name: z.string().nullable(), kind: z.enum(['ACCOUNT', 'CARD']) })
  .nullable()
  .refine((b) => !b || b.code !== OTHER_BANK || !!b.name?.trim(), 'Informe o nome do banco.')
