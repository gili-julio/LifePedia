# AGENTS.md — lifepedia-web

Leia antes o [`../AGENTS.md`](../AGENTS.md) (regras gerais, **segurança** e a regra de **documentação sempre
atualizada**). Aqui ficam só as regras do front. Mudou tela, rota, estrutura ou convenção? Atualize este arquivo e o
`README.md` desta pasta na mesma tarefa.

## Stack

React 19 · TypeScript 6 · Vite 8 · Tailwind CSS v4 (sem `tailwind.config`, tema em `src/index.css`) ·
shadcn/ui (estilo `radix-maia`: cantos bem arredondados, botões em pílula) · fonte Manrope (`@fontsource-variable/manrope`) · React Router 8 (data router) · TanStack Query 5 · React Hook Form + Zod 4 ·
Recharts 3 (via `components/ui/chart.tsx`) · dnd-kit (listas reordenáveis) · Tiptap 3 + lowlight (editor das notas) · three.js + three-mesh-bvh + gltf-transform + meshoptimizer + fflate (maquetes 3D) · next-themes · sonner · lucide-react. Lint com oxlint.

## Convenções

- Comentários: regra da raiz (só o essencial, JSDoc de uma linha). Não mexa nos de `components/ui/*`.
- **Por feature**: `src/features/<modulo>/{types.ts, api.ts, components/, pages/}`. Páginas entram em
  `src/app/router.tsx` com `lazy`.
- Import com alias `@/` (→ `src/`).
- **Componentes shadcn**: adicione com `npx shadcn@latest add <nome>`; não reescreva `components/ui/*` à mão.
  Atenção: o CLI atual às vezes gera `import { cn } from "cn"` e instala o pacote npm `cn` — troque o import por
  `@/lib/utils` e rode `npm uninstall cn`. Trocar o estilo: `style` no `components.json` + `add <todos> --overwrite`.
- **Dados do servidor só via TanStack Query**. Chaves começam com o nome do módulo (`['finance', ...]`) e as
  mutações invalidam o módulo inteiro. Chamadas HTTP sempre por `lib/api.ts` (envia cookie e `X-Requested-With`,
  trata 401, converte ProblemDetail em `ApiError`). Nunca use `fetch` direto. Baixar arquivo da API: `apiDownload`
  (blob + nome do `Content-Disposition`).
- **Sessão**: cookie HttpOnly controlado pela API. **Nunca** guarde token/dados sensíveis em `localStorage`,
  `sessionStorage` ou estado global persistido.
- **CSP** (nginx): só scripts do próprio site (exceção única: `accounts.google.com/gsi` para o botão do Google, só via
  `GoogleButton`, que carrega o script quando há Client ID). `'wasm-unsafe-eval'` e `connect-src blob:` só para as
  maquetes 3D (WebAssembly do meshopt e texturas lidas de arquivos locais); `eval`/`new Function` continuam barrados. Nada de script inline, CDN, fontes/imagens externas ou
  `dangerouslySetInnerHTML`. Dependências entram pelo npm e são empacotadas pelo Vite. Fontes nunca são embutidas
  como `data:` URL (`build.assetsInlineLimit` no `vite.config.ts`), pois a CSP só aceita `font-src 'self'`.
- **Categorias** vêm da API (`useCategoryLookup()`); nunca fixe lista de categorias no código. Use `CategorySelect`,
  `CategoryBadge` e `ColorDot` para exibi-las.
- **Totais**: sempre do `/summary` (componente `TotalCard`); não somar no front. Totais e indicadores usam
  `components/StatCard` (card tingido pelo tom: `income`, `expense` ou `data`).
- **Listagens**: sempre paginadas e ordenadas pela API com o kit `components/data-table/`: `useListState` (página,
  tamanho, ordenação → `params` da query), `SortableHead` (cabeçalho clicável), `SortSelect` (ordenação no mobile),
  `DataTablePagination` (rodapé: intervalo, total, 10/25/50/100, páginas) e `useClampPage`. Mudou filtro/mês/aba?
  `list.resetPage()`. Nunca ordene/pagine array no front; listas completas vêm de `/all`.
  Exceção: a tela de Categorias é uma grade de cartões com a lista completa (`useCategoryLookup`), sem paginação.
  Exceção: a agenda de Tarefas (por dia) e "Sem data" vêm inteiras e já ordenadas da API; "Concluídas" é paginada e
  agrupa por `completedDay` só os itens seguidos da página (o total do dia é o `dayCount` da API, nunca a soma da página).
