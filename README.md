# LifePedia

> Sua vida inteira, organizada num só lugar.

O **LifePedia** é uma plataforma pessoal para **centralizar dados da nossa vida** — finanças, metas, saúde,
rotina, documentos — e transformar isso em uma visão clara do todo. Em vez de uma planilha para cada
assunto e vários apps que não conversam entre si, tudo mora num lugar só, com o mesmo login e a mesma cara.

No celular, o site **instala como app** (PWA): ícone na tela de início, tela cheia e atalhos (nova tarefa, novo
lançamento, agenda). Basta abrir o site e tocar em *Instalar app* no menu do usuário.

A ideia é crescer **por módulos**: cada área da vida vira um módulo independente (API + telas), mas todos
compartilham usuário, autenticação, design e padrões.

## Módulos

| Módulo | Status | O que faz |
|---|---|---|
| **Usuários** | ✅ v1.3 | Cadastro com **@username** único e obrigatório, login (sessão em cookie HttpOnly, **lembrar de mim** por 30 dias), **entrar/cadastrar com o Google** e vincular no perfil, **notificações** (também **push** no celular/computador), **exportar/importar dados** e **excluir conta** |
| **Grupos** | ✅ v1.3 | Grupos com nome e **imagem** (recorte antes de salvar), convites diretos (aceitar/recusar) ou por **link**, admins e membros, **chat** em tempo real (responder, editar, apagar, não lidas na sidebar), **atalhos na sidebar** (até 3 fixados ou com atividade recente) e **planejamento** (compras e investimentos com prazo, simulação, cenários e "e se") |
| **Amigos** | ✅ v1.0 | Pedidos de amizade por nome ou @username (aceitar/recusar/cancelar), perfil do amigo e **conquistas** com níveis (tarefas, hábitos, amigos, grupos, lançamentos) |
| **Financeiro** | ✅ v1.3 | Ganhos e despesas **fixos** (mensais), lançamentos **ocasionais**, **categorias personalizáveis com cor**, comparativo do mês, histórico, gastos por categoria, **investimentos** (fora das despesas, somam na reserva do grupo), **adiantar/adiar parcelas**, **importação de extrato/fatura com IA, pelo site ou com o seu agente** e **finanças em grupo** |
| **Financiamentos** | ✅ v1.0 | **Simulador** sem pedir juros (casa, apartamento, carro, moto ou outro bem, novo ou usado): cenários com as **taxas médias dos bancos** do Banco Central, **ranking por banco**, **SAC x Price**, prazos lado a lado, **gastos padrão** editáveis (ITBI, cartório, seguros, IOF, tarifas, IPVA...), **CET**, **FGTS**, **cabe na renda**, gráfico e tabela de parcelas (CSV); **simulações salvas**; **financiamentos em andamento** com saldo, % quitado, **quitar hoje**, **amortização extra** (prévia e registro), **correção por índice** (TR/IPCA/INCC), **gastos do bem** com aviso, **parcelas nos fixos** (valor de cada mês), **financiamento do grupo** com a parte de cada um, atalho no **planejamento** e card no **Início** |
| **Tarefas** | ✅ v1.1 | Tarefas pessoais e **em grupo** para hoje, outros dias ou sem data, **atrasadas**, **repetição** (a cada N dias/semanas/meses/anos, dias da semana, dia do mês, a partir da conclusão), prioridade, horário e duração, **subtarefas**, **listas** e **etiquetas**; **agenda** em grade de horários (dia/semana, pessoal e no grupo); no grupo, **vários responsáveis** ou **todos**, "basta um" ou "cada um conclui a sua", **rodízio**, **comentários** e avisos; **levar tarefa pessoal para um grupo**; **semana** (arrastar muda o dia), **hábitos** (sequência e % do mês), **lembretes** e **ligação com fixos do financeiro** |
| **Notas** | ✅ v1.0 | Anotações em **markdown** com editor visual (barra, menu "/", checklist, tabela, código com cores, fotos), modo **markdown** e **lado a lado**, salvar automático, **pastas**, **etiquetas**, cor/ícone, **fixar**, **arquivar**, **lixeira** (30 dias), **modelos**, links `[[Nome]]` entre notas, ligação com **tarefa** ou **compromisso**, **notas do grupo** com atividade, baixar `.md` e card no **Início** |
| **Frequência** | ✅ v1.0 | Compromissos fixos (ex.: matérias) com dias da semana, horário, período e **limite de faltas**; marcar presença, falta ou sem aula em cada dia; **tarefa ligada** concluída conta presença; aparece na **agenda** e no **Início**; **importar do SIGAA** (UFRN) colando a página de frequência |
| Metas | 💡 ideia | Objetivos pessoais e financeiros com acompanhamento |
| Saúde | 💡 ideia | Consultas, exames, medicamentos, hábitos |
| Documentos | 💡 ideia | Documentos importantes, vencimentos, lembretes |

### Financeiro v1 — conceitos

- **Fixos (recorrentes)**: valores que se repetem todo mês (salário, aluguel, internet...). Têm mês de
  início, mês de fim opcional e podem ser pausados. Entram automaticamente no cálculo de cada mês em que estão vigentes.
  Um fixo pode ter o **valor real de um mês** (ex.: conta de luz que veio diferente), que substitui o cadastrado só naquele mês.
- **Ocasionais**: lançamentos avulsos com data (uma compra, um freela, um presente) e o **mês em que contam**
  (padrão: o da data; compra no cartão: o mês da fatura).
- **Tornar fixo**: num ocasional, abre o cadastro de fixo já preenchido; ao salvar, os ocasionais de mesmo nome dentro
  da vigência (você confirma quais) são removidos para não contar em dobro.
- **Categorias**: cada usuário tem as suas (de despesa e de ganho), com nome e **cor**. O cadastro já cria
  19 categorias padrão, que podem ser editadas, excluídas (movendo os lançamentos para outra) ou complementadas.
  A cor é usada nos gráficos e listas.
- **Comparativo**: para qualquer mês, mostra ganhos x despesas separando fixos e ocasionais, saldo,
  **% da renda fixa comprometida com despesas fixas**, histórico dos últimos 6 meses e composição por categoria
  (cada categoria abre os itens do mês agrupados pelo nome, ex.: "Lanche IMD 5×").
- **Ocultar valores** (olhinho): no topo de todas as telas e no cabeçalho das telas com dinheiro (financeiro, grupo).
  Troca todo valor em dinheiro do app por `R$ ••••` (totais, listas, gráficos, planejamento, Início, fixo da tarefa);
  campos de formulário abertos para editar continuam com o valor. A escolha fica lembrada só neste aparelho.
- **Investimentos**: despesa (fixa ou ocasional) numa categoria chamada **"Investir"**, **"Investimento"** ou
  **"Investimentos"** (de despesa; acento e maiúscula não importam) é dinheiro guardado: fica **fora das despesas** (e
  do gráfico por categoria), mas **desconta do saldo**. A visão geral mostra o card *Investido* (com % dos ganhos) e o
  gráfico dos últimos 12 meses. Num grupo, esses lançamentos **somam na reserva** (ver Planejamento).
- **Parcelas**: lançamento com a parcela no fim do nome (ex.: "Loja (3/10)", como vem da importação ou do
  planejamento) ganha a ação **Adiantar ou adiar parcela**: escolhe o novo mês e se vai **só esta** ou **esta e as
  seguintes** (mesmo deslocamento; nunca antes do mês da compra). No grupo, vale para as cópias de quem levou.
