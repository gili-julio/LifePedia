# lifepedia-api

API REST do [LifePedia](../README.md). Spring Boot 4 · Java 21 · PostgreSQL 17 · Flyway · JWT (HS256).

## Rodando

```bash
cd .. && docker compose up -d db && cd lifepedia-api     # Postgres em localhost:5440 (compose da raiz)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev    # API em http://localhost:8080, lendo ../.env
./mvnw test                                              # testes com Testcontainers (Docker precisa estar rodando)
./mvnw checkstyle:check                                  # estilo (checkstyle.xml); também roda no CI
```

O perfil `dev` (`application-dev.yml`) importa o `.env` da pasta raiz, então `JWT_SECRET` e `DB_PASSWORD` são os
mesmos do docker compose. Sem perfil e sem variáveis de ambiente a API **não sobe** (de propósito: não há segredo padrão).

> Se o Tomcat falhar com `Unable to establish loopback connection` no Windows, o `TEMP` está com caminho longo
> demais para o socket interno do Java: rode com
> `-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp`.

Imagem Docker: `docker build -t lifepedia-api .` (o `docker-compose.yml` da raiz já faz isso).

### Configuração (variáveis de ambiente)

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5440/lifepedia` | JDBC do Postgres |
| `DB_USER` | `lifepedia` | Usuário do banco |
| `DB_PASSWORD` | — (**obrigatória**) | Senha do banco |
| `JWT_SECRET` | — (**obrigatória**) | Segredo HS256, mín. 32 caracteres. Trocar invalida todas as sessões |
| `JWT_EXPIRATION` | `8h` | Validade da sessão sem "lembrar de mim" (com ele: `lifepedia.auth.remember-for`, 30 dias) |
| `COOKIE_SECURE` | `true` | Cookie de sessão só em HTTPS (navegadores aceitam em `http://localhost`) |
| `CORS_ORIGINS` | `http://localhost:5173` (no compose: localhost e 127.0.0.1, portas 3080 e 5173) | Origens liberadas (separadas por vírgula); origem fora da lista recebe 403 |
| `PORT` | `8080` | Porta HTTP |
| `TIME_ZONE` | `America/Sao_Paulo` | Fuso que define "hoje" nas tarefas (atrasadas, repetição); o container roda em UTC |
| `GOOGLE_CLIENT_ID` | vazio (login com Google desligado) | OAuth Client ID (Aplicativo da Web) do Google Cloud; público, sem secret. Passo a passo no README da raiz |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | vazio (push desligado) | Par VAPID P-256 em base64url (comando no README da raiz). A API confere na subida que formam um par |
| `VAPID_SUBJECT` | vazio | Contato para os serviços de push (`https://...` ou `mailto:`); obrigatório com as chaves |
| `STORAGE_DIR` | `data/uploads` (no compose: `/data/uploads`, volume `lifepedia-uploads`) | Pasta das imagens (`users/`, `groups/`, `plan-items/`, cada uma com `<chave>.jpg`) |

Limites de segurança ficam em `lifepedia.auth.*` no `application.yml` (tentativas por IP, bloqueio por e-mail).

## Autenticação

- **Navegador**: login/cadastro devolvem `{expiresAt, remember, user}` e o cookie `lifepedia_session`
  (HttpOnly, Secure, SameSite=Strict, Path=/api). O token nunca aparece no corpo. Sem `remember` o cookie não tem
  `Max-Age` (some ao fechar o navegador).
- **Sessões**: cada login grava uma linha em `user_sessions`; o JWT leva o `sid` e só vale enquanto ela existir e não
  vencer. `logout` apaga a sessão (o token antigo dá `401` na hora). Vencidas são apagadas às 3h30.
- **Google**: o front manda o ID token do botão oficial para `POST /api/auth/google`; a API confere assinatura (chaves
  públicas do Google), emissor, audiência (`GOOGLE_CLIENT_ID`) e validade.
- **Outros clientes** (mobile, scripts): envie `X-Auth-Mode: token` no login/cadastro para receber `token` no corpo
  e use `Authorization: Bearer <token>` depois.
- **CSRF**: requisições `POST/PUT/PATCH/DELETE` sem `Authorization` precisam do cabeçalho `X-Requested-With`
  (qualquer valor), senão `403`.
- **Limites**: 20 chamadas de login/cadastro por IP a cada 10 min (`429`); 5 senhas erradas para o mesmo e-mail
  bloqueiam esse e-mail por 15 min (`429`). Confirmação de senha (excluir conta) segue a mesma regra, por usuário.
- **Conta excluída**: as sessões somem junto (cascata); token de conta apagada → `401`.

## Endpoints

Todas as rotas, exceto `auth` (inclui `GET /api/auth/config`) e `health`, exigem sessão (cookie ou Bearer).

**Listagens paginadas** (`GET` de categorias, fixos e ocasionais, e dos itens de grupo): `page` (começa em 0), `size` (1 a 100, padrão 10)
e `sort=campo,asc|desc` (campos da lista de cada rota; outro campo → `400`). Resposta:
`{items, page, size, totalItems, totalPages}`. Para listas completas (seleção, importação) use `/all`.

