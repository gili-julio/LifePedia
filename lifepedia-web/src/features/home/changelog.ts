/** Áreas do app afetadas por uma mudança (viram badges). */
export type ChangeModule =
  | 'Geral'
  | 'Finanças'
  | 'Tarefas'
  | 'Agenda'
  | 'Frequência'
  | 'Grupos'
  | 'Chat'
  | 'Amigos'
  | 'Notificações'
  | 'Conta'
  | 'Privacidade'

export type Change = {
  id: string
  /** yyyy-MM-dd */
  date: string
  /** Resumo geral, do ponto de vista de quem usa */
  title: string
  modules: ChangeModule[]
  /** Pontos curtos e sem termos técnicos: o que a pessoa vê ou passa a poder fazer */
  details: string[]
}

/** Mudanças do sistema, da mais nova para a mais antiga. Toda entrega visível para o usuário ganha uma entrada aqui. */
export const CHANGES: Change[] = [
  {
    id: 'ocultar-valores',
    date: '2026-10-29',
    title: 'Olhinho para ocultar os valores',
    modules: ['Finanças', 'Grupos', 'Privacidade'],
    details: [
      'Um toque no olhinho, no topo da tela ou no cabeçalho das telas de finanças, esconde todos os valores em dinheiro.',
      'Vale para totais, listas, gráficos, planejamento do grupo e o resumo do Início.',
      'Bom para usar o app em público ou mostrando a tela. A escolha fica guardada neste aparelho.',
    ],
  },
  {
    id: 'lembrete-dias',
    date: '2026-10-29',
    title: 'Lembretes com mais antecedência nas tarefas',
    modules: ['Tarefas', 'Notificações'],
    details: [
      'Além das opções que já existiam, agora dá para ser lembrado 7, 15 ou 30 dias antes do horário da tarefa.',
      'Bom para provas, pagamentos e prazos que pedem preparo.',
    ],
  },
  {
    id: 'frequencia-sigaa',
    date: '2026-10-29',
    title: 'Traga suas matérias do SIGAA para a Frequência',
    modules: ['Frequência'],
    details: [
      'Na Frequência, toque em Importar do SIGAA e cole o texto da página de frequência da turma virtual. O passo a passo fica no "i" ao lado do campo.',
      'O nome, os dias, o horário, o período e o limite de faltas já vêm preenchidos para você conferir.',
      'Presenças e faltas registradas no SIGAA já entram marcadas. Dias de aula cancelada nas notícias e feriados podem entrar como sem aula.',
      'Importar de novo a mesma matéria só atualiza as marcações.',
    ],
  },
  {
    id: 'frequencia',
    date: '2026-10-29',
    title: 'Controle suas faltas nas matérias e compromissos fixos',
    modules: ['Frequência', 'Agenda', 'Tarefas', 'Geral'],
    details: [
      'Nova área Frequência, no menu Organização. Cadastre cada matéria com os dias, o horário e quantas faltas você pode ter.',
      'Depois de cada dia, marque Presente, Falta ou Sem aula. Os dias sem marcação ficam pendentes e não contam como falta.',
      'Ligue a uma tarefa: ao concluir a tarefa no dia, a presença já fica marcada.',
      'A barra de faltas fica laranja perto do limite e vermelha quando passa dele.',
      'Os compromissos aparecem na Agenda e no Início, e entram nas horas ocupadas da semana.',
    ],
  },
  {
    id: 'agenda-indicadores',
    date: '2026-10-29',
    title: 'Veja quanto da sua semana está ocupado e quanto sobra livre',
    modules: ['Agenda'],
    details: [
      'No fim da Agenda aparecem as horas ocupadas na semana, de segunda a sexta e no fim de semana, e quanto você dorme na semana.',
      'As horas livres já descontam o seu sono. Informe quanto você dorme em cada dia da semana.',
      'Mudou o sono e ainda não salvou? Os dias alterados ficam em laranja, com um aviso e a opção de descartar.',
      'Cada dia mostra uma barra com sono, ocupado e livre.',
      'No celular, o botão Indicadores no topo da Agenda leva direto até eles.',
    ],
  },
  {
    id: 'push',
    date: '2026-10-28',
    title: 'Receba avisos no celular mesmo com o app fechado',
    modules: ['Notificações', 'Tarefas', 'Grupos', 'Chat'],
    details: [
      'Ative em Meu perfil, na parte "Notificações no aparelho". Cada aparelho é ativado separadamente.',
      'Chegam lembretes de tarefas, avisos dos grupos, pedidos de amizade e mensagens do chat.',
      'Tocar no aviso abre direto a tela certa.',
      'Ao sair da conta, o aparelho para de receber avisos.',
    ],
  },
  {
    id: 'pwa',
    date: '2026-10-28',
    title: 'Instale o LifePedia como um aplicativo',
    modules: ['Geral'],
    details: [
      'No menu do seu perfil, toque em "Instalar app". No iPhone, siga o passo a passo que aparece.',
      'O LifePedia ganha um ícone na tela inicial e abre em tela cheia, sem a barra do navegador.',
      'Segure o ícone para criar uma tarefa ou um lançamento direto.',
      'Sem internet, aparece um aviso, e o app volta sozinho quando a conexão retorna.',
    ],
  },
  {
    id: 'termos',
    date: '2026-10-28',
    title: 'Termos de uso e Política de privacidade',
    modules: ['Conta', 'Privacidade'],
    details: [
      'Explicamos, em linguagem simples, quais dados o LifePedia usa, para quê e como eles são protegidos.',
      'No primeiro acesso depois desta mudança, o app pede que você leia e aceite.',
      'Os links ficam no rodapé da tela de entrada e em Meu perfil.',
    ],
  },
  {
    id: 'concluidas-por-dia',
    date: '2026-10-28',
    title: 'Tarefas concluídas separadas por dia',
    modules: ['Tarefas'],
    details: [
      'Na aba Concluídas, cada dia ganha um título com a quantidade de tarefas feitas.',
      'Cada tarefa mostra o horário em que foi concluída.',
    ],
  },
  {
    id: 'agenda',
    date: '2026-10-27',
    title: 'Agenda: suas tarefas numa grade de horários',
    modules: ['Agenda', 'Tarefas', 'Grupos'],
    details: [
      'Novo item Agenda no menu e nova aba Agenda em cada grupo.',
      'Veja o dia ou a semana com cada tarefa no seu horário. Tarefas no mesmo horário ficam lado a lado.',
      'Arraste uma tarefa para mudar o dia ou o horário, ou toque num horário vazio para criar.',
      'O app avisa quando duas tarefas batem de horário.',
    ],
  },
  {
    id: 'responsaveis',
    date: '2026-10-27',
    title: 'Tarefas de grupo para mais de uma pessoa',
    modules: ['Tarefas', 'Grupos'],
    details: [
      'Escolha várias pessoas como responsáveis, ou "Todos do grupo".',
      'Decida se basta uma pessoa concluir ou se cada um precisa concluir a sua parte.',
      'Leve uma tarefa pessoal para um grupo pelo menu da tarefa.',
    ],
  },
  {
    id: 'chat',
    date: '2026-10-27',
    title: 'Chat nos grupos',
    modules: ['Grupos', 'Chat'],
    details: [
      'Converse com os membros na aba Chat de cada grupo.',
      'Responda a uma mensagem específica, edite ou apague as suas.',
      'Mensagens não lidas aparecem como uma bolinha na foto do grupo, no menu.',
    ],
  },
  {
    id: 'google',
    date: '2026-10-27',
    title: 'Entre com a sua conta Google',
    modules: ['Conta'],
    details: [
      'Entre ou crie sua conta com um toque pelo Google.',
      'Já tem conta? Vincule o Google em Meu perfil e passe a entrar sem digitar a senha.',
      'Marque "Lembrar de mim" para continuar conectado neste aparelho.',
    ],
  },
  {
    id: 'investimentos',
    date: '2026-10-27',
    title: 'Investimentos separados e parcelas mais flexíveis',
    modules: ['Finanças', 'Grupos'],
    details: [
      'O que você lança na categoria Investir aparece à parte das despesas, com o total investido no mês.',
      'Adiante ou adie a parcela de uma compra, sozinha ou junto com as seguintes.',
      'As observações dos lançamentos aceitam textos bem maiores.',
    ],
  },
]
