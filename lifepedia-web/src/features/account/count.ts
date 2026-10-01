/** "1 lançamento", "3 lançamentos" (número no formato pt-BR). */
export function count(n: number, singular: string, plural: string) {
  return `${n.toLocaleString('pt-BR')} ${n === 1 ? singular : plural}`
}