- **Dialogs de formulário**: `DialogScrollContent` + `DialogBody` (`components/DialogLayout.tsx`): altura máxima da
  tela, cabeçalho e botões sempre visíveis, miolo com rolagem. No toque, abre sem focar o 1º campo
  (`skipAutoFocusOnTouch` de `lib/utils`; use também em `DialogContent` com campos); `autoFocus` só onde digitar é a
  ação principal (ex.: título da tarefa nova).
- **Datas**: `DatePicker` (dia) e `MonthField` (mês) de `src/components`; não use `<input type="date|month">`.
- **Imagens enviadas**: sempre por `components/image/ImagePicker` (valida tipo/tamanho, recorta em 512 px JPEG);
  `shape="rect"` para foto de produto (padrão: redondo, avatar). Envio por `FormData` via `lib/api.ts` (grupo, foto
  de perfil) ou data URL no JSON (`imageData` do item do planejamento). A CSP permite `blob:`/`data:` só em `img-src` (prévia).
- **Pessoas**: mostre sempre com `components/UserAvatar` (foto do `imageUrl` do `UserSummary`; sem foto ou se falhar,
  iniciais). Grupo: `GroupAvatar`. Os dois usam `PhotoAvatar`. Trocou a foto de perfil? `setUser` + invalidar todas as
  queries (a URL aparece em vários módulos).
- **Estado de dialog que recomeça a cada abertura**: coloque-o num componente filho do `DialogContent` (desmonta ao
  fechar) em vez de `useEffect` com `setState` (ver `InviteDialog`, `BringItemsDialog`).
- **Login/cadastro**: `RequireAuth` guarda a página pedida em `state.from`; login, cadastro e `GuestOnly` voltam para
  ela (`returnTo`), e os links entre login e cadastro repassam o `state` (ex.: link de convite aberto sem sessão).
- **Termos e privacidade** (`features/legal`): texto em `PrivacyPage`/`TermsPage` (seções com `Section`/`List`),
  versão e controlador em `legal.ts` (`LEGAL_VERSION` espelha `LegalTerms.VERSION` da API). `LegalGate` (no
  `AppLayout`, antes do `ProfileGate`) bloqueia o app sem `user.termsAccepted`; cadastro manda `acceptTerms`. Mudou o
  que o app coleta, compartilha ou guarda? Atualize a política e a versão nos dois lados. Sem travessão no texto.
- **Cadastro incompleto**: `ProfileGate` (no `AppLayout`) bloqueia o app com aviso e leva a `/perfil` quando
  `user.profileComplete` é false.
- **Grupos**: chaves `['groups', ...]`; mutações invalidam também `['notifications']` e `['finance', 'groups']`.
  Financeiro de grupo fica em `features/finance/group` (chaves `['finance', 'groups', groupId, ...]`, invalidadas por
  qualquer mutação do financeiro). Novo cadastro com "levar para grupos": `ShareFields` + campos `groupIds`/`shareMode`.
- **Planejamento**: chaves `['planning', groupId, ...]`, `staleTime: 0` (a base depende dos itens financeiros do
  grupo, alterados em outras telas); mutações invalidam o planejamento e `['finance', 'groups', groupId]`. Números
  (falta, data possível, saldo) sempre da API. Preço/foto de referência (`reference`) nunca aparece sem dizer a
  origem. Link externo só com `isSafeLink` + `LinkChip` (`http(s)`, nova aba,
  `noopener noreferrer`); foto de fora nunca direto no `<img>` — vem da API (data URL da prévia ou `/api/.../image`).
- **Reordenar arrastando**: `components/SortableItems` (`@dnd-kit`: alça com `setActivatorNodeRef` e `touch-none`,
  `KeyboardSensor`, anúncios em pt-BR); mostre a nova ordem na hora (estado local até a API responder). No
  planejamento também há setas.
- **Compartilhados**: `ColorPicker` (paleta em `lib/colors.ts`), `ConfirmDelete` e `SortableItems` ficam em
  `src/components` — não importe componente de outro módulo.
