# AGENTS.md — LifePedia (visão geral)

Guia para agentes de código (Claude, Codex, Copilot etc.) e pessoas novas no projeto.
Leia este arquivo e depois o `AGENTS.md` do projeto em que for trabalhar.

## O que é

Plataforma pessoal que centraliza dados da vida (finanças, metas, saúde...) em **módulos**.
Visão de produto e roadmap: [`README.md`](README.md).

## Mapa

```
LifePedia/                 ← monorepo (único repo git)
├── README.md / AGENTS.md  ← visão geral e este guia
├── docker-compose.yml     ← stack completo: db + api + web (local)
├── docker-compose.prod.yml ← produção no Coolify: sem portas, limites de memória
├── .github/workflows/     ← CI: api.yml, web.yml (cada um só quando a pasta muda), secrets.yml (gitleaks)
├── .env / .env.example    ← segredos locais (.env nunca vai para git)
├── lifepedia-api/         ← Spring Boot 4 + Java 21 + PostgreSQL
├── lifepedia-web/         ← React 19 + TS + Vite + Tailwind v4 + shadcn/ui
└── lifepedia-mobile/      ← ainda vazio (stack a definir); no celular hoje vale o PWA do web
```

## ⚠️ Documentação sempre atualizada (obrigatório)

**Toda alteração de código só está pronta quando a documentação reflete a mudança.** Na mesma tarefa, revise e
atualize o que for afetado:

- `README.md` da raiz — módulos/funcionalidades, como rodar, portas, variáveis de ambiente.
- `AGENTS.md` da raiz — regras, contratos API ↔ Web, checklists.
- `README.md` e `AGENTS.md` de cada projeto tocado — endpoints, telas, estrutura de pastas, convenções, comandos.
- `.env.example` — toda variável de ambiente nova ou removida.
- `lifepedia-web/src/features/home/changelog.ts` — toda mudança que o usuário percebe ganha uma entrada no topo
  (data, título-resumo, áreas afetadas e pontos curtos **sem termos técnicos**: o que a pessoa vê ou passa a poder
  fazer). Aparece em *Mudanças recentes no sistema* no Início.

Se nada precisou mudar na documentação, confirme isso explicitamente ao final da tarefa.

## Regras gerais

- **Idioma**: código (classes, variáveis, endpoints, colunas) em **inglês**; textos de UI, mensagens de erro
  para o usuário, comentários e documentação em **português (pt-BR)**.
- **Módulos isolados**: cada módulo tem seu pacote na API (`com.lifepedia.api.<modulo>`) e sua pasta no
  front (`src/features/<modulo>`). Não importe coisas internas de outro módulo; o que for compartilhado vai para
  `shared/` (API) ou `src/lib` / `src/components` (web).
- **Todo dado pertence a um usuário.** Toda tabela de módulo tem `user_id`, e toda consulta filtra pelo usuário
  do JWT. Buscar por id sempre como `findByIdAndUserId` → responde **404** (nunca 403) para dado de outro usuário.
  Exceção: **dados de grupo** (compartilhados) — acesso sempre por `GroupAccess.requireMember/requireAdmin`
  (não membro → 404; membro sem permissão → 403) e o item guarda `user_id` de quem levou.
- **Dinheiro**: `NUMERIC(14,2)` no banco, `BigDecimal` na API, `number` no front (só para exibição/entrada).
  Valores são sempre positivos; o `type` (`INCOME`/`EXPENSE`) define o sinal.
- **Meses** trafegam como `yyyy-MM` (ex.: `2026-09`), datas como `yyyy-MM-dd`.
- **Segredos** só por variável de ambiente (`.env` na raiz para o docker compose). Nunca commite `.env`.
- **Não faça commit/push** sem o usuário pedir.
- **Comentários: só o essencial, curtos.**
  - Javadoc/JSDoc de **uma linha** em classe/função/tipo quando o nome não basta; `@param` só se agregar.
  - Comentário de linha só para o que não é óbvio no código (um "porquê"), em poucas palavras.
  - Nada de comentário que repete o código, blocos longos, listas ou histórico de mudanças.
  - Explicação longa vai para `README.md`/`AGENTS.md`, não para o código.
  - Exceções: migrations já aplicadas (não editar) e `components/ui/*` (gerado pelo shadcn).

## Segurança (dados sensíveis — regras obrigatórias)

