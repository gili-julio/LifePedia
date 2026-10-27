# lifepedia-web

Front web do [LifePedia](../README.md). React 19 · TypeScript · Vite · Tailwind CSS v4 · shadcn/ui ·
React Router · TanStack Query · React Hook Form + Zod · Recharts · dnd-kit (arrastar para ordenar).

**Visual**: shadcn no estilo `radix-maia` (cantos arredondados, botões em pílula), fonte Manrope, tema claro e
escuro. Cores com significado fixo: **verde = ganho**, **vermelho = perda**, **azul = totais e indicadores**; totais
em cards tingidos dessas cores. Todo valor leva sinal `+`/`−` e seta, para não depender só da cor.

## Rodando

```bash
npm install
npm run dev       # http://localhost:5173 — /api é repassado para http://localhost:8080
npm run build     # typecheck + build de produção em dist/
npm run lint      # oxlint
```

A API precisa estar no ar (veja o [README da raiz](../README.md)). Para apontar o proxy para outro endereço:
`API_PROXY_TARGET=http://outro-host:8080 npm run dev`.

No container (`docker compose up -d --build` na raiz), um nginx **sem root** serve o build em http://localhost:3080,
faz o proxy de `/api` para o serviço `api` e envia os cabeçalhos de segurança (CSP, `X-Frame-Options`, etc.).
Atrás do proxy do Coolify, pega o IP real do cliente (`real_ip`, só de rede privada) e repassa o protocolo HTTPS.

**Sessão**: o login é guardado pela API num cookie HttpOnly — o front não lê nem guarda token. Ao abrir o app,
`AuthProvider` chama `/api/users/me` para saber se há sessão.

## Telas