### Auth / usuários

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/register` | `{name, username, email, password, acceptTerms: true}` → `201` (grava versão e data do aceite) · `400` sem o aceite · `409` se e-mail ou username já existe · cria as categorias padrão |
| POST | `/api/auth/login` | `{email, password, remember?}` → `200` · `401` se inválido · `429` se bloqueado |
| POST | `/api/auth/google` | `{credential, remember?}` (ID token do Google) → `200`. Conta vinculada entra; e-mail confirmado pelo Google de conta existente é vinculado; senão cria conta sem senha e sem @username (`profileComplete=false`). `409` se o e-mail não foi confirmado pelo Google ou a conta já tem outro Google · `401` token inválido · `404` sem `GOOGLE_CLIENT_ID` |
| GET | `/api/auth/config` | Público: `{googleClientId}` (nulo = sem botão do Google) |
| POST | `/api/auth/logout` | Encerra a sessão do token (cookie ou Bearer) e apaga o cookie → `204` |
| GET | `/api/users/me` | Usuário logado (`username`, `profileComplete`: false = falta dado obrigatório, `googleLinked`, `hasPassword`) |
| PUT / DELETE | `/api/users/me/google` | `{credential}` vincula a conta Google (`409` se já é de outro usuário ou se já há outra vinculada) · desvincula (`400` em conta sem senha) |
| PUT | `/api/users/me` | `{name, username}` — completa/edita o perfil · `409` se o username é de outro |
| PUT | `/api/users/me/terms` | `{version}` → `UserResponse` com `termsAccepted: true` (conta criada pelo Google ou termos novos) · `400` se não é a versão vigente (`LegalTerms.VERSION`) |
| PUT / DELETE | `/api/users/me/image` | Foto de perfil: multipart `file` (JPEG/PNG até 1 MB, 64–4096 px), regravada como JPEG ≤ 512 px · remover. A anterior sai do disco |
| GET | `/api/users/{id}/image?v=` | Foto de qualquer usuário (só logado); cache privado de 30 dias. `imageUrl` vem em `/me` e em todo `UserSummary` (membros, convites, autorias) |

### Conta (`/api/account`)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/export` | Arquivo JSON `lifepedia/account@3` (anexo): `profile`, `categories` (`ref`, nome, tipo, cor), `recurring` (`ref`, com `adjustments`), `transactions` (`category` = `ref`), `taskLists`/`taskTags` (`ref`, nome, cor), `tasks` (pessoais: regra, subtarefas, `list`/`tags`/`fixed` = `ref`, `occurrences` com o que mudou em cada dia) `groups` (itens da pessoa em cada grupo, só registro) e `commitments` (frequência com `marks`, só registro) · 20/h |
| GET | `/export/spreadsheet` | Os mesmos dados em `.xlsx` (abas Lançamentos, Fixos, Ajustes dos fixos, Categorias, Tarefas, Tarefas concluídas, Grupos), gerado sem biblioteca (`XlsxWriter`) |
| POST | `/import` | `{mode: MERGE\|REPLACE, dryRun, data}` (`data.format` `@1`, `@2` ou `@3`) → `{applied, created, skipped, removed, categoriesReused, syncedDetached}` (contagens com `tasks` e `taskLabels`). Tudo ou nada; `dryRun` só conta · `400` se versão/ref/tipo/mês inválidos · 10/h (prévia 60/h) |
| GET | `/deletion-preview` | Contagens pessoais + por grupo: `deletesGroup`, `newAdmin`, itens que saem |
| DELETE | `/` | `{password, confirmation: "EXCLUIR"}` → `204` + cookie apagado · `400` senha errada · `429` após 5 erros |

Importação: categoria com mesmo tipo+nome é reaproveitada (senão criada). **MERGE** não duplica ocasional igual
(descrição, tipo, categoria, valor, data e mês) nem fixo igual (descrição, tipo, categoria, valor e início); ajustes
entram só nos meses que faltam e dentro da vigência. **REPLACE** transforma os sincronizados em grupos em cópia,
apaga ocasionais e fixos pessoais e as categorias sem uso em grupo que não estão no arquivo (de tipo presente no
arquivo), e então importa. `profile` e `groups` do arquivo são ignorados.
Tarefas (`TaskTransferService`): sem `tasks` no arquivo (`@1`) nada muda nelas. Com `tasks`, **REPLACE** apaga as
tarefas, listas e etiquetas pessoais antes; **MERGE** reaproveita lista/etiqueta de mesmo nome e não duplica tarefa
igual (título, dia, horário e repetição). O fixo ligado vem pelo `ref` do fixo no mesmo arquivo.

Exclusão: em cada grupo roda o mesmo `GroupService.leave` do "Sair do grupo" (itens da pessoa saem, admin passa ao
membro mais antigo, grupo vazio é apagado com a imagem), os membros que ficam recebem `MEMBER_ACCOUNT_DELETED`, e o
usuário é apagado com a foto de perfil — FKs em cascata cuidam do resto (`user_groups.created_by` vira nulo, V13).

`username`: 3 a 30 caracteres, `a-z 0-9 . _`, sem `.`/`_` nas pontas; `@` inicial e maiúsculas são normalizados.

