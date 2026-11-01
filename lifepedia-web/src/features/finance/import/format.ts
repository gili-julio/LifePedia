/** Versão do formato (prompt em `ImportPrompt.java`); ao mudar, o parser deve continuar aceitando as anteriores. */
export const IMPORT_FORMAT = 'lifepedia/transactions@4'

/** De onde veio o item: extrato bancário ou fatura do cartão. */
export type ImportSource = 'statement' | 'card'

export const SOURCE_LABEL: Record<ImportSource, string> = {
  statement: 'Extrato',
  card: 'Fatura',
}