- **Tarefas** (`features/tasks`): chaves `['tasks', 'me' | groupId, ...]` (mutações invalidam o módulo e as
  notificações; mutações de grupo invalidam as tarefas). Rotas por `tasksBase(groupId)`; ação numa linha usa o
  `item.groupId` (a visão pessoal traz tarefas de grupo). Página pessoal e aba do grupo usam `TasksBoard` (contexto
  `BoardContext`: `groupId`, membros, nomes); arrastar só as do próprio quadro; "Excluir" só com `item.canDelete`;
  "Mudar responsável…" (`AssignDialog`) só em grupo. A agenda, as atrasadas, o
  "hoje" (`agenda.today`) e a ordem vêm da API. Ocorrência é `taskId` + `date` (`occurrenceKey`); ações na ocorrência
  mandam o `date` dela. Editar/excluir tarefa com regra fixa pergunta o escopo (`ScopeDialog`); "só esta" só quando
  regra/lista/etiquetas/duração/subtarefas/hábito/lembrete/fixo não mudaram (`changesSeries`). Ações das linhas e seus dialogs em
  `useTaskActions`. Horário: `<Input type="time">`; dia: `DatePicker`.
  Semana (`WeekView`): `@dnd-kit/core` (colunas droppable, alça, `DragOverlay`), mostra no dia novo até a agenda
  recarregar. Desktop (`lg`): quadro com os 7 dias lado a lado; cada dia recolhe numa faixa (lembrado por dia da
  semana no `localStorage`, só preferência de tela) e o espaço vai para os outros; cartão estreito (container query)
  põe o título na linha de baixo. Celular: dias empilhados, sem recolher. Título em `line-clamp-2` + `title`. Sequência/% do hábito sempre da API (`streak`, `/habits`). Fixo ligado só em tarefa pessoal
  (`FixedField`); ao concluir, `FixedPaymentDialog` grava o ajuste do mês pelas rotas do financeiro (valor igual ao
  cadastrado remove o ajuste). Responsáveis: `item.assignees` (lista; nome por `board.assigneesLabel`); "feita" para
  quem vê sempre por `isDoneFor` (em `EACH` a parte da pessoa) e progresso por `eachProgress`. Tarefa nova no grupo já
  vem com quem cria (`TaskDefaults.assignees`). Choque de horário só pela API (`useConflicts`, `ConflictWarning`).
  **Agenda** (`tasks/agenda`): os dias vêm da `useAgenda` (`sort: TIME`); a posição dos blocos é só desenho
  (`place`/`fit` em `layout.ts`), sem ordenar a lista. Arrastar: `@dnd-kit/core` com uma coluna droppable por dia e o
  `delta.y` em passos de 15 min; mostra no lugar novo até recarregar (como a semana); repetida com horário novo pergunta
  só esta (`/occurrence`) ou todas (`PUT` da tarefa com `fromTask`). Soltar não abre a tarefa (ref `dragged`). Cor
  escolhida no `localStorage` (só preferência de tela); horas visíveis na API (`useAgendaSettings`). Grade com rolagem
  própria (cabeçalho dos dias e coluna das horas fixos); semana com largura mínima e rolagem lateral só dentro dela.
  Indicadores (`AgendaMetrics`, só na pessoal): busca a semana inteira com `useAgenda` (mesma chave da vista semana) e
  calcula em `metrics.ts` (união dos horários do dia, sem duração = `DEFAULT_MINUTES`, puladas fora); sono vem de
  `sleepMinutes` do `useAgendaSettings`. Quem salva as configurações manda o objeto inteiro (`...settings`). O botão
  *Indicadores* da barra rola até o título (`METRICS_ID`) e põe o foco nele.
- **Login**: "Lembrar de mim" vai no corpo do login (`remember`); o cookie é da API. Google: `GoogleButton` (botão
  oficial, `ux_mode: popup`, tema pelo `dark`), Client ID por `useAuthConfig` (`/api/auth/config`); sem ID, nada do
  Google aparece. Conta criada pelo Google (`hasPassword` false) confirma ações sensíveis pelo Google.
- **Chat** (`features/chat`): chaves `['chat', groupId, 'messages']` (infinite, `staleTime: Infinity`, a 1ª página é a
  mais recente) e `['chat', 'unread']`. Um `EventSource` por aba (`useChatStream` no `AppLayout`) faz `upsertMessage`
  e invalida as não lidas; reconectou → invalida `['chat']`. Mutações também fazem upsert (o stream repete, sem
  duplicar). Abrir o chat (e voltar para a aba do navegador) marca como lido. Menu da mensagem `modal={false}` e sem
  devolver o foco ao botão (o foco vai para o campo de mensagem). Não lidas: bolinha na foto do grupo na sidebar
  (`link.group.unread`) e na aba *Chat*.