- **Observações** (lançamentos, fixos, itens do planejamento e tarefas): até 10 mil caracteres.
- **Listagens** (lançamentos e fixos): paginadas e ordenadas pela API (10/25/50/100 por página, clique no
  cabeçalho para ordenar). Os totais do topo sempre consideram todos os itens do mês.
- **Importar com IA**: dois caminhos para **extratos bancários** e **faturas do cartão** (juntos ou separados, de um ou
  vários meses), com o mesmo prompt, que traz as categorias, os fixos e os nomes que você já usa nos lançamentos.
  - **Pelo site** (padrão, com `AI_API_KEY`): você envia os arquivos (até 10 por vez, 15 MB somados, até 50 páginas por
    PDF, sem senha) e a API chama o Gemini **em segundo plano**: dá para sair da tela ou fechar o app. Ao terminar chegam
    toast, notificação e push. Limites por conta: **1 envio a cada 4 horas, 5 em 7 dias e 10 em 30 dias**, mostrados na
    tela; leitura que falha não conta. Os arquivos ficam só na memória enquanto a leitura roda e vão inteiros para o Google.
  - **Com o seu agente**: você copia o prompt para um agente (ChatGPT, Claude, Gemini...) junto com o documento; ele
    devolve um JSON no modelo `lifepedia/transactions@4` (com o banco de cada item) e você cola a resposta. Aqui o
    LifePedia não envia o documento a lugar nenhum.
  - Nos dois, você revisa numa prévia editável e importa tudo de uma vez. A revisão fica salva como **rascunho** na API a
    cada edição: saia e volte sem perder nada. Uma revisão aberta por pessoa; sem mexer por 30 dias, ela é apagada.
  - O agente marca a **origem** de cada item (extrato ou fatura) e tira as datas do documento: item de extrato conta
    no mês da data; compra no cartão conta no mês de vencimento da sua fatura. Na prévia dá para conferir e trocar a
    origem de cada linha.
  - Cada item traz a **descrição do documento** (guardada como veio, não editável) e um **nome padronizado**
    (editável): o mesmo lugar ganha sempre o mesmo nome, e nomes com as mesmas palavras em outra ordem
    ("Natal IMD" / "IMD Natal") são unificados — assim o agrupamento por nome da visão geral fica certo.
  - Itens que **correspondem a um fixo** (marcados pelo agente ou detectados por valor/categoria/descrição/dia) não viram
    lançamento: se o valor for igual, já estão contados; se for diferente, viram o **valor real do fixo naquele mês**.
  - Itens já importados antes vêm desmarcados. No extrato, o pagamento da fatura do cartão é ignorado (a fatura é
    importada à parte, item a item), assim como transferências entre contas próprias e aplicações/resgates.

### Usuários e grupos — conceitos

- **Entrar**: e-mail e senha ou **botão do Google** (se `GOOGLE_CLIENT_ID` estiver configurado). **Lembrar de mim**
  mantém a sessão por 30 dias (cookie persistente); sem marcar, a sessão acaba ao fechar o navegador (ou em 8h). Sair
  encerra a sessão no servidor (o token deixa de valer na hora).
  - **Google**: a primeira entrada cria a conta (sem senha; o app leva ao perfil para escolher o @username). E-mail que
    já tem conta e que o Google confirma é **vinculado automaticamente**. Em *Meu perfil → Conta Google* dá para
    vincular ou desvincular (conta criada pelo Google não desvincula, pois ficaria sem como entrar). Excluir conta sem
    senha pede para confirmar com o Google.
- **Termos de uso e Política de privacidade** (LGPD): páginas públicas em `/termos` e `/privacidade` (links no login,
  no cadastro, no perfil e nas próprias páginas). O cadastro exige marcar "Tenho 18 anos ou mais, li e aceito"; quem
  entra pelo Google ou chega depois de uma versão nova vê um aviso que bloqueia o app até aceitar (ou sair). A versão e
  a data do aceite ficam na conta. Controlador e contato de privacidade: `features/legal/legal.ts` (web).
- **@username**: obrigatório e único (3 a 30 caracteres: letras minúsculas, números, ponto e `_`). Contas antigas sem
  ele recebem um aviso ao abrir o app e vão para **Meu perfil** até completar o cadastro.
- **Notificações**: no menu do perfil (avatar com contador): convite para grupo
  ("*Fulano* te chamou para participar do grupo *X*" · **Ver**), com a foto do grupo, membro que excluiu a conta
  (avisa também quem virou admin) e tarefas de grupo (responsável, sua vez no rodízio, alguém concluiu a sua,
  comentário, atrasada), **lembretes** de tarefa (na hora ou X min antes; com o app aberto aparecem também na tela)
  e **amizade** (pedido recebido, pedido aceito) — **Ver** abre a tarefa (em *Tarefas* ou na aba *Tarefas* do grupo)
  ou *Amigos*.
- **Notificações push**: em *Meu perfil → Notificações no aparelho*, cada aparelho ativa as suas. Chegam com o app
  fechado as mesmas notificações acima e as **mensagens do chat** dos grupos (a última de cada grupo substitui as
  anteriores). Tocar abre a tela certa. Com o app aberto e em foco, não aparece nada (a tela já se atualiza). Sair da
  conta para o push do aparelho; entrar de novo com a mesma conta religa sozinho. No iPhone/iPad só pelo app instalado.
- **Início** (`/`, logo no topo): resumo do dia — tarefas de hoje e atrasadas, sequência dos hábitos e o mês no
  financeiro (entradas, saídas, saldo) — e, por último, **Mudanças recentes no sistema**: linha do tempo com as 3
  novidades mais recentes (título, áreas afetadas em badges, "Novo" por 7 dias; tocar abre o detalhe em linguagem
  simples) e *Ver todas* (`/novidades`).
- **Meus dados** (em *Meu perfil*):
  - **Exportar (.json)**: arquivo `lifepedia/account@5` com perfil, categorias, fixos (com os valores de cada mês),
    lançamentos e **tarefas pessoais** (com listas, etiquetas e o que foi feito em cada ocorrência) — é o que dá para
    importar; arquivos `@1` a `@4` antigos continuam aceitos e o `@1` não mexe nas tarefas — e, só como registro, os seus itens em cada grupo (lançamentos, fixos,
    planejamento), a **frequência** (compromissos com as marcações) as **notas** pessoais (com pasta e etiquetas) e os **financiamentos** e **simulações** pessoais. **Planilha (.xlsx)**: as mesmas informações em abas, para ver no Excel/Google Planilhas.
  - **Importar** (nesta ou em outra conta): mostra antes o que vai acontecer. *Somar* junta ao que já existe
    (categoria com mesmo tipo e nome é reaproveitada; lançamento/fixo igual não duplica). *Substituir* apaga os
    lançamentos e fixos pessoais antes (e as tarefas pessoais, se o arquivo tiver tarefas; pede confirmação); categorias sem uso saem, e itens **sincronizados** em grupos
    viram **cópia** e continuam no grupo. Perfil e itens de grupo do arquivo não são importados.