### Notificações

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/notifications?page=&size=` | Mais recentes primeiro (`size` até 50): `{id, type, data, groupId, imageUrl, read, createdAt}` |
| GET | `/api/notifications/unread-count` | `{count}` |
| POST | `/api/notifications/{id}/read` · `/api/notifications/read-all` | Marca como lida(s) |
| GET | `/api/push/config` | `{publicKey}` (chave VAPID para o navegador; nula = push desligado) |
| PUT | `/api/push/subscription` | `{endpoint, keys: {p256dh, auth}, auto?}` → `{active}`. Liga o aparelho à conta e à **sessão** atual (logout/sessão vencida param o envio). `auto: true` (ao abrir o app) só religa aparelho que já era da pessoa; nunca cria. Ativar de propósito manda um push de confirmação. `400` serviço de push fora da lista ou chaves inválidas · `503` push desligado |
| DELETE | `/api/push/subscription` | `{endpoint}` → `204` (só o aparelho da própria conta) |

Tipos: `GROUP_INVITE` (`data`: `actorName`, `actorUsername`, `groupName`, `inviteId`), `MEMBER_ACCOUNT_DELETED`
(`data`: `actorName`, `actorUsername`, `groupName`, `promoted` = `"true"` para quem virou admin) e, das tarefas de
grupo (`data`: `groupName`, `taskId`, `taskTitle`, `date` se houver e `actorName`/`actorUsername` de quem agiu):
`TASK_ASSIGNED` (virou responsável), `TASK_TURN` (sua vez no rodízio), `TASK_COMPLETED` (outra pessoa concluiu a
sua), `TASK_COMMENT` (+ `preview`, até 100 caracteres) e `TASK_OVERDUE` (aviso diário às 8h, sem ator).
`TASK_REMINDER` (lembrete; pessoal ou de grupo, sem ator): `taskId`, `taskTitle`, `date`, `time` (`HH:mm`), `minutes`
e `groupName` (só grupo). Amizade: `FRIEND_REQUEST` (`actorName`, `actorUsername`, `requestId`) e `FRIEND_ACCEPTED`
(`actorName`, `actorUsername`).

**Push**: toda notificação vira também push (texto em `PushTexts`, link para a tela) e cada mensagem nova do chat vai
para os outros membros (tag por grupo, a última substitui). O envio roda depois do commit, numa thread virtual, e
nunca afeta a ação; resposta `404`/`410` do serviço de push apaga a inscrição. Até 10 aparelhos por pessoa (os mais
antigos saem).

### Grupos

Não membro recebe `404`; ação de admin feita por membro comum, `403`.

| Método | Rota | Descrição |
|---|---|---|
| GET / POST | `/api/groups` | Meus grupos por nome (`{id, name, imageUrl, memberCount, role, pinned}`) · cria `{name}` (quem cria é admin) |
| GET | `/api/groups/shortcuts` | Até 3 grupos para a sidebar: fixados (na ordem em que foram fixados) e depois os de atividade mais recente |
| PUT | `/api/groups/{id}/pin` | `{pinned}` fixa/desafixa na sidebar de quem pede (até 3; o 4º → 400). Não conta como atividade |
| GET / PUT / DELETE | `/api/groups/{id}` | Detalhe com membros · renomear (admin) · excluir (admin) |
| PUT / DELETE | `/api/groups/{id}/image` | Multipart `file` (JPEG/PNG até 1 MB, 64–4096 px), regravado como JPEG ≤ 512 px · remover (admin) |
| GET | `/api/groups/{id}/image?v=` | Imagem (membros e convidados); cache privado de 30 dias (a URL muda a cada imagem) |
| POST | `/api/groups/{id}/leave` | Sair (último admin → membro mais antigo vira admin; último membro → grupo excluído) |
| DELETE | `/api/groups/{id}/members/{userId}` | Remover membro (admin) |
| PUT | `/api/groups/{id}/members/{userId}/role` | `{role: ADMIN\|MEMBER}` (admin; o grupo mantém ao menos um admin) |
| GET | `/api/groups/{id}/invite-candidates?q=` | Busca por nome/@username (admin, mín. 2 caracteres, até 10) com `status` `MEMBER`/`INVITED`/nulo |
| GET / POST | `/api/groups/{id}/invites` | Pendentes enviados · convidar `{userIds}` (até 20; ignora membros/já convidados) e notificar |
| DELETE | `/api/groups/{id}/invites/{inviteId}` | Cancelar convite (admin) |
| GET | `/api/groups/invites` · `/api/groups/invites/count` | Convites recebidos pendentes ("Solicitações") |
| POST | `/api/groups/invites/{inviteId}/accept` · `/decline` | Responder convite |
| GET / POST / DELETE | `/api/groups/{id}/invite-link` | Link ativo (`{token, expiresAt}`) · gerar novo (7 dias; invalida o anterior) · desativar |
| GET | `/api/groups/join/{token}` (+ `/image`) | Prévia do grupo pelo link (`{groupId, name, imageUrl, memberCount, member}`) |
| POST | `/api/groups/join/{token}` | Entrar pelo link (aceita convites pendentes do mesmo grupo) |

Toda escrita com sucesso em `/api/groups/{id}/...` (e entrar no grupo) atualiza a **última atividade** do grupo
(`last_activity_at`), usada nos atalhos.

### Chat do grupo (`/api/groups/{groupId}/chat`)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/messages?before=&size=` | Histórico por cursor: sem `before`, as mais recentes; `before` = id da mais antiga já carregada. `size` 1–100 (padrão 30). `{items (antiga → nova), hasMore}` |
| POST | `/messages` | `{body (até 2000), replyToId?}` → `201`. 30/min por pessoa (`429`). Quem envia fica com o chat lido |
| PUT / DELETE | `/messages/{id}` | `{body}` edita (`editedAt`) · apaga (`deleted`, `body` nulo). Só o autor (`403`); apagada não edita (`400`) |
| POST | `/read` | Marca como lido até agora → `204` (não conta como atividade) |
| GET | `/api/groups/chat/unread` | `{groupId: total}` dos meus grupos com não lidas (dos outros, depois da última leitura ou de quando entrei) |
| GET | `/api/groups/chat/stream` | SSE (`text/event-stream`) de todos os meus grupos: evento `message` com a mensagem nova, editada ou apagada. "Ping" (comentário) a cada 25 s; até 5 conexões por usuário |