- **Senha**: só `PasswordEncoder` (BCrypt 12). Nunca logar, devolver ou persistir senha/token em texto puro.
- **IA**: chave só no ambiente, só na API (`shared/ai/GeminiClient`, cabeçalho, nunca na URL). Prompt montado na API, nunca
  vindo do cliente. Documento e resposta nunca vão para log nem disco; só a lista lida fica no rascunho do dono.
- **Token**: nunca guardar em `localStorage`/`sessionStorage` nem expor ao JavaScript. No navegador a sessão é o
  cookie HttpOnly `lifepedia_session`. O JWT só leva o id do usuário (`sub`) — nada de dados pessoais.
- **CSRF**: toda requisição que altera dados sem `Authorization` precisa de `X-Requested-With` (o `lib/api.ts` já envia).
- **Novas rotas públicas** (`permitAll`) exigem justificativa; o padrão é autenticado.
- **Novos dados**: sempre com `user_id` e filtro por usuário; referências a outras entidades do usuário (ex.:
  `categoryId`) precisam ser validadas como dele (`CategoryService.requireUsable`).
- **URL vinda do usuário**: a API só acessa por `shared/net/SafeHttpClient` (anti-SSRF: IP público, portas 80/443,
  tamanho e tempo limitados) e com limite por usuário. Imagem de fora é baixada e regravada pela API; o front nunca
  carrega recurso externo. Link exibido: só `http(s)`, com `rel="noopener noreferrer"`.
- **Infra**: portas só em `127.0.0.1`; containers sem root; segredos só no `.env`; nada de valor padrão para segredo.
  Mudou serviço, variável ou volume no `docker-compose.yml`? Repita no `docker-compose.prod.yml` (produção, sem
  portas publicadas, variáveis no painel do Coolify) e atualize a seção **Deploy** do `README.md`.
- **Front**: respeitar a CSP (sem scripts inline, sem CDN/recursos externos, sem `dangerouslySetInnerHTML`). Única
  exceção: o script/iframe do botão do Google (`accounts.google.com/gsi`, liberado no `nginx.conf` e carregado só por
  `features/auth/GoogleButton.tsx`).
- **Push** (`notification/push`): só para hosts de `lifepedia.push.allowed-hosts` (`https`, 443, sem redirect);
  payload sempre cifrado (`PushCrypto`); aparelho só recebe com sessão de login válida (`session_id`); sincronização
  automática (`auto`) nunca cria nem toma o aparelho de outra pessoa.
- **PWA** (`lifepedia-web/public/sw.js`): o service worker só guarda a página offline. Nunca coloque em cache
  resposta de `/api` nem telas (dados sensíveis ficariam no aparelho depois de sair).
- Mudou algo de segurança? Atualize a seção **Segurança** do `README.md` da raiz e o `SecurityIntegrationTest`.

## Contrato API ↔ Web

- Base: `/api`. O front sempre chama caminhos relativos; o Vite (dev) e o nginx (container) fazem o proxy.
- **Termos/Política**: `user/LegalTerms.VERSION` (API) ↔ `features/legal/legal.ts` `LEGAL_VERSION` (web), mesma data.
  Cadastro exige `acceptTerms: true`; `UserResponse.termsAccepted` = aceitou a versão vigente; `PUT /api/users/me/terms
  {version}` registra (versão diferente da vigente → 400). Texto mudou de forma relevante? Nova data nos dois lados
  (todos aceitam de novo). Novo dado pessoal, serviço externo ou compartilhamento ⇒ atualizar a política na mesma tarefa.
- Auth: `POST /api/auth/register` | `login` (`remember` opcional) | `google` (`{credential, remember}`) | `logout`;
  `GET /api/auth/config` (público: `googleClientId` ou nulo). Cada login cria uma linha em `user_sessions` e o JWT leva
  `sid`; o token só vale com a sessão viva (logout apaga). Sem `remember`: cookie sem `Max-Age` e `JWT_EXPIRATION` (8h);
  com: cookie e sessão de 30 dias. Conta Google: `PUT/DELETE /api/users/me/google`; `UserResponse` traz `googleLinked`
  e `hasPassword` (false = conta criada pelo Google: entra só por ele, sem @username até completar o perfil).
  - Navegador: resposta `{ expiresAt, user }` + cookie HttpOnly `lifepedia_session` (enviado sozinho pelo navegador).
  - Outros clientes (mobile): cabeçalho `X-Auth-Mode: token` → resposta inclui `token`; depois `Authorization: Bearer <token>`.