- **Amigos** (menu *Social → Amigos*): *Adicionar amigo* busca por nome ou @username e envia o pedido (se a pessoa
  já tinha pedido, viram amigos na hora). Pedidos recebidos e enviados ficam em *Amigos → Pedidos*; desfazer a amizade
  fica no perfil do amigo. Até 200 amigos e 50 pedidos sem resposta.
  - **Conquistas** (*Amigos → Conquistas* e no perfil de cada amigo): cada nível fica gravado com a data em que foi
    conquistado e **não se perde** (excluir tarefas, sair de grupo etc. não tira o nível); no nível máximo a contagem
    para. Níveis: *Mãos à obra* (tarefas concluídas), *Trabalho em equipe* (tarefas de grupo concluídas),
    *Constância* (maior sequência num hábito em andamento), *Turma* (amigos), *Em grupo* (grupos) e *Organizado*
    (lançamentos registrados, **só você vê**). Amigos veem as demais no seu perfil.
- **Foto de perfil** (em *Meu perfil*): mesmo recorte da imagem do grupo. Aparece na sidebar, nos membros, nos
  convites, nos comentários e em quem cadastrou cada item do grupo. Trocar ou remover apaga o arquivo antigo do disco.
- **Excluir conta** (em *Meu perfil*): mostra o que some e o que acontece em cada grupo, oferece baixar os dados e
  pede **senha (ou confirmar com o Google, em conta sem senha) + digitar EXCLUIR**. Em cada grupo é como "Sair do grupo": seus itens (lançamentos, fixos,
  planejamento) saem, se você era o único admin o membro mais antigo vira admin, e grupo só seu é excluído. Os membros
  recebem um aviso. Não dá para desfazer.