Mensagem: `{id, groupId, author: UserSummary, body, replyTo: {id, author, body (até 140)} | null, createdAt, editedAt, deleted}`.
Não membro → `404` em tudo. Excluir a conta apaga as mensagens da pessoa (respostas a elas ficam sem a citação).

### Amigos (`/api/friends`) e conquistas

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/friends` | Amigos por nome (`[{user, since}]`, até 200) |
| GET | `/api/friends/candidates?q=` | Busca por nome/@username (mín. 2 caracteres, até 10) com `status` `FRIEND`/`SENT`/`RECEIVED`/nulo |
| GET | `/api/friends/requests` · `/requests/count` | Pendentes `{received, sent}` (`[{id, user, createdAt}]`) · `{count}` recebidos |
| POST | `/api/friends/requests` | `{userId}` envia o pedido (`{status: PENDING}`) ou, se a pessoa já tinha pedido, aceita (`ACCEPTED`). 30/h, até 50 sem resposta |
| POST | `/api/friends/requests/{id}/accept` | Aceitar (só quem recebeu) |
| DELETE | `/api/friends/requests/{id}` | Recusar (recebido) ou cancelar (enviado) |
| GET / DELETE | `/api/friends/{userId}` | Perfil do amigo `{user, since, achievements}` (sem as `ownerOnly`) · desfazer amizade. Não amigo → 404 |
| GET | `/api/achievements` | Minhas conquistas `[{code, value, level, maxLevel, target, unlockedAt, ownerOnly}]` (todas) |

Conquistas: cada nível alcançado fica em `user_achievements` com a data (`unlockedAt` = do nível atual) e não se perde
se os dados diminuírem; no nível máximo a contagem para. Confere depois de cada ação e ao ler. Códigos: `TASKS_DONE` (ocorrências concluídas por mim), `GROUP_TASKS_DONE` (as de
grupo), `HABIT_STREAK` (maior sequência entre os hábitos pessoais em andamento), `FRIENDS`, `GROUPS` e
`TRANSACTIONS` (lançamentos; `ownerOnly`). Os valores de cada nível ficam em `achievement/Achievement`.

### Financeiro em grupo (`/api/groups/{groupId}/finance`)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/items` | `{transactionIds, recurringIds, mode: SYNC\|COPY}` — leva itens pessoais (sincronizado repetido é ignorado) → `{added, skipped}` |
| GET | `/origins` | Meus itens pessoais que já estão no grupo (`{originId, synced}`) |
| GET | `/transactions?month=&type=&page=&size=&sort=` | Ocasionais do grupo. `sort`: `date` (padrão, desc), `description`, `category`, `member`, `amount` |
| GET | `/recurring?type=&page=&size=&sort=` | Fixos do grupo. `sort`: `amount` (padrão, desc), `description`, `category`, `member`, `day`, `start` |
| POST | `/transactions` · `/recurring` | Cadastra direto no grupo (mesmo corpo do pessoal; categoria de quem cadastra) → `201` com o item. Fica como cópia sem item pessoal |
| PUT / DELETE | `/transactions/{id}` · `/recurring/{id}` | Editar cópia (só quem levou; sincronizado → `400`) · tirar do grupo (quem levou ou admin) |
| PUT | `/transactions/{id}/month` | `{month, following?}` adianta/adia a parcela (cópia de quem levou) e, com `following`, as seguintes → `{moved}` |
| GET | `/summary?month=` | Resumo no formato do pessoal + `members: [{member, income, expense, invested, balance}]` + `reserve: {manual, invested, total}` (reserva do planejamento + investimentos do grupo até o mês); categorias somadas pelo nome (`categoryId` = `TIPO:nome`) |
| GET | `/summary/history?until=&months=` | Série mensal do grupo (1 a 24) |
| GET | `/summary/category-items?category=&month=` | Itens da categoria (chave do resumo) agrupados pela descrição |

Itens de grupo trazem `category {id, name, color}` (de quem levou), `member {id, name, username}`, `synced` e `mine`.
Ao **criar** fixo ou ocasional, `groupIds` (até 20) + `shareMode` (padrão `SYNC`) já levam o item para esses grupos.

### Planejamento em grupo (`/api/groups/{groupId}/planning`)