- Erros: RFC 9457 (`ProblemDetail`). O front mostra `detail`; erros de validação vêm em `errors: { campo: mensagem }`.
- **Categorias** são dados do usuário (tabela `categories`), referenciadas por `categoryId` nos lançamentos.
  As 19 padrão ficam em `DefaultCategories.java` (novos usuários) e na migration `V3` (usuários antigos).
- Espelhados manualmente — **mudou em um lado, mude no outro**:
  - `EntryType` (`INCOME`/`EXPENSE`) ↔ `lifepedia-web/src/features/finance/types.ts`
  - `NotificationType` ↔ `features/notifications/types.ts` + texto em `notification-content.tsx`
  - `GroupMember.Role` (`ADMIN`/`MEMBER`) ↔ `features/groups/types.ts`; `ShareMode` (`SYNC`/`COPY`) ↔ `features/finance/group/types.ts`
  - `PlanItem.Kind` (`PURCHASE`/`INVESTMENT`) e meses da média (0, 3, 6, 12) ↔ `features/planning/types.ts`
  - Regra do @username (`user/Usernames.java`) ↔ `features/auth/username.ts`
  - Parcela no nome ("Loja (3/10)": `finance/Installments.java`) ↔ `features/finance/installments.ts`
  - Nomes de categoria de investimento (`finance/category/InvestmentCategories.java`) ↔ textos que citam "Investir"/"Investimento" no front
  - Versão dos termos (`user/LegalTerms.VERSION`) ↔ `features/legal/legal.ts` (`LEGAL_VERSION`)
  - Versão do arquivo da conta (`account/AccountDtos.FORMAT`) ↔ `features/account/types.ts` (`ACCOUNT_FORMAT`)
  - Tarefas: `Priority`, `Recurrence.Unit/MonthMode`, `TaskOccurrence.Status`, `TaskDtos.Sort/Scope` ↔ `features/tasks/types.ts`
  - Frequência: `AttendanceDtos.DayStatus`, `CommitmentMark.Mark` ↔ `features/attendance/types.ts`
  - Notas: `NoteDtos.Status`, `NoteActivity.Action` ↔ `features/notes/types.ts`
  - Bancos: `finance/bank/Bank` (códigos e nomes), `BankKind` ↔ `src/lib/banks.ts` (+ logo em `src/assets/banks/<code>.svg`)
  - Importação: `ImportDraft.Origin/Status` ↔ `features/finance/types.ts` (`ImportDraft`); `ImportPrompt.FORMAT` ↔ `import/format.ts`
  - Financiamentos: `FinancingKind` (e prazos padrão/máximo), `ItemCondition`, `CorrectionIndex`,
    `FinancingMath.AmortizationSystem/ExtraMode`, `FinancingDtos.Scenario/CostWhen`, `FinancingCost.Frequency` ↔
    `features/financing/types.ts` (+ `MONTHS_LIMIT` em `labels.ts`)
  - Conquistas: `achievement.Achievement` (códigos) ↔ `features/friends/types.ts` + texto/ícone em `features/friends/achievements.ts`
  - DTOs da API ↔ `types.ts` de cada feature no front
- **Investimento** = despesa em categoria de despesa chamada "Investir"/"Investimento(s)" (sem acento/maiúscula;
  `InvestmentCategories`). `Totals.expense` e `MonthPoint.*Expense` vêm **sem** investimento; `invested` à parte;
  `balance` desconta os dois; `expensesByCategory` não traz as categorias de investimento. No grupo, `reserve` (resumo)
  e `totalReserve` (planejamento) = reserva informada + investimentos do grupo até o mês (`GroupSummaryService.reserve`);
  a simulação parte de `totalReserve` e o saldo previsto não desconta investimento.
- **Parcela**: `PUT /api/finance/transactions/{id}/month` e `/api/groups/{id}/finance/transactions/{id}/month`
  (`{month, following}`) mudam o `month`; `following` leva as parcelas seguintes da mesma compra (mesmo nome sem a
  parcela e mesmo total) com o mesmo deslocamento. Nunca antes do mês da `date` (400). No grupo, só cópias de quem levou.
