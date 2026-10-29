/** Parcela no fim do nome ("Loja (3/10)"); espelha `finance/Installments.java` da API. */
export function parseInstallment(description: string): { number: number; total: number } | null {
  const match = /^(.*\S)\s*\((\d{1,3})\s*\/\s*(\d{1,3})\)$/.exec(description.trim())
  if (!match) return null
  const number = Number(match[2])
  const total = Number(match[3])
  return number >= 1 && total >= 2 && number <= total ? { number, total } : null
}