| Método | Rota | Descrição |
|---|---|---|
| GET | `?scenario=&extraIncome=&extraExpense=` | Simulação completa: `settings`, `base` (de onde vem o saldo do mês), `outcome` (total, falta, ponto mais baixo, saldo final), `timeline` mês a mês, `items` (por prioridade, com `considered`, `shortfall`, `earliestMonth`, `monthlySaving`/`installmentAmount`, `overdue`) e `acquired`. `scenario` simula o cenário em vez dos ativos; `extra*` = "e se" (não salvo) |
| POST / PUT / DELETE | `/items` · `/items/{id}` | Item `{name, kind: PURCHASE|INVESTMENT, amount, targetMonth, installments (1–120), notes, active, url, linkTitle, linkSite, linkPrice, referenceSite, referenceTitle, referenceUrl, referencePrice, imageData, removeImage}`. `reference*` = produto parecido de onde vieram preço/foto. `imageData` = data URL JPEG/PNG (até 1 MB). Editar/excluir: quem cadastrou ou admin |
| PUT | `/items/{id}/active` | `{active}` — liga/desliga na simulação (qualquer membro) |
| PUT | `/items/order` | `{itemIds}` — nova prioridade (os demais vão para o fim) |
| POST / DELETE | `/items/{id}/acquire` | `{date, amount, launch, categoryId}` — marca como comprado/feito; `launch` lança despesa no grupo (em parcelas, se parcelado) · desfaz |
| GET | `/items/{id}/image` | Foto do item (membros; cache privado com `?v=`) |
| PUT | `/settings` | `{reserve, averageMonths: 0|3|6|12, manualBalance}` — base da simulação (qualquer membro). Resposta (e `settings` da simulação) traz também `invested` (investimentos do grupo até o mês atual) e `totalReserve` = `reserve + invested`, de onde a simulação parte |
| GET / POST | `/scenarios` | Lista · cria `{name, itemIds}` (`itemIds` nulo = ativos agora; até 20 por grupo) |
| PUT / DELETE | `/scenarios/{id}` | Edita nome/itens (qualquer membro) · exclui (quem criou ou admin) |
| POST | `/scenarios/{id}/apply` | Deixa ativos só os itens do cenário |
| GET | `/scenarios/compare?extraIncome=&extraExpense=` | `outcome` dos itens ativos e de cada cenário |
| POST | `/link-preview` | `{url}` → `{url, title, titleFromUrl, price, currency, site, imageData, blocked, reference}`. A API busca a página (anti-SSRF, 20 por usuário / 10 min); loja que bloqueia vem com `blocked` e nome tirado do link (ou do destino da página de verificação). Faltou preço ou foto → `reference` = `{site, title, url, price, stores, store, imageData}` de produto parecido no Buscapé (nulo se nada parecido) |
| POST | `/price-reference` | `{name}` → `{reference}` — mesmo que acima, pelo nome (sem link); conta no mesmo limite |

Simulação: saldo do mês = fixos do grupo no mês (vigência + ajustes) + média de (ganhos − despesas) ocasionais dos
`averageMonths` meses completos anteriores **que têm lançamentos** (nenhum → mês atual; lista em `base.averageBasis`)
(ou `manualBalance`) + "e se". Itens ativos entram por prioridade: cada um
só usa o que sobra depois dos anteriores (`shortfall` = falta a mais que ele causa). Mostra ao menos 24 meses (até o
fim do último pagamento, máx. 20 anos) e simula mais 5 anos para achar `earliestMonth`. Até 100 itens por grupo.

### Tarefas (`/api/tasks` e `/api/groups/{groupId}/tasks`)