- **Chat do grupo** (`/api/groups/{id}/chat/...`): histórico por **cursor** (`before` = id da mais antiga carregada,
  `size` até 100; resposta `{items (antiga → nova), hasMore}`), exceção à paginação por página (mensagens novas
  deslocariam as páginas). `POST/PUT/DELETE messages`, `POST read`; `GET /api/groups/chat/unread` (`{groupId: n}`) e
  `GET /api/groups/chat/stream` (SSE, evento `message` com a mensagem nova/editada/apagada; um por aba, no `AppLayout`).
  Apagar = `deleted` com `body` nulo. Não lidas = dos outros, depois da última leitura (ou de quando entrou).
- **Mês do ocasional** = campo `month` (padrão: mês da data; fatura do cartão: mês da fatura). Listagem e totais usam `month`, não `date`.
- **Totais vêm da API** (`/summary`) e consideram todos os itens, não a página; o front não soma valores salvos.
- **Listagens**: `GET /recurso` é paginado na API (`page` a partir de 0, `size` 1–100, padrão 10; `sort=campo,asc|desc`
  com campos permitidos por rota) e responde `{items, page, size, totalItems, totalPages}`. `GET /recurso/all` dá a lista
  completa (seleção, importação). **Nada de ordenar ou paginar no front.**
- **Valor de fixo no mês** = ajuste do mês (`recurring_adjustments`), se houver; senão o cadastrado. API
  (`SummaryService.amountIn`) e web (`features/finance/fixed.ts` → `valueIn`) seguem a mesma regra.
- **Usuário**: `username` obrigatório; `profileComplete=false` (conta antiga sem username) → o front bloqueia com
  aviso e leva a `/perfil` (`PUT /api/users/me`).
- **Notificações**: `NotificationService.notify(userId, type, actorId, groupId, data)`; `data` são strings gravadas
  no envio (nomes da época). O front monta o texto por `type`; o push usa o texto de `notification/push/PushTexts`
  (espelho de `notification-content.tsx`: **mudou um, mude o outro**).
- **Push** (`/api/push`): `GET /config` (`publicKey` VAPID ou nulo), `PUT /subscription`
  (`{endpoint, keys: {p256dh, auth}, auto}` → `{active}`; liga o aparelho à sessão do JWT; `auto` = religar ao abrir o
  app, só se o aparelho já era da pessoa) e `DELETE /subscription` (`{endpoint}`, só do dono). Envio por
  `PushService.send(userIds, PushMessage)` depois do commit; notificações e mensagens novas do chat já mandam. O
  payload é `{title, body, url, tag, urgent}`; o `sw.js` mostra (tag `test` aparece mesmo com o app em foco).
- **Itens de grupo** (`group_transactions`/`group_recurring_entries`) guardam os dados do item: **sincronizado**
  (`source_id`) é atualizado junto com o pessoal (`GroupFinanceSync`) e some com ele (FK em cascata); **cópia**
  (`source_id` nulo) é independente; item cadastrado direto no grupo é uma cópia sem `origin_id`. A categoria é a de quem levou; nas métricas do grupo as categorias somam pelo
  nome (`categoryId` do resumo = `TIPO:nome`). Fixo sincronizado usa os ajustes mensais do pessoal.
- **Atividade do grupo**: escrita com sucesso em `/api/groups/{groupId}/...` atualiza `user_groups.last_activity_at`
  (`GroupActivityInterceptor`; escrita que não é atividade leva `@NoGroupActivity`, ex.: fixar, prévia de link). Entrar
  no grupo também conta. `GET /api/groups/shortcuts` = até 3 grupos para a sidebar: fixados (`group_members.pinned_at`,
  até 3 por pessoa, `PUT /{id}/pin {pinned}`) e depois os de atividade mais recente. Como a atividade vem de qualquer
  módulo, o front invalida `['groups', 'shortcuts']` após toda mutação (`MutationCache` em `main.tsx`).
- **Amigos** (`/api/friends`): `friendships` com um registro por par (`PENDING` ou `ACCEPTED`; recusar, cancelar e
  desfazer apagam a linha). Pedido para quem já pediu vira amizade na hora. Perfil (`GET /{userId}`) só entre amigos
  (senão 404) e traz as conquistas sem as `ownerOnly`. Lista completa (até 200), sem paginação, como grupos. Amizades
  não entram no arquivo exportado (relação com outras pessoas, como os grupos).