- **Investimentos/parcelas**: `invested`, `investedPercent` e `reserve` sempre da API; parcela pelo nome com
  `parseInstallment` (só para mostrar a ação; a API decide quais seguem).
- **Frequência** (`features/attendance`): chaves `['attendance', ...]`; mutações invalidam o módulo, e as de tarefas
  também (tarefa ligada concluída conta presença). Contagem, status do dia e "pendente" sempre da API; o front só
  escolhe o tom (`absenceTone`: laranja com falta e até 2 sobrando, vermelho ao passar) e sempre mostra o texto junto.
  Dia futuro só aceita "Sem aula" (`MarkButtons future`). Agenda pessoal: `useAttendanceAgenda` no `AgendaBoard`, bloco
  `CommitmentBlock` (posição pela grade, `blockStyle`); compromisso com a tarefa ligada no mesmo dia vira selo no bloco
  da tarefa (sem duplicar). Indicadores somam os compromissos (menos "sem aula") em `metrics.ts`.
  **SIGAA**: `sigaa.ts` é puro (`parseSigaa` lê o texto colado; `sigaaMarks` filtra pelos dias/período escolhidos e até
  hoje); o texto nunca vai para a API, só `POST /api/attendance/import`. Horário da UFRN em `SLOT_STARTS` (aulas de
  50 min). Página do SIGAA mudou? Ajuste o parser e confira com um texto real; sempre mostre o que foi lido antes de salvar.
  O formulário da importação é o `CommitmentForm` (com `defaults` e `onSubmit`).
- **Notas** (`features/notes`): chaves `['notes', 'me' | groupId, ...]`; mutações invalidam o módulo, menos salvar,
  que atualiza a nota aberta no cache (`setQueryData`) e não recarrega a tela (o rascunho local manda). Rotas por
  `notesBase(groupId)`, telas por `noteUrl`/`notesListUrl`. Lista, busca, ordem, resumo (`excerpt`) e "citada em" sempre
  da API. Salvar: `useAutosave` (1 s depois da última mudança, com a `version` que a pessoa tinha; 409 → aviso com
  *Ver a versão nova* / *Manter a minha*; sai da tela → salva na hora; fechar a aba pendente → confirmação).
  **Editor** (`editor/`, Tiptap fixado na versão exata): conteúdo entra e sai em markdown (`contentType: 'markdown'`,
  `getMarkdown`); sem sublinhado (não existe em markdown). O editor escapa colchetes: passe sempre a saída por
  `unescapeWikiLinks`. `setContent`/`setEditable` sem emitir update (senão abrir a nota já regrava). Link só `http(s)`/
  `mailto`, nova aba, `noopener noreferrer`; imagem só `isNoteImage` (foto da API daquela nota). Checklist da prévia
  (lado a lado) escuta o `change` nativo (o checkbox é criado pelo editor, fora do React) e inverte no texto
  (`toggleTask`). Menus "/" e `[[` em `suggestions.ts` (`SuggestionMenu` posicionado no cursor); `[[Nome]]` só é
  destacado (`WikiLinks`, decoração), o texto não muda. "Lado a lado" em tela menor que `lg` aparece como Visual
  (`effective`; antes virava Markdown e a nota parecia sem formatação). Modo (visual/markdown/lado a lado) no `localStorage` (só
  preferência de tela). Foto: `shrinkImage` (1600 px, JPEG até 1 MB) e `uploadNoteImage`. Estilo do conteúdo em
  `.note-content` (`index.css`). Modelos prontos em `templates.ts` (item de lista com texto: markdown não guarda item
  vazio). Outras telas só levam para a lista por URL (`/notas?tarefa=` / `?compromisso=`), sem importar o módulo.
- **Financiamentos** (`features/financing`): banco por `BankSelect` (`bankCode` + `bank` só em outro banco); chaves
  `['financing', 'me' | groupId, ...]` e `['financing', 'overview']`; mutações invalidam o módulo e `['finance']` (o fixo ligado muda os fixos). Rotas por `financingBase(groupId)`, telas
  por `financingUrl`/`simulatorUrl`. **Nenhum cálculo no front**: parcelas, cenários, CET, renda, saldo, quitação e
  prévia da amortização vêm da API; o front só monta o pedido (`sim-form.ts`, campos como texto) e mostra. Simulação
  refeita com o formulário (debounce de 400 ms sobre o pedido em JSON, `keepPreviousData`); gasto editado vai em
  `costs` pela chave (0 tira da conta). Simulação salva abre o simulador com `state: {input, name}`. Planejamento e
  Início só levam por URL ou leem `useFinancingOverview`. Dinheiro sempre por `useMoney` e `hideValues` nas telas.
  Layout do simulador: 2 colunas só em `xl`; os resultados usam container queries (`@container`, `@2xl:`).
