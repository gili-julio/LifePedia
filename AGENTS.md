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
├── docker-compose.yml     ← stack completo: db + api + web
├── .env / .env.example    ← segredos locais (.env nunca vai para git)
├── lifepedia-api/         ← Spring Boot 4 + Java 21 + PostgreSQL
├── lifepedia-web/         ← React 19 + TS + Vite + Tailwind v4 + shadcn/ui
└── lifepedia-mobile/      ← ainda vazio (stack a definir)
```

## ⚠️ Documentação sempre atualizada (obrigatório)

**Toda alteração de código só está pronta quando a documentação reflete a mudança.** Na mesma tarefa, revise e
atualize o que for afetado:

- `README.md` da raiz — módulos/funcionalidades, como rodar, portas, variáveis de ambiente.
- `AGENTS.md` da raiz — regras, contratos API ↔ Web, checklists.
- `README.md` e `AGENTS.md` de cada projeto tocado — endpoints, telas, estrutura de pastas, convenções, comandos.
- `.env.example` — toda variável de ambiente nova ou removida.

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
- **Front**: respeitar a CSP (sem scripts inline, sem CDN/recursos externos, sem `dangerouslySetInnerHTML`).
- Mudou algo de segurança? Atualize a seção **Segurança** do `README.md` da raiz e o `SecurityIntegrationTest`.

## Contrato API ↔ Web

- Base: `/api`. O front sempre chama caminhos relativos; o Vite (dev) e o nginx (container) fazem o proxy.
- Auth: `POST /api/auth/register` | `login` | `logout`.
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
  - Versão do arquivo da conta (`account/AccountDtos.FORMAT`) ↔ `features/account/types.ts` (`ACCOUNT_FORMAT`)
  - Tarefas: `Priority`, `Recurrence.Unit/MonthMode`, `TaskOccurrence.Status`, `TaskDtos.Sort/Scope` ↔ `features/tasks/types.ts`
  - DTOs da API ↔ `types.ts` de cada feature no front
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
  no envio (nomes da época). O front monta o texto por `type`.
- **Itens de grupo** (`group_transactions`/`group_recurring_entries`) guardam os dados do item: **sincronizado**
  (`source_id`) é atualizado junto com o pessoal (`GroupFinanceSync`) e some com ele (FK em cascata); **cópia**
  (`source_id` nulo) é independente; item cadastrado direto no grupo é uma cópia sem `origin_id`. A categoria é a de quem levou; nas métricas do grupo as categorias somam pelo
  nome (`categoryId` do resumo = `TIPO:nome`). Fixo sincronizado usa os ajustes mensais do pessoal.
- **Planejamento** (`/api/groups/{id}/planning`): a simulação inteira vem da API (`GET` devolve base, itens com o
  resultado de cada um, linha do tempo e totais); o front não calcula saldo nem falta. Saldo do mês = fixos do grupo
  naquele mês (com ajustes) + média dos ocasionais dos N meses completos anteriores que têm lançamentos (nenhum → mês
  atual; meses usados em `base.averageBasis`), ou o valor manual; "e se" vai por
  query (`extraIncome`/`extraExpense`), nunca é salvo. Prioridade = `position`. Busca de link externo **só** pela API
  (`SafeHttpClient`); a foto volta como data URL JPEG e é reenviada no `imageData` ao salvar o item. Loja bloqueou
  ou faltou preço/foto → `reference` (produto parecido no Buscapé); o front avisa a origem e grava `reference*` no item.
- **Conta** (`/api/account`): `GET /export` (JSON `lifepedia/account@2`, anexo; importa `@1` e `@2`), `GET /export/spreadsheet` (.xlsx),
  `POST /import` (`{mode: MERGE|REPLACE, dryRun, data}`; `dryRun` = prévia com as contagens), `GET /deletion-preview`,
  `DELETE` (`{password, confirmation: "EXCLUIR"}` → 204 + cookie apagado). No arquivo, categorias ligam por `ref` (e fixos também, para a tarefa ligada a um fixo);
  tarefas pessoais vão em `taskLists`/`taskTags`/`tasks` (ausentes = a importação não mexe nas tarefas);
  `profile` e `groups` são só registro. Mudou o formato? Crie `@3` e mantenha a importação dos anteriores (usuários guardam
  arquivos antigos). Excluir conta = `GroupService.leave` em cada grupo + apagar o usuário (cascata).
- **Tarefas** (`/api/tasks` e `/api/groups/{id}/tasks`, mesmas rotas): dono é o usuário (`user_id`) ou um grupo
  (`group_id`). A regra de repetição fica
  na tarefa (`due_date` = início); as ocorrências são **geradas na leitura** e `task_occurrences` só guarda o que mudou
  numa delas (concluída, pulada, movida, editada só nela, subtarefas marcadas). Ocorrência = `taskId` + `date` (dia pela
  regra; sem data = `null`). "Hoje" vem da API (`Agenda.today`, fuso `TIME_ZONE`); atrasadas, ordem (`sort`) e
  agrupamento por dia também. `GET /agenda` (até 62 dias) e `GET /undated` devolvem a lista inteira (limitada pelo
  máximo de tarefas abertas); `GET /history` é paginado. Editar/excluir série: `?from=` (esta e as próximas) divide a
  tarefa; "só esta" é `PUT /{id}/occurrence`/`/move`/`/status`/`/assignee`. Em grupo: responsável fixo (`assignee_id`)
  ou rodízio (`task_rotation` + `rotation_start`); o responsável de cada ocorrência vem na agenda (`assigneeId`) e a
  agenda pessoal inclui as de grupo em que a pessoa é a responsável (item com `groupId`; ações vão para a rota do
  grupo). Excluir: criador ou admin (`canDelete` no item). Comentários só em grupo.
  **Hábito** (`habit`, só com regra de dias fixos): sequência/recorde/% do mês calculados na API (`HabitStats`;
  pulada é neutra, só dia passado pendente quebra) — `streak` no item da agenda e `GET /habits`. **Lembrete**
  (`reminderMinutes` 0–1440, exige `dueTime`) vira notificação `TASK_REMINDER` pelo `TaskReminderJob` (a cada minuto).
  **Fixo** (`recurringEntryId`, só pessoal e do próprio usuário): o front abre o diálogo e grava o ajuste do mês pela
  rota do financeiro; a API de tarefas não mexe em valores.
- **Importação por IA**: `POST /api/finance/import` grava ocasionais + ajustes de fixos (tudo ou nada). O modelo de
  resposta é `lifepedia/transactions@2`: `descriptionOficial` (como no documento, só leitura, gravada em
  `original_description` e nunca alterada depois) e `descriptionNormal` (nome padronizado, editável, vira
  `description`); campo opcional `fixed` = nome do fixo (prompt em
  `lifepedia-web/src/features/finance/import/prompt.ts`, parser em `import/parse.ts`). Mudou o formato? Crie `@3`
  e mantenha o parser aceitando os anteriores (`@1` usa só `description`; usuários podem ter prompts antigos salvos).

## Checklist: novo módulo

1. **API**: migration Flyway nova `V<n>__<descricao>.sql` (nunca edite migration já aplicada), entidade estendendo
   `BaseEntity` com `userId`, repository com `findByIdAndUserId`, service `@Transactional`, controller em
   `/api/<modulo>/...`, DTOs como `record` com Bean Validation (mensagens em pt-BR).
2. **Teste** de integração (Testcontainers) cobrindo o fluxo feliz, validação e isolamento entre usuários.
3. **Web**: `src/features/<modulo>/` com `types.ts`, `api.ts` (hooks React Query com chave iniciando pelo nome do
   módulo), `components/`, `pages/`; rota lazy em `src/app/router.tsx`; seção/links em `src/components/layout/nav-items.ts`
   (vale para a barra do desktop e o menu mobile).
4. Dados iniciais para novos usuários: escute `UserRegisteredEvent` (como `CategoryService.onUserRegistered`).
   Dados do módulo dentro de grupos: tabela com `group_id` + `user_id`, acesso por `GroupAccess` e limpeza em
   `GroupMemberRemovedEvent` (quem sai leva seus itens).
5. **Conta**: FK para `users` com `ON DELETE CASCADE` (ou `SET NULL` se o dado for do grupo), arquivo em disco apagado
   quando o dono sai/é excluído, e dados pessoais do módulo no export (`account/AccountExportService`; importável?
   então também no `AccountImportService` com nova versão do formato).
6. Atualize a tabela de módulos no `README.md` da raiz e os `AGENTS.md` afetados.

## Como verificar

```bash
cd lifepedia-api && ./mvnw test          # precisa do Docker rodando (Testcontainers)
cd lifepedia-web && npm run build && npm run lint
docker compose up -d --build             # na raiz: sobe tudo e confere em http://localhost:3080
```