- **Conquistas** (`/api/achievements`): cada nível alcançado é gravado em `user_achievements` com a data e **nunca se
  perde** (dados que diminuem não baixam o nível; a barra não fica abaixo da meta do nível). Só o próximo nível usa as
  contagens atuais; no nível máximo nada é contado. Confere depois do commit de cada ação (`AchievementProgress`,
  publicado ao concluir tarefa, lançar, entrar/criar grupo, virar amigo, importar a conta) e também ao ler (dados de
  antes da tabela). Cada código tem os valores de cada nível em `Achievement`; nível, meta e `unlockedAt` vêm da API.
  Conquista nova = valor no enum + `value(...)` em `AchievementService` + evento na ação + texto no front. Dado
  financeiro só como contagem e marcado `ownerOnly`.
- **Planejamento** (`/api/groups/{id}/planning`): a simulação inteira vem da API (`GET` devolve base, itens com o
  resultado de cada um, linha do tempo e totais); o front não calcula saldo nem falta. Saldo do mês = fixos do grupo
  naquele mês (com ajustes) + média dos ocasionais dos N meses completos anteriores que têm lançamentos (nenhum → mês
  atual; meses usados em `base.averageBasis`), ou o valor manual; "e se" vai por
  query (`extraIncome`/`extraExpense`), nunca é salvo. Prioridade = `position`. Busca de link externo **só** pela API
  (`SafeHttpClient`); a foto volta como data URL JPEG e é reenviada no `imageData` ao salvar o item. Loja bloqueou
  ou faltou preço/foto → `reference` (produto parecido no Buscapé); o front avisa a origem e grava `reference*` no item.
- **Conta** (`/api/account`): `GET /export` (JSON `lifepedia/account@5`, anexo; importa `@1` a `@5`), `GET /export/spreadsheet` (.xlsx),
  `POST /import` (`{mode: MERGE|REPLACE, dryRun, data}`; `dryRun` = prévia com as contagens), `GET /deletion-preview`,
  `DELETE` (`{password, confirmation: "EXCLUIR"}` → 204 + cookie apagado). No arquivo, categorias ligam por `ref` (e fixos também, para a tarefa ligada a um fixo);
  tarefas pessoais vão em `taskLists`/`taskTags`/`tasks` (ausentes = a importação não mexe nas tarefas);
  `profile`, `groups`, `commitments` (frequência, desde o `@3`), `notes` (desde o `@4`), `financings` e `financingSimulations` (desde o `@5`) são só registro. Mudou o formato? Crie `@6` e mantenha a importação dos anteriores (usuários guardam
  arquivos antigos). Excluir conta = `GroupService.leave` em cada grupo + apagar o usuário (cascata).
- **Tarefas** (`/api/tasks` e `/api/groups/{id}/tasks`, mesmas rotas): dono é o usuário (`user_id`) ou um grupo
  (`group_id`). A regra de repetição fica
  na tarefa (`due_date` = início); as ocorrências são **geradas na leitura** e `task_occurrences` só guarda o que mudou
  numa delas (concluída, pulada, movida, editada só nela, subtarefas marcadas). Ocorrência = `taskId` + `date` (dia pela
  regra; sem data = `null`). "Hoje" vem da API (`Agenda.today`, fuso `TIME_ZONE`); atrasadas, ordem (`sort`) e
  agrupamento por dia também. `GET /agenda` (até 62 dias) e `GET /undated` devolvem a lista inteira (limitada pelo
  máximo de tarefas abertas); `GET /history` é paginado. Editar/excluir série: `?from=` (esta e as próximas) divide a
  tarefa; "só esta" é `PUT /{id}/occurrence`/`/move`/`/status`/`/assignee`. Em grupo: responsáveis fixos
  (`task_assignees`, vários), `allMembers` (todos, resolvido na leitura) ou rodízio (`task_rotation` +
  `rotation_start`), nunca juntos; `completion` `ANY`/`EACH` (`EACH` só com mais de um; parte de cada um em
  `task_occurrence_done`, item com `doneBy`; status `DONE` quando todos fizeram). Os responsáveis de cada ocorrência vêm
  na agenda (`assignees`) e a agenda pessoal inclui as de grupo em que a pessoa é responsável (item com `groupId`;
  ações vão para a rota do grupo). `PUT /api/tasks/{id}/group {groupId}` leva a pessoal para um grupo (lista, etiquetas
  e fixo saem). `GET /conflicts` (choque de horário) e `GET/PUT /api/tasks/agenda-settings` (horas visíveis da agenda
  e `sleepMinutes` = sono de seg a dom, por pessoa; `sleepMinutes` nulo no PUT mantém o salvo). Excluir: criador ou
  admin (`canDelete` no item). Comentários só em grupo.
  **Hábito** (`habit`, só com regra de dias fixos): sequência/recorde/% do mês calculados na API (`HabitStats`;
  pulada é neutra, só dia passado pendente quebra) — `streak` no item da agenda e `GET /habits`. **Lembrete**
  (`reminderMinutes` 0–43200 = até 30 dias, exige `dueTime`) vira notificação `TASK_REMINDER` pelo `TaskReminderJob` (a cada minuto).
  **Fixo** (`recurringEntryId`, só pessoal e do próprio usuário): o front abre o diálogo e grava o ajuste do mês pela
  rota do financeiro; a API de tarefas não mexe em valores.