Mesmas rotas para as tarefas pessoais (`/api/tasks`) e as de um grupo (`/api/groups/{groupId}/tasks`, só membros —
quem não é recebe 404). Ocorrência = `taskId` + `date` (dia pela regra; `null` na tarefa sem data). Abaixo, `/api/tasks`
vale para as duas bases.

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/tasks/agenda?from=&to=&overdue=&list=&tag=&assignee=&sort=` | `{today, overdue, days: [{date, items}]}` de `from` a `to` (até 62 dias). `overdue=true` traz as pendentes antes de `from` (repetidas: últimos 90 dias). `sort`: `MANUAL` (padrão), `PRIORITY`, `TIME`; pendentes antes das concluídas; puladas não aparecem. Pessoal sem `list`/`tag`: inclui as ocorrências de grupo em que a pessoa é a responsável (item com `groupId`, depois das pessoais na ordem manual). `assignee` (grupo): só as desse responsável |
| GET | `/api/tasks/undated?list=&tag=&assignee=&sort=` | Pendentes sem data (mesma regra da agenda) |
| GET | `/api/tasks/conflicts?date=&time=&duration=&people=&task=` | Choque de horário: pendentes de `date` que se cruzam com `time` + `duration` (nula = 30 min; sem duração contam 30) → `[{taskId, date, title, dueTime, durationMinutes, groupId, people}]`. Pessoal: a agenda da pessoa (com as de grupo em que é responsável). Grupo: as do grupo com algum de `people` (ids separados por vírgula; `people` da resposta = quem tem o choque) ou sem responsável e, se quem consulta está em `people`, também a agenda pessoal dele. `task` sai da conta · `400` duração fora de 1 a 1440 ou mais de 50 pessoas |
| GET / PUT | `/api/tasks/agenda-settings` | Horas visíveis na agenda `{dayStart, dayEnd, sleepMinutes}` (0–23 e 1–24, pelo menos 4 horas; padrão 6 e 23), da pessoa (vale nos grupos). `sleepMinutes`: sono de segunda a domingo (7 valores, 0–960 min, padrão 480); nulo no PUT mantém o salvo |
| GET | `/api/tasks/history?page=&size=&sort=` | Concluídas (paginado). `sort`: `completedAt` (padrão, desc). Cada item traz `completedDay` (dia da conclusão no fuso `TIME_ZONE`) e `dayCount` (concluídas nesse dia, contando todas as páginas) |
| GET | `/api/tasks/habits` | Hábitos abertos: `{taskId, title, listId, groupId, repeat, start, streak, best, monthRate, monthDone, monthDue, days: [{date, state}]}` (7 últimos dias; `state` `DONE\|SKIPPED\|MISSED\|PENDING` ou nulo se o dia não é da regra) |
| GET | `/api/tasks/{id}` | Tarefa completa (regra, subtarefas) para editar |
| POST | `/api/tasks` | `{title, notes, priority, dueDate, dueTime, durationMinutes, listId, tagIds, habit, repeat, subtasks: [{id?, title}], reminderMinutes, recurringEntryId, assignees, allMembers, rotation, completion}` (os quatro últimos só em grupo; `recurringEntryId` só pessoal) |
| PUT | `/api/tasks/{id}?from=` | Sem `from`: todas as ocorrências. Com `from`: esta e as próximas — a série acaba antes do dia e uma nova começa (resposta = a nova) |
| PUT | `/api/tasks/{id}/occurrence` | Só esta: `{date, moveTo, title, notes, dueTime, priority}` |
| PUT | `/api/tasks/{id}/move` | `{date, to}` — muda o dia só desta (`to` nulo = sem data, só sem repetição) |
| PUT | `/api/tasks/{id}/status` | `{date, status: DONE\|SKIPPED\|PENDING}` (pular só em repetida) |
| PUT | `/api/tasks/{id}/overdue` | `{status: DONE\|SKIPPED}` para todas as atrasadas da tarefa → `{updated}` |
| PUT | `/api/tasks/{id}/subtasks/{subtaskId}/check` | `{date, done}` — checklist da ocorrência |
| PUT | `/api/tasks/{id}/assignee` | Grupo: `{date, assigneeId}` — uma pessoa só nesta ocorrência (`null` volta ao da tarefa; sem repetição vira a responsável da tarefa) |
| PUT | `/api/tasks/{id}/group` | Pessoal: `{groupId}` leva a tarefa para o grupo (membro; senão `404`); quem levou vira a responsável; lista, etiquetas e fixo saem; ocorrências e subtarefas ficam → tarefa do grupo · `400` pela rota do grupo ou grupo no limite |
| DELETE | `/api/tasks/{id}?scope=THIS\|FOLLOWING\|ALL&date=` | Só esta (= pular), esta e as próximas, ou a tarefa inteira (padrão). Grupo: só quem criou ou admin (403) |
| GET | `/api/tasks/{id}/comments?page=&size=&sort=` | Grupo: comentários paginados (`sort`: `createdAt`, padrão desc) `{id, author, body, createdAt, canDelete}` |
| POST / DELETE | `/api/tasks/{id}/comments[/{commentId}]` | Grupo: `{body}` (até 2000; 1000 por tarefa) · apagar: quem escreveu ou admin (403) |
| PUT | `/api/tasks/order` | `{taskIds}` — as listadas trocam de posição entre si, na ordem dada |
| GET | `/api/tasks/labels` | `{lists, tags}` com `openCount` |
| POST / PUT / DELETE | `/api/tasks/lists[/{id}]` e `/api/tasks/tags[/{id}]` | `{name, color}` · `409` se nome repetido · excluir desliga das tarefas |

`repeat`: `{unit: DAY|WEEK|MONTH|YEAR, interval: 1–999, weekdays: [1–7] (semanal), monthMode: DAY_OF_MONTH|LAST_DAY|WEEKDAY
(mensal), fromCompletion, until | count}`. Precisa de `dueDate` (início). "A partir da conclusão" tem uma ocorrência
por vez (`dueDate` avança ao concluir/pular: hoje + intervalo; desfazer só a última). Limites: 2000 tarefas abertas,
50 subtarefas, 10 etiquetas por tarefa, 50 listas, 100 etiquetas. Horário exige dia; duração exige horário; hábito
exige repetição em dias fixos (não "a partir da conclusão").

- **Hábito** (`HabitStats`, olhando até 400 dias): cada dia da regra é feito, pulado, não feito (passou pendente) ou
  pendente (hoje). `streak` = feitos desde o último não feito (pulado e hoje pendente não contam nem quebram);
  `best` = maior sequência; `monthRate` = feitos ÷ (feitos + não feitos) no mês (nulo sem dias). O item da agenda traz
  `streak` (nulo se não é hábito). Dividir a série cria outra tarefa: a sequência recomeça.
- **Lembrete** (`reminderMinutes` 0–43200, ou seja, até 30 dias; exige `dueTime`; tirar o horário apaga): `TaskReminderJob` roda a cada
  minuto (fuso `TIME_ZONE`) e avisa as ocorrências pendentes cujo horário menos os minutos caiu na janela; mover ou
  editar só esta usa o dia e o horário novos. Destino: pessoal → dono; grupo → responsável da ocorrência ou todos os
  membros. API parada: recupera no máximo 10 min. Cada tarefa gera as ocorrências só até onde o lembrete dela alcança
  (1 dia para os curtos; 7, 15 ou 30 dias para os antecipados).
- **Fixo** (`recurringEntryId`): só em tarefa pessoal e só fixo do próprio usuário (`400` senão); excluir o fixo
  desliga (`SET NULL`). O valor é registrado pelo front em `PUT /api/finance/recurring/{id}/adjustments/{month}`.

Em grupo: qualquer membro cria, edita, conclui e comenta; o item traz `groupId`, `assignees` (responsáveis da
ocorrência: trocado só nela, vez do rodízio, todos os membros com `allMembers` ou os fixos), `allMembers`, `rotating`,
`completion`, `doneBy`, `createdBy`, `completedBy`, `commentCount` e `canDelete`. `assignees` (até 50), `allMembers` e
`rotation` (2 a 50 membros, sem repetir; precisa de repetição) não vão juntos. `completion: EACH` (só com mais de um
responsável ou `allMembers`; senão vira `ANY`): `status DONE` de um responsável grava só a parte dele (`doneBy`) e a
ocorrência fecha quando todos fizeram; `PENDING` desfaz só a parte de quem pede; quem não é responsável conclui para
todos. Lembrete e aviso de atrasada vão para os responsáveis que não fizeram a sua parte. Rodízio: a ocorrência de índice *i* é de
`rotation[(rotation_start + i) mod n]`; "a partir da conclusão" avança `rotation_start` a cada ocorrência resolvida;
"esta e as próximas" continua a vez de onde parou. Ao concluir/pular, o responsável fica gravado na ocorrência. Quem sai
do grupo deixa de ser responsável, sai dos rodízios e leva os comentários (as tarefas ficam). Aviso diário das
atrasadas: `TaskOverdueJob` (8h no fuso `TIME_ZONE`). Agendamentos ligam com `lifepedia.scheduling.enabled` (padrão
`true`; `false` nos testes).

### Frequência (`/api/attendance`)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/attendance` | Compromissos da pessoa (lista completa, até 50, por nome) com `stats` `{presences, absences, pending, canceled, remaining, upcoming}` contados até hoje (ou até o fim); `upcoming` = encontros que ainda vão acontecer, de hoje até o fim (sem os já marcados; nulo sem data de fim) e `task` ligada |
| POST / PUT | `/api/attendance` · `/{id}` | `{name, color, weekdays [1..7], time "HH:mm", durationMinutes 5–720, startDate, endDate?, maxAbsences 0–999, taskId?}` · início até 5 anos atrás, período até 5 anos · `taskId` de tarefa pessoal da pessoa (senão 404) |
| DELETE | `/api/attendance/{id}` | Apaga com as marcações (a tarefa fica) → `204` |
| GET | `/api/attendance/{id}/days` | Dias que já chegaram, do mais novo: `{date, status, fromTask}` |
| PUT | `/api/attendance/{id}/days/{date}` | `{status: PRESENT \| ABSENT \| CANCELED \| null}` (nulo tira) · dia fora da regra → 400 · presença/falta depois de hoje → 400 |
| GET | `/api/attendance/agenda?from=&to=` | Dias dos compromissos no intervalo (até 62 dias) com `status`, `taskId` e as faltas do compromisso |
| GET | `/api/attendance/tasks` | Tarefas pessoais em aberto com data, para ligar |
| POST | `/api/attendance/import` | `{commitment, marks: [{date, status}]}` cria com as marcações; `{targetId, marks}` grava no existente (dados do compromisso não mudam; a marcação nova vale sobre a antiga) · até 500 dias · dia inválido → 400 e nada é gravado |

