import { formatCurrency, formatMonth, type YearMonth } from '@/lib/format'
import { sortCategories } from '../categories'
import { isActiveIn } from '../fixed'
import type { Category, RecurringEntry } from '../types'

/** Versão do formato; ao mudar, o parser deve continuar aceitando a anterior. */
export const IMPORT_FORMAT = 'lifepedia/transactions@2'

export type ImportSource = 'statement' | 'card'

export const SOURCE_LABEL: Record<ImportSource, string> = {
  statement: 'Extrato bancário',
  card: 'Fatura do cartão',
}

const SOURCE_RULES: Record<ImportSource, string> = {
  statement: `6. Ignore: pagamento da fatura do cartão de crédito (os gastos do cartão são importados pela fatura), transferências entre contas do próprio titular, aplicações e resgates de investimentos, saldos e totais.
7. Rendimentos (juros, dividendos) creditados na conta são "INCOME".`,
  card: `6. Ignore: pagamento da fatura anterior, saldo anterior, total da fatura, limite disponível e resumos.
7. Compras parceladas: inclua só a parcela cobrada neste mês, com a parcela na descrição (ex.: "Loja X (3/10)"). A data é a da compra mostrada na fatura, que pode ser de meses anteriores (até do ano anterior); nunca use data depois do mês da fatura.`,
}

/** Prompt para o agente de IA, com as categorias, os fixos e os nomes que o usuário já usa. */
export function buildImportPrompt(
  categories: Category[],
  fixedEntries: RecurringEntry[],
  referenceMonth: YearMonth,
  source: ImportSource,
  knownNames: string[] = [],
): string {
  const sorted = sortCategories(categories)
  const nameOf = new Map(categories.map((c) => [c.id, c.name]))
  const expenses = sorted.filter((c) => c.type === 'EXPENSE').map((c) => `- ${c.name}`)
  const incomes = sorted.filter((c) => c.type === 'INCOME').map((c) => `- ${c.name}`)
  const fixed = fixedEntries
    .filter((f) => isActiveIn(f, referenceMonth))
    .map((f) => `- ${f.description} — ${f.type === 'INCOME' ? 'ganho' : 'despesa'}, ${nameOf.get(f.categoryId) ?? 'sem categoria'}, ${formatCurrency(f.amount)}${f.dayOfMonth ? `, dia ${f.dayOfMonth}` : ''}`)
  const exampleExpense = sorted.find((c) => c.type === 'EXPENSE')?.name ?? 'Outros'
  const exampleFixed = fixedEntries.find((f) => isActiveIn(f, referenceMonth))
  const monthLabel = formatMonth(referenceMonth).toLowerCase()
  const [year] = referenceMonth.split('-')
  const document = source === 'statement' ? 'o extrato bancário' : 'a fatura do cartão de crédito'

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
    { "date": "${referenceMonth}-${String(exampleFixed.dayOfMonth ?? 10).padStart(2, '0')}", "descriptionOficial": "PIX ENVIADO EXEMPLO", "descriptionNormal": "Exemplo de fixo", "category": "${nameOf.get(exampleFixed.categoryId) ?? 'Outros'}", "type": "${exampleFixed.type}", "amount": ${exampleFixed.amount}, "fixed": "${exampleFixed.description}" }`
    : ''

  return `Você é um assistente que organiza finanças pessoais. Em anexo está ${document} de ${monthLabel} (${referenceMonth}). Extraia TODOS os lançamentos e classifique cada um em uma das minhas categorias.

## Minhas categorias de despesa (type "EXPENSE")
${expenses.join('\n') || '- Outros'}

## Minhas categorias de ganho (type "INCOME")
${incomes.join('\n') || '- Outros'}
${fixedSection}${namesSection}
## Regras
1. Um item para cada lançamento. Não agrupe, não some e não invente lançamentos.
2. "category": use EXATAMENTE um dos nomes das listas acima (mesma grafia), da lista do tipo correspondente. Se nenhuma servir, use "Outros".
3. "type": "EXPENSE" para saídas (compras, contas, tarifas, juros, IOF). "INCOME" para entradas (salário, estornos, reembolsos, cashback, créditos).
4. "amount": número positivo, com ponto decimal e sem símbolo de moeda (ex.: 1234.56).
5. "date": data no formato AAAA-MM-DD. Se o documento mostrar só dia/mês, use o ano ${year} (ou o anterior, se o mês for depois de ${monthLabel}).
${SOURCE_RULES[source]}
8. "descriptionOficial": o texto do lançamento como aparece no documento, no máximo 200 caracteres. Não inclua números de conta/cartão nem documentos (CPF/CNPJ).
9. "descriptionNormal": nome curto e padronizado do estabelecimento ou pessoa, no máximo 120 caracteres, sem códigos, prefixos ("PAG*", "PIX", "COMPRA"), cidade/UF ou números (exceto a parcela). O mesmo lugar tem SEMPRE o mesmo nome, mesmo que no documento venha abreviado ou com as palavras em outra ordem (ex.: "SUPERM NORDESTAO NATAL" e "NATAL NORDESTAO" → "Supermercado Nordestão").
10. "fixed": só quando o item corresponder a um dos fixos listados; senão omita.
11. "notes": opcional; só inclua se houver informação útil.

## Formato da resposta
Responda SOMENTE com um bloco de código JSON válido, sem nenhum texto antes ou depois, exatamente neste modelo:

\`\`\`json
{
  "format": "${IMPORT_FORMAT}",
  "items": [
    { "date": "${referenceMonth}-05", "descriptionOficial": "PAG*SUPERM EXEMPLO NATAL", "descriptionNormal": "Supermercado Exemplo", "category": "${exampleExpense}", "type": "EXPENSE", "amount": 245.9 }${fixedExample}
  ]
}
\`\`\``
}