- **Amigos** (`features/friends`): chaves `['friends', ...]`; mutações invalidam o módulo e as notificações.
  Conquistas: nível (nunca cai), meta, valor e `unlockedAt` sempre da API; o front só tem texto/ícone por código (`achievements.ts`, espelha
  `Achievement`). Perfil de não amigo → 404 (tela com volta para Amigos).
- **Instante x dia**: `yyyy-MM-dd` → `formatDate` (texto, sem fuso); instante ISO (`createdAt`, `since`) →
  `formatInstantDate`/`formatRelative` (fuso do navegador). Nunca `slice(0, 10)` num instante (vira o dia em UTC).
- **Início** (`features/home`, rota índice `/`): só lê hooks de outros módulos (`useAgenda`, `useHabits`, `useSummary`,
  `useCommitments` + os textos/tom de `attendance/absences.ts`, `useNotes`, `useFinancingOverview`);
  não some valores nem calcule sequência no front. **Mudanças do sistema**: lista em `home/changelog.ts` (mais nova
  primeiro; `modules` só com os valores de `ChangeModule`); toda entrega que o usuário percebe adiciona uma entrada,
  escrita para quem usa (sem nomes de tela internos, rota, API ou termos técnicos), sem travessão. "Novo" = até 7
  dias; Início mostra 3, `/novidades` todas.
- **Admin** (`features/admin`): rota `/admin` dentro de `RequireAdmin` (reconfere `/api/users/me` ao abrir e ao voltar
  para a aba; sem `admin` volta ao início) e item *Dashboard* no `UserMenu` só com `user.admin`. Isso só esconde: quem
  protege é a API (403). Chaves `['admin', ...]`, consultas sem `retry`. Mudar permissão, bloquear, encerrar sessões,
  publicar aviso e enviar notificação sempre com `ConfirmAction`. Números e ordem sempre da API. Avisos para todos:
  `features/announcements` (`AnnouncementBanner` no `AppLayout`); fechados ficam no `localStorage` só pelo id.
- **Integrações** (`features/integrations`): chaves `['integrations', ...]`; mutações invalidam também `['dev']`,
  `['friends']` (conquistas do Duolingo) e as notificações. Token só vai no corpo do POST/PUT e nunca volta (campo
  `type="password"`, `autoComplete="off"`). Textos de cada serviço (o que ganha, passo a passo, aviso) só em
  `providers.ts`. Google: `useGoogleStart` e `window.location.assign(url)`; a página lê `?conectado`/`?erro` (textos em
  `GOOGLE_ERRORS`) e limpa a URL. Eventos na Agenda pessoal por `useCalendarEvents` + `EventBlock` (slot `event` no
  `AgendaBoard`, só leitura; dia/minutos em `calendar.ts`, no fuso do navegador).
- **Dev** (`features/dev`): chaves `['dev', ...]`; mutações invalidam também `['integrations']` e `['tasks']` (issue vira
  tarefa). Busca de repositório espera 400 ms antes de chamar (`RepoDialog`). Link externo (MR, issue, atalho, chamada)
  sempre `target="_blank" rel="noopener noreferrer"`. Mapa de calor (`ActivityHeatmap`) num tom só (`--primary` misturado
  com `--card`), legenda Menos/Mais e números do dia em texto (`aria-live`) ao passar o mouse ou focar.
