# AGENTS.md — lifepedia-web

Leia antes o [`../AGENTS.md`](../AGENTS.md) (regras gerais, **segurança** e a regra de **documentação sempre
atualizada**). Aqui ficam só as regras do front. Mudou tela, rota, estrutura ou convenção? Atualize este arquivo e o
`README.md` desta pasta na mesma tarefa.

## Stack

React 19 · TypeScript 6 · Vite 8 · Tailwind CSS v4 (sem `tailwind.config`, tema em `src/index.css`) ·
shadcn/ui (estilo `radix-maia`: cantos bem arredondados, botões em pílula) · fonte Manrope (`@fontsource-variable/manrope`) · React Router 8 (data router) · TanStack Query 5 · React Hook Form + Zod 4 ·
Recharts 3 (via `components/ui/chart.tsx`) · dnd-kit (listas reordenáveis) · next-themes · sonner · lucide-react. Lint com oxlint.

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
- **CSP** (nginx): só scripts do próprio site. Nada de script inline, CDN, fontes/imagens externas ou
  `dangerouslySetInnerHTML`. Dependências entram pelo npm e são empacotadas pelo Vite.
- **Categorias** vêm da API (`useCategoryLookup()`); nunca fixe lista de categorias no código. Use `CategorySelect`,
  `CategoryBadge` e `ColorDot` para exibi-las.
- **Totais**: sempre do `/summary` (componente `TotalCard`); não somar no front. Totais e indicadores usam
  `components/StatCard` (card tingido pelo tom: `income`, `expense` ou `data`).
- **Listagens**: sempre paginadas e ordenadas pela API com o kit `components/data-table/`: `useListState` (página,
  tamanho, ordenação → `params` da query), `SortableHead` (cabeçalho clicável), `SortSelect` (ordenação no mobile),
  `DataTablePagination` (rodapé: intervalo, total, 10/25/50/100, páginas) e `useClampPage`. Mudou filtro/mês/aba?
  `list.resetPage()`. Nunca ordene/pagine array no front; listas completas vêm de `/all`.
  Exceção: a tela de Categorias é uma grade de cartões com a lista completa (`useCategoryLookup`), sem paginação.
  Exceção: a agenda de Tarefas (por dia) e "Sem data" vêm inteiras e já ordenadas da API; "Concluídas" é paginada.
- **Dialogs de formulário**: `DialogScrollContent` + `DialogBody` (`components/DialogLayout.tsx`): altura máxima da
  tela, cabeçalho e botões sempre visíveis, miolo com rolagem. No toque, abre sem focar o 1º campo
  (`skipAutoFocusOnTouch` de `lib/utils`; use também em `DialogContent` com campos); `autoFocus` só onde digitar é a
  ação principal (ex.: título da tarefa nova).
- **Datas**: `DatePicker` (dia) e `MonthField` (mês) de `src/components`; não use `<input type="date|month">`.
- **Imagens enviadas**: sempre por `components/image/ImagePicker` (valida tipo/tamanho, recorta em 512 px JPEG);
  `shape="rect"` para foto de produto (padrão: redondo, avatar). Envio por `FormData` via `lib/api.ts` (grupo) ou
  data URL no JSON (`imageData` do item do planejamento). A CSP permite `blob:`/`data:` só em `img-src` (prévia).
- **Estado de dialog que recomeça a cada abertura**: coloque-o num componente filho do `DialogContent` (desmonta ao
  fechar) em vez de `useEffect` com `setState` (ver `InviteDialog`, `BringItemsDialog`).
- **Login/cadastro**: `RequireAuth` guarda a página pedida em `state.from`; login, cadastro e `GuestOnly` voltam para
  ela (`returnTo`), e os links entre login e cadastro repassam o `state` (ex.: link de convite aberto sem sessão).
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
  recarregar. Sequência/% do hábito sempre da API (`streak`, `/habits`). Fixo ligado só em tarefa pessoal
  (`FixedField`); ao concluir, `FixedPaymentDialog` grava o ajuste do mês pelas rotas do financeiro (valor igual ao
  cadastrado remove o ajuste).
- **Início** (`features/home`, rota índice `/`): só lê hooks de outros módulos (`useAgenda`, `useHabits`, `useSummary`);
  não some valores nem calcule sequência no front.
- **Notificação nova**: tipo em `features/notifications/types.ts` + texto/link em `notification-content.tsx`.
  `useReminderToasts` (no `AppLayout`) mostra `TASK_REMINDER` como toast quando as não lidas sobem.
- **Conta** (`features/account`): importar manda só `format/categories/recurring/transactions/taskLists/taskTags/tasks`
  (perfil e grupos do arquivo são registro; aceita `IMPORTABLE_FORMATS`, `@1` sem tarefas); sempre prévia (`dryRun`) antes de gravar; `REPLACE` exige confirmação explícita. Excluir
  conta: senha + "EXCLUIR", e depois `logout()` (limpa o estado local).
- **Abas com muitos itens**: `components/ScrollTabsList` (com o `value` da aba) no lugar do `TabsList`: rola dentro da
  faixa no celular (a página não ganha rolagem lateral) e mantém a aba ativa à vista.
- **Ações do cabeçalho**: muitas ações? Marque as menos usadas como `secondary` (vão para o ⋮ no desktop). Excluir
  leva `destructive` (vermelho no menu).
- **Fixos**: vigência e valor do mês sempre por `features/finance/fixed.ts` (`isActiveIn`, `valueIn`).
- **Importação por IA**: o formato `lifepedia/transactions@3` é um contrato com prompts que o usuário já copiou.
  Mudou `import/prompt.ts`? Mantenha `import/parse.ts` compatível (ou crie `@4` aceitando todos; `@1` só tem
  `description`, `@2` não tem `source`/`invoiceMonth`). Prompt único para extrato e fatura: cada linha tem `source`;
  item de fatura conta no `invoiceMonth`. Trocar a origem na prévia sempre por `withSource` (recalcula o `month`). `descriptionOficial` → `originalDescription`: só leitura na revisão (`OriginalDescription`) e no
  lançamento; só `descriptionNormal` (a `description`) é editável.
- **Formulários**: React Hook Form + Zod; campos do form como string e conversão no submit
  (`parseAmount` aceita `1.234,56`). Use `Field`/`FieldLabel`/`FieldError` do shadcn.
- **Formatação**: sempre `lib/format.ts` (`formatCurrency`, `formatMonth`, `formatDate`...). Datas `yyyy-MM-dd`
  são tratadas como texto para evitar bug de fuso.
- **Cores de significado**: `--income` (verde, ganho), `--expense` (vermelho, perda) e `--data` (azul, totais e
  indicadores: saldo, %, planejado), com contraste AA também como texto em claro e escuro. Verde e vermelho se
  confundem para quem tem daltonismo: nunca dependa só da cor — valor sempre com sinal `+`/`−` (`Money` com `type`),
  ícone de seta (`TypeIcon`) e, nos gráficos, ganho à esquerda e perda à direita com legenda. Saldo negativo:
  `text-destructive`. Botão principal é neutro (branco/preto), não verde. Tarefa/hábito feito usa `bg-income`.
  Cores de **categoria** são do usuário: use-as em marcas (bolinha, barras), nunca como cor de texto.
- **Navegação**: links só em `components/layout/nav-items.ts` (desktop e mobile leem dali). Nada de rolagem
  horizontal no header: abaixo de `lg` a navegação é o `MobileNav`.
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

Para testar no navegador: API no ar (compose da raiz) + `npm run dev`.
