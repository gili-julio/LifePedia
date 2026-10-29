import { formatDate, todayIso } from '@/lib/format'

export type BuiltInTemplate = { id: string; name: string; icon: string; title: () => string; content: () => string }

/** Modelos prontos (os da pessoa vêm da API, como notas com `template`). Itens com texto: markdown não guarda item vazio. */
export const BUILT_IN_TEMPLATES: BuiltInTemplate[] = [
  {
    id: 'reuniao',
    name: 'Reunião',
    icon: '🤝',
    title: () => `Reunião ${formatDate(todayIso())}`,
    content: () => [
      '## Participantes', '', '- Nome', '',
      '## Pauta', '', '1. Assunto', '',
      '## Decisões', '', '- Decisão', '',
      '## Próximos passos', '', '- [ ] Quem faz o quê', '',
    ].join('\n'),
  },
  {
    id: 'aula',
    name: 'Aula',
    icon: '🎓',
    title: () => `Aula ${formatDate(todayIso())}`,
    content: () => [
      '## Assunto', '', 'Tema da aula', '',
      '## Anotações', '', '- Ponto importante', '',
      '## Dúvidas', '', '- Pergunta para o professor', '',
      '## Para estudar', '', '- [ ] Exercícios', '',
    ].join('\n'),
  },
  {
    id: 'diario',
    name: 'Diário',
    icon: '📔',
    title: () => `Diário ${formatDate(todayIso())}`,
    content: () => [
      '## Como foi o dia', '', 'Escreva aqui', '',
      '## Gratidão', '', '1. Algo bom de hoje', '',
      '## Amanhã', '', '- [ ] Prioridade', '',
    ].join('\n'),
  },
  {
    id: 'compras',
    name: 'Lista de compras',
    icon: '🛒',
    title: () => 'Lista de compras',
    content: () => ['## Mercado', '', '- [ ] Item', '', '## Farmácia', '', '- [ ] Item', ''].join('\n'),
  },
]