- **Arquitetura** (`features/architecture`): chaves `['architecture', ...]`; mutações invalidam o módulo e as
  notificações. Papel sempre da API (`canAct(role, 'EDIT')` só esconde botões). **Conversão no navegador**
  (`convert/convert.ts`, importado com `import()` só ao enviar; constantes e tipos em `convert/options.ts`): arquivos
  soltos ou `.zip` (`fflate`), loaders do three (texturas pelo nome via `LoadingManager.setURLModifier`, nunca de fora;
  espera as texturas terminarem contando `itemStart`/`itemEnd` do manager; `.dae` lido como texto e passado por
  `keepComponentNames`, senão o ColladaLoader troca "Porta"/"Cadeira" por `instance_N`), materiais viram `MeshStandardMaterial` dos dois lados, unidade/posição (chão em y = 0,
  centro em x/z = 0, metros), `GLTFExporter` (texturas até 2048 px) e gltf-transform (`dedup`, `prune`, `weld`, WebP no
  canvas, `meshopt` em `high`). O UV do SketchUp repete a textura fora de 0 a 1 e assim não comprime: `normalizeTexcoords`
  leva o UV de cada material para 0 a 1 e guarda a repetição em `KHR_texture_transform`. Nunca use `textureCompress` do gltf-transform: o `ndarray-pixels` é trocado por um stub no
  `vite.config.ts` (gera código com `new Function`, barrado pela CSP). Envio: reserva + `apiUpload` (GLB cru com
  progresso); download por `apiBinary` (com progresso). **Visualizador**: `viewer/ModelViewer.ts` (three.js puro, sem
  React; desenha só quando algo muda). Ao abrir, `viewer/optimize.ts` junta as malhas por tipo de peça e material e as
  arestas por cor (exportação do SketchUp traz dezenas de milhares de malhas: 20 mil chamadas de desenho viram ~100),
  guarda em cada vértice a peça de onde veio (`objectOf`, para a ficha) e acha a câmera salva no arquivo (vista inicial).
  Nomes vêm do JSON do GLB (`parser.json.nodes`; o loader tira pontos e barras). Tipo, nome e link por
  `viewer/categories.ts` (palavras no nome do componente; o `.dae` não leva as etiquetas do SketchUp); andares por
  `levels.ts` (pisos com parede em cima). Materiais por `materials.ts` (`MaterialTable`): variante = índice do material
  no GLB → cor/textura, modos realista/branca/raio-x e destaque; textura da biblioteca (`textures.ts`, desenhada no
  canvas, sem imagem de fora) usa o canal de UV 1 com projeção de caixa em metros (`ensureBoxUv`). Comparar desenha os
  dois lados com `setScissor` (outra versão é um segundo modelo sem colisão). Cômodos por raios logo acima das portas
  (`detectRoom`); luzes da noite = teto dos cômodos + as colocadas (até 16). Minimapa (`minimap.ts`) = corte das
  paredes a 1,2 m calculado da geometria, centrado em quem anda. Clique que não acha a maquete avisa (`onMiss`). Colisão e cliques num BVH do que está visível, montado no worker do
  `three-mesh-bvh` (`pickReady`; a tela não trava); raios com `raycastFirst` sem corte. Sol por `sun.ts`; sombra com
  `shadowSide = FrontSide`, senão a superfície de dois lados escurece inteira. Textura que o modelo pede e não veio
  (`LoadedModel.missing`) aparece como aviso no envio, com *Adicionar pasta/arquivos*; soltar pasta entra nas
  subpastas (`convert/dropped-files.ts`). Tela cheia = a página inteira (`documentElement`) com a maquete `fixed` por cima; sem a API de tela cheia
  (iPhone, ou recusada) a maquete ocupa a janela mesmo assim. Só a maquete em tela cheia escondia menus, diálogos e
  avisos (são desenhados no `body`). `ModelStage` monta as ferramentas (painéis e popovers em
  `components/stage/`) e serve também à prévia (sem `scene`) e ao link público (cena só para ver: `actions` só com
  `EDIT`); comandos de fora por `controlRef` (`pickTool`). Comentários, vistas e pins usam pontos em metros no espaço do GLB. Página pública
  `/maquete/:token` fica fora do `RequireAuth`.
- **Notificação nova**: tipo em `features/notifications/types.ts` + texto/link em `notification-content.tsx`.
  `useReminderToasts` (no `AppLayout`) mostra `TASK_REMINDER` como toast quando as não lidas sobem.
- **Conta** (`features/account`): importar manda só `format/categories/recurring/transactions/taskLists/taskTags/tasks`
  (perfil, grupos, frequência, notas, financiamentos e arquitetura do arquivo são registro; aceita `IMPORTABLE_FORMATS`, `@1` sem tarefas; exporta `@6`); sempre prévia (`dryRun`) antes de gravar; `REPLACE` exige confirmação explícita. Excluir
  conta: senha (ou Google, em conta sem senha) + "EXCLUIR", e depois `logout()` (limpa o estado local).
- **Abas com muitos itens**: `components/ScrollTabsList` (com o `value` da aba) no lugar do `TabsList`: rola dentro da
  faixa no celular (a página não ganha rolagem lateral) e mantém a aba ativa à vista.
- **Ações do cabeçalho**: muitas ações? Marque as menos usadas como `secondary` (vão para o ⋮ no desktop). Excluir
  leva `destructive` (vermelho no menu).
