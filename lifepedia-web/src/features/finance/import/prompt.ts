import { addMonths, currentYearMonth, formatCurrency, todayIso } from '@/lib/format'
import { sortCategories } from '../categories'
import type { Category, RecurringEntry } from '../types'

/** Versão do formato; ao mudar, o parser deve continuar aceitando as anteriores. */
export const IMPORT_FORMAT = 'lifepedia/transactions@3'

/** De onde veio o item: extrato bancário ou fatura do cartão. */
export type ImportSource = 'statement' | 'card'

export const SOURCE_LABEL: Record<ImportSource, string> = {
  statement: 'Extrato',
  card: 'Fatura',
}

/** Fixos encerrados há até 1 ano ainda entram no prompt (o documento pode ser antigo). */
const FIXED_MONTHS_BACK = 12

/**
 * Prompt para o agente de IA, com as categorias, os fixos e os nomes que o usuário já usa. Serve para extratos e
 * faturas juntos: o agente marca a origem de cada item e tira as datas (e o mês da fatura) do documento.
 */
export function buildImportPrompt(
  categories: Category[],
  fixedEntries: RecurringEntry[],
  knownNames: string[] = [],
): string {
  // Mês passado: o exemplo não pode ter data futura.
  const exampleMonth = addMonths(currentYearMonth(), -1)
  const oldest = addMonths(currentYearMonth(), -FIXED_MONTHS_BACK)
  const listed = (f: RecurringEntry) => f.active && (!f.endMonth || f.endMonth >= oldest)
  const sorted = sortCategories(categories)
  const nameOf = new Map(categories.map((c) => [c.id, c.name]))
  const expenses = sorted.filter((c) => c.type === 'EXPENSE').map((c) => `- ${c.name}`)
  const incomes = sorted.filter((c) => c.type === 'INCOME').map((c) => `- ${c.name}`)
  const fixed = fixedEntries
    .filter(listed)
    .map((f) => `- ${f.description} — ${f.type === 'INCOME' ? 'ganho' : 'despesa'}, ${nameOf.get(f.categoryId) ?? 'sem categoria'}, ${formatCurrency(f.amount)}${f.dayOfMonth ? `, dia ${f.dayOfMonth}` : ''}, de ${f.startMonth}${f.endMonth ? ` a ${f.endMonth}` : ' em diante'}`)
  const exampleExpense = sorted.find((c) => c.type === 'EXPENSE')?.name ?? 'Outros'
  const exampleFixed = fixedEntries.find(listed)
  const rules = [
    'Um item para cada lançamento. Não agrupe, não some e não invente lançamentos.',
    '"source": de onde o item veio — "statement" para extrato bancário (conta corrente, poupança, conta digital) e "card" para fatura de cartão de crédito. Identifique pelo documento; com vários documentos anexados, cada item leva o do seu.',
    '"category": use EXATAMENTE um dos nomes das listas acima (mesma grafia), da lista do tipo correspondente. Se nenhuma servir, use "Outros".',
    '"type": "EXPENSE" para saídas (compras, contas, tarifas, juros, IOF). "INCOME" para entradas (salário, estornos, reembolsos, cashback, créditos).',
    '"amount": número positivo, com ponto decimal e sem símbolo de moeda (ex.: 1234.56).',
    `"date": data no formato AAAA-MM-DD, com o ano tirado do documento (período, cabeçalho ou vencimento). Hoje é ${todayIso()}: nenhum lançamento tem data depois de hoje.`,
    'No extrato, ignore: pagamento da fatura do cartão de crédito (os gastos do cartão são importados pela fatura), transferências entre contas do próprio titular, aplicações e resgates de investimentos, saldos e totais. Rendimentos (juros, dividendos) creditados na conta são "INCOME".',
    'Na fatura, ignore: pagamento da fatura anterior, saldo anterior, total da fatura, limite disponível e resumos. Compras parceladas: inclua só a parcela cobrada na fatura, com a parcela na descrição (ex.: "Loja X (3/10)"); a data é a da compra mostrada na fatura, que pode ser de meses anteriores (até do ano anterior), nunca depois do mês da fatura.',
    '"invoiceMonth": só nos itens "card" — mês de VENCIMENTO da fatura em que o item veio, no formato AAAA-MM (ex.: fatura que vence em 10/11/2026 → "2026-11").',
    '"descriptionOficial": o texto do lançamento como aparece no documento, no máximo 200 caracteres. Não inclua números de conta/cartão nem documentos (CPF/CNPJ).',
    '"descriptionNormal": nome curto e padronizado do estabelecimento ou pessoa, no máximo 120 caracteres, sem códigos, prefixos ("PAG*", "PIX", "COMPRA"), cidade/UF ou números (exceto a parcela). O mesmo lugar tem SEMPRE o mesmo nome, mesmo que no documento venha abreviado ou com as palavras em outra ordem (ex.: "SUPERM NORDESTAO NATAL" e "NATAL NORDESTAO" → "Supermercado Nordestão").',
    '"fixed": só quando o item corresponder a um dos fixos listados; senão omita.',
    '"notes": opcional; só inclua se houver informação útil.',
  ]

  const fixedSection = fixed.length
    ? `
## Meus lançamentos fixos (já cadastrados)
${fixed.join('\n')}

Se um item corresponder a um desses fixos — mesmo com descrição ou valor um pouco diferentes (ex.: PIX para o locador = aluguel, conta de luz com outro valor) —, preencha "fixed" com o nome exato do fixo e mantenha o valor real do documento.
`
    : ''

  const namesSection = knownNames.length
    ? `
## Nomes que já uso nos lançamentos
${knownNames.map((n) => `- ${n}`).join('\n')}

Se o item for do mesmo estabelecimento ou pessoa de um desses nomes, use EXATAMENTE esse nome em "descriptionNormal".
`
    : ''

  const fixedExample = exampleFixed
    ? `,
    { "source": "statement", "date": "${exampleMonth}-${String(exampleFixed.dayOfMonth ?? 10).padStart(2, '0')}", "descriptionOficial": "PIX ENVIADO EXEMPLO", "descriptionNormal": "Exemplo de fixo", "category": "${nameOf.get(exampleFixed.categoryId) ?? 'Outros'}", "type": "${exampleFixed.type}", "amount": ${exampleFixed.amount}, "fixed": "${exampleFixed.description}" }`
    : ''

  return `Você é um assistente que organiza finanças pessoais. Em anexo estão um ou mais documentos: extratos bancários e/ou faturas de cartão de crédito, de um ou mais meses. Extraia TODOS os lançamentos e classifique cada um em uma das minhas categorias.

## Minhas categorias de despesa (type "EXPENSE")
${expenses.join('\n') || '- Outros'}

## Minhas categorias de ganho (type "INCOME")
${incomes.join('\n') || '- Outros'}
${fixedSection}${namesSection}
## Regras
${rules.map((rule, i) => `${i + 1}. ${rule}`).join('\n')}

## Formato da resposta
Responda SOMENTE com um bloco de código JSON válido, sem nenhum texto antes ou depois, exatamente neste modelo:

\`\`\`json
{
  "format": "${IMPORT_FORMAT}",
  "items": [
    { "source": "card", "date": "${exampleMonth}-05", "descriptionOficial": "PAG*SUPERM EXEMPLO NATAL", "descriptionNormal": "Supermercado Exemplo", "category": "${exampleExpense}", "type": "EXPENSE", "amount": 245.9, "invoiceMonth": "${currentYearMonth()}" }${fixedExample}
  ]
}
\`\`\``
}