- **Frequência** (`/api/attendance`): compromisso pessoal (`commitments`: dias da semana `1..7`, horário, duração,
  período, `maxAbsences`, `taskId` opcional) com os dias **gerados pela regra**; `commitment_marks` guarda só a marcação
  (`PRESENT`/`ABSENT`/`CANCELED`). Sem marcação: tarefa ligada (pessoal, `TaskLinks`) concluída no dia = `PRESENT`
  (`fromTask`); dia que já terminou (fuso `TIME_ZONE`) = `PENDING`; senão `UPCOMING`. Contagem (`stats`) só até hoje (ou o
  fim) e sempre da API; `stats.upcoming` = dias `UPCOMING` de hoje até o fim (nulo sem fim). `GET` (lista completa, até 50), `POST`, `PUT/DELETE /{id}`, `GET /{id}/days` (até hoje, do mais
  novo), `PUT /{id}/days/{date} {status}` (nulo tira; presença/falta só até hoje, "sem aula" também no futuro),
  `GET /agenda?from&to` (até 62 dias, com as faltas do compromisso), `GET /tasks` (tarefas pessoais abertas para ligar) e
  `POST /import {commitment | targetId, marks}` (cria, ou usa o existente sem mudar os dados, e grava as marcações; tudo
  ou nada). O texto do SIGAA é lido só no front (`features/attendance/sigaa.ts`) e nunca vai para a API.
  Tarefa excluída: `task_id` vira nulo. Mutação de tarefa invalida `['attendance']` no front.
- **Notas** (`/api/notes` e `/api/groups/{id}/notes`, mesmas rotas): dono é o usuário ou o grupo (`NoteOwner`), como
  nas tarefas; pastas (`note_folders`) e etiquetas (`note_tags`) também. Conteúdo é **markdown** (até 200 mil
  caracteres). `GET` paginado (`status` `ACTIVE`/`ARCHIVED`/`TRASH`/`TEMPLATE`, `q` no título e no texto, `folderId`,
  `tagId`, `taskId`, `commitmentId`; `sort` `updatedAt`/`createdAt`/`title`; fixadas sempre primeiro; item sem o conteúdo,
  com `excerpt`), `GET /all` (id/título/ícone das notas em uso, para `[[Nome]]`), `GET /{id}` (com `backlinks` = notas
  que citam `[[título]]`), `POST`, `PUT /{id}` (nota inteira + `version`: diferente da atual → **409**), `PUT /{id}/pin`,
  `PUT /{id}/archive`, `DELETE /{id}` (lixeira), `POST /{id}/restore`, `DELETE /{id}/permanent` (só da lixeira),
  `GET /{id}/activity` (30 últimas; edições seguidas da mesma pessoa em 30 min viram uma linha), `POST /{id}/images`
  (multipart `file`, devolve a URL) e `GET /{id}/images/{key}`, `GET/POST/PUT/DELETE /folders` e `/tags`, `GET /labels`
  e `GET /link-options`. Modelo (`template`) só pessoal; compromisso só em nota pessoal; tarefa do mesmo dono (pela
  fachada `TaskLinks`). Lixeira: só criador ou admin; `NoteTrashJob` apaga de vez depois de 30 dias. Foto fora do
  texto há mais de 1 h é apagada ao salvar.