- **Fixos**: vigência e valor do mês sempre por `features/finance/fixed.ts` (`isActiveIn`, `valueIn`).
- **Bancos**: catálogo em `lib/banks.ts` (espelha `Bank.java`; `matchBank` reconhece o banco em texto livre, como nomes
  do Banco Central). Mostrar: `components/bank/BankLogo` (`BankLogo`; `BankBadge` com nome; `BankMark` só logo, em linha sem espaço; coluna *Banco* só em `xl`); escolher: `BankSelect` (só a lista) e
  `BankField` (lista + conta ou cartão + nome em outro banco; `bankSchema` no Zod). Logos em `assets/banks/<code>.svg`,
  empacotados pelo Vite (CSP), sempre com o nome em texto ao lado. Banco novo = código em `Bank.java`, entrada em
  `BANKS` e o SVG (sem script e com `viewBox`). Filtro: `BankFilterSelect` + `toBankFilter` em `bank-filter.ts` (`chave` ou `chave|CARD`, na
  URL como `?banco=`); lote: `useSelection` + `BankAssignBar`. Totais por banco só da API (`useBankOverview`,
  `useSummary(month, filtro)`); gráfico `BankHistoryChart` com a cor presa à ordem das chaves, não ao valor.
- **Importação pelo site**: tudo a partir de `useImportDraft` (revisão aberta, confere a cada 4 s enquanto lê): `RUNNING`
  mostra a leitura, `FAILED` o motivo, `REVIEW` a prévia (`ImportReview`, remontada por `draft.id`). Rascunho **sempre na API**
  (`useDraftAutosave`, 1 s depois da última edição, ao sair da tela e ao esconder a aba), nunca em `localStorage`: é dado
  financeiro. Autosave fora do `useFinanceMutation`, senão recarrega o módulo a cada edição. Avisos do envio ficam no hook
  (`useSendDocuments`), pois a pessoa pode sair no meio. `useImportWatcher` (no `AppLayout`) recarrega as notificações quando a
  leitura termina e o toast vem de `useReminderToasts`, para aparecer uma vez só. Limites sempre da API (`useImportAssistant`).
- **Importação por IA**: o formato `lifepedia/transactions@4` é um contrato com prompts que o usuário já copiou.
  Mudou `ImportPrompt.java` na API? Mantenha `import/parse.ts` compatível (ou crie `@5` aceitando todos; `@1` só tem
  `description`, `@2` não tem `source`/`invoiceMonth`, `@3` não tem `bank`). Prompt único para extrato e fatura: cada linha tem `source`;
  item de fatura conta no `invoiceMonth`. Conta ou cartão sai da origem (`rowBank`); trocar o banco na prévia sempre por `withBank`. Trocar a origem na prévia sempre por `withSource` (recalcula o `month`). `descriptionOficial` → `originalDescription`: só leitura na revisão (`OriginalDescription`) e no
  lançamento; só `descriptionNormal` (a `description`) é editável.
- **Formulários**: React Hook Form + Zod; campos do form como string e conversão no submit
  (`parseAmount` aceita `1.234,56`). Use `Field`/`FieldLabel`/`FieldError` do shadcn.
- **Formatação**: sempre `lib/format.ts` (`formatCurrency`, `formatMonth`, `formatDate`...). Datas `yyyy-MM-dd`
  são tratadas como texto para evitar bug de fuso.
- **Ocultar valores** (`lib/hide-values.ts`): dinheiro na tela sempre por `const { formatCurrency } = useMoney()`
  (ou `Money`), que vira `R$ ••••` com o olhinho ligado; `formatCurrency` direto de `lib/format` só fora da tela (ex.:
  prompt da IA; campo de formulário usa `amountToInput`). Preferência lembrada no `localStorage` (só ligado/desligado).
  Botão: `HideValuesButton` no `TopBar` e `PageHeader hideValues` nas telas com dinheiro (financeiro, abas de dinheiro
  do grupo). Tela nova com valores? Use `useMoney` e passe `hideValues`.
- **Cores de significado**: `--income` (verde, ganho), `--expense` (vermelho, perda) e `--data` (azul, totais e
  indicadores: saldo, %, planejado), com contraste AA também como texto em claro e escuro. Verde e vermelho se
  confundem para quem tem daltonismo: nunca dependa só da cor — valor sempre com sinal `+`/`−` (`Money` com `type`),
  ícone de seta (`TypeIcon`) e, nos gráficos, ganho à esquerda e perda à direita com legenda. Saldo negativo:
  `text-destructive`. Botão principal é neutro (branco/preto), não verde. Tarefa/hábito feito usa `bg-income`.
  Cores de **categoria** são do usuário: use-as em marcas (bolinha, barras), nunca como cor de texto.