`status` do dia: marcação gravada; senão `PRESENT` com `fromTask` se a tarefa ligada foi concluída naquele dia (pela
regra); senão `PENDING` se já terminou (hoje: depois de `time + durationMinutes`, fuso `TIME_ZONE`) ou `UPCOMING`.

### Financeiro

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/finance/categories?type=&page=&size=&sort=` | Página de categorias, com `usageCount`. `sort`: `name` (padrão), `type`, `usage` |
| GET | `/api/finance/categories/all?type=INCOME\|EXPENSE` | Todas, sem paginação |
| POST | `/api/finance/categories` | `{name, type, color: "#rrggbb"}` · `409` se nome repetido no tipo |
| PUT | `/api/finance/categories/{id}` | Atualiza (tipo só muda se não houver lançamentos) |
| DELETE | `/api/finance/categories/{id}?replaceWith={id}` | Exclui; se em uso, `replaceWith` (mesmo tipo) é obrigatório e recebe os lançamentos (`409` sem ele). Não exclui a última do tipo |
| GET | `/api/finance/recurring?type=&page=&size=&sort=` | Página de fixos. `sort`: `amount` (padrão, desc), `description`, `category`, `day`, `start` |
| GET | `/api/finance/recurring/all?type=` | Todos, sem paginação |
| POST | `/api/finance/recurring` | Cria fixo; `removeTransactionIds` (opcional) exclui na mesma transação os ocasionais que ele substitui ("tornar fixo") |
| PUT | `/api/finance/recurring/{id}` | Atualiza fixo |
| DELETE | `/api/finance/recurring/{id}` | Exclui fixo |
| PUT | `/api/finance/recurring/{id}/adjustments/{yyyy-MM}` | `{amount}` — valor real do fixo naquele mês (cria ou substitui; mês precisa estar na vigência) |
| DELETE | `/api/finance/recurring/{id}/adjustments/{yyyy-MM}` | Volta o mês ao valor cadastrado |
| GET | `/api/finance/transactions?month=&type=&page=&size=&sort=` | Página de ocasionais do mês (padrão: atual). `sort`: `date` (padrão, desc), `description`, `category`, `amount` |
| GET | `/api/finance/transactions/all?month=` ou `?from=&to=&type=&description=` | Todos do período, sem paginação; `description` compara sem maiúsculas/espaços nas pontas |
| GET | `/api/finance/transactions/descriptions` | Até 200 descrições dos últimos 12 meses, as mais usadas primeiro (padronização da importação) |
| POST / PUT / DELETE | `/api/finance/transactions[/{id}]` | CRUD de ocasionais |
| PUT | `/api/finance/transactions/{id}/month` | `{month, following?}` adianta/adia a parcela ("Loja (3/10)"); `following` move as seguintes da mesma compra com o mesmo deslocamento (sincronizadas nos grupos também) → `{moved}` · `400` antes do mês da compra ou `following` sem parcela no nome |
| POST | `/api/finance/transactions/batch` | `{items: [...]}` (1 a 500) — lote de ocasionais, tudo ou nada; erro indica a linha |
| POST | `/api/finance/import` | `{transactions: [...], fixedAdjustments: [{recurringEntryId, month, amount}]}` — importação de extrato/fatura, tudo ou nada |
| GET | `/api/finance/summary?month=2026-09` | Comparativo do mês (fixos x ocasionais, % comprometido, `investedPercent`, categorias). `Totals` = `{income, expense, invested, balance}`: despesa em categoria "Investir"/"Investimento(s)" vai para `invested`, fica fora de `expense` e de `expensesByCategory` e desconta do `balance` |
| GET | `/api/finance/summary/categories/{categoryId}?month=` | Itens da categoria no mês: fixos + ocasionais agrupados pela descrição (`{description, fixed, count, total}`) |
| GET | `/api/finance/summary/history?until=2026-09&months=6` | Série mensal (1 a 24 meses), com `invested` por mês |

Exemplo de fixo:

```json
{
  "description": "Aluguel",
  "type": "EXPENSE",
  "categoryId": "5f0c…",
  "amount": 1800.00,
  "dayOfMonth": 10,
  "startMonth": "2026-01",
  "endMonth": null,
  "active": true,
  "notes": null
}
```

Um fixo entra no mês `M` quando `active` e `startMonth <= M <= endMonth` (fim nulo = sem fim), com o valor ajustado
do mês, se houver. A listagem de fixos traz `adjustments: [{month, amount}]`.

Ocasionais aceitam `originalDescription` (até 200, opcional): a descrição como veio no documento importado. Só é
gravada na criação (`POST` e importação); o `PUT` a ignora e ela volta sempre na resposta.

Erros seguem RFC 9457 (`application/problem+json`), com `errors: {campo: mensagem}` em falhas de validação.

## Estrutura

```
src/main/java/com/lifepedia/api/
├── config/        SecurityConfig (JWT, cookie, CORS, BCrypt), SecurityFilters (CSRF, rate limit), WebConfig
│                  (interceptors), propriedades
├── shared/        BaseEntity, exceções, handler global, CurrentUser, RateLimiter, Paging/PageResponse,
│                  image/ (validação e gravação de imagens), net/SafeHttpClient (busca de URL do usuário, anti-SSRF)
├── auth/          cadastro, login/logout, sessões no banco (UserSession, limpeza diária), cookie (SessionCookies),
│                  emissão de token, login/vínculo com Google (GoogleTokenVerifier), bloqueio por tentativas,
│                  confirmação de identidade
├── account/       exportar (JSON + XlsxWriter), importar (MERGE/REPLACE) e excluir a conta
├── user/          entidade User (@username), /api/users/me (ver/editar perfil, foto em UserImageStore, aceite dos termos:
│                 LegalTerms), UserRegisteredEvent
├── notification/  notificações (tipo + dados JSON), contador de não lidas; push/ (Web Push: inscrições, PushCrypto
│                 aes128gcm + VAPID, PushTexts, envio pelo PushGateway)
├── group/         grupos, membros, convites, link de convite, imagens (GroupImageStore), GroupAccess,
│                  GroupMemberRemovedEvent, GroupDeletedEvent, atividade (GroupActivityInterceptor), atalhos/fixar
├── friend/        pedidos de amizade, amigos, perfil do amigo
├── chat/          chat do grupo: mensagens (cursor), leitura/não lidas, stream SSE (ChatStreams, em memória)
├── achievement/   conquistas (Achievement: níveis; AchievementService: contagens + níveis gravados; AchievementProgress)
├── task/         tarefas: regra de repetição (Recurrence, pura), ocorrências, agenda (e choque de horário),
│                 horas visíveis e sono da agenda (AgendaPreference), listas/etiquetas, grupo (responsáveis, Completion)
│                 (responsável, rodízio, comentários, avisos, aviso diário de atrasadas), hábitos (HabitStats),
│                 lembretes (TaskReminderJob), export/import (TaskTransferService), TaskLinks (leitura para a frequência)
├── attendance/    frequência: compromissos (Commitment), marcações por dia (CommitmentMark), contagem e agenda
├── planning/      planejamento do grupo: itens, cenários, base, simulação (PlanningSimulator), prévia de link,
│                  preço de referência (PriceReferenceService + ProductMatcher)
└── finance/
    ├── EntryType, YearMonthConverter, Installments (parcela no nome: adiantar/adiar)
    ├── category/      categorias do usuário, padrões (DefaultCategories), exclusão com migração, InvestmentCategories
    ├── recurring/     lançamentos fixos e ajustes mensais (valor real)
    ├── importing/     importação de extrato/fatura (ocasionais + ajustes)
    ├── transaction/   lançamentos ocasionais
    ├── summary/       comparativo e histórico
    └── group/         itens em grupos (sincronizados/cópias), sync com o pessoal, métricas do grupo
src/main/resources/
├── application.yml, application-dev.yml
└── db/migration/   V1 users · V2 finance · V3 categorias do usuário · V4 ajustes mensais de fixos · V5 mês do lançamento
                    · V6 descrição original · V7 username · V8 grupos · V9 itens de grupo · V10 notificações
                    · V11 planejamento · V12 preço de referência do item · V13 criador do grupo anulável
                    · V14 tarefas · V15 tarefas de grupo (responsável, rodízio, comentários)
                    · V16 lembrete e fixo do financeiro nas tarefas · V17 foto de perfil
                    · V18 atividade/fixar grupo e amizades · V19 níveis de conquista
                    · V20 sessões, Google e observações de 10 mil · V21 chat do grupo
                    · V22 vários responsáveis, "cada um conclui", horas da agenda
                    · V23 push · V24 aceite dos termos · V25 sono por dia na agenda
                    · V26 frequência · V27 lembrete até 30 dias
```