- **Financiamentos** (`/api/financings` e `/api/groups/{id}/financings`, mesmas rotas): dono é o usuário ou o grupo
  (`FinancingOwner`). **Todo cálculo é da API** (`FinancingMath`: SAC/Price, seguro sobre o saldo, tarifa fixa,
  correção por índice, amortizações extras e CET); o front só mostra. `POST /simulate` (nada é salvo; sem
  `customRate` usa os quartis das taxas dos bancos: `LOW`/`MEDIAN`/`HIGH`; `costs` = valor editado de cada gasto padrão
  pela chave; `scenario`/`system` escolhem a tabela devolvida), `GET /rates?kind=` (médias para preencher o contrato),
  `GET/POST /simulations` e `DELETE /simulations/{id}` (guarda só o pedido), `GET` (lista do dono), `GET /overview`
  (pessoal: todos de que a pessoa participa, inclusive de grupos), `GET/POST/PUT/DELETE /{id}`, `POST
  /{id}/extras/preview` e `/{id}/extras`, `DELETE /{id}/extras/{extraId}`, `POST/PUT/DELETE /{id}/costs[/{costId}]` e
  `PUT /{id}/my-fixed {enabled, categoryId, shareToGroup}`. Parcelas calculadas na leitura (paga = vencimento antes de
  hoje). Taxas e índices vêm do Banco Central (`BcbClient`: Olinda `ConsultaUnificada` por intervalo de datas, SGS
  7811/433/7456), guardados em `financing_rates`/`financing_index_values` pelo `FinancingRateJob` (6 h) ou na hora se
  vazio (no máximo 1 tentativa por hora); sem dados, taxa padrão de `Modality` e aviso. Partes (`financing_members`):
  pessoal = dono 100%; grupo = soma 100 (igual ao criar). O fixo da parte (`ScheduledFixed`, fachada do financeiro)
  tem o valor do 1º mês e um ajuste em cada mês diferente; é refeito a cada mudança do financiamento. Aviso
  (`reminderDays`) às 8h pelo `FinancingReminderJob` (`FINANCING_DUE`, `FINANCING_COST_DUE`).
- **Bancos**: ocasionais, fixos e os itens de grupo têm `bank` (`{code, name, kind}`, `BankRef`, embutido na entidade;
  nulo = sem banco). `code` é do catálogo `Bank` ou `OTHER` (só aí `name`, digitado); `kind` `ACCOUNT`/`CARD` (padrão
  conta). Tudo que vem de fora passa por `BankRef.clean`. Filtro nas listas e no `/summary`: `bank` (chave da linha:
  código, `OTHER:<nome em minúsculas>` ou `NONE`) + `bankKind`. `GET /api/finance/summary/banks?month&months=6` e
  `/api/groups/{id}/finance/summary/banks` devolvem uma linha por banco (mês + `history`), sempre calculadas na API
  (`BankReport`, despesas sem investimento, sem banco por último). `GET /api/finance/banks/last` (banco do item com banco
  criado por último) e `PUT /api/finance/banks/assign {transactionIds, recurringIds, bank}` (lote, tudo ou nada; `bank`
  nulo tira; sincronizados acompanham). Financiamento guarda `bank` (nome) + `bankCode`; o fixo ligado herda o banco como
  conta (nome digitado antes do catálogo vira `OTHER`). O banco **não** entra no arquivo da conta.
- **Importação pelo site** (`AI_API_KEY`): `GET /api/finance/import/assistant` (`enabled`, limites de arquivo e `quota`),
  `POST /analyze` (multipart `files`, **202**: valida, grava `import_drafts` em `RUNNING` e a API chama o Gemini em segundo
  plano; o front nunca manda prompt), `GET /prompt` (o mesmo prompt, para o agente da pessoa). Limites por conta em
  `ImportQuota` (janelas móveis, contados no banco; falha não conta, descartar não devolve): 1 a cada 4 h, 5 em 7 dias, 10 em
  30 dias. Arquivos: `ImportDocuments` (10, 15 MB somados, 50 páginas por PDF, sem senha; tipo pelo conteúdo). Terminou:
  notificação `IMPORT_READY`/`IMPORT_FAILED` (com push). **Rascunho**: uma revisão aberta por pessoa, em `/draft`: `GET`
  (204 sem revisão), `GET /{id}/content` (`rows` ou, na 1ª abertura, `result`), `POST` (revisão manual), `PUT /{id}` (a cada
  edição; `rows` é JSON do front, a API não lê), `DELETE /{id}`. Importar com `draftId` fecha a revisão na mesma transação
  (repetir → 409). Linha fechada fica sem conteúdo, só para contar os limites.