- **Navegação**: só em `components/layout/nav-items.ts` (seção → item com ícone → subitens, todos com `icon`;
  `dynamic` para subitens da API, em `use-dynamic-links.ts`; subitem de grupo leva `group` e mostra a foto + alfinete).
  Grupos na sidebar = `useGroupShortcuts` (`/api/groups/shortcuts`, ordem da API); o `MutationCache` do `main.tsx`
  invalida `['groups', 'shortcuts']` após qualquer mutação (atividade de grupo vem de todo módulo). A sidebar (`AppSidebar`) é fixa a partir de `lg` (o `useIsMobile` de
  `hooks/use-mobile.ts` foi ajustado para 1024px) e gaveta abaixo disso. Com a sidebar aberta o conteúdo perde
  256px: grades de 4 colunas só em `xl`. Coisa que precisa existir com a gaveta fechada (notificações, toasts)
  fica no `AppLayout`, não dentro da sidebar.
  Item com `module` some quando a pessoa o esconde no perfil (`visibleSections` + `useHiddenModules`; o Início esconde
  os cartões do módulo e não busca os dados dele). Formato da sidebar vem da conta (`preferences.sidebarIcons` no
  `defaultOpen` do `SidebarProvider`; o cookie `sidebar_state` não é mais lido): Ctrl+B e a borda trocam só até
  recarregar; o perfil (`MenuPreferences`) grava e aplica na hora.
- **PWA**: manifest e ícones em `public/`; `public/sw.js` só guarda a página offline (`offline.html`/`offline.js`).
  Nunca ponha em cache resposta de `/api` nem telas. Mudou algo que o service worker guarda? Troque o nome do
  `CACHE` (o antigo é apagado ao ativar). Instalar app, cor da barra e recarga após deploy em `lib/pwa.ts`. Atalhos do
  manifest abrem formulários por parâmetro (`/tarefas?nova=1`, `/financeiro?novo=1`): mantenha essas rotas.
  O service worker só é registrado no build; teste no container.
  **Push**: tudo em `features/notifications/push.ts` (`useDevicePush`: estado do aparelho, ativar/desativar, religar
  ao abrir o app com `auto`); nunca chame `pushManager` em outro lugar. O `sw.js` manda `{type: 'push'}` (invalida
  notificações e não lidas do chat) e `{type: 'navigate', url}` (o `main.tsx` navega pelo router) para as telas
  abertas. Texto do push vem pronto da API (`PushTexts`): notificação nova precisa de texto lá também.
- **Mobile**: teste em 375px (e confira 320px). Sem scroll horizontal na página.
  - Campo editável com fonte de 16px no celular (`text-base md:text-sm`): menor que isso o iPhone dá zoom ao focar.
  - Alvo de toque pequeno (check, alça, chevron): amplie a área com `relative after:absolute after:-inset-2` sem mudar
    o visual (ver `TaskRow`).
  - Linha com nome + data/status: só o nome trunca (`min-w-0` + `truncate` no texto, como `CategoryBadge`); data e
    status ficam inteiros.
  - Ações de página: sempre via `PageHeader` (`actions` com uma `primary`; `toolbar` para filtros como o mês).
    Não coloque botões soltos no cabeçalho.
  - Listas: tabela a partir de `md`; abaixo disso lista com `EntryListItem` (ou card) e `⋮` abrindo `ActionSheet`.
  - Formulário por item no mobile: `Drawer` com rascunho local; remonte com `key` a cada abertura (ver `ImportRowSheet`).
  - `ActionSheet` executa a ação depois de fechar; mantenha o item selecionado no estado durante o fechamento
    (`{ open, item }`) e use dialogs controlados (ex.: `ConfirmDelete` com `open`).
  - Animações com `transition-*` do Tailwind e sempre com `motion-reduce:transition-none`.
- Textos de UI em pt-BR. Estados vazios sempre com uma ação (botão para cadastrar).

## Verificação

```bash
npm run build && npm run lint
```

O CI (`.github/workflows/web.yml`) roda os dois quando muda código em `lifepedia-web/` (só `.md` não dispara).

Para testar no navegador: API no ar (compose da raiz) + `npm run dev`.