- **Chat do grupo** (aba *Chat*, estilo WhatsApp): foto, nome e @ de quem escreveu, separador por dia, **responder**
  (cita a mensagem; tocar na citação leva até ela), **editar** e **apagar** a própria mensagem ("editada" / "Mensagem
  apagada"). Mensagens chegam na hora (SSE). As **não lidas** aparecem como bolinha na foto do grupo na sidebar e na
  aba *Chat*; abrir o chat marca como lido. Até 2.000 caracteres e 30 mensagens por minuto por pessoa. Excluir a conta
  apaga as suas mensagens; sair do grupo não (o histórico fica).
- **Grupos** (menu *Social → Grupos*): nome e imagem (JPEG/PNG/WebP; você arrasta, dá zoom e gira antes de salvar,
  como no WhatsApp). Quem cria vira **admin**; admins editam, convidam, geram o link, promovem e removem membros.
  Último admin que sai passa o posto ao membro mais antigo; último membro que sai apaga o grupo.
  - **Sidebar**: mostra 3 grupos com a foto: primeiro os **fixados** (alfinete no item da sidebar ou no cartão em
    *Grupos*; até 3, cada pessoa fixa os seus), depois os de **atividade mais recente** (qualquer escrita no grupo:
    tarefa, comentário, lançamento, fixo, planejamento, membros; fixar não conta). *Ver todos* abre a lista.
  - **Convite direto**: busca por nome ou @username; a pessoa recebe a notificação e aceita ou recusa em
    *Grupos → Solicitações (n)*.
  - **Link de convite**: vale 7 dias; gerar outro invalida o anterior; dá para desativar. Quem abre logado entra direto.
- **Bancos**: cada ocasional e fixo guarda o banco (logo, nome e se foi conta ou cartão), escolhido na mão, vindo da
  importação ou definido em lote nos itens antigos. A tela *Bancos* soma os gastos por banco, compara os últimos 6 meses
  e leva às listas filtradas; o que não tem banco aparece como *Sem banco*. Banco fora da lista: *Outro banco*, com o
  nome digitado. Logos embutidos no build (a CSP não aceita imagem externa), adaptados do repositório público
  [Bancos em SVG](https://github.com/Tgentil/Bancos-em-SVG), que não declara licença; as marcas são dos bancos.
- **Finanças em grupo**: cada membro **leva** para o grupo os fixos e ocasionais que quiser — pelo seletor
  *Levar meus itens* do grupo ou pela seção *Levar para grupos* ao cadastrar um fixo/ocasional (um ou vários grupos).
  - **Sincronizado**: espelho do item pessoal (editar ou excluir o pessoal reflete no grupo).
  - **Cópia**: independente; só quem levou edita. Fixo copiado usa o valor cadastrado (sem os ajustes mensais).
  - **Direto no grupo**: *Novo lançamento* (aba Lançamentos) e *Novo fixo* (aba Fixos) cadastram um item só do grupo,
    sem item pessoal — funciona como uma cópia de quem cadastrou (categoria dele, só ele edita).
  - Membros veem tudo do item (descrição, valor, categoria, data, quem levou). Quem levou (ou um admin) pode tirá-lo
    do grupo; quem sai do grupo leva seus itens junto.
  - **Visão do grupo**: totais do mês, **investido no mês** e **reserva total**, % da renda fixa comprometida,
    **quanto cada membro trouxe**, fixos x ocasionais, últimos 6 meses, investido nos últimos 12 meses e categorias (somadas pelo nome entre os membros; cada uma abre os itens agrupados).
- **Planejamento em grupo** (aba *Planejamento* do grupo): coisas que o grupo quer **comprar ou investir** até um mês.
  - Item: nome, valor, prazo, à vista ou **parcelado** (parcelas a partir do prazo), observação, **link** opcional e foto.
    Ao colar o link, a API tenta ler **nome, preço e foto** da página (Amazon e KaBuM funcionam). Se a loja bloqueia
    (Mercado Livre, Magalu, Shopee) ou falta preço/foto, o nome vem do próprio link e a API busca um **produto parecido
    no Buscapé** (comparador de preços): traz o menor preço e a foto e avisa de onde veio ("preço/foto de referência").
    Sem link, dá para **buscar preço e foto pelo nome** do item.
  - **Saldo previsto por mês** = ganhos fixos − despesas fixas do grupo (mês a mês, com vigência e ajustes) + média dos
    ocasionais (ganhos − despesas) dos últimos 3, 6 ou 12 meses completos — só os meses **com lançamentos** contam
    (mês vazio não puxa a média para baixo); sem nenhum, usa o **mês atual**. A tela mostra quais meses entraram.
    Ou um **valor definido** pelo grupo.
    **Reserva** = valor informado (guardado fora dos lançamentos) + **investimentos lançados no grupo** até o mês
    atual (despesas em categoria "Investir"/"Investimento", fixas e ocasionais). Por isso os investimentos não descontam
    do saldo previsto da simulação: o dinheiro continua do grupo, na reserva.
  - **Simulação**: saldo acumulado mês a mês, descontando os pagamentos dos itens **ativos** na ordem de **prioridade**
    (o 1º recebe o dinheiro primeiro; muda **arrastando** o item pela alça ⋮⋮ — mouse, toque ou teclado — ou pelas setas). Cada item mostra se **cabe no prazo**, **quanto falta**, a partir de quando daria
    e quanto guardar por mês (à vista) ou a parcela. **Linha do tempo** com e sem os planos.
  - **Ativar/desativar** item (vale para o grupo), **cenários** salvos (conjuntos de itens, comparados lado a lado,
    "ver" e "aplicar") e **"e se"** (renda ou gasto extra por mês, só na tela).
  - **Marcar como comprado/feito**: sai da simulação; opcionalmente vira despesa no grupo (em nome de quem marcou, com
    a categoria dele; parcelado vira uma despesa por mês).
  - Qualquer membro cadastra e liga/desliga itens; editar/excluir: quem cadastrou ou admin. Quem sai leva os seus itens.

### Tarefas — conceitos

- **Onde**: menu *Organização → Tarefas*. Abas **Hoje** (atrasadas + hoje), **Semana** (seg–dom em colunas; arrastar
  uma pendente pela alça muda o dia só dela), **Próximos** (a partir de amanhã, 7 dias por vez, até 8 semanas),
  **Sem data**, **Hábitos** e **Concluídas** (histórico separado por dia, com quantas foram concluídas em cada dia). Filtro por lista/etiqueta e ordem: *minha ordem*
  (arrastando pela alça ⋮⋮), *prioridade* ou *horário*. Campo rápido: digitar e Enter cria a tarefa do dia.
- **Tarefa**: título, notas, dia (ou sem data), horário e duração opcionais, prioridade (nenhuma/baixa/média/alta, cor
  no círculo), **lista** (uma) e **etiquetas** (até 10), **subtarefas** (checklist, até 50).
- **"Hoje"** segue o fuso `TIME_ZONE` (padrão `America/Sao_Paulo`). Pendente de dia que já passou = **atrasada**.
- **Repetição**: a cada N dias, semanas (escolhendo os dias), meses (mesmo dia — dia 31 vira o último em meses curtos —,
  "2ª terça" ou último dia) ou anos; termina nunca, numa data ou após N vezes. **A partir da conclusão**: a próxima
  conta de quando você concluiu (ex.: regar a planta 3 dias depois).
  - **Cada ocorrência é independente**: perdeu segunda e terça, as duas aparecem atrasadas (as da mesma tarefa ficam
    juntas numa linha, com "Concluir/Pular as N atrasadas"). Atrasadas de tarefa repetida: até 90 dias para trás.
  - Por ocorrência: **concluir**, **pular**, **mover para** outro dia (só ela) e o checklist marcado só nela.
  - **Editar** ou **excluir** pergunta: *só esta*, *esta e as próximas* (a série é dividida no dia) ou *todas*. "Só
    esta" muda título, notas, horário, prioridade e dia; regra, lista, etiquetas, duração e subtarefas valem para a série.
- **Agenda** (menu *Organização → Agenda* e aba *Agenda* do grupo): grade de horários por **dia** ou **semana** (no
  celular abre em dia, com os dias da semana no topo). Cada tarefa com horário ocupa o intervalo da duração (sem duração:
  30 min); as que se cruzam ficam **lado a lado** dividindo a largura (na semana, a partir de 3 aparece "+N", que abre a
  lista; o dia mostra até 6). **Cores**: pessoal por lista (padrão), grupo ou prioridade; no grupo por responsável
  (padrão, com legenda e as fotos no bloco), lista ou prioridade. A escolha fica salva no navegador.
  - **Arrastar** o bloco muda o dia e o horário (de 15 em 15 min); em tarefa repetida, mudar o horário pergunta *só
    esta* ou *todas*. **Clicar num horário vazio** cria a tarefa já com o dia e a hora. Linha vermelha marca **agora**
    (a agenda abre rolada até ela).
  - Tarefas do dia **sem horário** viram um selo "+N sem horário" no topo da coluna, que leva para *Tarefas* (hoje ou a
    semana daquele dia).
  - **Horário visível** (botão de ajustes): início e fim (padrão 6h às 23h, mínimo 4 horas), salvo na sua conta.
    Tarefas fora dele aparecem num aviso com *Mostrar o dia todo*.
  - **Indicadores da semana** (agenda pessoal, no fim da página; o botão *Indicadores* no topo leva até eles): horas
    ocupadas na semana, de segunda a sexta e no fim de semana, **sono da semana** (com a média por noite) e **horas
    livres** já descontando o **sono de cada dia**
    (editável por dia da semana, padrão 8h, salvo na conta; mudança ainda não salva fica em laranja, com aviso e
    *Descartar*). Ocupado = tarefas com horário (sem duração: 30 min; o que
    se cruza conta uma vez; puladas não contam). Barra por dia com sono, ocupado e livre.
  - **Choque de horário**: ao criar/editar, o formulário avisa (sem impedir) se o horário bate com outra tarefa pendente
    dos responsáveis: na pessoal, a sua agenda inteira; no grupo, as do grupo dessas pessoas e, se você está entre elas,
    a sua agenda também (tarefas pessoais de outra pessoa nunca aparecem). Ao arrastar, avisa pelo que está na tela.
  - Pessoal: filtro *Pessoais e grupos / Só pessoais / cada grupo*. Grupo: filtro por responsável.
- **Hábitos**: numa tarefa com repetição em dias fixos, ligue *Acompanhar como hábito*. Mostra a **sequência** (🔥, dias
  feitos seguidos), o recorde e o **% do mês** (feitos ÷ devidos até hoje). **Pular não quebra** a sequência; só quebra
  um dia que passou sem fazer. Na aba *Hábitos*, tocar num dos últimos 7 dias marca ou desmarca. Dividir a série
  ("esta e as próximas") começa uma sequência nova.
- **Lembrete** (tarefa com horário): na hora, 5/15/30/60 min antes, 1 dia antes ou 7, 15 ou 30 dias antes; chega como notificação (tarefa
  pessoal: você; de grupo: o responsável ou, sem ninguém, todos os membros). Checado a cada minuto; com a API parada,
  recupera no máximo os últimos 10 minutos.
- **Fixo do financeiro** (só tarefa pessoal): ligue a tarefa a um fixo seu (ex.: "Pagar luz"). Ao concluir, abre
  *Registrar no financeiro?* com o valor do mês; valor diferente do cadastrado vira o **ajuste daquele mês**. Excluir o
  fixo só desliga a tarefa.
- **Listas e etiquetas** (⋮ → *Listas e etiquetas*): nome e cor; excluir não apaga tarefas, só as desliga.
- **Em grupo** (aba *Tarefas* do grupo): mesmas abas e ações; listas/etiquetas do grupo são separadas das suas.
  - **Permissões**: qualquer membro cria, edita, conclui, pula e comenta; **excluir** é de quem criou ou de um admin.
  - **Responsáveis**: ninguém, **pessoas escolhidas** (uma ou várias; tarefa nova já vem com quem cria), **todos do
    grupo** (inclusive quem entrar depois) ou **rodízio** (ordem fixa; precisa de repetição). No rodízio cada
    ocorrência é de uma pessoa, na ordem (pular também passa a vez); em "a partir da conclusão" a vez passa ao
    concluir/pular. *Mudar responsável…* põe uma pessoa só naquela ocorrência. Filtro *Responsável* (todos, minhas,
    cada um).
  - **Com mais de um responsável**: *basta um concluir* (padrão; ex.: lavar a louça) ou *cada um conclui a sua* (ex.:
    academia juntos): cada responsável marca a própria parte ("1/3 fizeram") e a ocorrência fica concluída quando
    todos marcarem; desfazer tira só a sua parte. Quem não é responsável e conclui, conclui para todos.
  - **Levar para um grupo** (⋮ de uma tarefa pessoal): a tarefa vira do grupo, com você como responsável; lista,
    etiquetas e fixo ligado ficam para trás (são pessoais). Histórico e subtarefas vão junto.
  - **Na sua tela de Tarefas** aparecem também as de grupo em que você é responsável (inclusive "todos do grupo"; com
    o nome do grupo, depois das suas na ordem manual; sem filtro de lista/etiqueta).
  - **Comentários** dentro da tarefa (apagar: quem escreveu ou admin).
  - **Avisos**: você virou responsável (não em "todos do grupo"); é a sua vez no rodízio; alguém concluiu a sua;
    comentário novo (para quem criou, responsáveis e quem já comentou); e, todo dia às 8h, a ocorrência de ontem que
    ficou pendente (para os responsáveis que não fizeram a sua parte ou, sem responsável, quem criou). Lembrete vai
    para os responsáveis que ainda não fizeram a sua parte. Nunca avisa quem fez a ação.
  - **Quem sai do grupo** deixa de ser responsável, sai dos rodízios (rodízio de uma pessoa vira responsável fixo) e
    leva os comentários; as tarefas que criou ficam no grupo.

### Frequência - conceitos

- **Compromisso** (menu *Organização → Frequência*): nome, cor, **dias da semana**, horário e duração, início e fim
  opcional do período, **limite de faltas** (número) e, se quiser, uma **tarefa pessoal ligada**. Até 50 por pessoa.
- **Cada dia** que já aconteceu (hoje conta depois do fim do horário) fica **pendente** até você marcar **Presente**,
  **Falta** ou **Sem aula** (feriado, aula cancelada: não conta). Tocar de novo no marcado tira a marcação. Dias que
  ainda vêm aceitam só *Sem aula*. Pendente não conta como falta.
- **Tarefa ligada**: concluí-la num dia do compromisso conta **presença** nesse dia (sem marcação; marcar Falta vale
  mais). Excluir a tarefa só desliga.
- **Cores**: barra de faltas azul; **laranja** quando já faltou e sobram até 2 (ou nenhuma); **vermelha** ao passar do
  limite. Sempre com o texto ("Pode faltar mais 3", "No limite", "Passou do limite").
- **Encontros restantes**: com data de fim, o card mostra quantos ainda vão acontecer de hoje até o fim ("Restam 4
  encontros"), sem os que já passaram (pendentes) e sem os marcados como *Sem aula*.
- **Agenda pessoal**: bloco tracejado com o nome e as faltas; tocar abre *Presente / Falta / Sem aula*. Com a tarefa
  ligada no mesmo dia, aparece um bloco só (o da tarefa, com o selo do compromisso). Os **indicadores da semana**
  somam os compromissos (menos *Sem aula*). **Início**: card com faltas/limite de cada um e os dias pendentes.
- **Importar do SIGAA** (UFRN): na turma virtual, aba *Grupos* → *Frequência*, `Ctrl + A`, copiar e colar no campo (o
  passo a passo fica no "i" ao lado). O texto é lido no navegador e preenche: nome (do código da turma), dias da semana
  (os que se repetem no Mapa de Frequências), início (1ª aula) e fim (última avaliação), horário pelo código (ex.: `T12`
  = 13:00 às 14:40, aulas de 50 min) e o **limite** (aulas da carga horária × faltas permitidas ÷ aulas por dia; ex.:
  72 × 25% = 18 aulas ÷ 2 = 9 dias). Marca *Presente*, *Falta* (`N Falta(s)`) e, se quiser, *Sem aula* nos dias avisados
  nas notícias ("Não haverá aula...") e nos dias de aula que não aparecem no Mapa (feriados); "Não Registrada" fica
  pendente. Tudo aparece para conferir antes de salvar. Importar de novo na mesma matéria só atualiza as marcações.

### Financiamentos - conceitos

- **Simulador** (*Finanças → Financiamentos → Simular*, ou *Simular financiamento* num item de compra do
  planejamento): tipo do bem, novo/usado, preço, entrada, FGTS (imóvel), mês da entrada (1ª parcela no mês seguinte),
  prazo e renda (opcional). A taxa **não** é pedida: a API usa as taxas médias que cada banco informa ao Banco Central
  (dados abertos, modalidade "Aquisição de veículos", "Financiamento imobiliário com taxas de mercado" ou "Aquisição de
  outros bens", pessoa física) e monta três cenários pelos quartis da lista (**boa**, **média**, **alta**). Uma proposta
  do banco entra em *Avançado* (% ao mês ou ao ano) como quarto cenário. Imóvel corrige o saldo pela **TR** (média dos
  últimos 12 meses).
- **Resultado**: SAC x Price (1ª e última parcela, total pago, juros, **CET** = taxa que iguala o valor recebido às
  parcelas, com seguros e tarifas do banco), prazos lado a lado, bancos do mais barato ao mais caro com a parcela de
  cada um, se a parcela cabe em 30% da renda e o preço máximo, gráfico juros x amortização por ano e a tabela de
  parcelas (CSV). Avisos: entrada abaixo de 20% no imóvel, parcela acima de 30% da renda, sem dados do Banco Central.
- **Gastos padrão** (estimativas, editáveis; 0 tira da conta): imóvel = ITBI 3%, registro 1%, avaliação, seguros MIP
  (0,025% do saldo ao mês) e DFI (0,007% do valor), taxa de administração, IPTU e condomínio (apartamento); veículo =
  IOF, tarifa de cadastro, gravame (financiados), emplacamento (0 km) ou transferência e vistoria (usado), IPVA,
  seguro, licenciamento e revisões; outro bem = IOF e tarifa.
- **Simulações salvas** (pessoais ou do grupo): guardam só o que foi preenchido; ao abrir, refazem com as taxas do dia.
- **Financiamento em andamento**: dados do contrato (valor financiado, taxa, SAC/Price, prazo, 1ª parcela e dia,
  seguro sobre o saldo, valores fixos da parcela, índice de correção, banco). Sem a taxa, botões com as médias dos
  bancos. As parcelas são calculadas na hora: parcela paga = vencimento já passou. Mostra saldo devedor, % quitado,
  próxima parcela, fim, juros pagos e a pagar e **quitar hoje** (saldo devedor; economia = parcelas que faltam menos o
  saldo). Índice: meses já divulgados usam o valor real (SGS do Banco Central), os outros a média de 12 meses.
- **Amortização extra**: prévia (prazo e parcela antes e depois, juros economizados) e registro; *reduzir o prazo*
  mantém a parcela, *reduzir a parcela* mantém o prazo.
- **Gastos do bem** (IPVA, seguro, revisão, IPTU, condomínio): mensais ou anuais (mês e dia). Com o **aviso** ligado
  (no dia ou até 15 dias antes), cada participante recebe notificação da parcela (com a sua parte) e dos gastos, às 8h.
- **Nas suas finanças**: liga a parcela (ou a sua parte) a uma despesa fixa pessoal com o valor de cada mês (ajustes
  mensais), atualizada sozinha quando o financiamento muda; no grupo, pode ir sincronizada para os fixos do grupo.
  Excluir o financiamento apaga esses fixos.
- **Grupo** (aba *Financiamentos*): todos veem e editam; a parte de cada membro (%, soma 100, igual ao criar); excluir
  só quem cadastrou ou admin. Quem sai do grupo deixa de ter parte.

### Notas - conceitos

- **Nota** (menu *Organização → Notas*, ou aba *Notas* do grupo): título e texto em **markdown** (até 200 mil
  caracteres). O editor **Visual** mostra a estrutura pronta e grava markdown: barra de botões (títulos, negrito,
  itálico, riscado, código, link, listas, checklist, citação, bloco de código com cores, divisória, tabela e imagem),
  atalhos (`Ctrl+B`, `Ctrl+I`) e markdown digitado vira estrutura (`## `, `- `, `[ ] `, `**texto**`). Digitar `/` abre
  a lista de blocos. **Markdown** mostra o código para editar à mão; **Lado a lado** (computador) mostra o código e a
  nota pronta, e a checklist pode ser marcada na prévia. O modo fica lembrado no aparelho.
- **Salvar automático**: 1 s depois de parar de digitar. Cada gravação leva a versão que a pessoa tinha; se outra aba
  ou pessoa salvou antes, aparece o aviso com *Ver a versão nova* ou *Manter a minha* (nada se perde sem perguntar).
- **Organização**: pastas, etiquetas com cor, ícone (emoji) e cor da nota; **fixar** (aparece primeiro),
  **arquivar** (sai da lista sem excluir) e **lixeira** (some de vez depois de 30 dias; só quem criou ou o admin do
  grupo manda para a lixeira, restaura ou apaga de vez). Busca no título e no texto, filtro por pasta e etiqueta.
- **Modelos**: prontos (reunião, aula, diário, lista de compras) e os seus (nota marcada como modelo, só pessoal; fica
  na aba *Modelos* e aparece em *Nova nota*, também no grupo).
- **Links entre notas**: `[[Nome da nota]]` (digitar `[[` sugere as notas). `Ctrl + clique` abre; o rodapé lista os
  links (o que não existe é criado ao tocar) e onde a nota é **citada**.
- **Ligações**: nota pessoal com uma tarefa pessoal ou um compromisso da *Frequência*; nota do grupo com uma tarefa do
  grupo. O menu do compromisso e da tarefa pessoal tem *Notas*, que abre a lista filtrada.
- **Fotos**: enviadas do aparelho (reduzidas para até 1600 px), guardadas pela API e mostradas só para quem pode ver
  a nota. Foto tirada do texto é apagada depois de 1 hora; nota apagada leva as fotos junto.
- **Grupo**: todos os membros leem e editam; **Atividade** mostra quem criou, editou (edições seguidas viram uma
  linha), arquivou, mandou para a lixeira ou restaurou.
- **Extras**: palavras e tempo de leitura no rodapé, **Baixar .md** no menu da nota e card no **Início** (fixadas e
  editadas por último). As notas pessoais vão no arquivo da conta (só registro).

## Arquitetura

```
                ┌──────────────────────┐
  navegador ───►│ lifepedia-web        │  React + TypeScript + Vite + Tailwind + shadcn/ui
  app (PWA) ───►│ (nginx no container) │  instalável no celular/desktop (manifest + service worker)
                └──────────┬───────────┘
                           │ /api/*  (mesmo host; proxy do Vite em dev / nginx no container)
                ┌──────────▼───────────┐
  app mobile ──►│ lifepedia-api        │  Spring Boot 4 · Java 21 · Spring Security (JWT HS256)
   (futuro)     │                      │  JPA/Hibernate · Flyway
                └──────────┬───────────┘
                ┌──────────▼───────────┐
                │ PostgreSQL 17        │
                └──────────────────────┘
```

## CI

GitHub Actions em `.github/workflows/`: **API** (Checkstyle, testes, imagem Docker) só quando muda código em `lifepedia-api/`,
**Web** (lint, build) só quando muda código em `lifepedia-web/` (alterar só `.md` não dispara) e **Segredos** (gitleaks) em todo push/PR na `main`.

## Projetos

Monorepo (um único repositório git nesta pasta) com três projetos:

| Pasta | Stack | Descrição |
|---|---|---|
| [`lifepedia-api/`](lifepedia-api/README.md) | Java 21, Spring Boot 4, PostgreSQL | API REST de todos os módulos |
| [`lifepedia-web/`](lifepedia-web/README.md) | React 19, TypeScript, Vite, Tailwind v4, shadcn/ui | Front web, instalável como app (PWA) |
| [`lifepedia-mobile/`](lifepedia-mobile/README.md) | a definir | App nativo (ainda não iniciado; hoje o celular usa o PWA) |

Os arquivos da raiz (`README.md`, `AGENTS.md`, `docker-compose.yml`, `.env.example`) são a "cola" entre eles.

## Como rodar

Pré-requisitos: Docker Desktop. Para desenvolver fora do container: JDK 21, Maven (ou `./mvnw`), Node 24.

### Tudo em containers (jeito mais simples)

```bash
cp .env.example .env        # e preencha JWT_SECRET e DB_PASSWORD com valores aleatórios
docker compose up -d --build
```

| Serviço | URL |
|---|---|
| Front | http://localhost:3080 (ou http://127.0.0.1:3080; outro endereço precisa entrar em `CORS_ORIGINS` no `.env`) |
| API | http://localhost:8080 (health: `/actuator/health`) |
| Postgres | `localhost:5440` · db e usuário `lifepedia`, senha em `.env` |

Todas as portas escutam **só em `127.0.0.1`** (variável `BIND_ADDRESS`): outros dispositivos da rede não acessam.

Os containers usam `restart: unless-stopped`: sobem de novo sozinhos junto com o Docker.
Os dados ficam no volume `lifepedia-db` e as imagens (fotos de perfil, grupos e itens do planejamento) no volume
`lifepedia-uploads` (sobrevivem a `docker compose down`; só somem com `down -v`).

Comandos úteis:

```bash
docker compose ps                    # status
docker compose logs -f api           # logs da API
docker compose up -d --build api     # rebuild só da API depois de mudar código
docker compose down                  # para tudo (mantém os dados)
```

### Desenvolvimento com hot reload (híbrido)

Banco no container, API e front rodando local:

```bash
docker compose up -d db                                            # só o Postgres (porta 5440)
cd lifepedia-api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # API em :8080 (lê ../.env)
cd lifepedia-web && npm install && npm run dev                     # front em :5173 (proxy /api -> :8080)
```

> Se o stack completo estiver no ar, pare a API do container antes (`docker compose stop api`) para liberar a porta 8080.

### Dados de teste locais

Nenhum usuário é semeado: crie o seu pela tela de cadastro. Use uma senha só desse ambiente e nunca reaproveite
credenciais de teste fora dele.

## Deploy (Coolify)

Produção usa o `docker-compose.prod.yml`: nenhuma porta publicada (o proxy do Coolify, Traefik, atende HTTPS e fala
com o serviço `web` pela rede interna), limites de memória (db 512 MB, API 1 GB com heap em 70%, web 128 MB; cabe numa
VPS de 4 GB com o Coolify) e `COOKIE_SECURE` sempre ligado.

1. **Coolify** → New Resource → repositório (GitHub App) → build pack **Docker Compose**, arquivo
   `/docker-compose.prod.yml`.
2. Serviço `web`: domínio `https://app.seudominio.com.br` (porta interna 8080). O certificado sai sozinho.
3. Variáveis (o deploy falha se faltar alguma obrigatória):

   | Variável | Valor |
   |---|---|
   | `JWT_SECRET` | aleatório, 48 bytes (comando no `.env.example`); **diferente** do local |
   | `DB_PASSWORD` | aleatório; vale só na criação do volume do banco (trocar depois: `ALTER USER`) |
   | `CORS_ORIGINS` | o endereço público, ex.: `https://app.seudominio.com.br` |
   | `TIME_ZONE` | opcional, padrão `America/Sao_Paulo` |
   | `GOOGLE_CLIENT_ID` | opcional: login com Google (ver abaixo); vazio = sem botão |
   | `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | opcional: notificações push (ver abaixo); vazias = sem push |
   | `VAPID_SUBJECT` | com o push: contato, ex.: `https://app.seudominio.com.br` ou `mailto:...` |
   | `AI_API_KEY` | opcional: chave do Gemini para a importação pelo site (ver abaixo); vazia = só o agente da pessoa |
   | `AI_MODEL` / `AI_TIMEOUT` / `AI_DAILY_LIMIT` | opcionais: modelo (`gemini-3.5-flash-lite`), tempo máximo da leitura (`180s`) e leituras por dia somando todas as contas (`100`) |
   | `DB_USER` | opcional, padrão `lifepedia` |

4. Deploy. O primeiro build demora (Maven e npm baixam tudo).

**Login com Google** (opcional): no [Google Cloud Console](https://console.cloud.google.com/), crie (ou escolha) um
projeto e abra *Google Auth Platform* (antes *APIs e serviços → Tela de permissão OAuth*):

1. *Branding*: nome do app, e-mail de suporte e o domínio de produção em *Domínios autorizados*.
2. *Público-alvo*: **Externo** e **Publicar app** (em "Teste" só entram os e-mails listados como testadores). Com
   os escopos básicos (`openid`, `email`, `profile`) não há verificação do Google.
3. *Clientes* → *Criar cliente* → tipo **Aplicativo da Web** → *Origens JavaScript autorizadas*: o domínio público
   exato, com `https` e sem barra no fim (ex.: `https://app.seudominio.com.br`) e, para testar local,
   `http://localhost`, `http://localhost:3080` e `http://localhost:5173`. Sem URIs de redirecionamento.
4. Copie o **ID do cliente** para `GOOGLE_CLIENT_ID` (Coolify e, se quiser local, `.env`) e reinicie a API. Mudança de
   origem no Console pode levar alguns minutos para valer.

Não há secret: a API só confere a assinatura do token do Google com o ID. O mesmo botão serve para entrar, cadastrar
e vincular a conta em *Meu perfil*.

**Importação pelo site** (opcional): crie a chave no [Google AI Studio](https://aistudio.google.com/apikey), num projeto
com **cobrança ativa**, e coloque em `AI_API_KEY` (Coolify e, se quiser local, `.env`). A assinatura Google AI (Pro ou de
estudante) não cobre a API. Sem cobrança a chave cai no plano gratuito, em que o Google pode usar o conteúdo para melhorar
produtos e pessoas podem revisá-lo, e a Política de privacidade do app promete o contrário: não use chave gratuita com
documentos reais. Defina um teto de gastos no projeto do Google, porque a chave é uma só para todas as contas.

**Notificações push** (opcional): gere um par de chaves VAPID (uma vez; **diferente** do local) e coloque no Coolify:

```bash
node -e "const k=require('crypto').generateKeyPairSync('ec',{namedCurve:'prime256v1'}).privateKey.export({format:'jwk'});console.log('VAPID_PUBLIC_KEY='+Buffer.concat([Buffer.from([4]),Buffer.from(k.x,'base64url'),Buffer.from(k.y,'base64url')]).toString('base64url'));console.log('VAPID_PRIVATE_KEY='+k.d)"
```

`VAPID_SUBJECT` = o endereço do app (`https://...`) ou um `mailto:`. A privada é segredo (nunca no git); a pública
vai para o navegador. Trocar as chaves invalida as inscrições: cada pessoa ativa de novo no aparelho.

**Chat (SSE)**: as conexões ficam na memória da API, então o compose roda **uma instância** da API (com mais de uma,
mensagens de uma não chegariam às conexões da outra). O nginx não segura os eventos (`X-Accel-Buffering: no`) e a API
manda um "ping" a cada 25 s para o proxy não fechar a conexão.

O nginx do web confia no `X-Forwarded-For` só vindo de rede privada (o Traefik) e usa o último IP de fora da rede:
o limite de login por IP vale por cliente e não dá para forjar. Com a **Cloudflare**, deixe o registro DNS como
"DNS only" (nuvem cinza); com o proxy dela ligado, todos os acessos chegariam com IPs da Cloudflare.

**Backups**: o Coolify não copia os volumes deste compose. Faça `pg_dump` diário do banco e copie o volume
`lifepedia-uploads` (fotos) para fora do servidor (ex.: `rclone` para Cloudflare R2/Backblaze B2), e teste restaurar.

## Segurança

Os dados são sensíveis, então a segurança é parte do projeto, não um extra. Resumo do que está implementado:

| Tema | Como está |
|---|---|
| Senhas | **BCrypt custo 12** (hash com salt; nunca texto puro). Hashes antigos são atualizados no login. Senha mín. 8 caracteres |
| Sessão no navegador | JWT (HS256) em **cookie HttpOnly + Secure + SameSite=Strict**, `Path=/api`. O JavaScript não lê o token (protege contra roubo via XSS). O token só carrega o id do usuário e o id da sessão. Cada login cria uma **sessão no banco**: o token só vale enquanto ela existe (sair, ou excluir a conta, invalida na hora). Sem "lembrar de mim": cookie de sessão do navegador e 8h; com: cookie persistente de 30 dias. Sessões vencidas são apagadas toda madrugada |
| Login com Google | Botão oficial (Google Identity Services); a API confere o ID token (assinatura pelas chaves do Google, emissor, **audiência = nosso Client ID**, validade). Vincula automaticamente só e-mail **confirmado pelo Google**; nunca troca o Google já vinculado de uma conta. Mesmo limite por IP do login. Conta sem senha nunca entra por senha. Ação sensível em conta sem senha: login no Google dos últimos 10 min com a conta vinculada |
| CSRF | SameSite=Strict + cabeçalho `X-Requested-With` obrigatório em requisições que alteram dados |
| Força bruta | Limite de login/cadastro por IP (20 / 10 min; IP real do cliente atrás de proxy, sem dar para forjar) e bloqueio de 15 min por e-mail após 5 senhas erradas. Confirmação de senha (excluir conta): mesmo bloqueio, por usuário |
| LGPD | Política de privacidade e Termos de uso publicados (`/privacidade`, `/termos`), com controlador, encarregado e canal do titular. Aceite obrigatório no cadastro e a cada versão nova, gravado com versão e data (`users.terms_version`/`terms_accepted_at`). Direitos do titular no próprio app: acessar e levar os dados (exportar), corrigir (perfil), apagar (excluir conta), revogar consentimento (desvincular Google, desativar push). Sem cookies de rastreio ou publicidade |
| Conta | Excluir pede a senha atual (ou o Google, em conta sem senha) + "EXCLUIR"; apaga tudo do usuário (FKs em cascata, foto de perfil, fotos dos itens e das notas no disco) e o cookie. Token de conta que não existe mais é recusado (**401**) em toda requisição |
| Exportar/importar | Só os próprios dados, logado. Exportar: 20/h; importar: 10/h (prévia 60/h); arquivo até 25 MB (nginx), validado inteiro (versão, categorias, valores, datas) antes de gravar — tudo ou nada |
| Enumeração de contas | Login responde igual e no mesmo tempo para e-mail inexistente e senha errada |
| Isolamento | Todo dado tem dono; buscas sempre filtram pelo usuário do token (outro usuário recebe 404) |
| Foto de perfil | Visível para qualquer usuário logado (nunca sem sessão); só o dono troca ou remove. Mesma validação e regravação das imagens de grupo |
| Grupos | Tudo do grupo (itens, métricas, membros, imagem) só para membros; quem não é membro recebe **404**. Ações de admin → 403 para membro comum. Convite direto exige aceite |
| Link de convite | Token aleatório de 256 bits, válido por 7 dias, um por grupo, revogável; entrar exige estar logado |
| Uploads | Só JPEG/PNG até 1 MB (o front já envia recortado em 512 px); a API confere o tipo pelos bytes e as dimensões antes de decodificar e **regrava** um JPEG novo (sem metadados do arquivo). Servido só a membros/convidados |
| Prévia de links | A API busca a página do link (proteção contra **SSRF**): só http(s) nas portas 80/443, só **IPs públicos** (checado na conexão, sem brecha de DNS rebinding), sem proxy/cookies, redirecionamentos revalidados, 12 s e 3 MB no máximo, 20 buscas por usuário a cada 10 min (inclui a busca de produto parecido no Buscapé). A foto é baixada, validada e **regravada** pela API (o navegador nunca carrega imagem de fora). Links só abrem em http(s), em outra aba, sem `Referer` |
| Notas | Nota pessoal só do dono e nota de grupo só dos membros (**404** para os outros); lixeira só de quem criou ou do admin (**403**). O conteúdo é markdown puro: o editor não aceita HTML solto, links só `http(s)`/`mailto` (outra aba, sem `Referer`) e imagem só se for foto enviada pela API para aquela nota (endereço de fora não carrega). Fotos validadas e regravadas em JPEG pela API e servidas só a quem pode ver a nota |
| Financiamentos | Pessoal só do dono e do grupo só dos membros (**404** para os outros); excluir só quem cadastrou ou admin (**403**); partes só para membros do grupo. A busca de taxas e índices é feita pela API (`SafeHttpClient`) nos dados abertos do Banco Central, sem dado do usuário, e guardada no banco (atualiza a cada 6 h; sem resposta, usa taxa padrão e avisa) |
| Busca de usuários | Convite de grupo (só admins) e adicionar amigo: mín. 2 caracteres, até 10 resultados, devolve apenas nome, @username e foto (nunca e-mail) |
| Chat do grupo | Só membros leem, escrevem e recebem o stream (não membro → **404**); editar/apagar só a própria mensagem (403). O stream SSE é por usuário logado e só recebe mensagens de grupos de que ele é membro no envio. Texto puro (sem HTML), até 2.000 caracteres, 30 mensagens/min por pessoa |
| Amigos | Perfil e conquistas de alguém só para quem é amigo (senão **404**); conquistas financeiras nunca aparecem para amigos (só contagem, nunca valores). Pedidos: 30/h por usuário, até 50 sem resposta; aceitar só quem recebeu |
| Rede | Portas só em `127.0.0.1`; banco com senha aleatória; API e nginx rodam **sem root**; `no-new-privileges` |
| Navegador | CSP restritiva (única origem externa: `accounts.google.com/gsi`, para o botão do Google), `Cross-Origin-Opener-Policy: same-origin-allow-popups` (popup do Google), `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, sem versão do servidor |
| Notificações push | Web Push com chaves VAPID (a privada só no ambiente). O conteúdo vai **cifrado de ponta a ponta** até o aparelho (RFC 8291): o serviço de push do navegador (Google, Mozilla, Apple, Microsoft) não lê o texto. A API só envia para esses serviços (lista fixa, `https` na porta 443, sem redirecionamento). Cada aparelho fica ligado à **sessão de login**: sair, sessão vencida ou excluir a conta param o envio; outra pessoa no mesmo navegador não herda o push. Com o app em foco nada aparece. O texto da notificação aparece na tela de bloqueio, conforme o aparelho |
| App instalado (PWA) | O service worker **não guarda nada da API nem das telas**: só a página "Sem conexão" (e o ícone dela); também recebe e mostra os push. Os dados vêm sempre da rede, com a mesma sessão em cookie; sair do app não deixa dado salvo no aparelho. Instalar exige HTTPS (ou `localhost`) |
| Segredos | Só em `.env` (fora do git). A API **não sobe** sem `JWT_SECRET` |
| Erros | Sem stack trace nem mensagens internas nas respostas |

Pontos em aberto / recomendações:

- **HTTPS**: hoje é só local. Antes de expor fora da máquina, coloque um proxy com TLS (Caddy/Traefik) na frente.
- **Criptografia em disco**: o volume do Postgres não é criptografado — ative o **BitLocker** no Windows.
- **Backup**: ainda não há rotina de backup dos volumes `lifepedia-db` e `lifepedia-uploads`.
- **Busca de usuários**: qualquer usuário logado encontra outros pelo nome/@username ao adicionar amigo (sem opção de ficar oculto).
- **Importação com IA**: com o agente da pessoa, a fatura vai para o serviço que ela escolher e a tela orienta a ocultar
  dados do cartão. Pelo site, o arquivo vai inteiro para o Google com a chave do servidor, conforme avisa a tela. O prompt é
  montado na API, nunca vem do navegador, para a chave não virar um Gemini aberto a qualquer conta. Os arquivos só ficam na
  memória e o nginx não os grava em disco. O resultado da IA só vira lançamento depois da revisão da pessoa, e o rascunho
  tem os mesmos cuidados dos lançamentos (dono, `ON DELETE CASCADE`), mas não entra no arquivo exportado.
- **Prévia de links**: ao buscar um link, a loja vê um acesso vindo do servidor do LifePedia (não do navegador). O nome
  do produto (tirado do link ou digitado) vai para a busca do Buscapé quando a loja não libera os dados.
- **Preço de referência** é de um produto **parecido**, não necessariamente o mesmo (cor/versão podem mudar); a tela
  sempre avisa. Depende do formato da página de busca do Buscapé — se mudar, a referência só deixa de aparecer.
- **Taxas dos financiamentos**: são médias informadas pelos bancos ao Banco Central (com atraso de semanas) e os
  gastos padrão são estimativas; a tela avisa que a proposta real pode mudar. Depende do formato das APIs de dados
  abertos do Banco Central (Olinda e SGS): se mudar, a simulação cai na taxa padrão e mostra o aviso.
- **Textos legais**: escritos a partir do que o sistema faz hoje; não substituem revisão de um advogado. Mudou algo
  que trata dados (novo serviço externo, novo dado, retenção de logs)? Atualize a política e a versão.
- **Logs do servidor** (nginx/Traefik): têm IP e páginas acessadas; configure a retenção no Coolify/Docker (a política
  diz "pelo tempo necessário à segurança ou exigido por lei").
- **Arquivo exportado** (.json/.xlsx) tem todos os seus dados financeiros **sem criptografia**: guarde em lugar seguro.
- **Conta excluída**: notificações já enviadas a outras pessoas guardam o nome da época (ex.: convite antigo).

## Documentação para agentes

- [`AGENTS.md`](AGENTS.md) — visão geral, convenções e checklist para qualquer agente/IA que trabalhe no projeto.
- Cada projeto (`lifepedia-api/`, `lifepedia-web/`, `lifepedia-mobile/`) tem o seu `AGENTS.md` com detalhes da stack.
- **Regra do projeto:** toda alteração atualiza, na mesma tarefa, os `README.md`/`AGENTS.md` afetados e o `.env.example`.