- **Importação por IA**: `POST /api/finance/import` grava ocasionais + ajustes de fixos + banco de fixos sem banco
  (`fixedBanks`, nunca troca banco já definido; tudo ou nada). O modelo de resposta é `lifepedia/transactions@4`: `descriptionOficial` (como no documento, só leitura, gravada em
  `original_description` e nunca alterada depois) e `descriptionNormal` (nome padronizado, editável, vira
  `description`); campos `source` (`statement` | `card`, origem do item: fatura vira cartão, extrato vira conta) e `bank`
  (nome do catálogo ou outro; fora da lista vira `OTHER`, com aviso) e
  opcionais `fixed` = nome do fixo e `invoiceMonth` = mês de vencimento da fatura (item `card`; vira o `month` do
  ocasional) (prompt único para extrato e fatura em
  `lifepedia-api/.../finance/importing/ImportPrompt.java`, parser em `lifepedia-web/src/features/finance/import/parse.ts`). Mudou o formato? Crie `@5`
  e mantenha o parser aceitando os anteriores (`@1` usa só `description`, `@2` não tem `source`/`invoiceMonth`, `@3` não tem
  `bank`; usuários podem ter prompts antigos salvos).

## Checklist: novo módulo

1. **API**: migration Flyway nova `V<n>__<descricao>.sql` (nunca edite migration já aplicada), entidade estendendo
   `BaseEntity` com `userId`, repository com `findByIdAndUserId`, service `@Transactional`, controller em
   `/api/<modulo>/...`, DTOs como `record` com Bean Validation (mensagens em pt-BR).
2. **Teste** de integração (Testcontainers) cobrindo o fluxo feliz, validação e isolamento entre usuários.
3. **Web**: `src/features/<modulo>/` com `types.ts`, `api.ts` (hooks React Query com chave iniciando pelo nome do
   módulo), `components/`, `pages/`; rota lazy em `src/app/router.tsx`; seção/links em `src/components/layout/nav-items.ts`
   (vale para a sidebar no desktop e na gaveta do celular).
4. Dados iniciais para novos usuários: escute `UserRegisteredEvent` (como `CategoryService.onUserRegistered`).
   Dados do módulo dentro de grupos: tabela com `group_id` + `user_id`, acesso por `GroupAccess` e limpeza em
   `GroupMemberRemovedEvent` (quem sai leva seus itens).
5. **Conta**: FK para `users` com `ON DELETE CASCADE` (ou `SET NULL` se o dado for do grupo), arquivo em disco apagado
   quando o dono sai/é excluído, e dados pessoais do módulo no export (`account/AccountExportService`; importável?
   então também no `AccountImportService` com nova versão do formato).
6. Atualize a tabela de módulos no `README.md` da raiz e os `AGENTS.md` afetados.

## Como verificar

```bash
cd lifepedia-api && ./mvnw checkstyle:check && ./mvnw test   # testes precisam do Docker (Testcontainers)
cd lifepedia-web && npm run build && npm run lint
docker compose up -d --build             # na raiz: sobe tudo e confere em http://localhost:3080
```

**CI (GitHub Actions)**, em push na `main` e PR para a `main`:

- `api.yml` (só se mudar código/config em `lifepedia-api/**`; `.md` não conta): Checkstyle, testes e build da imagem
  Docker (sem publicar).
- `web.yml` (só se mudar código/config em `lifepedia-web/**`; `.md` não conta): lint (oxlint) e build (tsc + vite).
- `secrets.yml` (sempre, inclusive docs: segredo também vaza em `.md`): gitleaks nos commits; segredo commitado falha o check.

Novo projeto (ex.: `lifepedia-mobile`) ganha o próprio workflow com filtro `paths` da pasta dele (e `!pasta/**/*.md`). Workflow com filtro
`paths` não roda quando a pasta não muda: não marque esses checks como obrigatórios na proteção da branch.
