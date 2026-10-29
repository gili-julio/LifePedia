/** Versão vigente dos Termos e da Política (data de publicação); espelha `user/LegalTerms.VERSION` da API. */
export const LEGAL_VERSION = '2026-10-28'

/** Controlador dos dados (LGPD, art. 5º, VI) e canal do titular / encarregado (art. 41). */
export const CONTROLLER = {
  name: 'Giliardo Júlio de Medeiros Júnior',
  email: 'giliardocontato@gmail.com',
}

/** "28 de outubro de 2026" */
export function legalDate(iso: string = LEGAL_VERSION) {
  return new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'long', year: 'numeric' })
    .format(new Date(`${iso}T12:00:00`))
}