| Rota | Tela |
|---|---|
| `/login`, `/cadastro` | Autenticação (cadastro pede o @username) |
| `/` | **Início** (logo no topo): tarefas de hoje e atrasadas (até 5, abrem a tarefa), hábitos com a sequência e o mês no financeiro (entradas, saídas, saldo) |
| `/perfil` | Meu perfil: foto (envia ao confirmar o recorte), nome e @username (conta sem dado obrigatório é trazida para cá com um aviso); **Meus dados** (exportar .json/.xlsx, importar com prévia *Somar*/*Substituir*) e **Excluir conta** (prévia por grupo, baixar dados, senha + EXCLUIR) |
| `/tarefas` | Tarefas: **Hoje** (atrasadas + hoje, campo rápido), **Semana** (dias como "Seg - 26/10"; 7 colunas quando cabe, senão 4/2/1; título do cartão até 2 linhas com "..."; arrastar pela alça muda o dia), **Próximos** (por dia, +7 dias por vez), **Sem data**, **Hábitos** (sequência, recorde, % do mês, últimos 7 dias clicáveis) e **Concluídas** (`?aba=`; `?tarefa=<id>` abre a tarefa). Lembrete, hábito e fixo do financeiro no formulário; concluir tarefa ligada a fixo abre *Registrar no financeiro?*. Filtro por lista/etiqueta, ordem (minha ordem arrastando, prioridade, horário). Concluir, subtarefas, mover para, pular, editar/excluir com "só esta / esta e as próximas / todas"; listas e etiquetas no ⋮. Mostra também as de grupo em que você é o responsável (com o nome do grupo) |
| `/grupos` | Meus grupos (alfinete fixa na sidebar, até 3) e **Solicitações (n)** (convites recebidos: aceitar/recusar); criar grupo com imagem |
| `/amigos` | **Meus amigos** (abre o perfil), **Pedidos (n)** (recebidos: aceitar/recusar; enviados: cancelar) e **Conquistas** (as minhas, com nível, data em que foi conquistado e barra até a próxima meta) (`?aba=pedidos\|conquistas`); *Adicionar amigo* busca por nome/@username |
| `/amigos/:id` | Perfil do amigo: foto, desde quando são amigos e as conquistas dele (sem as "só você vê"); *Desfazer amizade* no ⋮ |
| `/grupos/:id` | Grupo: visão geral (totais, por membro, fixos x ocasionais, 6 meses, categorias), lançamentos, fixos, **planejamento**, **tarefas** (`?aba=tarefas&vista=`; responsável/rodízio, comentários, filtro por responsável; `&tarefa=<id>` abre a tarefa) e membros; levar meus itens, novo lançamento/fixo direto no grupo (mesmos formulários do pessoal), convidar (admin). No celular as abas rolam na própria faixa |
| `/grupos/convite/:token` | Entrar num grupo pelo link de convite (sem sessão: depois de entrar ou criar a conta, volta para o convite) |
| `/financeiro` | Visão geral do mês: totais, % da renda fixa comprometida, fixos x ocasionais, últimos 6 meses, categorias (expandem os itens agrupados pelo nome) |
| `/financeiro/fixos` | Ganhos e despesas fixas (mensais), paginados |
| `/financeiro/lancamentos` | Lançamentos ocasionais do mês, paginados; ação **Tornar fixo** em cada item |
| `/financeiro/importar` | Importar extratos e faturas com IA (juntos, um ou vários meses): copia o prompt (categorias + fixos + nomes já usados), cola a resposta do agente, revisa numa prévia editável e importa |
| `/financeiro/categorias` | Categorias de despesa e ganho em cartões (lista completa): criar, editar (nome/cor), excluir movendo os lançamentos |

O mês selecionado fica na URL (`?mes=2026-09`); a aba do grupo também (`?aba=lancamentos`).

**Notificações**: o avatar do menu mostra quantas não lidas há (atualiza a cada minuto); no menu, *Notificações*
abre a lista lateral com **Ver** em cada item. **Cadastros de fixo e ocasional** têm a seção expansível *Levar para
grupos* (foto, nome e participantes de cada grupo, com caixinhas e o modo sincronizado/cópia).

**Planejamento** (aba do grupo): cartões com saldo previsto por mês (ajustável), reserva, total planejado e situação
(tudo cabe / faltam R$ X); painel **E se...?** (renda/gasto extra só na tela); **linha do tempo** (saldo acumulado com
e sem os planos + pagamentos do mês); **itens** em ordem de prioridade (arrastar pela alça ⋮⋮ — mouse, toque ou teclado — ou setas ↑↓), com foto, link da loja, chave
liga/desliga e a situação de cada um; **cenários** lado a lado (ver, aplicar, excluir); **adquiridos** (desfazer).
No cadastro do item, colar o link busca nome, preço e foto pela API; dá para trocar a foto (recorte quadrado). Loja
que bloqueia: aviso "não deu para ler X" + preço/foto de produto parecido no Buscapé (caixa com link, removível); sem
link, "Buscar preço e foto pelo nome". O item mostra "Preço/foto de referência: Buscapé" ao lado do link.

**Navegação**: sidebar (shadcn `Sidebar`) com a logo no topo, seções (Finanças, Organização, Social) com itens que
expandem, cada subitem com ícone (Financeiro → telas do módulo; Tarefas → abas; Grupos → 3 atalhos com a foto, fixados
primeiro e depois os de atividade recente, alfinete ao passar o mouse, + *Ver todos*; Amigos → amigos, pedidos,
conquistas) e o usuário no rodapé
(notificações, perfil, tema, sair). A partir de 1024px fica fixa e recolhe para só ícones (botão no topo ou
`Ctrl+B`; subitens abrem num menu ao lado; a escolha fica no cookie `sidebar_state`). Abaixo disso vira gaveta
lateral aberta pelo ☰ do topo, que fecha ao navegar. O topo mostra o caminho (ex.: Financeiro › Lançamentos).

**Ações no mobile** (< 640px): o cabeçalho de cada página mostra só o atalho da ação principal (`+`) e um `⋮` que
abre um *action sheet* (sobe da base da tela, fecha arrastando) com todas as ações. O filtro de mês fica visível,
em linha própria abaixo de 1280px (no desktop largo, ao lado das ações). Abaixo de 768px as tabelas de Fixos e Lançamentos viram listas, e cada item tem um `⋮` com
Editar/Excluir (o mesmo vale para os cards de Categorias). No celular os formulários abrem sem subir o teclado
(só a nova tarefa já foca o título), e abas que não cabem rolam na própria faixa mantendo a aba ativa à vista.

Na **prévia da importação**, o desktop mostra a tabela editável; no mobile, uma lista compacta com todos os itens
(caixinha para incluir/excluir, avisos e "Corrigir: …" na própria linha). Tocar num item abre um formulário que sobe
da base da tela para editar só aquele lançamento; "Salvar" aplica, "Cancelar" descarta.

## Importar com IA — formato

Um prompt só serve para extratos bancários e faturas de cartão, juntos ou separados, de um ou vários meses: o agente
marca a origem de cada item (`source`) e tira as datas (e, na fatura, o mês de vencimento) do documento. Leva as
categorias, os fixos ativos ou encerrados há até 12 meses (com a vigência) e os nomes mais usados nos últimos 12 meses
(`GET /transactions/descriptions`). O agente responde com um JSON assim:

```json
{
  "format": "lifepedia/transactions@3",
  "items": [
    { "source": "card", "date": "2026-09-05", "descriptionOficial": "PAG*SUPERM NORDESTAO NATAL", "descriptionNormal": "Supermercado Nordestão", "category": "Alimentação", "type": "EXPENSE", "amount": 245.9, "invoiceMonth": "2026-10" },
    { "source": "statement", "date": "2026-09-10", "descriptionOficial": "PIX ENVIADO JOAO S", "descriptionNormal": "João S.", "category": "Moradia", "type": "EXPENSE", "amount": 1800, "fixed": "Aluguel" }
  ]
}
```

`descriptionOficial` é o texto do documento: aparece só para leitura na revisão e fica guardado no lançamento
(`originalDescription`, "No documento" na edição). `descriptionNormal` é o nome padronizado e editável. O parser ainda
unifica nomes com as mesmas palavras em qualquer ordem, usando a grafia já existente (ou a primeira da resposta).
Os formatos `@1` (só `description`) e `@2` (sem `source`/`invoiceMonth`) continuam aceitos.

Item de **extrato** conta no mês da data. Item de **fatura** conta no mês da fatura (`month` = `invoiceMonth`, mês de
**vencimento**, `AAAA-MM` ou `MM/AAAA`), mesmo compras de meses anteriores; sem ele ou antes da compra, no mês da
compra. Sem `source` (prompt antigo ou esquecido): com `invoiceMonth` é fatura, senão extrato, com aviso. A prévia mostra
a origem de cada linha (dá para trocar: recalcula o mês) e "Conta em …" quando o mês não é o da data. Data depois do
mês atual tem o ano ajustado para o anterior.

Na prévia, cada item tem **Lançar como**: *Ocasional (novo)* ou *Fixo: X*. Item de fixo não vira lançamento: com o
mesmo valor já está contado; com valor diferente, vira o valor real do fixo no mês. Além do `fixed` do agente, a tela
liga itens a fixos por conta própria (valor até 15% diferente + valor exato, mesma categoria, descrição parecida ou dia
próximo) e avisa "confira". Dá para trocar o vínculo em cada linha.

O parser é tolerante: aceita texto em volta, bloco ```json, lista pura, datas `dd/mm[/aaaa]`, valores `"1.234,56"`,
tipo em português e valores negativos (viram positivos e mantêm o tipo informado; sem tipo, o sinal decide pela origem do item:
no extrato negativo = despesa e positivo = ganho, na fatura negativo = ganho/crédito e positivo = despesa; sempre com
aviso).
Categoria desconhecida vira "Outros" com aviso. Lançamentos
com mesma data e valor de um já salvo aparecem como possível duplicado (desmarcados se a descrição padronizada ou a do
documento também bater).

## Estrutura

```
src/
├── app/router.tsx            rotas (páginas de módulo com lazy load)
├── components/
│   ├── ui/                   componentes shadcn (gerados pelo CLI — evite editar à mão)
│   ├── layout/
│   │   ├── AppLayout.tsx     sidebar + topo fino (☰/recolher, caminho da página), notificações
│   │   ├── AppSidebar.tsx    sidebar: logo, seções com itens que expandem, usuário no rodapé
│   │   ├── UserMenu.tsx      usuário no rodapé: notificações, perfil, tema, sair
│   │   ├── use-dynamic-links.ts subitens vindos da API (grupos)
│   │   └── nav-items.ts      seções, itens e subitens da navegação + regra de item ativo
│   ├── PageHeader.tsx        título + ações (botões no desktop, `secondary` no ⋮; `+` e ⋮ no mobile) + toolbar (mês; linha própria abaixo de xl)
│   ├── image/                ImagePicker (escolher → recortar), ImageCropDialog (zoom/girar, recorte redondo ou quadrado), crop-image
│   ├── PhotoAvatar.tsx       foto redonda com iniciais/ícone de reserva (base de UserAvatar e GroupAvatar)
│   ├── UserAvatar.tsx        foto de perfil de um usuário (sidebar, membros, convites, comentários, autorias)
│   ├── ActionSheet.tsx       folha de opções mobile (vaul/drawer)
│   ├── DialogLayout.tsx      dialog com altura máxima: cabeçalho/rodapé fixos, miolo rola
│   ├── StatCard.tsx          total/indicador em card tingido (ganho verde, perda vermelha, dados azul)
│   ├── ScrollTabsList.tsx    abas que rolam na faixa no celular, com a ativa sempre à vista
│   ├── DatePicker.tsx        campo de data do sistema (calendário pt-BR)
│   ├── MonthField.tsx        campo de mês do sistema (grade de meses, opcionalmente limpável)
│   ├── ColorPicker.tsx       paleta (lib/colors) + cor livre
│   ├── ConfirmDelete.tsx     confirmação de exclusão (com botão próprio ou controlada)
│   ├── SortableItems.tsx     lista reordenável (dnd-kit: alça, teclado, anúncios pt-BR)
│   ├── data-table/           listagens: useListState, SortableHead, SortSelect (mobile), DataTablePagination
│   └── Logo.tsx
├── features/
│   ├── auth/                 AuthProvider, guards de rota, login e cadastro, ProfilePage + ProfilePhoto, ProfileGate (aviso de cadastro incompleto), username
│   ├── account/              exportar/importar (ImportDialog com prévia) e excluir conta (DeleteAccountDialog) — seções do perfil
│   ├── home/                 página Início (resumo de tarefas, hábitos e financeiro)
│   ├── notifications/        hooks, NotificationsSheet, texto por tipo (notification-content), use-reminder-toasts
│   │                         (lembrete que chega com o app aberto vira toast)
│   ├── groups/               grupos: api, GroupAvatar, GroupFormDialog, InviteDialog + UserPicker, MembersList, páginas
│   ├── friends/              amigos e conquistas: api, achievements (texto/ícone por código), AchievementGrid,
│   │                         AddFriendDialog, FriendsPage, FriendProfilePage
│   ├── planning/             planejamento do grupo: api, link.ts (isSafeLink), PlanningTab, PlanItemDialog (link → prévia),
│   │                         PlanItemRow, PlanTimelineChart, ScenariosCard, AcquireDialog, BaseSettingsDialog
│   ├── tasks/                tarefas: api, dates (repetição em texto, dias), labels (listas/etiquetas por id),
│   │                         TaskRow, TaskItems (lista arrastável, atrasadas agrupadas), TaskDialog + RepeatFields +
│   │                         task-form, ScopeDialog, MoveDialog, LabelsDialog, QuickAdd, TaskViews, task-actions, TasksPage,
│   │                         board (contexto pessoal/grupo), TasksBoard (abas + filtros, na página e no grupo),
│   │                         GroupTasksTab, AssignmentFields (responsável/rodízio), AssignDialog, TaskComments,
│   │                         WeekView (semana com arrastar), HabitsView, FixedField + FixedPaymentDialog (fixo ligado)
│   └── finance/
│       ├── types.ts, api.ts          tipos e hooks React Query
│       ├── categories.ts             ordenação/normalização de nomes
│       ├── use-category-lookup.ts    categorias do usuário por id/tipo
│       ├── import/prompt.ts          gera o prompt para o agente de IA (formato lifepedia/transactions@3)
│       ├── import/parse.ts           lê a resposta do agente, casa categorias, detecta duplicados
│       ├── fixed.ts                  vigência e valor do fixo no mês (valueIn)
│       ├── components/ReplacedOccasionals.tsx  "tornar fixo": ocasionais de mesmo nome a remover
│       ├── import/FixedSelect.tsx    "Lançar como": ocasional ou fixo
│       ├── import/ImportRowItem.tsx  linha da prévia no mobile
│       ├── import/ImportRowSheet.tsx formulário de um item da prévia no mobile
│       ├── components/               dialogs, seletores, gráficos, EntryListItem (linha mobile)
│       ├── group/                    financeiro dentro dos grupos: visão geral, abas de itens, BringItemsDialog, GroupShareSection
│       └── pages/                    Dashboard, Fixos, Lançamentos, Importar, Categorias
├── hooks/use-mobile.ts       gerado pelo shadcn (sidebar); celular = abaixo de 1024px
├── lib/                      api (fetch com cookie + X-Requested-With), format (moeda/mês/data, formatInstantDate no fuso local), colors (paleta), utils (cn), skipAutoFocusOnTouch
└── index.css                 tema shadcn (maia) + Manrope + cores de significado (--income/--expense/--data)
```
