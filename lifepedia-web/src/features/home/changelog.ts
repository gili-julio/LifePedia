/** Áreas do app afetadas por uma mudança (viram badges). */
export type ChangeModule =
  | 'Geral'
  | 'Finanças'
  | 'Financiamentos'
  | 'Tarefas'
  | 'Agenda'
  | 'Frequência'
  | 'Notas'
  | 'Grupos'
  | 'Chat'
  | 'Amigos'
  | 'Notificações'
  | 'Conta'
  | 'Privacidade'
  | 'Arquitetura'
  | 'Dev'
  | 'Integrações'
  | 'Entregas'
  | 'Veículos'

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
    id: 'busca',
    date: '2026-11-17',
    title: 'Busca: encontre lançamentos, tarefas, notas e tudo o mais de qualquer tela',
    modules: ['Geral', 'Finanças', 'Tarefas', 'Notas', 'Grupos', 'Privacidade'],
    details: [
      'Nova busca no topo de todas as telas (ou Ctrl K e "/"): procura em lançamentos, fixos, cartões, tarefas, agenda, notas, entregas, veículos, grupos, amigos, Dev e Arquitetura de uma vez.',
      'Não precisa acertar acento nem maiúscula, e um erro de digitação ainda encontra ("padaira" acha "Padaria").',
      'Cada resultado mostra uma prévia com os detalhes, as palavras ficam destacadas e, nas notas, aparece o trecho onde achou.',
      'Filtre por módulo, período, entrada ou saída e valor, ou digite o valor na busca: "> 100", "até 50", "100-200" ou "50,90".',
      'Também leva às telas e a ações rápidas, como novo lançamento, compra rápida no cartão e importar fatura, e lembra as buscas e itens recentes neste aparelho até você sair.',
      'Dentro de um grupo, a busca começa só no que é do grupo.',
      'Lançamentos: busque em todos os meses, veja quanto somou tudo o que achou e marque vários para mudar categoria, mês ou banco, ou excluir de uma vez.',
      'Campo de busca também em Fixos, Tarefas, Categorias, Entregas, Veículos, Financiamentos, Grupos, Amigos, Dev, Arquitetura e nas abas de lançamentos e fixos do grupo.',
    ],
  },
  {
    id: 'carteira',
    date: '2026-11-17',
    title: 'Carteira: acompanhe a fatura do cartão enquanto ela está aberta',
    modules: ['Finanças', 'Grupos', 'Notificações', 'Conta', 'Privacidade'],
    details: [
      'Nova tela Carteira: cada cartão aparece desenhado com o valor da fatura atual, se está aberta, fechada, paga ou vencida, e uma linha do ciclo até o fechamento e o vencimento.',
      'Com o dia do fechamento e do vencimento cadastrados, a compra no cartão vai sozinha para a fatura certa, e as parcelas para as seguintes.',
      'Na fatura: previsão de quanto vai fechar pelo seu ritmo de compras, média das últimas, meta da fatura, limite livre e as próximas faturas já comprometidas.',
      'Contas com o saldo de hoje e do fim do mês, já descontando as faturas que ela paga.',
      'Lançamento rápido com valor, descrição e cartão, e a opção de colar o aviso de compra do banco para preencher tudo.',
      'Botão "Paguei" para marcar a fatura como paga sem contar o gasto duas vezes.',
      'Ao importar a fatura fechada, o que você já tinha lançado fica conferido em vez de duplicar, e aparece o que você lançou e não veio na fatura. Também dá para importar a fatura ainda aberta.',
      'Recalcular faturas: mostra os lançamentos antigos que estão na fatura errada e só muda os que você marcar.',
      'Avisos no celular 3 dias antes de fechar e de vencer, e quando a fatura passa de 80% e 100% da meta.',
      'No financeiro do grupo, os cartões que os membros escolherem mostrar, com a fatura aberta e quanto do gasto do grupo está em cada um.',
      'Os exemplos de nomes nas telas de Dev e nos textos de ajuda agora são fictícios.',
    ],
  },
  {
    id: 'veiculos',
    date: '2026-11-16',
    title: 'Veículos: km, abastecimentos, manutenção e documentos',
    modules: ['Veículos', 'Finanças', 'Grupos', 'Amigos', 'Notificações', 'Conta', 'Privacidade', 'Integrações'],
    details: [
      'Nova área Veículos: cadastre carro, moto, caminhão ou bicicleta com foto, placa e o modelo da Tabela FIPE, que mostra o valor do veículo e atualiza todo mês.',
      'Anote o km de tempos em tempos: o app calcula quantos km você roda por dia e prevê quando cada troca vai chegar.',
      'Lance abastecimentos (litros, posto, tanque cheio) e outros gastos com foto do comprovante; cada um já entra no seu financeiro e, se quiser, no do grupo.',
      'Veja o consumo em km/l, o custo por km, o preço médio do litro e o gasto de cada mês em gráficos.',
      'Itens de manutenção (óleo, corrente, filtros, freios...) a cada tantos km ou meses, com uma tabela sugerida pronta, plano de revisões e documentos como IPVA, licenciamento, seguro e CNH.',
      'Avisos no celular quando algo está chegando e quando passou da hora.',
      'Compartilhe com amigos ou grupos escolhendo se a pessoa só vê, pode lançar ou pode editar; quem recebe vê de quem é o veículo.',
      'Conectar o Google Agenda voltou a funcionar: a volta do Google não dava mais erro.',
    ],
  },
  {
    id: 'entregas-e-simulador',
    date: '2026-11-12',
    title: 'Rastreio de entregas e simulador do financeiro',
    modules: ['Entregas', 'Finanças', 'Financiamentos', 'Grupos', 'Notificações', 'Conta', 'Privacidade'],
    details: [
      'Nova área Entregas: cole o código de rastreio que a loja mandou, dê um nome e uma foto ao item e acompanhe as etapas (postado, em trânsito, saiu para entrega, entregue) com todos os eventos.',
      'Mostra a previsão de entrega, há quantos dias está a caminho e avisa quando a entrega fica parada.',
      'Avisos no celular quando sai para entrega, é entregue, chega para retirar, tem problema ou fica aguardando pagamento de imposto ou taxa.',
      'Endereços de entrega salvos com apelido (Casa, Trabalho...), preenchidos pelo CEP e reaproveitados nas próximas compras; também em Meu perfil.',
      'Compartilhe uma entrega com um grupo: quem mora junto vê o andamento e recebe os avisos.',
      'Entregues vão para o histórico uma semana depois; o Início mostra o que chega hoje.',
      'Novo Simulador no Financeiro: veja o saldo dos próximos meses com seus fixos e a média dos ocasionais, some ganhos e gastos livres e compare com o cenário sem mudanças.',
      'Simule investimentos no CDI, Tesouro Selic, IPCA+, prefixado ou poupança, com as taxas atuais do Banco Central, aporte mensal e desconto do imposto de renda.',
      'Veja os valores em reais de hoje (descontada a inflação), salve simulações e compare até três no mesmo gráfico.',
      'Os índices do Banco Central nos financiamentos voltaram a atualizar.',
      'A Política de privacidade passou a explicar as entregas (só o código de rastreio vai ao serviço de rastreio).',
    ],
  },
  {
    id: 'dev-e-integracoes',
    date: '2026-11-11',
    title: 'Módulo Dev e central de integrações',
    modules: ['Dev', 'Integrações', 'Agenda', 'Tarefas', 'Amigos', 'Notificações', 'Privacidade'],
    details: [
      'Nova central de Integrações: ligue Google Agenda, GitLab, GitHub e Duolingo, veja o que cada um traz, o passo a passo para conectar e se cada conta está sincronizando.',
      'Dá para ligar várias contas do mesmo serviço (Gmail pessoal e do trabalho, por exemplo), cada uma com apelido e cor.',
      'Novo módulo Dev: um projeto por trabalho, com atalhos para board, homologação e documentação, e os repositórios de cada um.',
      'Painel "Hoje no dev" com as reuniões do dia, seus MRs, as revisões pedidas a você, pipelines quebradas e issues.',
      'Raias automáticas: ao abrir o MR, a issue ganha a etiqueta que você escolheu (ex.: esperando MR); ao aceitar, vai para a próxima (ex.: livre para testes).',
      'Issues atribuídas a você viram tarefas e concluem sozinhas quando fecham. Se quiser, concluir a tarefa fecha a issue.',
      'Resumo do daily montado pela IA (ontem, hoje e bloqueios), pronto para copiar.',
      'Gráfico de atividade com commits, MRs e revisões das últimas semanas, e avisos no celular quando a pipeline falha ou o MR é aprovado, aceito ou comentado.',
      'Os eventos do Google Agenda aparecem na sua Agenda, com a cor de cada conta e o link da chamada.',
      'Ofensiva do Duolingo no Início e duas conquistas novas que seus amigos veem.',
      'A política de privacidade explica as contas ligadas: aceite a versão nova ao entrar.',
    ],
  },
  {
    id: 'menu-do-seu-jeito',
    date: '2026-11-10',
    title: 'Menu do seu jeito e correções nas notas e na maquete',
    modules: ['Geral', 'Notas', 'Arquitetura', 'Privacidade'],
    details: [
      'No perfil, em Menu, escolha o formato no computador: completo, como antes, ou só com ícones, que libera espaço na tela. A escolha vale em todos os seus aparelhos.',
      'Desligue os módulos que você não usa: eles somem do menu e do Início, e nada é apagado.',
      'Notas com checklist voltam a abrir formatadas no celular e no tablet, mesmo depois de usar o modo lado a lado no computador.',
      'No modo Markdown das notas, um aviso explica que a formatação aparece como símbolos.',
      'A maquete em tela cheia agora mostra comentários, menus e avisos, e no iPhone ocupa a tela toda.',
    ],
  },
  {
    id: 'maquete-acabamentos-comodos',
    date: '2026-11-10',
    title: 'Maquete: acabamentos, cômodos, noite e passeio pela casa',
    modules: ['Arquitetura', 'Privacidade'],
    details: [
      'Teste cores e texturas de paredes, pisos e móveis: toque na superfície ou escolha na lista de materiais. Há texturas prontas de madeira, pisos, pedras, tijolo e tecidos, e as combinações podem ser salvas como variantes.',
      'Compare lado a lado, arrastando a divisória: o original e uma variante, ou duas versões da casa.',
      'Veja a maquete realista, como maquete branca ou em raio-x, com as paredes transparentes.',
      'Toque numa peça para ver o nome, o tipo, as medidas, o material e o link de compra quando ele vem no nome.',
      'Marque os cômodos tocando no piso: o app acha as paredes e calcula a área. Depois é só tocar no nome para ir até lá.',
      'Ao caminhar, o modo fantasma atravessa paredes e portas fechadas e deixa subir e descer, e uma planta pequena mostra onde você está.',
      'A noite acende uma luz no teto de cada cômodo, e quem edita pode colocar outras.',
      'Separe a casa por andar e por tipo de peça (portas, janelas, móveis, louças, eletrodomésticos).',
      'Faça um tour animado pelas vistas salvas e baixe uma foto em alta da vista atual.',
      'Ficou mais fácil comentar: use Novo comentário ao lado da maquete. Tocar fora da casa agora avisa.',
      'A compressão guarda as texturas ocupando menos espaço e mantém os nomes dos componentes do SketchUp.',
      'A Política de privacidade agora cita as variantes, os cômodos e as luzes, que também aparecem no link público.',
    ],
  },
  {
    id: 'arquitetura-maquete-3d',
    date: '2026-11-10',
    title: 'Arquitetura: a maquete 3D da casa no app',
    modules: ['Arquitetura', 'Notificações', 'Privacidade'],
    details: [
      'Novo item Arquitetura no fim do menu: crie um projeto e envie a maquete exportada do SketchUp. Ela é comprimida no seu aparelho antes de subir e ocupa bem menos espaço.',
      'Gire, aproxime, ande por dentro da casa como se estivesse lá, meça distâncias, corte na altura para ver a planta de cima e esconda partes como o telhado.',
      'Veja como o sol entra e onde fica a sombra em cada mês e horário.',
      'Guarde versões da maquete, salve vistas favoritas, comente pontos da casa e junte renders, fotos e plantas em PDF.',
      'Compartilhe com amigos ou grupos (só ver, comentar ou editar) ou por um link com validade, que abre sem precisar de conta.',
      'Maquetes grandes abrem mais rápido e a navegação fica fluida, começando na última vista salva no SketchUp.',
      'Ao enviar, o app avisa se alguma textura ficou de fora (madeira, pisos, tecidos) e deixa adicionar a pasta delas.',
      'A Política de privacidade foi atualizada para explicar como guardamos e compartilhamos os projetos.',
    ],
  },
  {
    id: 'avisos-e-administracao',
    date: '2026-11-09',
    title: 'Avisos do sistema e painel de administração',
    modules: ['Geral', 'Notificações', 'Conta', 'Privacidade'],
    details: [
      'Avisos importantes, como manutenções, agora aparecem numa faixa no topo do app. Dá para fechar cada um.',
      'Você pode receber notificações da equipe do LifePedia com novidades e comunicados.',
      'A equipe ganhou um painel para acompanhar o uso do app e cuidar das contas. Ela vê só dados de cadastro e quantidades, nunca o que você registra.',
      'A Política de privacidade foi atualizada: agora guardamos a data do seu último acesso e explicamos o que a administração pode ver.',
    ],
  },
  {
    id: 'importar-pelo-site',
    date: '2026-11-01',
    title: 'Importe extratos e faturas direto pelo site',
    modules: ['Finanças', 'Notificações', 'Privacidade'],
    details: [
      'Nova forma de importar: envie seus extratos e faturas no próprio site e a IA separa tudo nas suas categorias.',
      'Pode enviar até 10 arquivos de uma vez, com até 15 MB somados e até 50 páginas por PDF, sem senha.',
      'Cada conta tem 1 envio a cada 4 horas, 5 em 7 dias e 10 em 30 dias. Os limites e o quanto você já usou aparecem na tela.',
      'A leitura continua mesmo se você sair da tela ou fechar o app. Quando terminar, avisamos com notificação.',
      'A revisão agora fica salva como rascunho, pelo site ou com o seu agente: saia, volte depois e continue de onde parou.',
      'A importação com o seu agente continua disponível na outra aba.',
      'A Política de privacidade e os Termos foram atualizados, porque os arquivos enviados pelo site vão para o Google (Gemini).',
    ],
  },
  {
    id: 'bancos',
    date: '2026-11-01',
    title: 'Bancos: saiba de onde saiu cada gasto',
    modules: ['Finanças', 'Financiamentos', 'Grupos'],
    details: [
      'Lançamentos e fixos agora guardam o banco, com o logo, e se foi na conta ou no cartão.',
      'Nova tela Bancos em Finanças: quanto saiu de cada banco no mês, as maiores categorias de cada um e a comparação dos últimos 6 meses.',
      'Filtre lançamentos e fixos por banco; os totais da tela acompanham o filtro.',
      'Na importação com IA, o banco vem do próprio extrato ou fatura. Dá para corrigir linha por linha ou aplicar o mesmo banco a todas.',
      'Um fixo sem banco passa a usar o banco do extrato ou da fatura que trouxe o pagamento dele.',
      'Tem itens antigos? Selecione vários na lista e defina o banco de uma vez. O que não tiver banco aparece como Sem banco.',
      'Grupos mostram o banco dos itens e os gastos por banco. No financiamento, o banco escolhido vai para a parcela ligada às finanças.',
      'Novo lançamento já abre com o último banco que você usou.',
    ],
  },
  {
    id: 'financiamentos',
    date: '2026-10-30',
    title: 'Financiamentos: simule antes de comprar e acompanhe os que estão em andamento',
    modules: ['Financiamentos', 'Finanças', 'Grupos', 'Notificações'],
    details: [
      'Nova área Financiamentos em Finanças: escolha casa, apartamento, carro, moto ou outro bem, novo ou usado, e informe preço, entrada e prazo.',
      'Sem precisar saber os juros: mostramos a parcela com taxas boas, médias e altas, tiradas das médias que os bancos informam ao Banco Central, e a lista de bancos do mais barato ao mais caro.',
      'Compare SAC e Price, veja vários prazos lado a lado, o custo efetivo total e se a parcela cabe na sua renda.',
      'Gastos comuns já entram na conta (ITBI, cartório, seguros, IOF, tarifas, IPVA, seguro e revisões) e você pode ajustar cada valor; no imóvel dá para usar o FGTS.',
      'Salve simulações para comparar depois ou transforme uma em financiamento.',
      'No financiamento em andamento: saldo devedor, quanto já foi quitado, próxima parcela, quanto custa quitar hoje e a tabela de parcelas para baixar.',
      'Simule e registre amortizações extras para reduzir o prazo ou a parcela, e cadastre os gastos do bem com aviso antes de vencer.',
      'Ligue as parcelas às suas finanças: viram uma despesa fixa com o valor certo de cada mês.',
      'Nos grupos, dividam o financiamento com a parte de cada um; no planejamento, um item pode ir direto para o simulador.',
    ],
  },
  {
    id: 'notas',
    date: '2026-10-29',
    title: 'Notas: anotações com formatação, pastas e modelos',
    modules: ['Notas', 'Grupos'],
    details: [
      'Nova área Notas no menu Organização para anotar aulas, reuniões, ideias e listas.',
      'Formate com a barra de botões ou digitando "/" para escolher títulos, listas, checklist, tabela, código e imagem.',
      'Veja a nota pronta, o texto em markdown ou os dois lado a lado no computador.',
      'Salva sozinha enquanto você escreve e avisa se a nota mudou em outro lugar.',
      'Organize com pastas, etiquetas, cores e ícones; fixe as importantes no topo, arquive ou mande para a lixeira (fica 30 dias).',
      'Comece de um modelo pronto (reunião, aula, diário, compras) ou crie os seus.',
      'Escreva [[Nome da nota]] para ligar notas entre si e veja onde cada nota é citada.',
      'Ligue uma nota a uma tarefa ou a um compromisso da Frequência e abra as notas direto pelo menu deles.',
      'Grupos ganham a aba Notas, com o registro de quem fez o quê em cada nota.',
      'Baixe qualquer nota como arquivo .md e veja as notas fixadas e recentes no Início.',
    ],
  },
  {
    id: 'frequencia-restantes',
    date: '2026-10-29',
    title: 'Quantos encontros ainda restam',
    modules: ['Frequência'],
    details: [
      'O card de cada compromisso com data de fim mostra quantos encontros ainda vão acontecer, de hoje até o fim.',
      'Dias que já passaram e os marcados como sem aula não entram, para comparar com as faltas que ainda cabem.',
    ],
  },
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
